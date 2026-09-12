package nl.constantdynamics.everyday.kern

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val DATUM_MET_TIJD = Regex(
    """(?<!\d)(\d{4})[-_.]?(\d{2})[-_.]?(\d{2})[-_ T]?(\d{2})[-_.:]?(\d{2})[-_.:]?(\d{2})(?!\d)""",
)

private val ALLEEN_DATUM = Regex("""(?<!\d)(\d{4})[-_.]?(\d{2})[-_.]?(\d{2})(?!\d)""")

private const val VROEGSTE_JAAR = 1990
private const val LAATSTE_JAAR = 2100

/**
 * Haalt het opnamemoment uit een bestandsnaam. Camera's en schermafbeeldingen zetten
 * de datum er vrijwel altijd in — IMG_20240612_153045, PXL_20240612_153045123,
 * 2024-06-12 15.30.45 — en dat scheelt een hoop vragen bij het importeren.
 *
 * Geeft null als er geen geloofwaardige datum in staat.
 */
fun datumUitBestandsnaam(bestandsnaam: String): LocalDateTime? {
    DATUM_MET_TIJD.find(bestandsnaam)?.let { treffer ->
        val (jaar, maand, dag, uur, minuut, seconde) = treffer.destructured
        maakMoment(jaar, maand, dag, uur, minuut, seconde)?.let { return it }
    }
    ALLEEN_DATUM.find(bestandsnaam)?.let { treffer ->
        val (jaar, maand, dag) = treffer.destructured
        maakDatum(jaar, maand, dag)?.let { return it.atStartOfDay() }
    }
    return null
}

private fun maakMoment(
    jaar: String,
    maand: String,
    dag: String,
    uur: String,
    minuut: String,
    seconde: String,
): LocalDateTime? {
    val datum = maakDatum(jaar, maand, dag) ?: return null
    val u = uur.toIntOrNull() ?: return null
    val m = minuut.toIntOrNull() ?: return null
    val s = seconde.toIntOrNull() ?: return null
    if (u !in 0..23 || m !in 0..59 || s !in 0..59) return null
    return LocalDateTime.of(datum, LocalTime.of(u, m, s))
}

private fun maakDatum(jaar: String, maand: String, dag: String): LocalDate? {
    val j = jaar.toIntOrNull() ?: return null
    val ma = maand.toIntOrNull() ?: return null
    val d = dag.toIntOrNull() ?: return null
    if (j !in VROEGSTE_JAAR..LAATSTE_JAAR) return null
    if (ma !in 1..12) return null
    return runCatching { LocalDate.of(j, ma, d) }.getOrNull()
}
