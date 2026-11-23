package nl.constantdynamics.dagelijks.core

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Photos are stored as YYYY-MM-DD.jpg: one per day, and plain alphabetical order
 * is chronological order in any file manager.
 */
object PhotoNames {

    private val photoPattern = Regex("""^(\d{4})-(\d{2})-(\d{2})\.jpe?g$""", RegexOption.IGNORE_CASE)

    fun fileNameFor(date: LocalDate): String =
        "%04d-%02d-%02d.jpg".format(date.year, date.monthValue, date.dayOfMonth)

    /** The day a file belongs to, or null when it is not one of the app's photos. */
    fun dateFromFileName(fileName: String): LocalDate? {
        val match = photoPattern.matchEntire(fileName) ?: return null
        val (year, month, day) = match.destructured
        return try {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        } catch (_: DateTimeException) {
            null // for example 2026-02-30
        }
    }

    /** For example "Mijn gezicht_2026-11-23_tot_2027-11-23.mp4". */
    fun timelapseFileName(projectFolderName: String, from: LocalDate, to: LocalDate): String =
        "${projectFolderName}_${isoDate(from)}_tot_${isoDate(to)}.mp4"

    private fun isoDate(date: LocalDate): String =
        "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)

    const val PROJECT_FILE = "project.json"
    const val NO_MEDIA_FILE = ".nomedia"
    const val TIMELAPSE_FOLDER = "timelapses"

    /** Prefix of a photo that is still being written; never shown, cleaned up when stale. */
    const val TEMPORARY_PREFIX = ".bezig-"
}
