package nl.constantdynamics.dagelijks.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.Lens
import nl.constantdynamics.dagelijks.core.PixelSize
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * The folder on disk is the source of truth. These tests run the real repository
 * on a real (temporary) folder and look at the files it leaves behind.
 */
class ProjectRepositoryTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var root: File
    private lateinit var tree: LocalFileTree
    private val today = LocalDate.of(2026, 11, 23)

    @Before
    fun setUp() {
        root = temporary.newFolder("Dagelijks")
        tree = LocalFileTree(root)
    }

    private fun repository(photoSize: PixelSize? = null) = ProjectRepository(
        tree = tree,
        rootId = tree.pickedFolderId,
        io = Dispatchers.Unconfined,
        today = { today },
        photoSize = { photoSize },
    )

    private fun jpeg(content: String): File = temporary.newFile().apply { writeText(content) }

    private fun File.names(): List<String> = list()!!.sorted()

    // --- Creating projects -------------------------------------------------------------------

    @Test
    fun `a new project gets its own folder with a project json`() = runBlocking {
        val repository = repository()
        val project = repository.createProject("Mijn gezicht", Lens.FRONT, AspectRatio.PORTRAIT_9_16)

        assertEquals("Mijn gezicht", project.folderName)
        assertEquals(listOf(".nomedia", "Mijn gezicht"), root.names())
        assertEquals(listOf("project.json"), File(root, "Mijn gezicht").names())
        assertEquals(Lens.FRONT, project.settings.lens)
        assertEquals(AspectRatio.PORTRAIT_9_16, project.settings.aspectRatio)
        assertEquals(35, project.settings.overlayPercent)
        assertEquals(today, project.settings.createdOn)
        assertTrue(project.photos.isEmpty())
    }

    @Test
    fun `the main folder is hidden from galleries before anything else is written`() = runBlocking {
        repository().refresh()
        assertEquals(listOf(".nomedia"), root.names())
        assertEquals(0L, File(root, ".nomedia").length())
    }

    @Test
    fun `several projects live side by side, each with its own camera and ratio`() = runBlocking {
        val repository = repository()
        repository.createProject("Mijn gezicht", Lens.FRONT, AspectRatio.PORTRAIT_3_4)
        repository.createProject("De boom in de tuin", Lens.BACK, AspectRatio.LANDSCAPE_16_9)

        val projects = repository.refresh()
        assertEquals(listOf("De boom in de tuin", "Mijn gezicht"), projects.map { it.settings.name })
        assertEquals(Lens.BACK, projects[0].settings.lens)
        assertEquals(AspectRatio.LANDSCAPE_16_9, projects[0].settings.aspectRatio)
        assertEquals(Lens.FRONT, projects[1].settings.lens)
    }

    @Test
    fun `two projects with the same name get different folders`() = runBlocking {
        val repository = repository()
        val first = repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        val second = repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        assertEquals("Tuin", first.folderName)
        assertEquals("Tuin 2", second.folderName)
        assertEquals("Tuin", second.settings.name)
    }

    @Test
    fun `a name with characters a folder cannot have keeps its spelling in the app`() = runBlocking {
        val project = repository().createProject("Wie/wat: waar?", Lens.BACK, AspectRatio.SQUARE)
        assertEquals("Wie wat waar", project.folderName)
        assertEquals("Wie/wat: waar?", project.settings.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a project needs a name`() {
        runBlocking { repository().createProject("   ", Lens.BACK, AspectRatio.SQUARE) }
    }

    // --- Photos ------------------------------------------------------------------------------

    @Test
    fun `a photo is stored under the name of its day`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        val project = repository.savePhoto("Tuin", today, jpeg("vandaag"))

        assertEquals(listOf("2026-11-23.jpg", "project.json"), File(root, "Tuin").names())
        assertEquals("vandaag", File(root, "Tuin/2026-11-23.jpg").readText())
        assertEquals(listOf(today), project.dates)
        assertNotNull(project.photoOn(today))
    }

    @Test
    fun `photos come back sorted by date whatever the order they were added in`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today, jpeg("c"))
        repository.savePhoto("Tuin", today.minusDays(2), jpeg("a"))
        val project = repository.savePhoto("Tuin", today.minusDays(1), jpeg("b"))

        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), project.dates)
        assertEquals(today, project.latestPhoto!!.date)
    }

    @Test
    fun `saving again on the same day replaces the photo and leaves nothing else behind`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today, jpeg("eerste poging"))
        val project = repository.savePhoto("Tuin", today, jpeg("tweede"))

        assertEquals(listOf("2026-11-23.jpg", "project.json"), File(root, "Tuin").names())
        assertEquals("tweede", File(root, "Tuin/2026-11-23.jpg").readText())
        assertEquals(1, project.photos.size)
    }

    @Test
    fun `a replaced photo gets a new cache key so no old picture is shown`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        val before = repository.savePhoto("Tuin", today, jpeg("kort")).photoOn(today)!!
        val after = repository.savePhoto("Tuin", today, jpeg("een flink stuk langer")).photoOn(today)!!
        assertFalse(before.cacheKey == after.cacheKey)
    }

    @Test
    fun `a failed replacement keeps the old photo`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today, jpeg("origineel"))

        val missing = File(temporary.root, "bestaat-niet.jpg")
        val failure = runCatching { repository.savePhoto("Tuin", today, missing) }

        assertTrue(failure.isFailure)
        assertEquals("origineel", File(root, "Tuin/2026-11-23.jpg").readText())
        assertEquals(listOf("2026-11-23.jpg", "project.json"), File(root, "Tuin").names())
    }

    @Test
    fun `deleting removes exactly that day's photo`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today.minusDays(1), jpeg("gisteren"))
        repository.savePhoto("Tuin", today, jpeg("vandaag"))

        val project = repository.deletePhoto("Tuin", today)

        assertEquals(listOf(today.minusDays(1)), project.dates)
        assertEquals(listOf("2026-11-22.jpg", "project.json"), File(root, "Tuin").names())
    }

    @Test
    fun `photos copied in by hand are picked up, other files are ignored`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        val folder = File(root, "Tuin")
        File(folder, "2026-01-05.jpg").writeText("oud")
        File(folder, "2026-01-06.JPG").writeText("oud")
        File(folder, "vakantie.jpg").writeText("geen dagfoto")
        File(folder, "2026-01-07 (1).jpg").writeText("dubbel")
        File(folder, "notities.txt").writeText("x")

        val project = repository.refresh().single()
        assertEquals(listOf(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6)), project.dates)
    }

    // --- Settings ----------------------------------------------------------------------------

    @Test
    fun `the overlay strength is remembered per project`() = runBlocking {
        val repository = repository()
        repository.createProject("Gezicht", Lens.FRONT, AspectRatio.PORTRAIT_3_4)
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)

        repository.updateSettings("Gezicht") { it.copy(overlayPercent = 60) }

        val fresh = repository().refresh().associateBy { it.folderName }
        assertEquals(60, fresh.getValue("Gezicht").settings.overlayPercent)
        assertEquals(35, fresh.getValue("Tuin").settings.overlayPercent)
    }

    @Test
    fun `the aspect ratio can change until the first photo and is fixed after it`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)

        val changed = repository.updateSettings("Tuin") { it.copy(aspectRatio = AspectRatio.PORTRAIT_9_16) }
        assertEquals(AspectRatio.PORTRAIT_9_16, changed.settings.aspectRatio)

        repository.savePhoto("Tuin", today, jpeg("foto"))
        val locked = repository.updateSettings("Tuin") { it.copy(aspectRatio = AspectRatio.LANDSCAPE_16_9, name = "Achtertuin") }
        assertEquals(AspectRatio.PORTRAIT_9_16, locked.settings.aspectRatio)
        assertEquals("Achtertuin", locked.settings.name)
        assertFalse(locked.canChangeAspectRatio)
    }

    @Test
    fun `renaming changes the name shown, not the folder`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        val renamed = repository.updateSettings("Tuin") { it.copy(name = "Achtertuin") }
        assertEquals("Tuin", renamed.folderName)
        assertEquals("Achtertuin", renamed.settings.name)
        assertTrue(File(root, "Tuin").isDirectory)
    }

    // --- After a reinstall -------------------------------------------------------------------

    @Test
    fun `picking the folder again after a reinstall brings back every project and setting`() = runBlocking {
        val before = repository()
        before.createProject("Mijn gezicht", Lens.FRONT, AspectRatio.PORTRAIT_9_16)
        before.createProject("De boom", Lens.BACK, AspectRatio.LANDSCAPE_16_9)
        before.updateSettings("Mijn gezicht") { it.copy(overlayPercent = 50, timelapseFps = 24) }
        before.savePhoto("Mijn gezicht", today.minusDays(1), jpeg("a"))
        before.savePhoto("Mijn gezicht", today, jpeg("b"))

        // A new repository knows nothing: everything has to come from the folder.
        val after = ProjectRepository(LocalFileTree(root), root.absolutePath, Dispatchers.Unconfined, { today })
        assertNull(after.projects.value)
        val projects = after.refresh().associateBy { it.folderName }

        assertEquals(setOf("Mijn gezicht", "De boom"), projects.keys)
        val face = projects.getValue("Mijn gezicht")
        assertEquals(Lens.FRONT, face.settings.lens)
        assertEquals(AspectRatio.PORTRAIT_9_16, face.settings.aspectRatio)
        assertEquals(50, face.settings.overlayPercent)
        assertEquals(24, face.settings.timelapseFps)
        assertEquals(listOf(today.minusDays(1), today), face.dates)
        assertEquals(AspectRatio.LANDSCAPE_16_9, projects.getValue("De boom").settings.aspectRatio)
    }

    @Test
    fun `a project that lost its project json is rebuilt from its photos`() = runBlocking {
        val folder = File(root, "Oud project").apply { mkdirs() }
        File(folder, "2026-03-01.jpg").writeText("foto")
        File(folder, "2026-03-02.jpg").writeText("foto")

        val project = repository(photoSize = PixelSize(2250, 4000)).refresh().single()

        assertEquals("Oud project", project.settings.name)
        assertEquals(AspectRatio.PORTRAIT_9_16, project.settings.aspectRatio)
        assertEquals(2, project.photos.size)
        // And it is written back, so the next start reads it normally.
        assertTrue(File(folder, "project.json").isFile)
    }

    @Test
    fun `a damaged project json does not make the project disappear`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today, jpeg("foto"))
        File(root, "Tuin/project.json").writeText("{ kapot")

        val project = repository(photoSize = PixelSize(3000, 3000)).refresh().single()
        assertEquals("Tuin", project.folderName)
        assertEquals(AspectRatio.SQUARE, project.settings.aspectRatio)
        assertEquals(listOf(today), project.dates)
    }

    @Test
    fun `folders that have nothing to do with the app are left alone`() = runBlocking {
        File(root, "Belastingpapieren").mkdirs()
        File(root, "Belastingpapieren/aangifte.pdf").writeText("x")
        File(root, "los bestand.txt").writeText("x")

        assertTrue(repository().refresh().isEmpty())
        assertEquals(listOf("aangifte.pdf"), File(root, "Belastingpapieren").names())
    }

    // --- Timelapses --------------------------------------------------------------------------

    @Test
    fun `a timelapse lands in the timelapses folder of the project`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        val video = temporary.newFile().apply { writeText("video") }

        val saved = repository.saveTimelapse("Tuin", "Tuin_2026-11-23_tot_2027-11-23.mp4", video)

        assertEquals("Tuin_2026-11-23_tot_2027-11-23.mp4", saved.name)
        assertEquals("video", File(root, "Tuin/timelapses/Tuin_2026-11-23_tot_2027-11-23.mp4").readText())
        assertEquals(listOf(saved.name), repository.project("Tuin")!!.timelapses.map { it.name })
    }

    @Test
    fun `rendering the same period again replaces the earlier video`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.saveTimelapse("Tuin", "Tuin_a_tot_b.mp4", temporary.newFile().apply { writeText("eerste") })
        repository.saveTimelapse("Tuin", "Tuin_a_tot_b.mp4", temporary.newFile().apply { writeText("tweede versie") })

        assertEquals(listOf("Tuin_a_tot_b.mp4"), File(root, "Tuin/timelapses").names())
        assertEquals("tweede versie", File(root, "Tuin/timelapses/Tuin_a_tot_b.mp4").readText())
    }

    @Test
    fun `timelapses are not mistaken for photos and can be deleted`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today, jpeg("foto"))
        val saved = repository.saveTimelapse("Tuin", "Tuin_a_tot_b.mp4", temporary.newFile().apply { writeText("v") })

        assertEquals(1, repository.project("Tuin")!!.photos.size)
        val project = repository.deleteTimelapse("Tuin", saved)
        assertTrue(project.timelapses.isEmpty())
        assertEquals(1, project.photos.size)
    }

    // --- Deleting a project, housekeeping ----------------------------------------------------

    @Test
    fun `deleting a project removes its folder and nothing else`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.createProject("Gezicht", Lens.FRONT, AspectRatio.PORTRAIT_3_4)
        repository.savePhoto("Tuin", today, jpeg("foto"))

        repository.deleteProject("Tuin")

        assertEquals(listOf(".nomedia", "Gezicht"), root.names())
        assertEquals(listOf("Gezicht"), repository.projects.value!!.map { it.folderName })
    }

    @Test
    fun `a half-written photo from an interrupted save is cleaned up later`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        val stale = File(root, "Tuin/.bezig-2026-11-20.jpg").apply {
            writeText("half")
            setLastModified(System.currentTimeMillis() - 3 * 60 * 60 * 1000L)
        }
        val recent = File(root, "Tuin/.bezig-2026-11-21.jpg").apply { writeText("bezig") }

        val project = repository.refresh().single()

        assertTrue(project.photos.isEmpty())
        assertFalse(stale.exists())
        assertTrue(recent.exists()) // may still be in use
    }

    @Test(expected = IOException::class)
    fun `saving into a project that no longer exists fails clearly`() {
        runBlocking { repository().savePhoto("Verdwenen", today, jpeg("x")) }
    }
}
