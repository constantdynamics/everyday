package nl.constantdynamics.dagelijks.core

import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/**
 * Photos are stored as YYYY-MM-DD.jpg: one per day, and plain alphabetical order
 * is chronological order in any file manager.
 */
object PhotoNames {

    private val photoPattern = Regex("""^(\d{4})-(\d{2})-(\d{2})\.jpe?g$""", RegexOption.IGNORE_CASE)

    // Locale.ROOT: the digits in a file name are 0-9, whatever language the phone is set to.
    fun fileNameFor(date: LocalDate): String =
        String.format(Locale.ROOT, "%04d-%02d-%02d.jpg", date.year, date.monthValue, date.dayOfMonth)

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
        String.format(Locale.ROOT, "%04d-%02d-%02d", date.year, date.monthValue, date.dayOfMonth)

    const val PROJECT_FILE = "project.json"

    /** Where a project.json that cannot be understood is kept, next to its replacement. */
    const val DAMAGED_PROJECT_FILE = "project-beschadigd.json"
    const val NO_MEDIA_FILE = ".nomedia"
    const val TIMELAPSE_FOLDER = "timelapses"

    /** Prefix of a file that is still being written; never shown, cleaned up when stale. */
    const val TEMPORARY_PREFIX = ".bezig-"

    private val temporaryPattern = Regex("""^\.bezig-(\d{1,18})-(.+)$""")

    /**
     * The name a file has while it is being written: ".bezig-<bytes>-<final name>".
     *
     * It carries the size the file must reach, so after an interruption a complete file
     * can be told from half a one without opening it.
     */
    fun temporaryNameFor(finalName: String, size: Long): String = "$TEMPORARY_PREFIX$size-$finalName"

    /** What a temporary file was going to become, or null when [fileName] is not one. */
    fun temporaryTarget(fileName: String): TemporaryTarget? {
        val match = temporaryPattern.matchEntire(fileName) ?: return null
        return TemporaryTarget(finalName = match.groupValues[2], size = match.groupValues[1].toLong())
    }

    data class TemporaryTarget(val finalName: String, val size: Long)
}
