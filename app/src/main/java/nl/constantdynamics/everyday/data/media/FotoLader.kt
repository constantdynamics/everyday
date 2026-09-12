package nl.constantdynamics.everyday.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Eigen bitmaplader. Bewust geen Coil of Glide: die brengen allebei de
 * INTERNET-permissie mee in hun manifest, en we laden hier uitsluitend lokale
 * bestanden. Er wordt altijd verkleind gedecodeerd, zodat een reeks van honderden
 * foto's op volle resolutie het geheugen niet opblaast.
 */
class FotoLader(private val context: Context) {

    private val cache = object : LruCache<String, Bitmap>(cacheGrootteInKb()) {
        override fun sizeOf(sleutel: String, waarde: Bitmap): Int = waarde.byteCount / 1024
    }

    suspend fun laad(uri: Uri, maxZijde: Int): Bitmap? {
        val sleutel = "$uri@$maxZijde"
        cache.get(sleutel)?.let { return it }
        val bitmap = decodeer(uri, maxZijde) ?: return null
        cache.put(sleutel, bitmap)
        return bitmap
    }

    /**
     * Zonder cache, voor grote beelden die maar één keer nodig zijn — zoals bij het
     * renderen van een bewerking. Zo blijft de cache gevuld met kleine miniaturen.
     */
    suspend fun laadVers(uri: Uri, maxZijde: Int): Bitmap? = decodeer(uri, maxZijde)

    fun leegCache() = cache.evictAll()

    private suspend fun decodeer(uri: Uri, maxZijde: Int): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, grenzen) } }
        if (grenzen.outWidth <= 0 || grenzen.outHeight <= 0) return@withContext null

        val opties = BitmapFactory.Options().apply {
            inSampleSize = bepaalVerkleining(grenzen.outWidth, grenzen.outHeight, maxZijde)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val ruw = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opties) }
        }.getOrNull() ?: return@withContext null

        val draaiing = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees }
        }.getOrNull() ?: 0

        val gedraaid = if (draaiing != 0) draai(ruw, draaiing) else ruw
        krimpNaar(gedraaid, maxZijde)
    }

    private fun draai(bitmap: Bitmap, graden: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(graden.toFloat()) }
        val nieuw = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (nieuw != bitmap) bitmap.recycle()
        return nieuw
    }

    /** Laatste stap naar de exacte doelmaat; inSampleSize werkt alleen in machten van twee. */
    private fun krimpNaar(bitmap: Bitmap, maxZijde: Int): Bitmap {
        val langste = maxOf(bitmap.width, bitmap.height)
        if (langste <= maxZijde) return bitmap
        val factor = maxZijde.toFloat() / langste
        val breedte = (bitmap.width * factor).toInt().coerceAtLeast(1)
        val hoogte = (bitmap.height * factor).toInt().coerceAtLeast(1)
        val nieuw = Bitmap.createScaledBitmap(bitmap, breedte, hoogte, true)
        if (nieuw != bitmap) bitmap.recycle()
        return nieuw
    }

    companion object {
        fun bepaalVerkleining(breedte: Int, hoogte: Int, maxZijde: Int): Int {
            var verkleining = 1
            var langste = maxOf(breedte, hoogte)
            while (langste / 2 >= maxZijde && verkleining < 64) {
                langste /= 2
                verkleining *= 2
            }
            return verkleining
        }

        private fun cacheGrootteInKb(): Int {
            val beschikbaarInKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            return (beschikbaarInKb / 8).coerceIn(4 * 1024, 96 * 1024)
        }
    }
}
