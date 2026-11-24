package nl.constantdynamics.dagelijks.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.constantdynamics.dagelijks.core.PixelSize
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Loads photos from the user's folder as bitmaps of a sensible size.
 *
 * Written by hand on purpose: the usual image libraries (Coil, Glide) request the
 * INTERNET permission, and this app only ever reads local files.
 *
 * Small sizes are also kept as tiny JPEGs in the app's cache folder, so a gallery
 * with hundreds of photos opens instantly the second time.
 */
class ImageLoader(
    cacheDirectory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3),
) {

    private val thumbnailDirectory = File(cacheDirectory, "miniaturen")

    private val memory = object : LruCache<String, Bitmap>(memoryBudgetKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** A bitmap that is already in memory, for showing something without a flicker. */
    fun cached(cacheKey: String, maxSide: Int): Bitmap? = memory.get(memoryKey(cacheKey, maxSide))

    /**
     * @param cacheKey identifies the file and its current content
     * @param maxSide the longest side of the result is at most this many pixels
     * @param open opens the photo; only called when it is not cached
     */
    suspend fun load(cacheKey: String, maxSide: Int, open: () -> InputStream): Bitmap? {
        val key = memoryKey(cacheKey, maxSide)
        memory.get(key)?.let { return it }
        return withContext(dispatcher) {
            memory.get(key)?.let { return@withContext it }
            val bitmap = runCatching {
                if (maxSide <= THUMBNAIL_MAX_SIDE) loadThumbnail(cacheKey, maxSide, open) else decode(open, maxSide)
            }.getOrNull()
            if (bitmap != null) memory.put(key, bitmap)
            bitmap
        }
    }

    /** Drops what is held in memory for one photo. */
    fun forget(cacheKey: String) {
        memory.snapshot().keys.filter { it.startsWith("$cacheKey@") }.forEach { memory.remove(it) }
    }

    /** Removes the oldest thumbnails once the cache folder grows too large. */
    suspend fun trimDiskCache() = withContext(dispatcher) {
        val files = thumbnailDirectory.listFiles()?.sortedByDescending { it.lastModified() } ?: return@withContext
        var total = 0L
        for (file in files) {
            total += file.length()
            if (total > MAX_DISK_BYTES) file.delete()
        }
    }

    private fun loadThumbnail(cacheKey: String, maxSide: Int, open: () -> InputStream): Bitmap? {
        val file = File(thumbnailDirectory, sha1("$cacheKey@$maxSide") + ".jpg")
        if (file.isFile && file.length() > 0) {
            BitmapFactory.decodeFile(file.path)?.let {
                file.setLastModified(System.currentTimeMillis())
                return it
            }
        }
        val bitmap = decode(open, maxSide) ?: return null
        runCatching {
            thumbnailDirectory.mkdirs()
            val temporary = File(thumbnailDirectory, file.name + ".tmp")
            temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            temporary.renameTo(file)
        }
        return bitmap
    }

    private fun decode(open: () -> InputStream, maxSide: Int): Bitmap? {
        // One read of the file serves the size check, the EXIF check and the decode.
        val bytes = open().use { it.readBytes() }
        return decodeScaled(bytes, maxSide)
    }

    private fun memoryKey(cacheKey: String, maxSide: Int) = "$cacheKey@$maxSide"

    companion object {
        /** Up to this size results are also cached on disk. */
        const val THUMBNAIL_MAX_SIDE = 512

        private const val MAX_DISK_BYTES = 150L * 1024 * 1024

        private fun memoryBudgetKb(): Int {
            val heapKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            return (heapKb / 5).coerceIn(16 * 1024, 128 * 1024)
        }

        private fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { String.format(Locale.ROOT, "%02x", it) }

        /** Pixel size of a photo as it is viewed, taking an EXIF rotation into account. */
        fun readSize(input: InputStream): PixelSize? {
            val bytes = input.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val quarterTurn = exifRotation(bytes).let { it == 90 || it == 270 }
            return if (quarterTurn) PixelSize(bounds.outHeight, bounds.outWidth) else PixelSize(bounds.outWidth, bounds.outHeight)
        }

        /**
         * Decodes a JPEG so that its longest side is at most [maxSide], upright. Photos
         * made by this app are stored upright already; files copied in from elsewhere
         * may rely on an EXIF rotation, which is honoured here.
         */
        fun decodeScaled(bytes: ByteArray, maxSide: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide && sample < 64) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

            val longest = maxOf(bitmap.width, bitmap.height)
            val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
            val rotation = exifRotation(bytes)
            if (scale != 1f || rotation != 0) {
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    if (rotation != 0) postRotate(rotation.toFloat())
                }
                val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (transformed !== bitmap) bitmap.recycle()
                bitmap = transformed
            }
            return bitmap
        }

        private fun exifRotation(bytes: ByteArray): Int =
            runCatching { ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees }.getOrDefault(0)
    }
}
