package nl.constantdynamics.dagelijks.storage

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * [FileTree] on ordinary files. Ids are absolute paths. Used by the tests, where it
 * stands in for the phone's storage; it mimics the one surprising habit of the real
 * thing, namely picking another name when the wanted one is taken.
 */
class LocalFileTree(private val root: File) : FileTree {

    init {
        root.mkdirs()
    }

    override val pickedFolderId: String = root.absolutePath

    override fun nameOf(id: String): String? = File(id).takeIf { it.exists() }?.name

    override fun list(directoryId: String): List<Node> =
        File(directoryId).listFiles()?.map(::nodeOf)?.sortedBy { it.name } ?: emptyList()

    override fun createDirectory(parentId: String, name: String): Node {
        val directory = uniqueFile(File(parentId), name)
        if (!directory.mkdir()) throw IOException("Could not create folder ${directory.path}")
        return nodeOf(directory)
    }

    override fun createFile(parentId: String, name: String, mimeType: String): Node {
        val file = uniqueFile(File(parentId), name)
        if (!file.createNewFile()) throw IOException("Could not create file ${file.path}")
        return nodeOf(file)
    }

    override fun openInput(id: String): InputStream = File(id).inputStream()

    override fun openOutput(id: String): OutputStream = File(id).outputStream()

    override fun delete(id: String): Boolean = File(id).deleteRecursively()

    override fun rename(id: String, newName: String): Node? {
        val source = File(id)
        val target = File(source.parentFile, newName)
        if (target.exists() || !source.renameTo(target)) return null
        return nodeOf(target)
    }

    override fun location(id: String): String = File(id).toURI().toString()

    private fun nodeOf(file: File) = Node(
        id = file.absolutePath,
        name = file.name,
        isDirectory = file.isDirectory,
        size = if (file.isFile) file.length() else 0,
        lastModified = file.lastModified(),
    )

    private fun uniqueFile(parent: File, name: String): File {
        var candidate = File(parent, name)
        var number = 1
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty() || !name.contains('.')) "" else ".$it" }
        while (candidate.exists()) {
            candidate = File(parent, "$base ($number)$extension")
            number++
        }
        return candidate
    }
}
