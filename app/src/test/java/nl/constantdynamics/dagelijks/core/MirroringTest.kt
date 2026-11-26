package nl.constantdynamics.dagelijks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The selfie pitfall, pinned down: the live image of the front camera is mirrored,
 * the stored photo never is, and so the overlay must be mirrored for the front
 * camera only.
 */
class MirroringTest {

    @Test
    fun `the front camera shows a mirrored live image`() {
        assertTrue(Mirroring.isPreviewMirrored(Lens.FRONT))
        assertFalse(Mirroring.isPreviewMirrored(Lens.BACK))
    }

    @Test
    fun `the overlay is mirrored exactly when the live image is`() {
        Lens.entries.forEach { assertEquals(Mirroring.isPreviewMirrored(it), Mirroring.isOverlayMirrored(it)) }
        assertTrue(Mirroring.isOverlayMirrored(Lens.FRONT))
        assertFalse(Mirroring.isOverlayMirrored(Lens.BACK))
    }

    @Test
    fun `a stored photo is never mirrored`() {
        assertFalse(Mirroring.MIRROR_SAVED_PHOTO)
    }

    @Test
    fun `mirroring moves a point to the opposite side and twice brings it back`() {
        assertEquals(900f, Mirroring.mirrorX(100f, 1000f))
        assertEquals(500f, Mirroring.mirrorX(500f, 1000f))
        assertEquals(100f, Mirroring.mirrorX(Mirroring.mirrorX(100f, 1000f), 1000f))
    }

    /**
     * Why the flip is needed: a mole on the photographer's left cheek is on the right
     * side of the stored photo (as others see it), but on the left of the mirrored live
     * image. Only a flipped overlay puts yesterday's mole over today's.
     */
    @Test
    fun `the flipped overlay coincides with the mirrored live image`() {
        val width = 1000f
        val moleInStoredPhoto = 800f
        val moleInLiveImage = Mirroring.mirrorX(moleInStoredPhoto, width)

        val overlayX = if (Mirroring.isOverlayMirrored(Lens.FRONT)) {
            Mirroring.mirrorX(moleInStoredPhoto, width)
        } else {
            moleInStoredPhoto
        }
        assertEquals(moleInLiveImage, overlayX)
    }
}
