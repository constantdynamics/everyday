import com.android.build.api.artifact.SingleArtifact
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Properties
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.process.ExecOperations
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The app is always signed with one fixed key, so that every new build installs
 * over the previous one without having to uninstall first.
 *
 * The key lives outside the repository, in the user's home folder. It is created
 * automatically on the first build; the random password is stored next to it and
 * never ends up in version control. Both files must be backed up together.
 */
abstract class FixedSigningKey : ValueSource<Map<String, String>, FixedSigningKey.Parameters> {

    interface Parameters : ValueSourceParameters {
        val directory: Property<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): Map<String, String> {
        val directory = File(parameters.directory.get())
        val propertiesFile = File(directory, PROPERTIES_NAME)
        val storeFile = File(directory, STORE_NAME)

        if (!propertiesFile.isFile && !storeFile.isFile) {
            create(directory, storeFile, propertiesFile)
        } else if (!propertiesFile.isFile || !storeFile.isFile) {
            // Never overwrite half a key: that would silently make updates impossible.
            throw GradleException(
                "De sleutelmap is onvolledig: ${directory.absolutePath}\n" +
                    "Daar horen twee bestanden te staan: $STORE_NAME en $PROPERTIES_NAME.\n" +
                    "Zet je reservekopie van deze map terug. Heb je die niet, verwijder dan de " +
                    "hele map; er wordt dan een nieuwe sleutel gemaakt en je moet de app één " +
                    "keer van je telefoon verwijderen voordat je hem opnieuw installeert " +
                    "(je foto's blijven staan).",
            )
        }

        val properties = Properties()
        propertiesFile.inputStream().use { properties.load(it) }
        return mapOf(
            "storeFile" to storeFile.absolutePath,
            "storePassword" to properties.getProperty("storePassword"),
            "keyAlias" to properties.getProperty("keyAlias"),
            "keyPassword" to properties.getProperty("keyPassword"),
        )
    }

    private fun create(directory: File, storeFile: File, propertiesFile: File) {
        check(directory.isDirectory || directory.mkdirs()) {
            "De sleutelmap kon niet worden aangemaakt: ${directory.absolutePath}"
        }
        val password = randomPassword()
        val windows = System.getProperty("os.name").lowercase().contains("win")
        val keytool = File(System.getProperty("java.home"), if (windows) "bin/keytool.exe" else "bin/keytool")
        val temporaryStore = File(directory, "$STORE_NAME.tmp")
        temporaryStore.delete()

        val output = ByteArrayOutputStream()
        val result = execOperations.exec {
            // The password travels through the environment, not the command line.
            environment("DAGELIJKS_SLEUTEL_WACHTWOORD", password)
            commandLine(
                keytool.absolutePath,
                "-genkeypair", "-noprompt",
                "-keystore", temporaryStore.absolutePath,
                "-storetype", "PKCS12",
                "-storepass:env", "DAGELIJKS_SLEUTEL_WACHTWOORD",
                "-keypass:env", "DAGELIJKS_SLEUTEL_WACHTWOORD",
                "-alias", KEY_ALIAS,
                "-keyalg", "RSA",
                "-keysize", "4096",
                "-validity", "36500",
                "-dname", "CN=Dagelijks, O=Persoonlijk gebruik, C=NL",
            )
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0 || !temporaryStore.isFile) {
            temporaryStore.delete()
            throw GradleException("De ondertekensleutel kon niet worden gemaakt:\n$output")
        }

        propertiesFile.writeText(
            "# Dagelijks: de vaste ondertekensleutel van jouw app.\n" +
                "# Bewaar deze hele map goed en maak er een reservekopie van.\n" +
                "# Deel hem met niemand en zet hem nooit in versiebeheer.\n" +
                "storePassword=$password\n" +
                "keyAlias=$KEY_ALIAS\n" +
                "keyPassword=$password\n",
        )
        check(temporaryStore.renameTo(storeFile)) {
            "De nieuwe sleutel kon niet op zijn plek worden gezet in ${directory.absolutePath}"
        }

        println()
        println("================================================================")
        println(" Er is een nieuwe ondertekensleutel voor Dagelijks aangemaakt in:")
        println("   ${directory.absolutePath}")
        println(" Maak een reservekopie van deze map (bijvoorbeeld op een usb-stick).")
        println("================================================================")
        println()
    }

    private fun randomPassword(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val random = SecureRandom()
        return (1..32).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private companion object {
        const val STORE_NAME = "dagelijks-sleutel.p12"
        const val PROPERTIES_NAME = "sleutel.properties"
        const val KEY_ALIAS = "dagelijks"
    }
}

/**
 * Fails the build when the merged manifest (the app's own manifest plus everything
 * the libraries add) contains anything that breaks the privacy promises: an
 * unexpected permission, backups switched on, or missing backup exclusion rules.
 * It runs with every build and prints its findings.
 */
abstract class PrivacyCheck : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val applicationId: Property<String>

    @TaskAction
    fun check() {
        val android = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(mergedManifest.get().asFile)

        fun names(tag: String): List<String> {
            val nodes = document.getElementsByTagName(tag)
            return (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(android, "name") }
        }

        val requested = (names("uses-permission") + names("uses-permission-sdk-23")).distinct().sorted()
        // Added by AndroidX itself: a private, signature-level permission that only this
        // app can hold. It guards the app's own internal broadcasts and grants no access.
        val internal = applicationId.get() + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val allowed = setOf("android.permission.CAMERA", internal)
        val application = document.getElementsByTagName("application").item(0) as Element

        val problems = mutableListOf<String>()
        (requested - allowed).forEach { problems += "onverwachte permissie: $it" }
        if ("android.permission.CAMERA" !in requested) problems += "de CAMERA-permissie ontbreekt"
        if (application.getAttributeNS(android, "allowBackup") != "false") problems += "allowBackup staat niet op false"
        if (application.getAttributeNS(android, "hasFragileUserData") != "true") problems += "hasFragileUserData staat niet op true"
        if (application.getAttributeNS(android, "dataExtractionRules").isEmpty()) problems += "dataExtractionRules ontbreekt"
        if (application.getAttributeNS(android, "fullBackupContent").isEmpty()) problems += "fullBackupContent ontbreekt"
        if (application.getAttributeNS(android, "usesCleartextTraffic") != "false") problems += "usesCleartextTraffic staat niet op false"

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Privacycontrole (${variantName.get()}) MISLUKT:\n" + problems.joinToString("\n") { "  - $it" },
            )
        }
        logger.lifecycle(
            "Privacycontrole (${variantName.get()}) geslaagd: geen INTERNET of andere netwerkpermissie, " +
                "enige toestelpermissie is CAMERA, allowBackup=false, back-upregels sluiten alles uit.",
        )
    }
}

// Default: <home>/Dagelijks-sleutel. Can be pointed elsewhere with the Gradle property
// "dagelijks.sleutelmap" or the environment variable DAGELIJKS_SLEUTELMAP.
val signingKeyDirectory: Provider<String> = providers.gradleProperty("dagelijks.sleutelmap")
    .orElse(providers.environmentVariable("DAGELIJKS_SLEUTELMAP"))
    .orElse(providers.systemProperty("user.home").map { File(it, "Dagelijks-sleutel").absolutePath })

val fixedSigningKey: Map<String, String> = providers.of(FixedSigningKey::class) {
    parameters.directory.set(signingKeyDirectory)
}.get()

android {
    namespace = "nl.constantdynamics.dagelijks"
    compileSdk {
        // Compose needs the 37.1 APIs or newer; 37.2 is the current stable platform.
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "nl.constantdynamics.dagelijks"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // The interface is Dutch only; keep the Dutch texts of the libraries and drop the rest.
        localeFilters += listOf("nl")
    }

    signingConfigs {
        create("vast") {
            storeFile = file(fixedSigningKey.getValue("storeFile"))
            storeType = "pkcs12"
            storePassword = fixedSigningKey.getValue("storePassword")
            keyAlias = fixedSigningKey.getValue("keyAlias")
            keyPassword = fixedSigningKey.getValue("keyPassword")
        }
    }

    buildTypes {
        // Debug (the green ▶ in Android Studio) and release use the same fixed key and
        // the same applicationId, so either one can be installed over the other.
        debug {
            signingConfig = signingConfigs.getByName("vast")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("vast")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Lets the JVM tests render real pixels, so screens can be checked without a phone.
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("dagelijks.screenshots", layout.buildDirectory.dir("schermafbeeldingen").get().asFile.absolutePath)
                it.maxHeapSize = "2g"
            }
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercase() }
        val privacyCheck = tasks.register<PrivacyCheck>("controleerPrivacy$suffix") {
            group = "verification"
            description = "Controleert het samengevoegde manifest van de ${variant.name}-build op privacy-eisen."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            variantName.set(variant.name)
            applicationId.set(variant.applicationId)
        }
        // No APK is produced without passing the check.
        tasks.matching { it.name == "package$suffix" || it.name == "assemble$suffix" }
            .configureEach { dependsOn(privacyCheck) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)


    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
