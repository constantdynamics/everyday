package nl.constantdynamics.everyday.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Houdt een verkleinde versie van de laatste foto per serie op schijf, zodat het
 * opnamescherm de ghost overlay meteen kan tonen zonder eerst een foto van volle
 * resolutie te decoderen.
 */
class GhostCache(
    private val context: Context,
    private val fotoLader: FotoLader,
) {

    private val map: File
        get() = File(context.cacheDir, "ghost").also { if (!it.exists()) it.mkdirs() }

    suspend fun ghost(fotoId: Long, bronUri: android.net.Uri, versie: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val bestand = File(map, bestandsnaam(fotoId, versie))
            if (bestand.exists()) {
                runCatching { BitmapFactory.decodeFile(bestand.path) }.getOrNull()
                    ?.let { return@withContext it }
            }
            val bitmap = fotoLader.laad(bronUri, MAX_ZIJDE) ?: return@withContext null
            runCatching {
                bestand.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                // Oudere ghosts van dezelfde foto opruimen.
                map.listFiles()
                    ?.filter { it.name.startsWith("$fotoId-") && it.name != bestand.name }
                    ?.forEach { it.delete() }
            }
            bitmap
        }

    fun leeg() {
        runCatching { map.listFiles()?.forEach { it.delete() } }
    }

    private fun bestandsnaam(fotoId: Long, versie: String): String = "$fotoId-$versie.jpg"

    companion object {
        /** Ruim genoeg om scherp over het camerabeeld te liggen, klein genoeg om snel te laden. */
        const val MAX_ZIJDE = 1280
    }
}
