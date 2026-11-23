package nl.constantdynamics.dagelijks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DaysTest {

    private val today = LocalDate.of(2026, 11, 23)
    private fun day(daysAgo: Long): LocalDate = today.minusDays(daysAgo)

    // --- "Has today's photo been taken?" ----------------------------------------------------

    @Test
    fun `no photos means not taken today`() {
        assertFalse(Days.hasPhotoOn(emptyList(), today))
    }

    @Test
    fun `a photo from yesterday does not count for today`() {
        assertFalse(Days.hasPhotoOn(listOf(day(1), day(2)), today))
    }

    @Test
    fun `a photo dated today counts`() {
        assertTrue(Days.hasPhotoOn(listOf(day(2), day(1), today), today))
    }

    // --- Choosing the previous photo for the overlay ------------------------------------------

    @Test
    fun `on the first day there is no overlay`() {
        assertNull(Days.overlayDateFor(emptyList(), today))
    }

    @Test
    fun `retaking the very first photo on the first day still has no overlay`() {
        assertNull(Days.overlayDateFor(listOf(today), today))
    }

    @Test
    fun `the overlay is the most recent earlier photo`() {
        assertEquals(day(1), Days.overlayDateFor(listOf(day(3), day(1), day(2)), today))
    }

    @Test
    fun `a missed day is simply skipped`() {
        assertEquals(day(4), Days.overlayDateFor(listOf(day(9), day(4)), today))
    }

    @Test
    fun `retaking today's photo lines up against yesterday, not against the photo being replaced`() {
        assertEquals(day(1), Days.overlayDateFor(listOf(day(2), day(1), today), today))
    }

    @Test
    fun `replacing an older photo uses the photo before it`() {
        val dates = listOf(day(5), day(4), day(3), day(1), today)
        assertEquals(day(4), Days.overlayDateFor(dates, target = day(3)))
        // Also when the day being replaced has no photo of its own.
        assertEquals(day(3), Days.overlayDateFor(dates, target = day(2)))
    }

    @Test
    fun `replacing the oldest photo falls back to the one after it`() {
        val dates = listOf(day(5), day(4), day(3))
        assertEquals(day(4), Days.overlayDateFor(dates, target = day(5)))
    }

    @Test
    fun `the order in which dates are given does not matter`() {
        val dates = listOf(day(1), day(7), day(3), day(2))
        assertEquals(Days.overlayDateFor(dates, today), Days.overlayDateFor(dates.shuffled(), today))
        assertEquals(day(1), Days.overlayDateFor(dates.sorted(), today))
    }

    // --- Period for the timelapse ----------------------------------------------------------

    @Test
    fun `without a period all photos are used, oldest first`() {
        assertEquals(listOf(day(3), day(2), day(1)), Days.inPeriod(listOf(day(1), day(3), day(2)), null, null))
    }

    @Test
    fun `both ends of a period are included`() {
        val dates = (0L..9L).map(::day)
        assertEquals(listOf(day(6), day(5), day(4)), Days.inPeriod(dates, from = day(6), to = day(4)))
        assertEquals(listOf(day(1), today), Days.inPeriod(dates, from = day(1), to = null))
        assertEquals(listOf(day(9), day(8)), Days.inPeriod(dates, from = null, to = day(8)))
    }

    @Test
    fun `a period without photos is empty`() {
        assertEquals(emptyList<LocalDate>(), Days.inPeriod(listOf(day(1)), from = day(9), to = day(5)))
    }
}
