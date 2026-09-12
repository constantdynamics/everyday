package nl.constantdynamics.everyday.kern

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Hoe een foto het videobeeld vult als de verhoudingen niet gelijk zijn. */
enum class Passing { VULLEN, PASSEND }

/** Waar bij vullen wordt weggesneden. Bij portretten zit het gezicht zelden in het midden. */
enum class Snijpunt { BOVEN, MIDDEN, ONDER }

enum class Overgang { CUT, CROSSFADE }

enum class StempelPositie { GEEN, LINKSBOVEN, RECHTSBOVEN, LINKSONDER, RECHTSONDER, ONDER_MIDDEN }

enum class DatumOpmaak { CIJFERS, KORTE_MAAND, LANGE_MAAND, ISO }

enum class Beeldverhouding(val breedteDeel: Int, val hoogteDeel: Int, val omschrijving: String) {
    STAAND_9_16(9, 16, "9:16"),
    PORTRET_4_5(4, 5, "4:5"),
    VIERKANT_1_1(1, 1, "1:1"),
    LIGGEND_16_9(16, 9, "16:9"),
}

enum class Resolutie(val korteZijde: Int, val omschrijving: String) {
    P720(720, "720p"),
    P1080(1080, "1080p"),
    P1440(1440, "1440p"),
}

data class TimelapseInstellingen(
    val fps: Int = STANDAARD_FPS,
    val beeldverhouding: Beeldverhouding = Beeldverhouding.STAAND_9_16,
    val resolutie: Resolutie = Resolutie.P1080,
    val passing: Passing = Passing.VULLEN,
    val snijpunt: Snijpunt = Snijpunt.MIDDEN,
    val overgang: Overgang = Overgang.CUT,
    val overgangsduurSeconden: Float = 0.3f,
    val stempelPositie: StempelPositie = StempelPositie.GEEN,
    val datumOpmaak: DatumOpmaak = DatumOpmaak.KORTE_MAAND,
    val stempelGrootte: Float = 1f,
    val muziekUri: String? = null,
    val muziekVolume: Float = 1f,
) {
    companion object {
        const val STANDAARD_FPS = 12
        val KEUZE_FPS = listOf(6, 8, 12, 15, 24, 30)

        /** Bij een crossfade zijn er tussenbeelden nodig; een harde cut heeft die niet. */
        const val UITVOER_FPS_BIJ_CROSSFADE = 30
    }
}

/** Even getallen: video-encoders willen geen oneven afmetingen. */
fun videoAfmeting(verhouding: Beeldverhouding, resolutie: Resolutie): Pair<Int, Int> {
    val kort = resolutie.korteZijde
    val staand = verhouding.hoogteDeel >= verhouding.breedteDeel
    val breedte: Int
    val hoogte: Int
    if (staand) {
        breedte = kort
        hoogte = (kort.toLong() * verhouding.hoogteDeel / verhouding.breedteDeel).toInt()
    } else {
        hoogte = kort
        breedte = (kort.toLong() * verhouding.breedteDeel / verhouding.hoogteDeel).toInt()
    }
    return naarEven(breedte) to naarEven(hoogte)
}

private fun naarEven(waarde: Int): Int = if (waarde % 2 == 0) waarde else waarde + 1

/** De lengte van de video: elke foto krijgt evenveel tijd. */
fun duurSeconden(aantalFotos: Int, fps: Int): Float {
    if (aantalFotos <= 0 || fps <= 0) return 0f
    return aantalFotos.toFloat() / fps
}

/** Omgekeerd: welke fps hoort bij een gewenste totale lengte. */
fun fpsVoorDuur(aantalFotos: Int, seconden: Float): Int {
    if (aantalFotos <= 0 || seconden <= 0f) return TimelapseInstellingen.STANDAARD_FPS
    return (aantalFotos / seconden).roundToInt().coerceIn(1, 60)
}

/** De framesnelheid waarmee de video daadwerkelijk wordt weggeschreven. */
fun uitvoerFps(instellingen: TimelapseInstellingen): Int = when (instellingen.overgang) {
    Overgang.CUT -> instellingen.fps
    Overgang.CROSSFADE -> maxOf(instellingen.fps, TimelapseInstellingen.UITVOER_FPS_BIJ_CROSSFADE)
}

/** Hoeveel beelden er in totaal worden gerenderd. */
fun aantalBeelden(aantalFotos: Int, instellingen: TimelapseInstellingen): Int {
    if (aantalFotos <= 0) return 0
    return when (instellingen.overgang) {
        Overgang.CUT -> aantalFotos
        Overgang.CROSSFADE ->
            ceil(duurSeconden(aantalFotos, instellingen.fps) * uitvoerFps(instellingen)).toInt()
                .coerceAtLeast(aantalFotos)
    }
}

/**
 * Welke foto's op beeld [beeldIndex] te zien zijn en hoe sterk de tweede doorkomt.
 * [tweedeIndex] is gelijk aan [eersteIndex] zolang er niet wordt overgevloeid.
 */
data class BeeldSamenstelling(
    val eersteIndex: Int,
    val tweedeIndex: Int,
    val menging: Float,
)

fun samenstellingVoorBeeld(
    beeldIndex: Int,
    aantalFotos: Int,
    instellingen: TimelapseInstellingen,
): BeeldSamenstelling {
    if (aantalFotos <= 0) return BeeldSamenstelling(0, 0, 0f)
    if (instellingen.overgang == Overgang.CUT) {
        val index = beeldIndex.coerceIn(0, aantalFotos - 1)
        return BeeldSamenstelling(index, index, 0f)
    }

    val tijd = beeldIndex.toFloat() / uitvoerFps(instellingen)
    val plaats = tijd * instellingen.fps
    val index = plaats.toInt().coerceIn(0, aantalFotos - 1)
    val binnenVak = plaats - index

    // De overgang zit aan het eind van elk vak, uitgedrukt als deel van dat vak.
    val overgangDeel = (instellingen.overgangsduurSeconden * instellingen.fps).coerceIn(0.05f, 0.9f)
    val start = 1f - overgangDeel
    if (index >= aantalFotos - 1 || binnenVak < start) {
        return BeeldSamenstelling(index, index, 0f)
    }
    val menging = ((binnenVak - start) / overgangDeel).coerceIn(0f, 1f)
    return BeeldSamenstelling(index, index + 1, menging)
}
