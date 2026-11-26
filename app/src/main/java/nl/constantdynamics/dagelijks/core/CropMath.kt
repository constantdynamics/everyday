package nl.constantdynamics.dagelijks.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** A rectangle in whole pixels; right and bottom are exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

/**
 * The geometry behind saving a photo: where to cut, and what the size becomes
 * after turning the picture upright. Kept free of Android classes so it can be
 * tested exhaustively on a plain JVM.
 */
object CropMath {

    /** Rotation in whole quarter turns clockwise: 0, 90, 180 or 270. */
    fun normalizeRotation(degrees: Int): Int {
        val normalized = ((degrees % 360) + 360) % 360
        require(normalized % 90 == 0) { "Rotation must be a multiple of 90 degrees, was $degrees" }
        return normalized
    }

    fun isQuarterTurn(degrees: Int): Boolean = normalizeRotation(degrees).let { it == 90 || it == 270 }

    /** Size of an image after rotating it. */
    fun rotatedSize(width: Int, height: Int, degrees: Int): PixelSize =
        if (isQuarterTurn(degrees)) PixelSize(height, width) else PixelSize(width, height)

    /**
     * The largest centred rectangle inside [bounds] whose width:height equals
     * [ratioWidth]:[ratioHeight].
     */
    fun centerCrop(bounds: PixelRect, ratioWidth: Int, ratioHeight: Int): PixelRect {
        require(ratioWidth > 0 && ratioHeight > 0) { "Aspect ratio must be positive" }
        if (bounds.isEmpty) return bounds
        // Compare bounds.width / bounds.height with ratioWidth / ratioHeight without division.
        val boundsIsWider = bounds.width.toLong() * ratioHeight > bounds.height.toLong() * ratioWidth
        val cropWidth: Int
        val cropHeight: Int
        if (boundsIsWider) {
            cropHeight = bounds.height
            cropWidth = (bounds.height.toDouble() * ratioWidth / ratioHeight).roundToInt().coerceIn(1, bounds.width)
        } else {
            cropWidth = bounds.width
            cropHeight = (bounds.width.toDouble() * ratioHeight / ratioWidth).roundToInt().coerceIn(1, bounds.height)
        }
        val left = bounds.left + (bounds.width - cropWidth) / 2
        val top = bounds.top + (bounds.height - cropHeight) / 2
        return PixelRect(left, top, left + cropWidth, top + cropHeight)
    }

    /**
     * Where to cut a freshly captured image so that, once it is turned upright,
     * it has the project's aspect ratio.
     *
     * [bufferWidth] x [bufferHeight] is the image as the camera delivered it,
     * [rotationDegrees] is how far it still has to be turned clockwise to be
     * upright, and [suggested] is the cut the camera library proposes so that the
     * photo shows exactly what the preview showed. That suggestion is used when it
     * has the right shape; otherwise a centred cut of the right shape is taken from
     * it, so the saved photo always has the project's aspect ratio.
     */
    fun captureCrop(
        bufferWidth: Int,
        bufferHeight: Int,
        rotationDegrees: Int,
        aspectRatio: AspectRatio,
        suggested: PixelRect?,
    ): PixelRect {
        val buffer = PixelRect(0, 0, bufferWidth, bufferHeight)
        val start = suggested?.let { intersect(buffer, it) }?.takeUnless { it.isEmpty } ?: buffer

        // The ratio is defined for the upright photo; in the unrotated buffer a
        // quarter turn swaps width and height.
        val quarterTurn = isQuarterTurn(rotationDegrees)
        val ratioWidth = if (quarterTurn) aspectRatio.height else aspectRatio.width
        val ratioHeight = if (quarterTurn) aspectRatio.width else aspectRatio.height

        return if (hasRatio(start, ratioWidth, ratioHeight)) start else centerCrop(start, ratioWidth, ratioHeight)
    }

    /** True when the rectangle has the ratio, allowing for one pixel of rounding. */
    fun hasRatio(rect: PixelRect, ratioWidth: Int, ratioHeight: Int): Boolean {
        if (rect.isEmpty) return false
        val idealHeight = rect.width.toDouble() * ratioHeight / ratioWidth
        val idealWidth = rect.height.toDouble() * ratioWidth / ratioHeight
        return abs(idealHeight - rect.height) <= 1.0 || abs(idealWidth - rect.width) <= 1.0
    }

    private fun intersect(a: PixelRect, b: PixelRect): PixelRect = PixelRect(
        maxOf(a.left, b.left),
        maxOf(a.top, b.top),
        minOf(a.right, b.right),
        minOf(a.bottom, b.bottom),
    )

    /**
     * How strongly a JPEG can be shrunk while decoding (in powers of two) and still
     * be at least [targetWidth] x [targetHeight], so nothing gets blurry.
     */
    fun sampleSizeFor(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight && sample < 64) {
            sample *= 2
        }
        return sample
    }
}

/**
 * Mirroring, the classic selfie pitfall, in one place.
 *
 * The front camera's live image is shown mirrored, like a mirror, because that is
 * what feels natural while posing. The photo itself is saved unmirrored: the way
 * other people see you. So the saved photo must never be flipped, and the overlay
 * of the previous photo must be flipped whenever it lies over a mirrored live image.
 */
object Mirroring {

    /** The live preview is mirrored for the front camera only. */
    fun isPreviewMirrored(lens: Lens): Boolean = lens == Lens.FRONT

    /** The overlay follows the preview, otherwise the two would not line up. */
    fun isOverlayMirrored(lens: Lens): Boolean = isPreviewMirrored(lens)

    /** Photos are stored the way the world sees the subject, for either camera. */
    const val MIRROR_SAVED_PHOTO: Boolean = false

    /** Where a horizontal position ends up after mirroring within [width]. */
    fun mirrorX(x: Float, width: Float): Float = width - x
}
