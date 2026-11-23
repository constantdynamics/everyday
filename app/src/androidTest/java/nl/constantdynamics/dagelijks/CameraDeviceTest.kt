package nl.constantdynamics.dagelijks

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.Lens
import nl.constantdynamics.dagelijks.storage.LocalFileTree
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.screens.CameraTags
import nl.constantdynamics.dagelijks.ui.screens.OVERLAY_SLIDER_DESCRIPTION
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

/**
 * The real camera screen on a real (emulated) phone: the photo that ends up on disk,
 * the question before replacing, the volume buttons, the flash and above all whether
 * the overlay of the previous photo coincides with the live image.
 *
 * The app runs on a plain folder of its own here, so no system folder picker is
 * involved; that part has its own test.
 */
@RunWith(AndroidJUnit4::class)
class CameraDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as DagelijksApp
    private val folder = File(app.getExternalFilesDir(null), "cameratest/Dagelijks")
    private val today: LocalDate = LocalDate.now()

    @get:Rule(order = 0)
    val ownFolder = object : ExternalResource() {
        override fun before() {
            folder.deleteRecursively()
            folder.mkdirs()
            runBlocking { app.container.useTreeForTesting(LocalFileTree(folder)) }
        }
    }

    @get:Rule(order = 1)
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 3)
    val screenshotOnFailure = DeviceFixtures.ScreenshotOnFailure()

    private val repository: ProjectRepository
        get() = (app.container.folder.value as FolderState.Ready).repository

    // --- Small steps --------------------------------------------------------------------------

    private fun createProject(name: String, lens: Lens, ratio: AspectRatio) {
        runBlocking { repository.createProject(name, lens, ratio) }
        waitForText(name)
    }

    private fun waitForText(text: String, timeoutMillis: Long = 30_000) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openCamera(project: String) {
        compose.onNodeWithContentDescription("Maak de foto van vandaag voor $project").performClick()
        waitForShutter()
    }

    private fun waitForShutter() {
        compose.waitUntil(40_000) {
            runCatching { compose.onNodeWithTag(CameraTags.SHUTTER).assertIsEnabled() }.isSuccess
        }
    }

    private fun shoot() {
        compose.onNodeWithTag(CameraTags.SHUTTER).performClick()
    }

    /**
     * Presses a hardware key the way a finger would. A key goes to whichever window has
     * the focus, so the app must really be in front first.
     */
    private fun pressKey(keyCode: Int) {
        DeviceFixtures.dismissSystemDialogs()
        compose.waitUntil(15_000) {
            DeviceFixtures.dismissSystemDialogs()
            compose.activity.hasWindowFocus()
        }
        DeviceFixtures.device.pressKeyCode(keyCode)
    }

    private fun photoFile(project: String, day: LocalDate = today) = File(folder, "$project/$day.jpg")

    private fun jpegsIn(project: String): List<String> =
        File(folder, project).list().orEmpty().filter { it.endsWith(".jpg") }.sorted()

    private fun hasFrontCamera() = app.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)

    // --- The photo on disk --------------------------------------------------------------------

    @Test
    fun everyShapeAndBothCamerasGiveAnUprightPhotoOfTheRightShape() {
        val combinations = buildList {
            AspectRatio.entries.forEach { add(Lens.BACK to it) }
            if (hasFrontCamera()) {
                add(Lens.FRONT to AspectRatio.PORTRAIT_3_4)
                add(Lens.FRONT to AspectRatio.PORTRAIT_9_16)
                add(Lens.FRONT to AspectRatio.LANDSCAPE_16_9)
            }
        }
        val pixels = mutableMapOf<Pair<Lens, AspectRatio>, Int>()
        combinations.forEachIndexed { index, (lens, ratio) ->
            val name = "Proef $index"
            createProject(name, lens, ratio)
            openCamera(name)
            Thread.sleep(600) // let the live image appear for the screenshot
            DeviceFixtures.screenshot("camera-${lens.id}-${ratio.id.replace(':', 'x')}")
            shoot()
            waitForText("Klaar")

            val file = photoFile(name)
            assertTrue("$lens ${ratio.id}: no photo at ${file.path}", file.isFile)
            assertEquals(listOf("$today.jpg"), jpegsIn(name))

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            Log.i("DagelijksTest", "$lens ${ratio.id}: stored ${bounds.outWidth} x ${bounds.outHeight}, ${file.length()} bytes")
            val shape = bounds.outWidth.toDouble() / bounds.outHeight
            assertTrue(
                "$lens ${ratio.id}: stored as ${bounds.outWidth} x ${bounds.outHeight}",
                abs(shape - ratio.value) < 0.004,
            )
            assertTrue("a photo should have a serious number of pixels", bounds.outWidth >= 480 && bounds.outHeight >= 480)
            assertEquals("$lens ${ratio.id}: a landscape project must come out wider than high", ratio.isLandscape, bounds.outWidth > bounds.outHeight)
            pixels[lens to ratio] = bounds.outWidth * bounds.outHeight

            // Upright in its pixels, dated, and without anything that says where or with what it was taken.
            val exif = ExifInterface(file)
            assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
            assertNotNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertTrue(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)!!.startsWith(today.toString().replace('-', ':')))
            assertNull(exif.latLong)
            listOf(
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
            ).forEach { assertNull("$it must not be stored", exif.getAttribute(it)) }

            compose.onNodeWithText("Klaar").performClick()
            waitForText("Nieuw project")
            runBlocking { repository.deleteProject(name) }
            compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
        }

        // A 16:9 photo is taken with the phone on its side and must use the sensor just as
        // fully as a 9:16 photo taken upright. Fewer pixels would mean it was cut out of an
        // image that was still standing upright.
        for (lens in Lens.entries) {
            val tall = pixels[lens to AspectRatio.PORTRAIT_9_16] ?: continue
            val wide = pixels[lens to AspectRatio.LANDSCAPE_16_9] ?: continue
            assertEquals("$lens: 16:9 and 9:16 should have the same number of pixels", tall, wide)
        }
    }

    // --- One photo per day, volume buttons ----------------------------------------------------

    @Test
    fun aSecondPhotoOnTheSameDayOnlyReplacesAfterAYes_andTheVolumeButtonsShoot() {
        createProject("Tuin", Lens.BACK, AspectRatio.PORTRAIT_3_4)
        openCamera("Tuin")

        // First photo of the day, taken with the volume-up button: stored without any question.
        pressKey(KeyEvent.KEYCODE_VOLUME_UP)
        waitForText("Klaar")
        val file = photoFile("Tuin")
        assertTrue(file.isFile)
        val first = file.readBytes()

        // Again, now with volume-down: the app asks before touching today's photo.
        compose.onNodeWithText("Opnieuw").performClick()
        waitForShutter()
        pressKey(KeyEvent.KEYCODE_VOLUME_DOWN)
        waitForText("Foto vervangen?")
        DeviceFixtures.screenshot("camera-vraag-vervangen")
        compose.onNodeWithText("Nee, bewaar de oude").performClick()
        waitForShutter()
        assertArrayEquals("saying no must leave today's photo untouched", first, file.readBytes())

        // And once more with the button on the screen, this time saying yes.
        shoot()
        waitForText("Foto vervangen?")
        compose.onNodeWithText("Ja, vervang").performClick()
        waitForText("Klaar")
        assertEquals(listOf("$today.jpg"), jpegsIn("Tuin"))
        assertTrue("nothing half-written may be left behind", File(folder, "Tuin").list()!!.none { it.startsWith(".bezig-") })
        assertEquals(1, repository.project("Tuin")!!.photos.size)
    }

    // --- Flash --------------------------------------------------------------------------------

    @Test
    fun theFlashIsOffEveryTimeTheCameraOpens() {
        createProject("Tuin", Lens.BACK, AspectRatio.PORTRAIT_3_4)
        openCamera("Tuin")
        assumeTrue(
            "this camera has no flash to test",
            compose.onAllNodesWithTag(CameraTags.FLASH).fetchSemanticsNodes().isNotEmpty(),
        )
        fun flashIs(state: String) =
            compose.onAllNodesWithContentDescription("Flitser staat $state", substring = true).fetchSemanticsNodes().isNotEmpty()

        assertTrue("the flash must start switched off", flashIs("uit"))
        compose.onNodeWithTag(CameraTags.FLASH).performClick()
        compose.waitUntil(5_000) { flashIs("aan") }

        // Leave and come back: off again.
        compose.onNodeWithContentDescription("Sluiten").performClick()
        waitForText("Nieuw project")
        openCamera("Tuin")
        assertTrue("switching the flash on must not be remembered", flashIs("uit"))
    }

    // --- The overlay --------------------------------------------------------------------------

    private fun setOverlay(percent: Int) {
        compose.onNodeWithContentDescription(OVERLAY_SLIDER_DESCRIPTION)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(percent.toFloat()) }
        compose.waitForIdle()
        Thread.sleep(400)
    }

    private fun viewfinder(): Bitmap = compose.onNodeWithTag(CameraTags.VIEWFINDER).captureToImage().asAndroidBitmap()

    /** A part of the viewfinder, in fractions of its width and height. */
    private class Part(val name: String, val fromX: Float, val toX: Float, val fromY: Float, val toY: Float) {
        fun of(viewfinder: Bitmap) = DeviceFixtures.grey(viewfinder, fromX, toX, fromY, toY)
    }

    private val halves = listOf(
        Part("left half", 0.04f, 0.5f, 0.05f, 0.95f),
        Part("right half", 0.5f, 0.96f, 0.05f, 0.95f),
        Part("top half", 0.05f, 0.95f, 0.04f, 0.5f),
        Part("bottom half", 0.05f, 0.95f, 0.5f, 0.96f),
    )

    /**
     * The emulator's camera looks into a room with a television that keeps changing.
     * Only a part of the picture that stands still can be compared with a photo taken a
     * moment ago. Of the halves that stand still, the one with the most detail in it is
     * used: that is where a shift of the overlay would show most clearly.
     */
    private fun stillPartWithMostDetail(live: List<Bitmap>, label: String): Part? {
        class Judged(val part: Part, val stillness: Double, val detail: Double)

        val judged = halves.map { part ->
            val moments = live.map(part::of)
            val first = moments.first()
            // As still as its least still moment.
            val stillness = moments.drop(1).minOf { DeviceFixtures.likeness(first, it) }
            // How clearly a shift of 8 steps shows, in the direction in which it shows least.
            val detail = 1 - maxOf(DeviceFixtures.likeness(first, first, dx = 8), DeviceFixtures.likeness(first, first, dy = 8))
            Judged(part, stillness, detail)
        }
        Log.i("DagelijksTest", "overlay $label: " + judged.joinToString { "${it.part.name} stillness ${it.stillness} detail ${it.detail}" })
        return judged.filter { it.stillness > 0.97 }.maxByOrNull { it.detail }?.part
    }

    private fun overlayAlignmentFor(ratio: AspectRatio, name: String): DeviceFixtures.Alignment {
        createProject(name, Lens.BACK, ratio)
        openCamera(name)
        Thread.sleep(1500) // let exposure settle
        shoot()
        waitForText("Klaar")
        compose.onNodeWithText("Klaar").performClick()
        waitForText("Nieuw project")

        // Pretend that photo was yesterday's, so that it becomes the overlay for today.
        assertTrue(photoFile(name).renameTo(photoFile(name, today.minusDays(1))))
        runBlocking { repository.refresh() }
        waitForText("Vandaag nog niet")

        openCamera(name)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag(CameraTags.OVERLAY).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(1500)
        val shape = ratio.id.replace(':', 'x')
        DeviceFixtures.screenshot("overlay-$shape-35")

        setOverlay(100)
        DeviceFixtures.screenshot("overlay-$shape-100")
        val previousPhoto = viewfinder()

        setOverlay(0)
        DeviceFixtures.screenshot("overlay-$shape-0")
        // Three moments spread over a few seconds, to be sure what moves and what does not.
        val live = viewfinder()
        Thread.sleep(1200)
        val liveLater = viewfinder()
        Thread.sleep(1200)
        val liveLatest = viewfinder()

        val part = stillPartWithMostDetail(listOf(live, liveLater, liveLatest), ratio.id)
        assumeTrue("${ratio.id}: the camera scene is moving everywhere; nothing to compare", part != null)
        val liveImage = part!!.of(live)
        val overlay = part.of(previousPhoto)

        val alignment = DeviceFixtures.bestAlignment(liveImage, overlay)
        val shifted = maxOf(
            DeviceFixtures.likeness(liveImage, overlay, dx = 8),
            DeviceFixtures.likeness(liveImage, overlay, dy = 8),
        )
        Log.i(
            "DagelijksTest",
            "overlay ${ratio.id}: compared on the ${part.name}; best fit at dx=${alignment.dx} dy=${alignment.dy}, " +
                "likeness ${alignment.likeness}; unshifted ${DeviceFixtures.likeness(liveImage, overlay)}, shifted by 8 at most $shifted",
        )
        // The comparison must be able to notice a shift, or it proves nothing.
        assertTrue("${ratio.id}: the scene is too plain to judge alignment on", alignment.likeness - shifted > 0.03)

        compose.onNodeWithContentDescription("Sluiten").performClick()
        waitForText("Nieuw project")
        runBlocking { repository.deleteProject(name) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
        return alignment
    }

    /**
     * The stored photo, laid over the live image, must be exactly the picture that the
     * viewfinder shows: same cut, same size, same way up.
     */
    private fun assertOverlayCoincides(ratio: AspectRatio) {
        val alignment = overlayAlignmentFor(ratio, "Uitlijning")
        assertTrue("${ratio.id}: the overlay does not look like the live image (likeness ${alignment.likeness})", alignment.likeness > 0.9)
        // One step is 1/120 of the compared part, well under half a percent of the picture's width.
        assertTrue(
            "${ratio.id}: the overlay is shifted by ${alignment.dx}, ${alignment.dy} steps",
            abs(alignment.dx) <= 1 && abs(alignment.dy) <= 1,
        )
    }

    // 3:4 uses the whole sensor; the other shapes are cut out of it, and the cut of the stored
    // photo must be exactly the cut that the viewfinder shows. 16:9 is shot with the screen
    // turned on its side, where the camera image has to be turned along with it.

    @Test
    fun theOverlayCoincidesWithTheLiveImage_3x4() = assertOverlayCoincides(AspectRatio.PORTRAIT_3_4)

    @Test
    fun theOverlayCoincidesWithTheLiveImage_9x16() = assertOverlayCoincides(AspectRatio.PORTRAIT_9_16)

    @Test
    fun theOverlayCoincidesWithTheLiveImage_1x1() = assertOverlayCoincides(AspectRatio.SQUARE)

    @Test
    fun theOverlayCoincidesWithTheLiveImage_16x9() = assertOverlayCoincides(AspectRatio.LANDSCAPE_16_9)

    @Test
    fun theOverlaySettingIsRememberedPerProject() {
        createProject("Regelaar", Lens.BACK, AspectRatio.PORTRAIT_3_4)
        openCamera("Regelaar")
        shoot()
        waitForText("Klaar")
        compose.onNodeWithText("Klaar").performClick()
        waitForText("Nieuw project")
        assertTrue(photoFile("Regelaar").renameTo(photoFile("Regelaar", today.minusDays(1))))
        runBlocking { repository.refresh() }
        waitForText("Vandaag nog niet")

        openCamera("Regelaar")
        waitForText("35%")
        setOverlay(60)
        compose.onNodeWithContentDescription("Sluiten").performClick()
        waitForText("Nieuw project")

        // It is in project.json, and it is back when the camera opens again.
        compose.waitUntil(10_000) { repository.project("Regelaar")?.settings?.overlayPercent == 60 }
        assertTrue(File(folder, "Regelaar/project.json").readText().contains("\"overlayZichtbaarheid\": 60"))
        openCamera("Regelaar")
        waitForText("60%")
    }
}
