package nl.constantdynamics.everyday.kern

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * De bewerking van een foto: kwartslagen, een vrije hoek om recht te zetten, en een
 * uitsnede. De volgorde ligt vast: eerst draaien, dan rechtzetten (waarbij de lege
 * hoeken automatisch wegvallen), en pas daarna de uitsnede. De uitsnede wordt dus
 * uitgedrukt in het rechtgezette beeld, van 0 tot 1.
 */
data class Bewerking(
    val kwartslagen: Int = 0,
    val rechtzetHoek: Float = 0f,
    val links: Float = 0f,
    val boven: Float = 0f,
    val rechts: Float = 1f,
    val onder: Float = 1f,
) {
    val isLeeg: Boolean
        get() = kwartslagen % 4 == 0 &&
            abs(rechtzetHoek) < 0.01f &&
            links <= 0.001f && boven <= 0.001f && rechts >= 0.999f && onder >= 0.999f

    val graden: Int get() = ((kwartslagen % 4) + 4) % 4 * 90

    companion object {
        const val MAX_RECHTZET_HOEK = 15f
    }
}

/**
 * De grootste rechthoek met dezelfde verhouding als [breedte] bij [hoogte] die nog
 * helemaal binnen het beeld past nadat dat over [gradenHoek] is gedraaid. Zo houden
 * we bij rechtzetten geen lege hoeken over.
 *
 * Geeft breedte en hoogte terug in dezelfde eenheid als de invoer.
 */
fun grootsteRechthoekNaDraaien(
    breedte: Float,
    hoogte: Float,
    gradenHoek: Float,
): Pair<Float, Float> {
    if (breedte <= 0f || hoogte <= 0f) return 0f to 0f
    val hoek = Math.toRadians(gradenHoek.toDouble())
    val sinus = abs(sin(hoek)).toFloat()
    val cosinus = abs(cos(hoek)).toFloat()
    if (sinus < 1e-6f) return breedte to hoogte

    val breedteIsLanger = breedte >= hoogte
    val langeZijde = if (breedteIsLanger) breedte else hoogte
    val korteZijde = if (breedteIsLanger) hoogte else breedte

    return if (korteZijde <= 2f * sinus * cosinus * langeZijde || abs(sinus - cosinus) < 1e-6f) {
        // Halve zijde bepaalt de uitkomst; de rechthoek raakt het midden van de zijden.
        val half = 0.5f * korteZijde
        if (breedteIsLanger) (half / sinus) to (half / cosinus) else (half / cosinus) to (half / sinus)
    } else {
        val cosDubbel = cosinus * cosinus - sinus * sinus
        val nieuweBreedte = (breedte * cosinus - hoogte * sinus) / cosDubbel
        val nieuweHoogte = (hoogte * cosinus - breedte * sinus) / cosDubbel
        nieuweBreedte to nieuweHoogte
    }
}
