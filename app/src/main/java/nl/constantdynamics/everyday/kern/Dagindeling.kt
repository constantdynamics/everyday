package nl.constantdynamics.everyday.kern

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Bepaalt bij welke dag een moment hoort.
 *
 * Standaard loopt een dag van 00:00 tot 24:00 lokale tijd. Met een dagstart van
 * bijvoorbeeld 4 hoort een foto van 01:30 nog bij de dag ervoor. De dagsleutel
 * wordt eenmalig bij het opslaan berekend en daarna vastgelegd, zodat foto's niet
 * alsnog van dag verschuiven als je naar een andere tijdzone reist.
 */
object Dagindeling {

    const val STANDAARD_DAGSTART_UUR = 0

    fun dagSleutel(
        moment: Instant,
        dagStartUur: Int = STANDAARD_DAGSTART_UUR,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = moment.atZone(zone).minusHours(dagStartUur.toLong()).toLocalDate()
}
