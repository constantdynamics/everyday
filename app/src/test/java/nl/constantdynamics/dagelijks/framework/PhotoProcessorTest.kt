package nl.constantdynamics.dagelijks.framework

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.PixelRect
import nl.constantdynamics.dagelijks.image.PhotoProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

/**
 * From camera picture to stored photo, with real pixels: turned upright, cut to the
 * project's shape, never mirrored, and stripped of all metadata but the date.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class PhotoProcessorTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val takenAt = LocalDateTime.of(2026, 11, 23, 8, 15, 30)

    /** A camera delivers its picture lying on its side: wider than high. */
    private val sensorPicture = Fixtures.jpeg(Fixtures.quarters(400, 300))

    private fun process(
        rotation: Int,
        ratio: AspectRatio,
        jpeg: ByteArray = sensorPicture,
        crop: PixelRect? = null,
    ): File {
        val target = temporary.newFile("uit-$rotation-${ratio.name}.jpg")
        PhotoProcessor.process(jpeg, crop, rotation, ratio, takenAt, target)
        return target
    }

    private fun decode(file: File) = BitmapFactory.decodeFile(file.path)!!

    // --- Turning upright, never mirroring ------------------------------------------------------

    @Test
    fun `the back camera's picture is turned a quarter clockwise`() {
        val photo = decode(process(rotation = 90, ratio = AspectRatio.PORTRAIT_3_4))
        assertEquals(300, photo.width)
        assertEquals(400, photo.height)
        // What was bottom left on the sensor ends up top left, and so on round.
        assertEquals(listOf("blauw", "rood", "geel", "groen"), Fixtures.corners(photo))
    }

    @Test
    fun `the front camera's picture is turned the other way and is not mirrored`() {
        val photo = decode(process(rotation = 270, ratio = AspectRatio.PORTRAIT_3_4))
        assertEquals(300, photo.width)
        assertEquals(400, photo.height)
        assertEquals(listOf("groen", "geel", "rood", "blauw"), Fixtures.corners(photo))
        // A mirrored selfie would read: geel, groen, blauw, rood.
    }

    @Test
    fun `a picture that is already upright keeps its sides where they are`() {
        val photo = decode(process(rotation = 0, ratio = AspectRatio.LANDSCAPE_16_9))
        assertEquals(listOf("rood", "groen", "blauw", "geel"), Fixtures.corners(photo))
    }

    @Test
    fun `an upside-down picture is turned half a turn`() {
        val photo = decode(process(rotation = 180, ratio = AspectRatio.LANDSCAPE_16_9))
        assertEquals(listOf("geel", "blauw", "groen", "rood"), Fixtures.corners(photo))
    }

    // --- Cutting to the project's shape -------------------------------------------------------

    @Test
    fun `every ratio comes out with the right shape for a portrait hold`() {
        val expected = mapOf(
            AspectRatio.PORTRAIT_3_4 to (300 to 400),
            AspectRatio.PORTRAIT_9_16 to (225 to 400),
            AspectRatio.SQUARE to (300 to 300),
        )
        for ((ratio, size) in expected) {
            val photo = decode(process(rotation = 90, ratio = ratio))
            assertEquals("${ratio.id} width", size.first, photo.width)
            assertEquals("${ratio.id} height", size.second, photo.height)
        }
    }

    @Test
    fun `a landscape project with the phone on its side uses the full width`() {
        val photo = decode(process(rotation = 0, ratio = AspectRatio.LANDSCAPE_16_9))
        assertEquals(400, photo.width)
        assertEquals(225, photo.height)
    }

    @Test
    fun `the cut is taken from the middle`() {
        // A white bar exactly in the middle of the sensor picture must stay in the middle.
        val bitmap = Fixtures.quarters(400, 300)
        Canvas(bitmap).drawRect(190f, 0f, 210f, 300f, Paint().apply { color = Color.WHITE })
        val photo = decode(process(rotation = 0, ratio = AspectRatio.SQUARE, jpeg = Fixtures.jpeg(bitmap)))

        assertEquals(300, photo.width)
        assertEquals("wit", Fixtures.colourName(photo.getPixel(150, 150)))
        assertEquals("rood", Fixtures.colourName(photo.getPixel(100, 50)))
        assertEquals("groen", Fixtures.colourName(photo.getPixel(200, 50)))
    }

    @Test
    fun `the cut that matches the preview is used when the camera supplies one`() {
        // An off-centre 16:9 window that only covers the right half: green above yellow.
        val window = PixelRect(220, 100, 380, 190)
        val photo = decode(process(rotation = 0, ratio = AspectRatio.LANDSCAPE_16_9, crop = window))

        assertEquals(160, photo.width)
        assertEquals(90, photo.height)
        assertEquals("groen", Fixtures.colourName(photo.getPixel(80, 20)))
        assertEquals("geel", Fixtures.colourName(photo.getPixel(80, 75)))
    }

    // --- Metadata -----------------------------------------------------------------------------

    @Test
    fun `the stored photo records when it was taken and that it stands upright`() {
        val exif = ExifInterface(process(rotation = 90, ratio = AspectRatio.PORTRAIT_3_4))
        assertEquals("2026:11:23 08:15:30", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("2026:11:23 08:15:30", exif.getAttribute(ExifInterface.TAG_DATETIME))
        assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
        assertEquals(0, exif.rotationDegrees)
    }

    @Test
    fun `location and device details from the camera never reach the stored photo`() {
        // A camera picture that carries a GPS position and the phone's make and model.
        val source = temporary.newFile("camera.jpg").apply { writeBytes(sensorPicture) }
        ExifInterface(source).apply {
            setLatLong(52.3702, 4.8952)
            setAltitude(12.0)
            setAttribute(ExifInterface.TAG_MAKE, "Fabrikant")
            setAttribute(ExifInterface.TAG_MODEL, "Telefoon X")
            setAttribute(ExifInterface.TAG_SOFTWARE, "Camera 1.0")
            setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS")
            saveAttributes()
        }
        assertTrue("the test picture must really contain a position", ExifInterface(source).latLong != null)

        val stored = ExifInterface(process(rotation = 90, ratio = AspectRatio.PORTRAIT_3_4, jpeg = source.readBytes()))

        assertNull(stored.latLong)
        val mustBeAbsent = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        )
        mustBeAbsent.forEach { assertNull(it, stored.getAttribute(it)) }
    }

    @Test
    fun `the stored photo is a JPEG of high quality`() {
        val file = process(rotation = 90, ratio = AspectRatio.PORTRAIT_3_4)
        val bytes = file.readBytes()
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        assertEquals(95, PhotoProcessor.JPEG_QUALITY)
        // Flat colours must still be flat after encoding at this quality.
        val photo = decode(file)
        val pixel = photo.getPixel(photo.width / 4, photo.height / 4)
        assertTrue(Color.blue(pixel) > 235 && Color.red(pixel) < 20 && Color.green(pixel) < 20)
    }
}
