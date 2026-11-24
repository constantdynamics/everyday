package nl.constantdynamics.dagelijks.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.Lens
import nl.constantdynamics.dagelijks.core.PixelSize
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
import java.io.InputStream
import java.io.OutputStream
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
        root = temporary.newFolder("Everyday")
        tree = LocalFileTree(root)
    }

    private fun repository(photoSize: PixelSize? = null) = ProjectRepository(
        tree = tree,
        rootId = tree.pickedFolderId,
        io = Dispatchers.Unconfined,
        today = { today },
        photoSize = { photoSize },
    )

    private fun repositoryOn(tree: FileTree, photoSize: (InputStream) -> PixelSize? = { null }) = ProjectRepository(
        tree = tree,
        rootId = tree.pickedFolderId,
        io = Dispatchers.Unconfined,
        today = { today },
        photoSize = photoSize,
    )

    private fun jpeg(content: String): File = temporary.newFile().apply { writeText(content) }

    private fun hoursAgo(hours: Int): Long = System.currentTimeMillis() - hours * 60 * 60 * 1000L

    private fun File.names(): List<String> = list()!!.sorted()

    // --- Creating projects -------------------------------------------------------------------

    @Test
    fun `a new project gets its own folder with a project json`() = runBlocking {
        val repository = repository()
        val project = repository.createProject("Mijn gezicht", Lens.FRONT, AspectRatio.PORTRAIT_9_16)

        assertEquals("Mijn gezicht", project.folderName)
        assertEquals(listOf(".nomedia", "Mijn gezicht"), root.names())
        assertEquals(listOf(".nomedia", "project.json"), File(root, "Mijn gezicht").names())
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

        assertEquals(listOf(".nomedia", "2026-11-23.jpg", "project.json"), File(root, "Tuin").names())
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

        assertEquals(listOf(".nomedia", "2026-11-23.jpg", "project.json"), File(root, "Tuin").names())
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
        assertEquals(listOf(".nomedia", "2026-11-23.jpg", "project.json"), File(root, "Tuin").names())
    }

    @Test
    fun `deleting removes exactly that day's photo`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today.minusDays(1), jpeg("gisteren"))
        repository.savePhoto("Tuin", today, jpeg("vandaag"))

        val project = repository.deletePhoto("Tuin", today)

        assertEquals(listOf(today.minusDays(1)), project.dates)
        assertEquals(listOf(".nomedia", "2026-11-22.jpg", "project.json"), File(root, "Tuin").names())
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
    fun `a project json that cannot be understood is kept next to a fresh one`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.FRONT, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today, jpeg("foto"))
        File(root, "Tuin/project.json").writeText("{ \"naam\": \"Mijn tuin\", kapot")

        repository(photoSize = PixelSize(3000, 3000)).refresh()

        assertEquals("{ \"naam\": \"Mijn tuin\", kapot", File(root, "Tuin/project-beschadigd.json").readText())
        // The fresh file is a normal one again: the next start reads it without rebuilding.
        val again = repository().refresh().single()
        assertEquals(AspectRatio.SQUARE, again.settings.aspectRatio)
        assertEquals(
            listOf(".nomedia", "2026-11-23.jpg", "project-beschadigd.json", "project.json"),
            File(root, "Tuin").names(),
        )
    }

    @Test
    fun `an empty project json is simply written again`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today, jpeg("foto"))
        File(root, "Tuin/project.json").writeText("")

        val project = repository(photoSize = PixelSize(3000, 3000)).refresh().single()

        assertEquals(AspectRatio.SQUARE, project.settings.aspectRatio)
        assertTrue(File(root, "Tuin/project.json").length() > 0)
        assertEquals(listOf(".nomedia", "2026-11-23.jpg", "project.json"), File(root, "Tuin").names())
    }

    @Test
    fun `the shape of a rebuilt project follows the newest photo that can be read`() = runBlocking {
        val folder = File(root, "Oud project").apply { mkdirs() }
        File(folder, "2026-03-01.jpg").writeText("goed")
        File(folder, "2026-03-02.jpg").writeText("goed")
        File(folder, "2026-03-03.jpg").writeText("onleesbaar")

        val project = repositoryOn(tree) { stream ->
            if (stream.readBytes().toString(Charsets.UTF_8) == "goed") PixelSize(2250, 4000) else null
        }.refresh().single()

        assertEquals(AspectRatio.PORTRAIT_9_16, project.settings.aspectRatio)
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
        val stale = File(root, "Tuin/.bezig-4000-2026-11-20.jpg").apply {
            writeText("half")
            setLastModified(hoursAgo(3))
        }
        val recent = File(root, "Tuin/.bezig-4000-2026-11-21.jpg").apply { writeText("bezig") }

        val project = repository.refresh().single()

        assertTrue(project.photos.isEmpty())
        assertFalse(stale.exists())
        assertTrue(recent.exists()) // may still be in use
    }

    @Test
    fun `a photo whose save was stopped right before the last step still becomes the photo of its day`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        // Complete (the size in the name is reached) and nothing else claims that day.
        File(root, "Tuin/.bezig-9-2026-11-20.jpg").apply {
            writeText("helemaal!")
            setLastModified(hoursAgo(3))
        }

        val project = repository.refresh().single()

        assertEquals(listOf(LocalDate.of(2026, 11, 20)), project.dates)
        assertEquals("helemaal!", File(root, "Tuin/2026-11-20.jpg").readText())
        assertEquals(listOf(".nomedia", "2026-11-20.jpg", "project.json"), File(root, "Tuin").names())
    }

    @Test
    fun `a complete temporary photo never pushes aside the photo that is already there`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today, jpeg("de echte"))
        File(root, "Tuin/.bezig-6-2026-11-23.jpg").apply {
            writeText("andere")
            setLastModified(hoursAgo(3))
        }

        val project = repository.refresh().single()

        assertEquals("de echte", File(project.photoOn(today)!!.nodeId).readText())
        assertEquals(listOf(".nomedia", "2026-11-23.jpg", "project.json"), File(root, "Tuin").names())
    }

    @Test
    fun `an empty file with the name of a day is not a photo and does not block that day`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        File(root, "Tuin/2026-11-23.jpg").writeText("")

        assertTrue(repository.refresh().single().photos.isEmpty())

        val project = repository.savePhoto("Tuin", today, jpeg("nu echt"))
        assertEquals(listOf(today), project.dates)
        assertEquals("nu echt", File(root, "Tuin/2026-11-23.jpg").readText())
        assertEquals(listOf(".nomedia", "2026-11-23.jpg", "project.json"), File(root, "Tuin").names())
    }

    @Test
    fun `every project folder is hidden from galleries on its own`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        assertEquals(0L, File(root, "Tuin/.nomedia").length())

        // A project copied in by hand, or made by an earlier version of the app.
        val copied = File(root, "Oud project").apply { mkdirs() }
        File(copied, "2026-03-01.jpg").writeText("foto")
        repository.refresh()
        assertTrue(File(copied, ".nomedia").isFile)
    }

    @Test
    fun `a storage that cannot rename still stores and replaces photos, leaving nothing behind`() = runBlocking {
        val repository = repositoryOn(object : FileTree by tree {
            override fun rename(id: String, newName: String): Node? = null
        })
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.savePhoto("Tuin", today, jpeg("eerste"))
        val project = repository.savePhoto("Tuin", today, jpeg("tweede poging"))

        assertEquals("tweede poging", File(project.photoOn(today)!!.nodeId).readText())
        assertEquals(listOf(".nomedia", "2026-11-23.jpg", "project.json"), File(root, "Tuin").names())
    }

    // --- The app dying half-way --------------------------------------------------------------

    /** Stands in for the app being killed: unlike an exception, nothing gets the chance to clean up. */
    private class Killed : Error()

    /**
     * Lets changes to the folder through until the app "dies" at change number [dieAt].
     * Opening a file for writing and writing into it count separately, so the app also
     * dies with an empty file and with half a file. Once dead, nothing reaches the disk.
     */
    private class DyingTree(private val disk: FileTree, private val dieAt: Int) : FileTree {
        private var changes = 0
        var died = false
            private set

        private fun alive() { if (died) throw Killed() }
        private fun change() {
            alive()
            if (changes++ == dieAt) { died = true; throw Killed() }
        }

        override val pickedFolderId: String get() = disk.pickedFolderId
        override fun nameOf(id: String): String? { alive(); return disk.nameOf(id) }
        override fun list(directoryId: String): List<Node> { alive(); return disk.list(directoryId) }
        override fun openInput(id: String): InputStream { alive(); return disk.openInput(id) }
        override fun location(id: String): String = disk.location(id)
        override fun createDirectory(parentId: String, name: String): Node { change(); return disk.createDirectory(parentId, name) }
        override fun createFile(parentId: String, name: String, mimeType: String): Node { change(); return disk.createFile(parentId, name, mimeType) }
        override fun delete(id: String): Boolean { change(); return disk.delete(id) }
        override fun rename(id: String, newName: String): Node? { change(); return disk.rename(id, newName) }

        override fun openOutput(id: String): OutputStream {
            alive()
            val real = disk.openOutput(id) // from here on the file is empty
            try { change() } catch (killed: Killed) { real.close(); throw killed }
            return object : OutputStream() {
                private var first = true
                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    alive()
                    if (first) {
                        first = false
                        try { change() } catch (killed: Killed) {
                            real.write(bytes, offset, length / 2)
                            real.close()
                            throw killed
                        }
                    }
                    real.write(bytes, offset, length)
                }
                override fun flush() = real.flush()
                override fun close() = real.close()
            }
        }
    }

    /**
     * Runs [save] again and again on a fresh copy of the same starting point, letting the
     * app die one step later each time, until the save gets through. After every death the
     * app "starts again" and [check] looks at what it finds; then the leftovers are given
     * time to go stale and [check] looks once more.
     */
    private suspend fun dieAtEveryStep(
        prepare: suspend (ProjectRepository) -> Unit,
        save: suspend (ProjectRepository) -> Unit,
        check: (project: Project, folder: File, dieAt: Int) -> Unit,
    ): Int {
        var dieAt = 0
        while (true) {
            val folder = temporary.newFolder()
            prepare(repositoryOn(LocalFileTree(folder)))

            val dying = DyingTree(LocalFileTree(folder), dieAt)
            val outcome = runCatching { save(repositoryOn(dying)) }

            // The app starts again and reads the folder.
            check(repositoryOn(LocalFileTree(folder)).refresh().single(), folder, dieAt)

            // Much later: whatever was left half-way has been cleared away.
            folder.walkTopDown().filter { it.name.startsWith(".bezig-") }.forEach { it.setLastModified(hoursAgo(3)) }
            check(repositoryOn(LocalFileTree(folder)).refresh().single(), folder, dieAt)
            val leftovers = folder.walkTopDown().filter { it.name.startsWith(".bezig-") }.map { it.name }.toList()
            assertEquals("leftovers after dying at step $dieAt", emptyList<String>(), leftovers)

            if (!dying.died) {
                assertTrue("an undisturbed save should succeed", outcome.isSuccess)
                return dieAt
            }
            dieAt++
            check(dieAt < 50) { "the save never got through" }
        }
    }

    @Test
    fun `whenever the app dies while replacing a photo, that day still has a whole photo`() = runBlocking {
        val old = "de oude foto"
        val new = "de nieuwe foto, een stuk langer"
        val steps = dieAtEveryStep(
            prepare = { it.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE); it.savePhoto("Tuin", today, jpeg(old)) },
            save = { it.savePhoto("Tuin", today, jpeg(new)) },
            check = { project, _, dieAt ->
                val photo = project.photoOn(today) ?: throw AssertionError("no photo left after dying at step $dieAt")
                val content = File(photo.nodeId).readText()
                assertTrue("after dying at step $dieAt the photo reads \"$content\"", content == old || content == new)
            },
        )
        assertTrue("a replacement takes several steps, saw $steps", steps >= 4)
    }

    @Test
    fun `whenever the app dies while saving the first photo of a day, no half photo stays behind`() = runBlocking {
        val new = "de foto van vandaag"
        dieAtEveryStep(
            prepare = { it.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE); it.savePhoto("Tuin", today.minusDays(1), jpeg("gisteren")) },
            save = { it.savePhoto("Tuin", today, jpeg(new)) },
            check = { project, _, dieAt ->
                assertEquals("gisteren", File(project.photoOn(today.minusDays(1))!!.nodeId).readText())
                project.photoOn(today)?.let { assertEquals("after dying at step $dieAt", new, File(it.nodeId).readText()) }
            },
        )
        Unit
    }

    @Test
    fun `whenever the app dies while a timelapse is replaced, a whole video remains`() = runBlocking {
        val old = "de eerste video"
        val new = "de tweede video, langer"
        dieAtEveryStep(
            prepare = {
                it.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
                it.saveTimelapse("Tuin", "Tuin_a_tot_b.mp4", jpeg(old))
            },
            save = { it.saveTimelapse("Tuin", "Tuin_a_tot_b.mp4", jpeg(new)) },
            check = { project, _, dieAt ->
                val video = project.timelapses.singleOrNull() ?: throw AssertionError("${project.timelapses.size} videos after dying at step $dieAt")
                assertEquals("Tuin_a_tot_b.mp4", video.name)
                val content = File(video.nodeId).readText()
                assertTrue("after dying at step $dieAt the video reads \"$content\"", content == old || content == new)
            },
        )
        Unit
    }

    @Test
    fun `a video that is still being written is not shown as a timelapse`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.saveTimelapse("Tuin", "Tuin_a_tot_b.mp4", jpeg("klaar"))
        File(root, "Tuin/timelapses/.bezig-9000-Tuin_c_tot_d.mp4").writeText("nog niet af")

        assertEquals(listOf("Tuin_a_tot_b.mp4"), repository.refresh().single().timelapses.map { it.name })
    }

    @Test
    fun `a video that cannot be copied in leaves the earlier one alone`() = runBlocking {
        val repository = repository()
        repository.createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)
        repository.saveTimelapse("Tuin", "Tuin_a_tot_b.mp4", jpeg("eerste"))

        val failure = runCatching { repository.saveTimelapse("Tuin", "Tuin_a_tot_b.mp4", File(temporary.root, "bestaat-niet.mp4")) }

        assertTrue(failure.isFailure)
        assertEquals("eerste", File(root, "Tuin/timelapses/Tuin_a_tot_b.mp4").readText())
        assertEquals(listOf("Tuin_a_tot_b.mp4"), File(root, "Tuin/timelapses").names())
    }

    @Test(expected = IOException::class)
    fun `saving into a project that no longer exists fails clearly`() {
        runBlocking { repository().savePhoto("Verdwenen", today, jpeg("x")) }
    }
}
