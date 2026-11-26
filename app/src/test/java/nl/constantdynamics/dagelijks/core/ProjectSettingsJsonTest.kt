package nl.constantdynamics.dagelijks.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectSettingsJsonTest {

    private val settings = ProjectSettings(
        name = "Mijn gezicht",
        lens = Lens.FRONT,
        aspectRatio = AspectRatio.PORTRAIT_9_16,
        overlayPercent = 42,
        timelapseFps = 12,
        createdOn = LocalDate.of(2026, 11, 23),
    )

    @Test
    fun `settings survive being written and read back`() {
        assertEquals(settings, ProjectSettingsJson.decode(ProjectSettingsJson.encode(settings), "map"))
    }

    @Test
    fun `every combination of camera and ratio survives`() {
        for (lens in Lens.entries) {
            for (ratio in AspectRatio.entries) {
                val variant = settings.copy(lens = lens, aspectRatio = ratio)
                assertEquals(variant, ProjectSettingsJson.decode(ProjectSettingsJson.encode(variant), "map"))
            }
        }
    }

    @Test
    fun `the file is readable Dutch for whoever opens it`() {
        val json = JSONObject(ProjectSettingsJson.encode(settings))
        assertEquals("Mijn gezicht", json.getString("naam"))
        assertEquals("voor", json.getString("camera"))
        assertEquals("9:16", json.getString("beeldverhouding"))
        assertEquals(42, json.getInt("overlayZichtbaarheid"))
        assertEquals(12, json.getInt("timelapseFotosPerSeconde"))
        assertEquals("2026-11-23", json.getString("aangemaakt"))
    }

    @Test
    fun `names with quotes and emoji are stored safely`() {
        val tricky = settings.copy(name = "Zeg \"kaas\" \\ 😀 é")
        assertEquals(tricky, ProjectSettingsJson.decode(ProjectSettingsJson.encode(tricky), "map"))
    }

    @Test
    fun `missing optional values fall back to the defaults`() {
        val decoded = ProjectSettingsJson.decode("""{"camera":"achter","beeldverhouding":"1:1"}""", "Tuin")
        assertNotNull(decoded)
        assertEquals("Tuin", decoded!!.name) // the folder name stands in for a missing name
        assertEquals(Lens.BACK, decoded.lens)
        assertEquals(AspectRatio.SQUARE, decoded.aspectRatio)
        assertEquals(35, decoded.overlayPercent)
        assertEquals(10, decoded.timelapseFps)
        assertNull(decoded.createdOn)
    }

    @Test
    fun `values outside their range are pulled back in`() {
        val decoded = ProjectSettingsJson.decode(
            """{"camera":"voor","beeldverhouding":"3:4","overlayZichtbaarheid":250,"timelapseFotosPerSeconde":0}""",
            "x",
        )!!
        assertEquals(100, decoded.overlayPercent)
        assertEquals(1, decoded.timelapseFps)
    }

    @Test
    fun `a damaged file is reported as unreadable instead of guessed`() {
        assertNull(ProjectSettingsJson.decode("", "x"))
        assertNull(ProjectSettingsJson.decode("{ half", "x"))
        assertNull(ProjectSettingsJson.decode("""{"naam":"zonder camera"}""", "x"))
        assertNull(ProjectSettingsJson.decode("""{"camera":"voor","beeldverhouding":"4:3"}""", "x"))
        assertNull(ProjectSettingsJson.decode("""{"camera":"zijkant","beeldverhouding":"3:4"}""", "x"))
    }

    @Test
    fun `an unreadable creation date does not make the whole file unreadable`() {
        val decoded = ProjectSettingsJson.decode(
            """{"camera":"voor","beeldverhouding":"3:4","aangemaakt":"gisteren"}""",
            "x",
        )
        assertNotNull(decoded)
        assertNull(decoded!!.createdOn)
    }

    @Test
    fun `the default overlay is about a third`() {
        assertEquals(35, ProjectSettings.DEFAULT_OVERLAY_PERCENT)
        assertTrue(ProjectSettings.DEFAULT_TIMELAPSE_FPS in 1..30)
    }
}
