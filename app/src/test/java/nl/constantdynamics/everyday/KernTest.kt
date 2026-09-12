package nl.constantdynamics.everyday

import nl.constantdynamics.everyday.kern.Bewerking
import nl.constantdynamics.everyday.kern.Dagindeling
import nl.constantdynamics.everyday.kern.datumUitBestandsnaam
import nl.constantdynamics.everyday.kern.grootsteRechthoekNaDraaien
import nl.constantdynamics.everyday.kern.maakMapNaam
import nl.constantdynamics.everyday.kern.maakUniekeMapNaam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

class BewerkingTest {

    @Test
    fun `zonder wijzigingen is de bewerking leeg`() {
        assertTrue(Bewerking().isLeeg)
        assertFalse(Bewerking(kwartslagen = 1).isLeeg)
        assertFalse(Bewerking(rechtzetHoek = 3f).isLeeg)
        assertFalse(Bewerking(links = 0.1f).isLeeg)
    }

    @Test
    fun `kwartslagen lopen rond en worden graden`() {
        assertEquals(0, Bewerking(kwartslagen = 0).graden)
        assertEquals(270, Bewerking(kwartslagen = 3).graden)
        assertEquals(0, Bewerking(kwartslagen = 4).graden)
        assertEquals(270, Bewerking(kwartslagen = -1).graden)
    }

    @Test
    fun `zonder hoek blijft het beeld ongemoeid`() {
        val (breedte, hoogte) = grootsteRechthoekNaDraaien(4000f, 3000f, 0f)
        assertEquals(4000f, breedte, 0.01f)
        assertEquals(3000f, hoogte, 0.01f)
    }

    @Test
    fun `rechtzetten levert een kleiner beeld met dezelfde verhouding op`() {
        val (breedte, hoogte) = grootsteRechthoekNaDraaien(4000f, 3000f, 10f)
        assertTrue("breedte moet krimpen", breedte < 4000f)
        assertTrue("hoogte moet krimpen", hoogte < 3000f)
        assertTrue("maar niet te veel", breedte > 3000f)
        assertEquals(4000f / 3000f, breedte / hoogte, 0.02f)
    }

    @Test
    fun `links en rechts draaien geeft hetzelfde resultaat`() {
        val (breedteLinks, hoogteLinks) = grootsteRechthoekNaDraaien(3000f, 4000f, -7f)
        val (breedteRechts, hoogteRechts) = grootsteRechthoekNaDraaien(3000f, 4000f, 7f)
        assertEquals(breedteLinks, breedteRechts, 0.01f)
        assertEquals(hoogteLinks, hoogteRechts, 0.01f)
    }

    @Test
    fun `een vierkant beeld krimpt bij elke hoek`() {
        val (breedte, hoogte) = grootsteRechthoekNaDraaien(1000f, 1000f, 15f)
        assertEquals(breedte, hoogte, 0.01f)
        assertTrue(breedte < 1000f)
    }
}

class DatumUitNaamTest {

    @Test
    fun `herkent de gangbare camerabestandsnamen`() {
        assertEquals(
            LocalDateTime.of(2024, 6, 12, 15, 30, 45),
            datumUitBestandsnaam("IMG_20240612_153045.jpg"),
        )
        assertEquals(
            LocalDateTime.of(2024, 6, 12, 15, 30, 45),
            datumUitBestandsnaam("PXL_20240612_153045.jpg"),
        )
        assertEquals(
            LocalDateTime.of(2024, 6, 12, 15, 30, 45),
            datumUitBestandsnaam("2024-06-12 15.30.45.jpg"),
        )
        assertEquals(
            LocalDateTime.of(2024, 6, 12, 15, 30, 45),
            datumUitBestandsnaam("Screenshot_20240612-153045.png"),
        )
    }

    @Test
    fun `valt terug op alleen een datum`() {
        assertEquals(
            LocalDateTime.of(2024, 6, 12, 0, 0),
            datumUitBestandsnaam("vakantie-2024-06-12.jpg"),
        )
    }

    @Test
    fun `weigert onmogelijke en ongeloofwaardige datums`() {
        assertNull(datumUitBestandsnaam("IMG_20241332_153045.jpg"))
        assertNull(datumUitBestandsnaam("IMG_18700612_153045.jpg"))
        assertNull(datumUitBestandsnaam("zonder datum.jpg"))
        assertNull(datumUitBestandsnaam("IMG_1234.jpg"))
    }

    @Test
    fun `bij een onmogelijke tijd blijft de datum wel staan`() {
        assertEquals(
            LocalDateTime.of(2024, 6, 12, 0, 0),
            datumUitBestandsnaam("IMG_20240612_256045.jpg"),
        )
    }

    @Test
    fun `laat zich niet misleiden door een langer getal`() {
        assertNull(datumUitBestandsnaam("bestand_123456789012.jpg"))
    }
}
