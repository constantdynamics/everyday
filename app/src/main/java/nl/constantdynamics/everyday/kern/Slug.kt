package nl.constantdynamics.everyday.kern

import java.text.Normalizer

private val ACCENTEN = Regex("\\p{Mn}+")
private val NIET_TOEGESTAAN = Regex("[^a-z0-9]+")

/**
 * Maakt van een serienaam een veilige mapnaam. Accenten gaan eraf, alles wat geen
 * letter of cijfer is wordt een koppelteken. De mapnaam verandert daarna nooit meer,
 * ook niet als de serie wordt hernoemd.
 */
fun maakMapNaam(naam: String): String {
    val zonderAccenten = Normalizer.normalize(naam, Normalizer.Form.NFD).replace(ACCENTEN, "")
    val slug = zonderAccenten.lowercase().replace(NIET_TOEGESTAAN, "-").trim('-')
    return if (slug.isBlank()) "serie" else slug.take(40).trim('-')
}

/** Voegt een suffix toe zolang [isBezet] aangeeft dat de mapnaam al bestaat. */
inline fun maakUniekeMapNaam(basis: String, isBezet: (String) -> Boolean): String {
    if (!isBezet(basis)) return basis
    var teller = 2
    while (isBezet("$basis-$teller")) teller++
    return "$basis-$teller"
}
