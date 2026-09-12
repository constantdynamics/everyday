plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Ondertekenen gebeurt met een keystore die buiten de repo blijft. Zonder deze
// omgevingsvariabelen levert een release-build een ongetekende APK op; zie README.
val keystoreBestand: String? = System.getenv("EVERYDAY_KEYSTORE_FILE")

android {
    namespace = "nl.constantdynamics.everyday"
    compileSdk = 37

    defaultConfig {
        applicationId = "nl.constantdynamics.everyday"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreBestand != null) {
            create("release") {
                storeFile = file(keystoreBestand)
                storePassword = System.getenv("EVERYDAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EVERYDAY_KEY_ALIAS")
                keyPassword = System.getenv("EVERYDAY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Bewust geen applicationIdSuffix: een afwijkende applicationId is voor
            // Android een andere app, waardoor MediaStore-eigendom van de foto's
            // en de SAF-toestemming op de backupmap niet meeverhuizen.
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // AGP 9 heeft ingebouwde Kotlin-ondersteuning; de losse kotlin-android-plugin
    // hoort hier niet meer bij. De Kotlin-bytecode volgt automatisch de doelversie
    // hieronder, dus die hoeft niet apart te worden ingesteld.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
}
