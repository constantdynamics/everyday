package nl.constantdynamics.everyday.data.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import nl.constantdynamics.everyday.kern.DatumOpmaak
import nl.constantdynamics.everyday.kern.Passing
import nl.constantdynamics.everyday.kern.Snijpunt
import nl.constantdynamics.everyday.kern.StempelPositie
import nl.constantdynamics.everyday.kern.TimelapseInstellingen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Stelt één videobeeld samen: de foto ingepast of vullend, eventueel met de volgende
 * foto eroverheen voor een crossfade, en eventueel een datumstempel.
 */
class BeeldMaker(
    private val breedte: Int,
    private val hoogte: Int,
    private val instellingen: TimelapseInstellingen,
) {

    private val doel: Bitmap = Bitmap.createBitmap(breedte, hoogte, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(doel)

    private val fotoVerf = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    private val tekstGrootte = hoogte * 0.035f * instellingen.stempelGrootte
    private val tekstVerf = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = tekstGrootte
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        // Wit met een subtiele schaduw blijft op elke achtergrond leesbaar.
        setShadowLayer(tekstGrootte * 0.14f, 0f, tekstGrootte * 0.05f, Color.argb(190, 0, 0, 0))
    }

    private val bronVak = Rect()
    private val doelVak = RectF()

    fun maak(eerste: Bitmap, tweede: Bitmap?, menging: Float, datum: LocalDate): Bitmap {
        canvas.drawColor(Color.BLACK)
        fotoVerf.alpha = 255
        tekenFoto(eerste)
        if (tweede != null && menging > 0f) {
            fotoVerf.alpha = (menging * 255f).roundToInt().coerceIn(0, 255)
            tekenFoto(tweede)
            fotoVerf.alpha = 255
        }
        tekenStempel(datum)
        return doel
    }

    fun sluit() {
        doel.recycle()
    }

    private fun tekenFoto(bron: Bitmap) {
        if (bron.width <= 0 || bron.height <= 0) return
        val bronVerhouding = bron.width.toFloat() / bron.height
        val doelVerhouding = breedte.toFloat() / hoogte

        if (instellingen.passing == Passing.VULLEN) {
            doelVak.set(0f, 0f, breedte.toFloat(), hoogte.toFloat())
            if (bronVerhouding > doelVerhouding) {
                // De foto is breder dan het beeld: links en rechts valt weg.
                val nieuweBreedte = (bron.height * doelVerhouding).roundToInt().coerceAtMost(bron.width)
                val x = ((bron.width - nieuweBreedte) / 2).coerceAtLeast(0)
                bronVak.set(x, 0, x + nieuweBreedte, bron.height)
            } else {
                // De foto is hoger: boven of onder valt weg, afhankelijk van het snijpunt.
                val nieuweHoogte = (bron.width / doelVerhouding).roundToInt().coerceAtMost(bron.height)
                val ruimte = (bron.height - nieuweHoogte).coerceAtLeast(0)
                val y = when (instellingen.snijpunt) {
                    Snijpunt.BOVEN -> 0
                    Snijpunt.MIDDEN -> ruimte / 2
                    Snijpunt.ONDER -> ruimte
                }
                bronVak.set(0, y, bron.width, y + nieuweHoogte)
            }
        } else {
            bronVak.set(0, 0, bron.width, bron.height)
            val schaal = min(breedte.toFloat() / bron.width, hoogte.toFloat() / bron.height)
            val nieuweBreedte = bron.width * schaal
            val nieuweHoogte = bron.height * schaal
            val links = (breedte - nieuweBreedte) / 2f
            val boven = (hoogte - nieuweHoogte) / 2f
            doelVak.set(links, boven, links + nieuweBreedte, boven + nieuweHoogte)
        }
        canvas.drawBitmap(bron, bronVak, doelVak, fotoVerf)
    }

    private fun tekenStempel(datum: LocalDate) {
        if (instellingen.stempelPositie == StempelPositie.GEEN) return
        val tekst = opmaak(instellingen.datumOpmaak).format(datum)
        val tekstBreedte = tekstVerf.measureText(tekst)
        val marge = min(breedte, hoogte) * 0.045f
        val bovenLijn = marge + tekstGrootte
        val onderLijn = hoogte - marge

        val x = when (instellingen.stempelPositie) {
            StempelPositie.LINKSBOVEN, StempelPositie.LINKSONDER -> marge
            StempelPositie.RECHTSBOVEN, StempelPositie.RECHTSONDER -> breedte - marge - tekstBreedte
            StempelPositie.ONDER_MIDDEN -> (breedte - tekstBreedte) / 2f
            StempelPositie.GEEN -> return
        }
        val y = when (instellingen.stempelPositie) {
            StempelPositie.LINKSBOVEN, StempelPositie.RECHTSBOVEN -> bovenLijn
            else -> onderLijn
        }
        canvas.drawText(tekst, x, y, tekstVerf)
    }

    private companion object {
        val NEDERLANDS: Locale = Locale.forLanguageTag("nl-NL")

        fun opmaak(keuze: DatumOpmaak): DateTimeFormatter = when (keuze) {
            DatumOpmaak.CIJFERS -> DateTimeFormatter.ofPattern("dd-MM-yyyy", NEDERLANDS)
            DatumOpmaak.KORTE_MAAND -> DateTimeFormatter.ofPattern("d MMM yyyy", NEDERLANDS)
            DatumOpmaak.LANGE_MAAND -> DateTimeFormatter.ofPattern("d MMMM yyyy", NEDERLANDS)
            DatumOpmaak.ISO -> DateTimeFormatter.ofPattern("yyyy-MM-dd", NEDERLANDS)
        }
    }
}
