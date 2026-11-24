package nl.constantdynamics.dagelijks.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The back stack. A screen that is no longer on it has to be reported, because that is
 * the moment its remembered state (the page the viewer was on, a half-filled form) is
 * thrown away; a screen that is merely covered by another one keeps its state.
 */
class NavigatorTest {

    private val day = LocalDate.of(2026, 11, 20)
    private val left = ArrayList<Screen>()
    private val stack = arrayListOf<Screen>(Screen.Home)
    private val navigator = Navigator(stack) { left += it }

    @Test
    fun `going back reports the screen that was closed, and only that one`() {
        navigator.open(Screen.Gallery("Tuin"))
        navigator.open(Screen.Viewer("Tuin", day))
        assertTrue(left.isEmpty())

        navigator.back()

        assertEquals(listOf<Screen>(Screen.Viewer("Tuin", day)), left)
        assertEquals(Screen.Gallery("Tuin"), navigator.current)
    }

    @Test
    fun `a screen that is only covered by another one is not reported`() {
        navigator.open(Screen.Gallery("Tuin"))
        navigator.open(Screen.Viewer("Tuin", day))
        navigator.open(Screen.Camera("Tuin", day))
        navigator.back()

        assertEquals(listOf<Screen>(Screen.Camera("Tuin", day)), left)
        assertEquals(Screen.Viewer("Tuin", day), navigator.current)
    }

    @Test
    fun `the start screen is never closed`() {
        navigator.back()
        assertFalse(navigator.canGoBack)
        assertEquals(Screen.Home, navigator.current)
        assertTrue(left.isEmpty())
    }

    @Test
    fun `replacing a screen reports the one that made way`() {
        navigator.open(Screen.NewProject)
        navigator.replace(Screen.Gallery("Tuin"))

        assertEquals(listOf<Screen>(Screen.NewProject), left)
        assertEquals(listOf(Screen.Home, Screen.Gallery("Tuin")), navigator.screens)
    }

    @Test
    fun `a deleted project takes all its screens with it`() {
        navigator.open(Screen.Gallery("Tuin"))
        navigator.open(Screen.Viewer("Tuin", day))
        navigator.open(Screen.Camera("Tuin", day))

        navigator.closeProject("Tuin")

        assertEquals(listOf<Screen>(Screen.Home), navigator.screens)
        assertEquals(
            setOf(Screen.Gallery("Tuin"), Screen.Viewer("Tuin", day), Screen.Camera("Tuin", day)),
            left.toSet(),
        )
    }

    @Test
    fun `screens of other projects stay when a project is deleted`() {
        navigator.open(Screen.Gallery("Gezicht"))
        navigator.closeProject("Tuin")

        assertEquals(listOf(Screen.Home, Screen.Gallery("Gezicht")), navigator.screens)
        assertTrue(left.isEmpty())
    }

    @Test
    fun `every screen survives being written down and read back`() {
        val screens = listOf(
            Screen.Home,
            Screen.NewProject,
            Screen.About,
            Screen.Gallery("Mijn gezicht"),
            Screen.Camera("Mijn gezicht"),
            Screen.Camera("Mijn gezicht", day),
            Screen.Viewer("Mijn gezicht", day),
            Screen.Timelapse("Mijn gezicht"),
            Screen.Player("Mijn gezicht", "Mijn gezicht_2026-11-20_tot_2026-11-23.mp4"),
        )
        assertEquals(screens, screens.map { Screen.decode(it.encode()) })
        // Each one gets a key of its own, so they never share remembered state.
        assertEquals(screens.size, screens.map { it.encode() }.toSet().size)
    }
}
