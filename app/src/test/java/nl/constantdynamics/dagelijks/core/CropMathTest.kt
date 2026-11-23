package nl.constantdynamics.dagelijks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CropMathTest {

    // --- Rotation ---------------------------------------------------------------------------

    @Test
    fun `rotation is brought back to a quarter turn between 0 and 270`() {
        assertEquals(0, CropMath.normalizeRotation(0))
        assertEquals(0, CropMath.normalizeRotation(360))
        assertEquals(90, CropMath.normalizeRotation(450))
        assertEquals(270, CropMath.normalizeRotation(-90))
        assertEquals(180, CropMath.normalizeRotation(-180))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a rotation that is not a quarter turn is refused`() {
        CropMath.normalizeRotation(45)
    }

    @Test
    fun `a quarter turn swaps width and height`() {
        assertEquals(PixelSize(3000, 4000), CropMath.rotatedSize(4000, 3000, 90))
        assertEquals(PixelSize(3000, 4000), CropMath.rotatedSize(4000, 3000, 270))
        assertEquals(PixelSize(4000, 3000), CropMath.rotatedSize(4000, 3000, 180))
        assertEquals(PixelSize(4000, 3000), CropMath.rotatedSize(4000, 3000, 0))
    }

    // --- Centred cut --------------------------------------------------------------------------

    @Test
    fun `a wide image loses its sides`() {
        val crop = CropMath.centerCrop(PixelRect(0, 0, 4000, 3000), 1, 1)
        assertEquals(PixelRect(500, 0, 3500, 3000), crop)
    }

    @Test
    fun `a tall image loses top and bottom`() {
        val crop = CropMath.centerCrop(PixelRect(0, 0, 3000, 4000), 16, 9)
        assertEquals(3000, crop.width)
        assertEquals(1688, crop.height) // 3000 * 9 / 16 = 1687.5
        assertEquals(crop.top, 4000 - crop.bottom) // equally much off both ends
    }

    @Test
    fun `an image that already has the ratio is kept whole`() {
        val bounds = PixelRect(0, 0, 3000, 4000)
        assertEquals(bounds, CropMath.centerCrop(bounds, 3, 4))
    }

    @Test
    fun `the cut stays inside bounds that do not start at zero`() {
        val bounds = PixelRect(100, 50, 1100, 850)
        val crop = CropMath.centerCrop(bounds, 1, 1)
        assertEquals(PixelRect(200, 50, 1000, 850), crop)
    }

    // --- Cutting a captured picture ---------------------------------------------------------

    private val sensors = listOf(4000 to 3000, 4032 to 3024, 4624 to 3468, 8160 to 6120, 1920 to 1080, 3264 to 2448)

    @Test
    fun `whatever the sensor and rotation, the upright photo has the project's ratio`() {
        for ((width, height) in sensors) {
            for (rotation in listOf(0, 90, 180, 270)) {
                for (ratio in AspectRatio.entries) {
                    val crop = CropMath.captureCrop(width, height, rotation, ratio, suggested = null)
                    val upright = CropMath.rotatedSize(crop.width, crop.height, rotation)
                    val actual = upright.width.toDouble() / upright.height
                    assertTrue(
                        "$width x $height, $rotation degrees, ${ratio.id}: got $upright",
                        abs(actual - ratio.value) < 0.002,
                    )
                    assertTrue(crop.left >= 0 && crop.top >= 0 && crop.right <= width && crop.bottom <= height)
                }
            }
        }
    }

    @Test
    fun `a 3 by 4 portrait from a 4 by 3 sensor uses every pixel`() {
        val crop = CropMath.captureCrop(4000, 3000, 90, AspectRatio.PORTRAIT_3_4, suggested = null)
        assertEquals(PixelRect(0, 0, 4000, 3000), crop)
    }

    @Test
    fun `a 9 by 16 portrait keeps the full height of the scene`() {
        // The sensor image lies on its side: its width becomes the photo's height.
        val crop = CropMath.captureCrop(4000, 3000, 90, AspectRatio.PORTRAIT_9_16, suggested = null)
        assertEquals(4000, crop.width)
        assertEquals(2250, crop.height)
        assertEquals(375, crop.top)
    }

    @Test
    fun `a 16 by 9 landscape with the phone on its side uses the full sensor width`() {
        val crop = CropMath.captureCrop(4000, 3000, 0, AspectRatio.LANDSCAPE_16_9, suggested = null)
        assertEquals(PixelRect(0, 375, 4000, 2625), crop)
    }

    @Test
    fun `the cut proposed by the camera is used when it has the right shape`() {
        // Slightly off-centre, as a camera may propose so that preview and photo match exactly.
        val suggested = PixelRect(8, 380, 3992, 2621) // 3984 x 2241 = 16:9
        val crop = CropMath.captureCrop(4000, 3000, 90, AspectRatio.PORTRAIT_9_16, suggested)
        assertEquals(suggested, crop)
    }

    @Test
    fun `a proposed cut of the wrong shape is corrected instead of trusted`() {
        // The whole sensor proposed for a square project: cut the square out of it.
        val crop = CropMath.captureCrop(4000, 3000, 90, AspectRatio.SQUARE, PixelRect(0, 0, 4000, 3000))
        assertEquals(PixelRect(500, 0, 3500, 3000), crop)
    }

    @Test
    fun `a proposed cut that sticks out of the picture is clipped first`() {
        val crop = CropMath.captureCrop(4000, 3000, 0, AspectRatio.LANDSCAPE_16_9, PixelRect(-50, -50, 5000, 4000))
        assertEquals(PixelRect(0, 375, 4000, 2625), crop)
    }

    @Test
    fun `ratio check allows one pixel of rounding`() {
        assertTrue(CropMath.hasRatio(PixelRect(0, 0, 3000, 1688), 16, 9))
        assertTrue(CropMath.hasRatio(PixelRect(0, 0, 3000, 1687), 16, 9))
        assertFalse(CropMath.hasRatio(PixelRect(0, 0, 3000, 1700), 16, 9))
        assertFalse(CropMath.hasRatio(PixelRect(0, 0, 0, 0), 16, 9))
    }

    // --- Decoding at reduced size -----------------------------------------------------------

    @Test
    fun `photos are decoded smaller only while they stay at least as large as needed`() {
        assertEquals(1, CropMath.sampleSizeFor(3000, 4000, 1600, 2133))
        assertEquals(2, CropMath.sampleSizeFor(3000, 4000, 1080, 1440))
        assertEquals(8, CropMath.sampleSizeFor(3000, 4000, 360, 480))
        assertEquals(1, CropMath.sampleSizeFor(800, 600, 1080, 1440))
        assertEquals(1, CropMath.sampleSizeFor(0, 0, 100, 100))
    }
}
