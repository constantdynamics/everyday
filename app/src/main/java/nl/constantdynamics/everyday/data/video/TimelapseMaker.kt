package nl.constantdynamics.everyday.data.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import nl.constantdynamics.everyday.data.media.FotoLader
import nl.constantdynamics.everyday.data.media.MediaOpslag
import nl.constantdynamics.everyday.data.toonUri
import nl.constantdynamics.everyday.kern.TimelapseInstellingen
import nl.constantdynamics.everyday.kern.aantalBeelden
import nl.constantdynamics.everyday.kern.samenstellingVoorBeeld
import nl.constantdynamics.everyday.kern.uitvoerFps
import nl.constantdynamics.everyday.kern.videoAfmeting
import java.time.LocalDate
import kotlin.coroutines.coroutineContext

/**
 * Maakt de timelapse. De foto's worden streamend verwerkt: er zijn nooit meer dan
 * twee gedecodeerde beelden tegelijk in het geheugen, elk al verkleind naar de
 * doelresolutie. Een serie van honderden foto's op volle resolutie kost daarmee
 * niet meer geheugen dan een serie van tien.
 */
class TimelapseMaker(
    private val context: Context,
    private val fotoLader: FotoLader,
    private val mediaOpslag: MediaOpslag,
    private val muziekEncoder: MuziekEncoder,
) {

    suspend fun maak(
        serie: SerieEntiteit,
        fotos: List<FotoEntiteit>,
        instellingen: TimelapseInstellingen,
        voortgang: suspend (Float) -> Unit,
    ): Uri? = withContext(Dispatchers.Default) {
        if (fotos.isEmpty()) return@withContext null

        val (breedte, hoogte) = videoAfmeting(instellingen.beeldverhouding, instellingen.resolutie)
        val beeldenPerSeconde = uitvoerFps(instellingen)
        val totaalBeelden = aantalBeelden(fotos.size, instellingen)
        if (totaalBeelden <= 0) return@withContext null

        // Eerst de muziek: die moet als spoor bekend zijn voordat de muxer start.
        val muziek = instellingen.muziekUri?.let { tekst ->
            voortgang(0f)
            muziekEncoder.maakSpoor(
                bronUri = Uri.parse(tekst),
                duurMicros = totaalBeelden.toLong() * 1_000_000L / beeldenPerSeconde,
                volume = instellingen.muziekVolume.coerceIn(0f, 1f),
                fadeSeconden = FADE_SECONDEN,
            )
        }

        val doelUri = mediaOpslag.maakVideo(
            mediaOpslag.videoBestandsnaam(serie.mapNaam, LocalDate.now()),
        ) ?: return@withContext null

        var gelukt = false
        try {
            context.contentResolver.openFileDescriptor(doelUri, "rw").use { beschrijver ->
                requireNotNull(beschrijver) { "De videopositie kon niet worden geopend" }
                val schrijver = VideoSchrijver(
                    breedte = breedte,
                    hoogte = hoogte,
                    fps = beeldenPerSeconde,
                    bestandsBeschrijver = beschrijver.fileDescriptor,
                    muziek = muziek,
                )
                val beeldMaker = BeeldMaker(breedte, hoogte, instellingen)
                val voorraad = FotoVoorraad(fotoLader, maxOf(breedte, hoogte))
                try {
                    schrijver.start()
                    for (index in 0 until totaalBeelden) {
                        coroutineContext.ensureActive()
                        val samenstelling = samenstellingVoorBeeld(index, fotos.size, instellingen)
                        val eersteFoto = fotos[samenstelling.eersteIndex]
                        val eerste = voorraad.haal(eersteFoto) ?: continue
                        val tweede = if (samenstelling.tweedeIndex != samenstelling.eersteIndex) {
                            voorraad.haal(fotos[samenstelling.tweedeIndex])
                        } else {
                            null
                        }
                        val beeld = beeldMaker.maak(
                            eerste = eerste,
                            tweede = tweede,
                            menging = samenstelling.menging,
                            datum = eersteFoto.dagSleutel,
                        )
                        schrijver.schrijfBeeld(
                            beeld,
                            index.toLong() * 1_000_000_000L / beeldenPerSeconde,
                        )
                        if (index % VOORTGANG_ELKE == 0) {
                            voortgang(index.toFloat() / totaalBeelden)
                        }
                    }
                    schrijver.rondAf()
                    gelukt = true
                } finally {
                    schrijver.sluit()
                    beeldMaker.sluit()
                    voorraad.leeg()
                }
            }
        } finally {
            if (gelukt) {
                mediaOpslag.rondVideoAf(doelUri)
            } else {
                // Een halve video hoort niet in je galerij te blijven staan.
                mediaOpslag.verwijderBestand(doelUri)
            }
        }

        voortgang(1f)
        doelUri
    }

    /** Houdt hooguit twee gedecodeerde foto's vast: de huidige en die waarnaar wordt overgevloeid. */
    private class FotoVoorraad(
        private val fotoLader: FotoLader,
        private val maxZijde: Int,
    ) {
        private val beelden = LinkedHashMap<Long, Bitmap>()

        suspend fun haal(foto: FotoEntiteit): Bitmap? {
            beelden[foto.id]?.let { return it }
            val beeld = fotoLader.laadVers(foto.toonUri(), maxZijde) ?: return null
            beelden[foto.id] = beeld
            while (beelden.size > MAX_IN_GEHEUGEN) {
                val oudste = beelden.entries.iterator()
                if (!oudste.hasNext()) break
                val vermelding = oudste.next()
                oudste.remove()
                vermelding.value.recycle()
            }
            return beeld
        }

        fun leeg() {
            beelden.values.forEach { it.recycle() }
            beelden.clear()
        }

        private companion object {
            const val MAX_IN_GEHEUGEN = 2
        }
    }

    private companion object {
        const val VOORTGANG_ELKE = 4

        /** Zachtjes uitfaden aan het eind, zodat de muziek niet abrupt afbreekt. */
        const val FADE_SECONDEN = 1.5f
    }
}
