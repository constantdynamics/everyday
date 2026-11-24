package nl.constantdynamics.dagelijks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class PhotoNamesTest {

    @Test
    fun `a photo is named after its day`() {
        assertEquals("2026-11-23.jpg", PhotoNames.fileNameFor(LocalDate.of(2026, 11, 23)))
        assertEquals("2027-01-05.jpg", PhotoNames.fileNameFor(LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun `the day is read back from the file name`() {
        assertEquals(LocalDate.of(2026, 11, 23), PhotoNames.dateFromFileName("2026-11-23.jpg"))
        assertEquals(LocalDate.of(2026, 11, 23), PhotoNames.dateFromFileName("2026-11-23.JPG"))
        assertEquals(LocalDate.of(2026, 11, 23), PhotoNames.dateFromFileName("2026-11-23.jpeg"))
    }

    @Test
    fun `name and date survive a round trip for every day of a leap year`() {
        var day = LocalDate.of(2028, 1, 1)
        while (day.year == 2028) {
            assertEquals(day, PhotoNames.dateFromFileName(PhotoNames.fileNameFor(day)))
            day = day.plusDays(1)
        }
    }

    @Test
    fun `other files are not mistaken for photos`() {
        val notPhotos = listOf(
            "project.json",
            ".nomedia",
            "2026-11-23 (1).jpg", // a duplicate made by a file manager
            "2026-11-23.png",
            "2026-11-23.jpg.tmp",
            ".bezig-2026-11-23.jpg", // a photo that is still being written
            "IMG_20261123_101500.jpg",
            "2026-1-5.jpg",
            "26-11-23.jpg",
            "2026-11-23",
            "x2026-11-23.jpg",
        )
        notPhotos.forEach { assertNull(it, PhotoNames.dateFromFileName(it)) }
    }

    @Test
    fun `a date that does not exist is rejected`() {
        assertNull(PhotoNames.dateFromFileName("2026-02-30.jpg"))
        assertNull(PhotoNames.dateFromFileName("2026-13-01.jpg"))
        assertNull(PhotoNames.dateFromFileName("2026-00-10.jpg"))
        assertNull(PhotoNames.dateFromFileName("2027-02-29.jpg")) // 2027 is not a leap year
        assertEquals(LocalDate.of(2028, 2, 29), PhotoNames.dateFromFileName("2028-02-29.jpg"))
    }

    @Test
    fun `alphabetical order of file names is chronological order`() {
        val days = listOf(
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2026, 2, 10),
            LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 23),
            LocalDate.of(2026, 2, 9),
        )
        val sortedByName = days.map(PhotoNames::fileNameFor).sorted().map(PhotoNames::dateFromFileName)
        assertEquals(days.sorted(), sortedByName)
    }

    @Test
    fun `a timelapse is named after the project and its period`() {
        assertEquals(
            "Mijn gezicht_2026-11-23_tot_2027-11-23.mp4",
            PhotoNames.timelapseFileName("Mijn gezicht", LocalDate.of(2026, 11, 23), LocalDate.of(2027, 11, 23)),
        )
    }

    @Test
    fun `file names have ordinary digits whatever language the phone is set to`() {
        val usual = Locale.getDefault()
        try {
            // Arabic as written in Egypt counts with its own digits.
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("2026-11-23.jpg", PhotoNames.fileNameFor(LocalDate.of(2026, 11, 23)))
            assertEquals(
                "Tuin_2026-11-23_tot_2027-01-05.mp4",
                PhotoNames.timelapseFileName("Tuin", LocalDate.of(2026, 11, 23), LocalDate.of(2027, 1, 5)),
            )
        } finally {
            Locale.setDefault(usual)
        }
    }

    @Test
    fun `a file that is still being written carries its final name and its size`() {
        val name = PhotoNames.temporaryNameFor("2026-11-23.jpg", 4_812_345)
        assertEquals(".bezig-4812345-2026-11-23.jpg", name)
        assertEquals(PhotoNames.TemporaryTarget("2026-11-23.jpg", 4_812_345), PhotoNames.temporaryTarget(name))

        val video = PhotoNames.temporaryNameFor("Mijn gezicht_2026-11-23_tot_2027-11-23.mp4", 12)
        assertEquals("Mijn gezicht_2026-11-23_tot_2027-11-23.mp4", PhotoNames.temporaryTarget(video)!!.finalName)
    }

    @Test
    fun `a temporary file is never taken for a photo, and a photo never for a temporary file`() {
        assertNull(PhotoNames.dateFromFileName(PhotoNames.temporaryNameFor("2026-11-23.jpg", 100)))
        assertNull(PhotoNames.temporaryTarget("2026-11-23.jpg"))
        assertNull(PhotoNames.temporaryTarget(".nomedia"))
        assertNull(PhotoNames.temporaryTarget(".bezig-zonder-grootte.jpg"))
    }
}
