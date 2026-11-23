package nl.constantdynamics.dagelijks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AspectRatioTest {

    @Test
    fun `the video is 1080 pixels on the short side`() {
        assertEquals(PixelSize(1080, 1920), AspectRatio.PORTRAIT_9_16.videoSize)
        assertEquals(PixelSize(1080, 1440), AspectRatio.PORTRAIT_3_4.videoSize)
        assertEquals(PixelSize(1080, 1080), AspectRatio.SQUARE.videoSize)
        assertEquals(PixelSize(1920, 1080), AspectRatio.LANDSCAPE_16_9.videoSize)
    }

    @Test
    fun `video sizes are even, as H264 requires`() {
        AspectRatio.entries.forEach {
            assertEquals(0, it.videoSize.width % 2)
            assertEquals(0, it.videoSize.height % 2)
        }
    }

    @Test
    fun `only 16 by 9 is shot with the phone on its side`() {
        assertTrue(AspectRatio.LANDSCAPE_16_9.isLandscape)
        assertFalse(AspectRatio.PORTRAIT_9_16.isLandscape)
        assertFalse(AspectRatio.PORTRAIT_3_4.isLandscape)
        assertFalse(AspectRatio.SQUARE.isLandscape)
    }

    @Test
    fun `ratios are read from the text in project json`() {
        assertEquals(AspectRatio.PORTRAIT_9_16, AspectRatio.fromId("9:16"))
        assertEquals(AspectRatio.PORTRAIT_3_4, AspectRatio.fromId(" 3:4 "))
        assertEquals(AspectRatio.SQUARE, AspectRatio.fromId("1:1"))
        assertEquals(AspectRatio.LANDSCAPE_16_9, AspectRatio.fromId("16:9"))
        assertNull(AspectRatio.fromId("4:3"))
        assertNull(AspectRatio.fromId(null))
    }

    @Test
    fun `the ratio of an existing photo is recognised`() {
        assertEquals(AspectRatio.PORTRAIT_3_4, AspectRatio.closestTo(3000, 4000))
        assertEquals(AspectRatio.PORTRAIT_9_16, AspectRatio.closestTo(2250, 4000))
        assertEquals(AspectRatio.SQUARE, AspectRatio.closestTo(3000, 3001))
        assertEquals(AspectRatio.LANDSCAPE_16_9, AspectRatio.closestTo(4000, 2250))
        assertEquals(AspectRatio.DEFAULT, AspectRatio.closestTo(0, 0))
    }
}
