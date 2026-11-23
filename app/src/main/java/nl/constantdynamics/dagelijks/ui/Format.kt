package nl.constantdynamics.dagelijks.ui

import nl.constantdynamics.dagelijks.DagelijksApp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dates and sizes the way they are written in Dutch. */
object Format {
    private val dutch: Locale = DagelijksApp.DUTCH
    private val long = DateTimeFormatter.ofPattern("d MMMM yyyy", dutch)
    private val longWithWeekday = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", dutch)
    private val dayAndMonth = DateTimeFormatter.ofPattern("d MMM", dutch)
    private val monthAndYear = DateTimeFormatter.ofPattern("LLLL yyyy", dutch)

    /** 23 november 2026 */
    fun date(date: LocalDate): String = long.format(date)

    /** maandag 23 november 2026 */
    fun dateWithWeekday(date: LocalDate): String = longWithWeekday.format(date)

    /** 23 nov */
    fun shortDate(date: LocalDate): String = dayAndMonth.format(date).removeSuffix(".")

    /** november 2026 */
    fun month(date: LocalDate): String = monthAndYear.format(date)

    /** 12,3 MB */
    fun fileSize(bytes: Long): String {
        val megabytes = bytes / (1024.0 * 1024.0)
        return if (megabytes >= 10) "%.0f MB".format(dutch, megabytes) else "%.1f MB".format(dutch, megabytes)
    }
}
