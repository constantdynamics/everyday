package nl.constantdynamics.dagelijks.core

import kotlin.math.roundToInt

/** Small pieces of Dutch wording that depend on a number. */
object DutchText {

    fun photoCount(count: Int): String = if (count == 1) "1 foto" else "$count foto's"

    /**
     * For example "36 seconden" or "2 minuten en 5 seconden". Whole seconds are
     * rounded down (a year of photos at ten per second is "36 seconden"); under ten
     * seconds one decimal is shown, because there a second more or less matters.
     */
    fun duration(seconds: Double): String {
        if (seconds < 0.95) return "minder dan 1 seconde"
        if (seconds < 9.95) {
            val tenths = (seconds * 10).roundToInt()
            return when {
                tenths == 10 -> "1 seconde"
                tenths % 10 == 0 -> "${tenths / 10} seconden"
                else -> "${tenths / 10},${tenths % 10} seconden"
            }
        }
        val total = seconds.toInt()
        val minutes = total / 60
        val rest = total % 60
        val minutesText = if (minutes == 1) "1 minuut" else "$minutes minuten"
        val secondsText = if (rest == 1) "1 seconde" else "$rest seconden"
        return when {
            minutes == 0 -> secondsText
            rest == 0 -> minutesText
            else -> "$minutesText en $secondsText"
        }
    }

    /** The line under the speed slider: "365 foto's ≈ 36 seconden". */
    fun timelapseSummary(photoCount: Int, photosPerSecond: Int): String =
        "${photoCount(photoCount)} ≈ ${duration(TimelapseMath.durationSeconds(photoCount, photosPerSecond))}"

    fun photosPerSecond(speed: Int): String = if (speed == 1) "1 foto per seconde" else "$speed foto's per seconde"
}
