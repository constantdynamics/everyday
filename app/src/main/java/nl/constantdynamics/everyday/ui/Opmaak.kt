package nl.constantdynamics.everyday.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val NEDERLANDS: Locale = Locale.forLanguageTag("nl-NL")

private val KORTE_DATUM = DateTimeFormatter.ofPattern("d MMM yyyy", NEDERLANDS)
private val LANGE_DATUM = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", NEDERLANDS)
private val MAAND_JAAR = DateTimeFormatter.ofPattern("MMMM yyyy", NEDERLANDS)
private val TIJD = DateTimeFormatter.ofPattern("HH:mm", NEDERLANDS)

fun korteDatum(datum: LocalDate): String = KORTE_DATUM.format(datum)

fun langeDatum(datum: LocalDate): String = LANGE_DATUM.format(datum)

fun maandJaar(datum: LocalDate): String =
    MAAND_JAAR.format(datum).replaceFirstChar { it.titlecase(NEDERLANDS) }

fun korteDatum(moment: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    KORTE_DATUM.format(moment.atZone(zone))

fun tijdstip(moment: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    TIJD.format(moment.atZone(zone))

fun aantalFotos(aantal: Int): String = if (aantal == 1) "1 foto" else "$aantal foto's"

/** Bestandsgroottes zoals je ze wilt lezen: "1,2 GB" in plaats van een berg cijfers. */
fun leesbareGrootte(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) {
        String.format(NEDERLANDS, "%.1f GB", mb / 1024)
    } else if (mb >= 10) {
        String.format(NEDERLANDS, "%.0f MB", mb)
    } else {
        String.format(NEDERLANDS, "%.1f MB", mb)
    }
}
