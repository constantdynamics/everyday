package nl.constantdynamics.dagelijks.core

import java.time.LocalDate

/** Small questions about which days already have a photo. */
object Days {

    /** One photo per day: has today's been taken already? */
    fun hasPhotoOn(dates: Collection<LocalDate>, day: LocalDate): Boolean = day in dates

    /**
     * Which earlier photo to lay over the camera image when shooting the photo for
     * [target] (normally today).
     *
     * It is the most recent photo from before that day, so retaking today's photo
     * still lines up against yesterday's rather than against the photo being
     * replaced. On the very first day there is nothing to show. Only when an old
     * photo without any predecessor is being replaced does the next photo serve
     * as the reference.
     */
    fun overlayDateFor(dates: Collection<LocalDate>, target: LocalDate): LocalDate? =
        dates.filter { it < target }.maxOrNull() ?: dates.filter { it > target }.minOrNull()

    /** The photos that fall inside an optional period, oldest first. */
    fun inPeriod(dates: Collection<LocalDate>, from: LocalDate?, to: LocalDate?): List<LocalDate> =
        dates.filter { (from == null || it >= from) && (to == null || it <= to) }.distinct().sorted()
}
