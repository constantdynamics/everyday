package nl.constantdynamics.everyday

import nl.constantdynamics.everyday.kern.Dagindeling
import nl.constantdynamics.everyday.kern.maakMapNaam
import nl.constantdynamics.everyday.kern.maakUniekeMapNaam
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MapNaamTest {

    @Test
    fun `accenten en leestekens verdwijnen`() {
        assertEquals("eenmaal-per-dag", maakMapNaam("Éénmaal per dag"))
        assertEquals("mees-ik", maakMapNaam("Mees & ik"))
        assertEquals("zelfportret", maakMapNaam("  Zelfportret  "))
    }

    @Test
    fun `lege naam levert een bruikbare mapnaam op`() {
        assertEquals("serie", maakMapNaam("***"))
    }

    @Test
    fun `botsende mapnamen krijgen een suffix`() {
        val bezet = setOf("mees", "mees-2")
        assertEquals("mees-3", maakUniekeMapNaam("mees") { it in bezet })
        assertEquals("anna", maakUniekeMapNaam("anna") { it in bezet })
    }
}

class DagindelingTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    @Test
    fun `kalenderdag is de standaard`() {
        val moment = LocalDateTime.of(2026, 9, 11, 23, 40).atZone(amsterdam).toInstant()
        assertEquals(LocalDate.of(2026, 9, 11), Dagindeling.dagSleutel(moment, 0, amsterdam))
    }

    @Test
    fun `met een dagstart van vier uur hoort de nacht bij de dag ervoor`() {
        val moment = LocalDateTime.of(2026, 9, 12, 1, 30).atZone(amsterdam).toInstant()
        assertEquals(LocalDate.of(2026, 9, 11), Dagindeling.dagSleutel(moment, 4, amsterdam))
    }

    @Test
    fun `met een dagstart van vier uur hoort de ochtend bij vandaag`() {
        val moment = LocalDateTime.of(2026, 9, 12, 7, 45).atZone(amsterdam).toInstant()
        assertEquals(LocalDate.of(2026, 9, 12), Dagindeling.dagSleutel(moment, 4, amsterdam))
    }
}
