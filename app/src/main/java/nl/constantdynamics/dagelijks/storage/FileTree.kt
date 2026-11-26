package nl.constantdynamics.dagelijks.storage

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** One file or folder inside the user's photo folder. The id is opaque. */
data class Node(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
)

/**
 * The few file operations the app needs, on the folder the user picked.
 *
 * On the phone this is backed by the Storage Access Framework ([SafTree]); in tests
 * by plain files ([LocalFileTree]). Everything here blocks and may throw
 * [IOException], so it is always called from a background thread.
 */
interface FileTree {

    /** The folder the user picked in the system folder picker. */
    val pickedFolderId: String

    /** Name of a file or folder, or null when it no longer exists. */
    fun nameOf(id: String): String?

    fun list(directoryId: String): List<Node>

    fun createDirectory(parentId: String, name: String): Node

    /**
     * Creates an empty file. The storage may pick a different name when [name] is
     * taken, so callers check the name of the returned node.
     */
    fun createFile(parentId: String, name: String, mimeType: String): Node

    fun openInput(id: String): InputStream

    /** Opens a file for writing, replacing its content. */
    fun openOutput(id: String): OutputStream

    /** Deletes a file, or a folder with everything in it. */
    fun delete(id: String): Boolean

    /** Renames a file; null when the storage cannot do that. */
    fun rename(id: String, newName: String): Node?

    /** An address the video player can open, as a URI string. */
    fun location(id: String): String
}

fun FileTree.child(directoryId: String, name: String): Node? =
    list(directoryId).firstOrNull { it.name.equals(name, ignoreCase = true) }
