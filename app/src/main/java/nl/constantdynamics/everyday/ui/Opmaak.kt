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
