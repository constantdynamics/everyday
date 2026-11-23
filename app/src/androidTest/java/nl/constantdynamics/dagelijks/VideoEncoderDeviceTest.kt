package nl.constantdynamics.dagelijks

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.Lens
import nl.constantdynamics.dagelijks.core.TimelapseMath
import nl.constantdynamics.dagelijks.storage.LocalFileTree
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.timelapse.RenderState
import nl.constantdynamics.dagelijks.timelapse.TimelapseRenderer
import nl.constantdynamics.dagelijks.timelapse.TimelapseRequest
import nl.constantdynamics.dagelijks.timelapse.VideoEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** The timelapse, made by the phone's real video encoder and then taken apart again. */
@RunWith(AndroidJUnit4::class)
class VideoEncoderDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val colours = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.WHITE, Color.MAGENTA)

    @Test
    fun stillPicturesBecomeAPlayableH264Video() {
        val file = File(context.cacheDir, "encoder-test.mp4").apply { delete() }
        val speed = 10
        val framesPerPhoto = TimelapseMath.framesPerPhoto(speed)
        val picture = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)

        VideoEncoder(file, 720, 1280, TimelapseMath.videoFps(speed), TimelapseMath.bitRate(720, 1280)).use { encoder ->
            var frame = 0
            for (colour in colours) {
                picture.eraseColor(colour)
                encoder.setPicture(picture)
                repeat(framesPerPhoto) {
                    encoder.writeFrame(TimelapseMath.presentationTimeMicros(frame, speed) * 1000L)
                    frame++
                }
            }
            encoder.finish()
        }

        val video = DeviceFixtures.inspectVideo(file.path)
        assertEquals(1, video.tracks)
        assertEquals("video/avc", video.mime)
        assertEquals(720, video.width)
        assertEquals(1280, video.height)
        assertEquals(colours.size * framesPerPhoto, video.frames)
        // Six photos at ten per second: 0.6 seconds, give or take one video frame.
        assertEquals(600_000.0, video.durationMicros.toDouble(), 70_000.0)

        // Every photo is really in there, in order, each in the middle of its own time slot.
        colours.forEachIndexed { index, colour ->
            val middle = (index * 100_000L) + 50_000L
            val seen = DeviceFixtures.colourInVideo(file.path, middle)
            assertTrue("photo $index: expected ${Integer.toHexString(colour)}, saw ${Integer.toHexString(seen)}", DeviceFixtures.roughlySame(colour, seen))
        }
    }

    @Test
    fun aWholeProjectIsRenderedIntoItsTimelapsesFolder() = runBlocking {
        val root = File(context.cacheDir, "timelapse-test/Everyday").apply {
            deleteRecursively()
            mkdirs()
        }
        val tree = LocalFileTree(root)
        val repository = ProjectRepository(tree, tree.pickedFolderId)
        repository.createProject("Proef", Lens.BACK, AspectRatio.PORTRAIT_9_16)
        val firstDay = LocalDate.of(2026, 11, 1)
        val photoCount = 12
        repeat(photoCount) { index ->
            val source = File(context.cacheDir, "bron.jpg")
            source.writeBytes(DeviceFixtures.solidJpeg(900, 1600, colours[index % colours.size]))
            repository.savePhoto("Proef", firstDay.plusDays(index.toLong()), source)
        }
        val project = repository.project("Proef")!!
        assertEquals(photoCount, project.photos.size)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val renderer = TimelapseRenderer(context, scope)
        val speed = 6
        renderer.start(TimelapseRequest("Proef", project.photos, AspectRatio.PORTRAIT_9_16, speed, showDate = true), repository)
        val result = withTimeout(180_000) { renderer.state.first { it is RenderState.Finished || it is RenderState.Failed } }

        assertTrue("rendering failed: $result", result is RenderState.Finished)
        result as RenderState.Finished
        assertEquals(photoCount, result.photosUsed)
        assertEquals(0, result.photosSkipped)

        val expectedName = "Proef_2026-11-01_tot_2026-11-12.mp4"
        assertEquals(expectedName, result.timelapse.name)
        val file = File(root, "Proef/timelapses/$expectedName")
        assertTrue("the video must be in the project's timelapses folder", file.isFile)
        assertEquals(listOf(expectedName), File(root, "Proef/timelapses").list()!!.toList())

        val video = DeviceFixtures.inspectVideo(file.path)
        Log.i("DagelijksTest", "timelapse rendered at ${video.width}x${video.height}, ${video.frames} frames, ${file.length()} bytes")
        // 1080 on the short side; a phone whose encoder cannot do that falls back to 720.
        assertTrue("size ${video.width}x${video.height}", (video.width == 1080 && video.height == 1920) || (video.width == 720 && video.height == 1280))
        assertEquals("video/avc", video.mime)
        assertEquals(photoCount * TimelapseMath.framesPerPhoto(speed), video.frames)
        assertEquals(photoCount * 1_000_000.0 / speed, video.durationMicros.toDouble(), 70_000.0)

        // Nothing is left behind in the app's private work folder.
        assertTrue(File(context.cacheDir, "timelapse").listFiles().orEmpty().isEmpty())

        // The third photo is blue; its slot is at 2/6 to 3/6 of a second.
        val seen = DeviceFixtures.colourInVideo(file.path, 420_000L)
        assertTrue("saw ${Integer.toHexString(seen)}", DeviceFixtures.roughlySame(Color.BLUE, seen, tolerance = 70))
    }
}
