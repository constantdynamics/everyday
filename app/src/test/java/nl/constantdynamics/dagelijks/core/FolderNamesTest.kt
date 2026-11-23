package nl.constantdynamics.dagelijks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderNamesTest {

    @Test
    fun `an ordinary name is kept as it is`() {
        assertEquals("Mijn gezicht", FolderNames.safeFolderName("Mijn gezicht"))
        assertEquals("De boom in de tuin", FolderNames.safeFolderName("  De boom   in de tuin "))
        assertEquals("Café René ☕", FolderNames.safeFolderName("Café René ☕"))
    }

    @Test
    fun `characters that Windows refuses are removed`() {
        assertEquals("Wie wat waar", FolderNames.safeFolderName("Wie/wat\\waar?"))
        assertEquals("a b c d e", FolderNames.safeFolderName("a:b*c\"d|e"))
        assertEquals("Tuin 2026", FolderNames.safeFolderName("Tuin <2026>"))
    }

    @Test
    fun `leading and trailing dots and spaces are dropped`() {
        assertEquals("verborgen", FolderNames.safeFolderName(".verborgen"))
        assertEquals("Einde", FolderNames.safeFolderName("Einde. . "))
    }

    @Test
    fun `nothing usable left gives a default name`() {
        assertEquals("Project", FolderNames.safeFolderName(""))
        assertEquals("Project", FolderNames.safeFolderName("///"))
        assertEquals("Project", FolderNames.safeFolderName(" . "))
    }

    @Test
    fun `names reserved by Windows are avoided`() {
        assertEquals("CON map", FolderNames.safeFolderName("CON"))
        assertEquals("nul map", FolderNames.safeFolderName("nul"))
        assertEquals("Conny", FolderNames.safeFolderName("Conny"))
    }

    @Test
    fun `very long names are shortened`() {
        val name = FolderNames.safeFolderName("a".repeat(200))
        assertTrue(name.length <= 60)
    }

    @Test
    fun `a taken name gets a number`() {
        assertEquals("Tuin", FolderNames.uniqueFolderName("Tuin", listOf("Gezicht")))
        assertEquals("Tuin 2", FolderNames.uniqueFolderName("Tuin", listOf("Tuin")))
        assertEquals("Tuin 3", FolderNames.uniqueFolderName("Tuin", listOf("Tuin", "Tuin 2")))
    }

    @Test
    fun `names that differ only in capitals count as the same`() {
        assertEquals("tuin 2", FolderNames.uniqueFolderName("tuin", listOf("TUIN")))
    }
}
