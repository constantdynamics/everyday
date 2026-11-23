package nl.constantdynamics.dagelijks

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import nl.constantdynamics.dagelijks.ui.screens.CameraTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.regex.Pattern

/**
 * The whole first-use story through the real system folder picker and the phone's
 * real storage: pick Documents, get a hidden "Dagelijks" folder, make a project,
 * take a photo, check the files on disk, check that the gallery does not see them,
 * and finally "reinstall" and get everything back by picking the folder again.
 */
@RunWith(AndroidJUnit4::class)
class FolderDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as DagelijksApp
    private val device = DeviceFixtures.device
    private val today: LocalDate = LocalDate.now()

    private val mainFolder = "/sdcard/Documents/Dagelijks"

    // One word: the shell commands of the test framework cannot quote a name with a space in it.
    private val project = "Gezicht"

    @get:Rule(order = 0)
    val freshStart = object : ExternalResource() {
        override fun before() {
            shell("rm -rf $mainFolder")
            shell("mkdir -p /sdcard/Documents")
            forgetChosenFolder()
        }

        override fun after() {
            shell("rm -rf $mainFolder")
            shell("rm -f /sdcard/Pictures/dagelijks-controle.jpg")
        }
    }

    @get:Rule(order = 1)
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shell(command: String): String = device.executeShellCommand(command)

    /** What a reinstall does to the app: its own memory of the folder and the access to it are gone. */
    private fun forgetChosenFolder() {
        app.getSharedPreferences("opslag", Context.MODE_PRIVATE).edit().clear().commit()
        app.contentResolver.persistedUriPermissions.forEach {
            app.contentResolver.releasePersistableUriPermission(it.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        runBlocking { app.container.reloadFolder() }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 30_000) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Presses a button of the system folder picker, which is another app. */
    private fun pressInPicker(label: String) {
        val button = device.wait(Until.findObject(By.text(Pattern.compile("(?i)$label"))), 20_000)
        if (button == null) {
            DeviceFixtures.screenshot("mapkiezer-niet-gevonden-${label.replace(' ', '-')}")
            throw AssertionError("The folder picker shows no \"$label\" button")
        }
        button.click()
    }

    private fun pickDocumentsFolder(screenshotName: String) {
        waitForText("Kies een map")
        compose.onNodeWithText("Kies een map").performScrollTo().performClick()
        device.waitForIdle()
        Thread.sleep(1500)
        DeviceFixtures.screenshot(screenshotName)
        pressInPicker("use this folder")
        pressInPicker("allow")
        waitForText("Nieuw project", 40_000)
    }

    @Test
    fun firstUseThroughTheRealFolderPicker_andEverythingComesBackAfterAReinstall() {
        // --- First start: choose where the photos go ------------------------------------------
        DeviceFixtures.screenshot("welkom")
        pickDocumentsFolder("mapkiezer")

        // Documents was picked; the app made its own, hidden folder inside it.
        val root = shell("ls -a $mainFolder")
        assertTrue("no .nomedia in $mainFolder: $root", root.lines().any { it.trim() == ".nomedia" })

        // --- A project and a photo, all through the Storage Access Framework ------------------
        compose.onNodeWithText("Nieuw project").performScrollTo().performClick()
        waitForText("Maak project")
        compose.onNode(hasSetTextAction()).performTextInput(project)
        compose.onNodeWithText("Achterkant").performClick()
        compose.onNodeWithText("Maak project").performScrollTo().performClick()
        waitForText("Maak timelapse")

        val settings = shell("cat $mainFolder/$project/project.json")
        Log.i("DagelijksTest", "project.json on disk: $settings")
        assertTrue(settings, settings.contains("\"naam\": \"$project\""))
        assertTrue(settings, settings.contains("\"camera\": \"achter\""))
        assertTrue(settings, settings.contains("\"beeldverhouding\": \"3:4\""))

        compose.onNodeWithText("Maak de foto van vandaag").performClick()
        compose.waitUntil(40_000) { runCatching { compose.onNodeWithTag(CameraTags.SHUTTER).assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag(CameraTags.SHUTTER).performClick()
        waitForText("Klaar")
        compose.onNodeWithText("Klaar").performClick()
        waitForText("Vandaag gelukt")
        DeviceFixtures.screenshot("galerij-na-eerste-foto")

        val files = shell("ls -a $mainFolder/$project").lines().map { it.trim() }.filter { it.isNotEmpty() && it != "." && it != ".." }
        assertEquals("files in the project folder: $files", setOf("project.json", "$today.jpg"), files.toSet())
        val size = shell("stat -c %s $mainFolder/$project/$today.jpg").trim().toLong()
        assertTrue("the stored photo is only $size bytes", size > 20_000)

        // --- The gallery and Google Photos must not see the photo -----------------------------
        // A control picture in the ordinary Pictures folder proves that the media scan does run.
        shell("cp $mainFolder/$project/$today.jpg /sdcard/Pictures/dagelijks-controle.jpg")
        shell("content call --uri content://media --method scan_volume --arg external_primary")
        shell("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/dagelijks-controle.jpg")
        var images = ""
        for (attempt in 1..20) {
            images = shell("content query --uri content://media/external/images/media --projection _data")
            if (images.contains("dagelijks-controle.jpg")) break
            Thread.sleep(1000)
        }
        Log.i("DagelijksTest", "images known to the gallery: $images")
        assertTrue("the control picture should be visible to the gallery: $images", images.contains("dagelijks-controle.jpg"))
        assertFalse("photos in the Dagelijks folder must stay out of the gallery: $images", images.contains("Documents/Dagelijks"))

        // --- "Reinstall": the app forgets everything, the folder stays -------------------------
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForText("Nieuw project")
        forgetChosenFolder()
        waitForText("Kies een map")

        pickDocumentsFolder("mapkiezer-na-herinstallatie")

        // Same folder picked again: the project, its settings and its photo are back.
        waitForText(project)
        waitForText("Vandaag gelukt")
        waitForText("1 foto")
        DeviceFixtures.screenshot("start-na-herinstallatie")
        assertEquals(listOf("Dagelijks"), shell("ls /sdcard/Documents").lines().map { it.trim() }.filter { it == "Dagelijks" })
    }
}
