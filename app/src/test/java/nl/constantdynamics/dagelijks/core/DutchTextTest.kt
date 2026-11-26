package nl.constantdynamics.dagelijks.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DutchTextTest {

    @Test
    fun `photos are counted in proper Dutch`() {
        assertEquals("0 foto's", DutchText.photoCount(0))
        assertEquals("1 foto", DutchText.photoCount(1))
        assertEquals("365 foto's", DutchText.photoCount(365))
    }

    @Test
    fun `a year of photos at ten per second is about 36 seconds`() {
        assertEquals("365 foto's ≈ 36 seconden", DutchText.timelapseSummary(365, 10))
    }

    @Test
    fun `short videos get one decimal`() {
        assertEquals("1 foto ≈ minder dan 1 seconde", DutchText.timelapseSummary(1, 10))
        assertEquals("1 seconde", DutchText.duration(1.0))
        assertEquals("1,7 seconden", DutchText.duration(5 / 3.0))
        assertEquals("3 seconden", DutchText.duration(3.0))
        assertEquals("9,5 seconden", DutchText.duration(9.5))
    }

    @Test
    fun `long videos are told in minutes and seconds`() {
        assertEquals("59 seconden", DutchText.duration(59.9))
        assertEquals("1 minuut", DutchText.duration(60.0))
        assertEquals("1 minuut en 1 seconde", DutchText.duration(61.0))
        assertEquals("2 minuten en 5 seconden", DutchText.duration(125.4))
        assertEquals("6 minuten en 5 seconden", DutchText.duration(365.0))
    }

    @Test
    fun `speed is written out`() {
        assertEquals("1 foto per seconde", DutchText.photosPerSecond(1))
        assertEquals("10 foto's per seconde", DutchText.photosPerSecond(10))
    }

    @Test
    fun `skipped photos are reported in good Dutch for one and for more`() {
        assertEquals("1 foto kon niet worden gelezen en is overgeslagen.", DutchText.photosSkipped(1))
        assertEquals("3 foto's konden niet worden gelezen en zijn overgeslagen.", DutchText.photosSkipped(3))
    }
}
