package nl.constantdynamics.everyday.data.media

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.constantdynamics.everyday.kern.Bewerking
import nl.constantdynamics.everyday.kern.grootsteRechthoekNaDraaien
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Past een bewerking toe op een foto. Het origineel wordt nooit aangeraakt: er
 * ontstaat altijd een nieuw beeld.
 */
class Bewerker(private val fotoLader: FotoLader) {

    /** Alleen draaien en rechtzetten; dit is het beeld waarop je de uitsnede aanwijst. */
    suspend fun rechtgezet(uri: Uri, kwartslagen: Int, rechtzetHoek: Float, maxZijde: Int): Bitmap? =
        withContext(Dispatchers.Default) {
            val bron = fotoLader.laadVers(uri, maxZijde) ?: return@withContext null
            zetRecht(bron, kwartslagen, rechtzetHoek)
        }

    /** Het volledige resultaat: draaien, rechtzetten en bijsnijden. */
    suspend fun render(uri: Uri, bewerking: Bewerking, maxZijde: Int): Bitmap? =
        withContext(Dispatchers.Default) {
            val recht = rechtgezet(uri, bewerking.kwartslagen, bewerking.rechtzetHoek, maxZijde)
                ?: return@withContext null
            snijUit(recht, bewerking)
        }

    suspend fun rendeerNaarJpeg(
        uri: Uri,
        bewerking: Bewerking,
        uitvoer: OutputStream,
        maxZijde: Int = MAX_BEWAAR_ZIJDE,
        kwaliteit: Int = JPEG_KWALITEIT,
    ): Boolean = withContext(Dispatchers.Default) {
        val beeld = render(uri, bewerking, maxZijde) ?: return@withContext false
        try {
            beeld.compress(Bitmap.CompressFormat.JPEG, kwaliteit, uitvoer)
        } finally {
            beeld.recycle()
        }
    }

    private fun zetRecht(bron: Bitmap, kwartslagen: Int, rechtzetHoek: Float): Bitmap {
        var beeld = bron
        var isVanMij = false

        val graden = (((kwartslagen % 4) + 4) % 4) * 90
        if (graden != 0) {
            val gedraaid = draai(beeld, graden.toFloat())
            if (isVanMij && gedraaid !== beeld) beeld.recycle()
            beeld = gedraaid
            isVanMij = true
        }

        if (abs(rechtzetHoek) >= 0.01f) {
            val gedraaid = draai(beeld, rechtzetHoek)
            val (breedte, hoogte) = grootsteRechthoekNaDraaien(
                beeld.width.toFloat(),
                beeld.height.toFloat(),
                rechtzetHoek,
            )
            val bijgesneden = snijMidden(gedraaid, breedte.roundToInt(), hoogte.roundToInt())
            if (bijgesneden !== gedraaid) gedraaid.recycle()
            if (isVanMij && beeld !== bijgesneden) beeld.recycle()
            beeld = bijgesneden
            isVanMij = true
        }

        // Nooit de bron van de lader teruggeven zonder kopie: die kan gecached zijn.
        return if (isVanMij) beeld else beeld.copy(beeld.config ?: Bitmap.Config.ARGB_8888, false) ?: beeld
    }

    private fun snijUit(bron: Bitmap, bewerking: Bewerking): Bitmap {
        val links = (bewerking.links.coerceIn(0f, 1f) * bron.width).roundToInt()
        val boven = (bewerking.boven.coerceIn(0f, 1f) * bron.height).roundToInt()
        val rechts = (bewerking.rechts.coerceIn(0f, 1f) * bron.width).roundToInt()
        val onder = (bewerking.onder.coerceIn(0f, 1f) * bron.height).roundToInt()
        val breedte = (rechts - links).coerceAtLeast(1).coerceAtMost(bron.width - links)
        val hoogte = (onder - boven).coerceAtLeast(1).coerceAtMost(bron.height - boven)
        if (links == 0 && boven == 0 && breedte == bron.width && hoogte == bron.height) return bron
        val uitsnede = Bitmap.createBitmap(bron, links, boven, breedte, hoogte)
        if (uitsnede !== bron) bron.recycle()
        return uitsnede
    }

    private fun draai(bron: Bitmap, graden: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(graden) }
        return Bitmap.createBitmap(bron, 0, 0, bron.width, bron.height, matrix, true)
    }

    private fun snijMidden(bron: Bitmap, breedte: Int, hoogte: Int): Bitmap {
        val b = breedte.coerceIn(1, bron.width)
        val h = hoogte.coerceIn(1, bron.height)
        val x = ((bron.width - b) / 2).coerceAtLeast(0)
        val y = ((bron.height - h) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(bron, x, y, b, h)
    }

    companion object {
        /**
         * Ruim boven wat een telefooncamera levert, en tegelijk een grens die het
         * geheugen niet opblaast.
         */
        const val MAX_BEWAAR_ZIJDE = 4096
        const val JPEG_KWALITEIT = 95
        const val VOORBEELD_ZIJDE = 1400
    }
}
