package nl.constantdynamics.dagelijks.timelapse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import nl.constantdynamics.dagelijks.DagelijksApp
import nl.constantdynamics.dagelijks.R
import nl.constantdynamics.dagelijks.core.CropMath
import nl.constantdynamics.dagelijks.core.PixelRect
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.min

/**
 * Builds one video picture out of one photo: the photo filling the whole frame and,
 * when asked for, the date in a corner.
 *
 * Photos of a project share the frame's aspect ratio, so normally nothing is cut
 * off. A photo with another shape (copied into the folder by hand) is cut centred;
 * it is never stretched.
 */
class FrameComposer(
    context: Context,
    private val width: Int,
    private val height: Int,
    private val showDate: Boolean,
) {

    private val frame: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(frame)
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val source = Rect()
    private val destination = Rect(0, 0, width, height)

    private val textSize = min(width, height) * 0.05f
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = this@FrameComposer.textSize
        // The bundled body font is a variable font; ask for its bold weight.
        runCatching {
            typeface = context.resources.getFont(R.font.work_sans)
            fontVariationSettings = "'wght' 700"
        }.onFailure { typeface = Typeface.DEFAULT_BOLD }
        // White with a soft dark edge stays readable on any photo.
        setShadowLayer(this@FrameComposer.textSize * 0.16f, 0f, this@FrameComposer.textSize * 0.05f, Color.argb(200, 0, 0, 0))
    }

    /** The returned bitmap is reused for the next call. */
    fun compose(photo: Bitmap, date: LocalDate): Bitmap {
        canvas.drawColor(Color.BLACK)
        val crop = CropMath.centerCrop(PixelRect(0, 0, photo.width, photo.height), width, height)
        source.set(crop.left, crop.top, crop.right, crop.bottom)
        canvas.drawBitmap(photo, source, destination, photoPaint)
        if (showDate) {
            val margin = min(width, height) * 0.05f
            canvas.drawText(DATE_FORMAT.format(date), margin, height - margin, datePaint)
        }
        return frame
    }

    fun release() {
        frame.recycle()
    }

    companion object {
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", DagelijksApp.DUTCH)

        /**
         * Decodes a photo just large enough to fill a [frameWidth] x [frameHeight]
         * frame sharply, and upright. Decoding at reduced size is what keeps a
         * timelapse of thousands of large photos within a modest amount of memory.
         */
        fun decodeForFrame(bytes: ByteArray, frameWidth: Int, frameHeight: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val rotation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees }.getOrDefault(0)
            val upright = CropMath.rotatedSize(bounds.outWidth, bounds.outHeight, rotation)

            // Only the centred part with the frame's shape ends up in the video; decode
            // it no smaller than the frame itself, so nothing gets blurry.
            val used = CropMath.centerCrop(PixelRect(0, 0, upright.width, upright.height), frameWidth, frameHeight)
            val sample = CropMath.sampleSizeFor(used.width, used.height, frameWidth, frameHeight)

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            if (rotation == 0) return decoded
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            return rotated
        }
    }
}
