package nl.constantdynamics.dagelijks.framework

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.constantdynamics.dagelijks.core.PixelSize
import nl.constantdynamics.dagelijks.image.ImageLoader
import nl.constantdynamics.dagelijks.timelapse.FrameComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.time.LocalDate

/** Loading photos for the screen, and composing the pictures of the timelapse. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class ImagePipelineTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val upright = Fixtures.jpeg(Fixtures.quarters(300, 400))

    /** A photo copied in from elsewhere: stored on its side, with an EXIF note to turn it. */
    private fun sidewaysWithExifRotation(): ByteArray {
        val file = temporary.newFile().apply { writeBytes(Fixtures.jpeg(Fixtures.quarters(400, 300))) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        return file.readBytes()
    }

    // --- Decoding for the screen -----------------------------------------------------------

    @Test
    fun `a photo is decoded no larger than asked for`() {
        val bitmap = ImageLoader.decodeScaled(upright, maxSide = 200)!!
        assertEquals(150, bitmap.width)
        assertEquals(200, bitmap.height)
        assertEquals(listOf("rood", "groen", "blauw", "geel"), Fixtures.corners(bitmap))
    }

    @Test
    fun `a small photo is not blown up`() {
        val bitmap = ImageLoader.decodeScaled(upright, maxSide = 4000)!!
        assertEquals(300, bitmap.width)
        assertEquals(400, bitmap.height)
    }

    @Test
    fun `a photo that relies on EXIF rotation is shown upright`() {
        val bytes = sidewaysWithExifRotation()
        val bitmap = ImageLoader.decodeScaled(bytes, maxSide = 400)!!
        assertEquals(300, bitmap.width)
        assertEquals(400, bitmap.height)
        assertEquals(listOf("blauw", "rood", "geel", "groen"), Fixtures.corners(bitmap))
        assertEquals(PixelSize(300, 400), ImageLoader.readSize(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `something that is not a picture gives nothing instead of a crash`() {
        assertNull(ImageLoader.decodeScaled("geen foto".toByteArray(), 200))
        assertNull(ImageLoader.readSize(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `a loaded photo is remembered and a thumbnail is kept on disk`() = runBlocking {
        val cache = temporary.newFolder("cache")
        val loader = ImageLoader(cache, Dispatchers.Unconfined)
        var opened = 0
        val open = { opened++; ByteArrayInputStream(upright) }

        val first = loader.load("foto|1|1", 200, open)
        val second = loader.load("foto|1|1", 200, open)

        assertNotNull(first)
        assertSame(first, second)
        assertSame(first, loader.cached("foto|1|1", 200))
        assertEquals(1, opened)
        assertEquals(1, cache.resolve("miniaturen").listFiles()!!.size)

        // A fresh loader (the app was restarted) reads the thumbnail from disk instead of the photo.
        val restarted = ImageLoader(cache, Dispatchers.Unconfined)
        assertNotNull(restarted.load("foto|1|1", 200, open))
        assertEquals(1, opened)

        // Another version of the file has another key, so it is read afresh.
        assertNotNull(restarted.load("foto|2|2", 200, open))
        assertEquals(2, opened)
    }

    // --- Pictures for the timelapse ----------------------------------------------------------

    @Test
    fun `a photo is decoded just large enough for the video frame`() {
        val large = Fixtures.jpeg(Fixtures.quarters(1200, 1600), quality = 80)
        val forFrame = FrameComposer.decodeForFrame(large, 270, 360)!!
        // 1200 x 1600 can be halved twice and still cover 270 x 360.
        assertEquals(300, forFrame.width)
        assertEquals(400, forFrame.height)

        val exact = FrameComposer.decodeForFrame(large, 1080, 1440)!!
        assertEquals(1200, exact.width)
    }

    @Test
    fun `a video frame shows the whole photo when the shapes match`() {
        val composer = FrameComposer(ApplicationProvider.getApplicationContext(), 270, 360, showDate = false)
        val frame = composer.compose(Fixtures.quarters(300, 400), LocalDate.of(2026, 11, 23))
        assertEquals(270, frame.width)
        assertEquals(360, frame.height)
        assertEquals(listOf("rood", "groen", "blauw", "geel"), Fixtures.corners(frame))
        composer.release()
    }

    @Test
    fun `a photo of another shape is cut in the middle, never stretched`() {
        // A square frame from a wide photo: the left and right edges fall away.
        val wide = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until 400) for (y in 0 until 200) {
                setPixel(x, y, if (x < 100 || x >= 300) Color.BLACK else if (x < 200) Fixtures.RED else Fixtures.GREEN)
            }
        }
        val composer = FrameComposer(ApplicationProvider.getApplicationContext(), 200, 200, showDate = false)
        val frame = composer.compose(wide, LocalDate.of(2026, 11, 23))
        assertEquals("rood", Fixtures.colourName(frame.getPixel(5, 100)))
        assertEquals("groen", Fixtures.colourName(frame.getPixel(195, 100)))
        composer.release()
    }

    @Test
    fun `the date is only drawn when asked for`() {
        val photo = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Fixtures.BLUE) }
        val context = ApplicationProvider.getApplicationContext<Application>()

        fun whitePixelsInBottomStrip(showDate: Boolean): Int {
            val composer = FrameComposer(context, 540, 720, showDate)
            val frame = composer.compose(photo, LocalDate.of(2026, 11, 23))
            var white = 0
            for (x in 0 until 540 step 2) for (y in 620 until 720 step 2) {
                val pixel = frame.getPixel(x, y)
                if (Color.red(pixel) > 200 && Color.green(pixel) > 200 && Color.blue(pixel) > 200) white++
            }
            composer.release()
            return white
        }

        assertEquals(0, whitePixelsInBottomStrip(showDate = false))
        assertTrue(whitePixelsInBottomStrip(showDate = true) > 30)
    }
}
