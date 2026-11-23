package nl.constantdynamics.dagelijks.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.Lens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Which folder becomes the app's main folder after the user picked one. The main
 * folder must always be a dedicated one, because Android ignores (and removes) a
 * .nomedia file in the standard folders such as Documents.
 */
class RootResolverTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private fun resolve(picked: File): File = File(RootResolver.resolve(LocalFileTree(picked)))

    @Test
    fun `picking Documents makes a Everyday folder inside it`() {
        val documents = temporary.newFolder("Documents")
        File(documents, "brief.pdf").writeText("x")

        val root = resolve(documents)

        assertEquals(File(documents, "Everyday"), root)
        assertTrue(root.isDirectory)
    }

    @Test
    fun `an empty standard folder is not used as the main folder either`() {
        val documents = temporary.newFolder("Documents")
        assertEquals(File(documents, "Everyday"), resolve(documents))
    }

    @Test
    fun `picking a folder named Everyday uses that folder itself`() {
        val picked = temporary.newFolder("Documents", "Everyday")
        assertEquals(picked, resolve(picked))
        assertEquals(emptyList<String>(), picked.list()!!.toList())
    }

    @Test
    fun `the name is recognised regardless of capitals`() {
        val picked = temporary.newFolder("everyday")
        assertEquals(picked, resolve(picked))
    }

    @Test
    fun `after a reinstall, picking Documents again finds the existing Everyday folder`() {
        val documents = temporary.newFolder("Documents")
        val first = resolve(documents)
        File(first, "Mijn gezicht").mkdirs()

        val second = resolve(documents)

        assertEquals(first, second)
        assertEquals(listOf("Everyday"), documents.list()!!.toList())
    }

    @Test
    fun `a main folder that was renamed on the computer is recognised by its projects`() = runBlocking {
        val renamed = temporary.newFolder("Fotoprojecten")
        val tree = LocalFileTree(renamed)
        ProjectRepository(tree, tree.pickedFolderId, Dispatchers.Unconfined)
            .createProject("Tuin", Lens.BACK, AspectRatio.SQUARE)

        assertEquals(renamed, resolve(renamed))
        assertFalse(File(renamed, "Everyday").exists())
    }

    @Test
    fun `some other project json does not make a folder ours`() {
        val documents = temporary.newFolder("Documents")
        File(documents, "website").mkdirs()
        File(documents, "website/project.json").writeText("""{"name":"my site","version":"1.0"}""")

        assertEquals(File(documents, "Everyday"), resolve(documents))
    }

    @Test
    fun `the main folder gets exactly one nomedia file`() {
        val picked = temporary.newFolder("Everyday")
        val tree = LocalFileTree(picked)
        val rootId = RootResolver.resolve(tree)

        RootResolver.ensureNoMedia(tree, rootId)
        RootResolver.ensureNoMedia(tree, rootId)

        assertEquals(listOf(".nomedia"), picked.list()!!.toList())
    }
}
