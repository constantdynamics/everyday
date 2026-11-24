package nl.constantdynamics.dagelijks.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.CropMath
import nl.constantdynamics.dagelijks.core.Mirroring
import nl.constantdynamics.dagelijks.core.PixelRect
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Turns the JPEG that comes out of the camera into the photo that is stored.
 *
 * The result is cut to the project's aspect ratio, stands upright in its pixels
 * (so every program shows it the right way up, also those that ignore EXIF), is
 * never mirrored, and carries no metadata except the date and time it was taken
 * and an orientation tag that says "upright".
 *
 * Because the picture is decoded and encoded again here, nothing of the camera's
 * own metadata can survive: no location, no phone model, nothing.
 */
object PhotoProcessor {

    const val JPEG_QUALITY = 95

    data class Processed(val file: File, val width: Int, val height: Int)

    /**
     * @param jpeg the image as delivered by the camera
     * @param suggestedCrop the area the camera library says matches the preview, in
     *   the coordinates of [jpeg]; null to take a centred cut
     * @param rotationDegrees how far [jpeg] must be turned clockwise to stand upright
     * @param sampleSize 1 keeps every pixel, 2 halves the picture in both directions.
     *   Normally 1: the processor itself falls back to half the size when the phone
     *   has too little memory for the full picture.
     * @return the stored file and its size in pixels
     */
    fun process(
        jpeg: ByteArray,
        suggestedCrop: PixelRect?,
        rotationDegrees: Int,
        aspectRatio: AspectRatio,
        takenAt: LocalDateTime,
        target: File,
        sampleSize: Int = 1,
    ): Processed {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("The camera did not deliver a readable image")

        val rotation = CropMath.normalizeRotation(rotationDegrees)
        val crop = CropMath.captureCrop(bounds.outWidth, bounds.outHeight, rotation, aspectRatio, suggestedCrop)

        val upright = try {
            decodeUpright(jpeg, crop, bounds.outWidth, bounds.outHeight, rotation, sampleSize)
        } catch (_: OutOfMemoryError) {
            // A very large sensor on a phone with little memory to spare. Half the size in
            // each direction is still far more than a timelapse needs, and beats no photo.
            decodeUpright(jpeg, crop, bounds.outWidth, bounds.outHeight, rotation, sampleSize * 2)
        }
        try {
            target.outputStream().buffered().use { output ->
                if (!upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw IOException("The photo could not be encoded")
                }
            }
            writeMinimalExif(target, takenAt)
            return Processed(target, upright.width, upright.height)
        } finally {
            upright.recycle()
        }
    }

    private fun decodeUpright(
        jpeg: ByteArray,
        crop: PixelRect,
        fullWidth: Int,
        fullHeight: Int,
        rotation: Int,
        sampleSize: Int,
    ): Bitmap {
        val cut = decodeRegion(jpeg, crop, fullWidth, fullHeight, sampleSize)
        return try {
            turnUpright(cut, rotation)
        } catch (failure: OutOfMemoryError) {
            cut.recycle()
            throw failure
        }
    }

    /** Decodes only the part that is kept, which roughly halves memory use for cropped ratios. */
    private fun decodeRegion(jpeg: ByteArray, crop: PixelRect, fullWidth: Int, fullHeight: Int, sampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize
        }
        val isWholeImage = crop.left == 0 && crop.top == 0 && crop.width == fullWidth && crop.height == fullHeight

        if (!isWholeImage) {
            val region = runCatching {
                val decoder = newRegionDecoder(jpeg)
                try {
                    decoder.decodeRegion(Rect(crop.left, crop.top, crop.right, crop.bottom), options)
                } finally {
                    decoder.recycle()
                }
            }.onFailure { if (it is OutOfMemoryError) throw it }.getOrNull()
            if (region != null) return region
        }

        // Whole image wanted, or the region decoder is not available for this file.
        val full = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
            ?: throw IOException("The camera image could not be decoded")
        if (isWholeImage) return full
        // The decoded picture is [sampleSize] times smaller than the original; so is the cut.
        val left = (crop.left / sampleSize).coerceIn(0, full.width - 1)
        val top = (crop.top / sampleSize).coerceIn(0, full.height - 1)
        val width = (crop.width / sampleSize).coerceIn(1, full.width - left)
        val height = (crop.height / sampleSize).coerceIn(1, full.height - top)
        val cut = try {
            Bitmap.createBitmap(full, left, top, width, height)
        } catch (failure: OutOfMemoryError) {
            full.recycle()
            throw failure
        }
        if (cut !== full) full.recycle()
        return cut
    }

    @Suppress("DEPRECATION")
    private fun newRegionDecoder(jpeg: ByteArray): BitmapRegionDecoder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(jpeg, 0, jpeg.size)
        } else {
            BitmapRegionDecoder.newInstance(jpeg, 0, jpeg.size, false)
        } ?: throw IOException("No region decoder")

    /**
     * Rotates in whole quarter turns, which moves pixels without touching them.
     * There is deliberately no mirroring step: see [Mirroring].
     */
    private fun turnUpright(source: Bitmap, rotation: Int): Bitmap {
        check(!Mirroring.MIRROR_SAVED_PHOTO)
        if (rotation == 0) return source
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
        if (rotated !== source) source.recycle()
        return rotated
    }

    /** The only metadata a stored photo gets: when it was taken, and that it stands upright. */
    private fun writeMinimalExif(file: File, takenAt: LocalDateTime) {
        val stamp = EXIF_DATE_TIME.format(takenAt)
        val exif = ExifInterface(file)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
        exif.setAttribute(ExifInterface.TAG_DATETIME, stamp)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()
    }

    private val EXIF_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
}
