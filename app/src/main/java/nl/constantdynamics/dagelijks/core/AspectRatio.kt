package nl.constantdynamics.dagelijks.core

import kotlin.math.abs
import kotlin.math.ln

/**
 * The shape of every photo in a project, written as width:height the way the
 * photo is viewed. It is fixed once the first photo exists, because the overlay
 * of the previous photo only lines up when every photo has the same shape.
 */
enum class AspectRatio(val id: String, val width: Int, val height: Int) {
    PORTRAIT_9_16("9:16", 9, 16),
    PORTRAIT_3_4("3:4", 3, 4),
    SQUARE("1:1", 1, 1),
    LANDSCAPE_16_9("16:9", 16, 9);

    /** Width divided by height. */
    val value: Float get() = width.toFloat() / height

    /** A landscape project is shot with the phone held sideways. */
    val isLandscape: Boolean get() = width > height

    /** Size of the timelapse video: 1080 pixels on the short side. */
    val videoSize: PixelSize
        get() = if (width <= height) {
            PixelSize(VIDEO_SHORT_SIDE, VIDEO_SHORT_SIDE * height / width)
        } else {
            PixelSize(VIDEO_SHORT_SIDE * width / height, VIDEO_SHORT_SIDE)
        }

    companion object {
        const val VIDEO_SHORT_SIDE = 1080

        val DEFAULT = PORTRAIT_3_4

        fun fromId(id: String?): AspectRatio? = entries.firstOrNull { it.id == id?.trim() }

        /** The supported ratio that is nearest to a photo of the given size. */
        fun closestTo(width: Int, height: Int): AspectRatio {
            if (width <= 0 || height <= 0) return DEFAULT
            val target = ln(width.toDouble() / height)
            return entries.minBy { abs(ln(it.value.toDouble()) - target) }
        }
    }
}

data class PixelSize(val width: Int, val height: Int)
