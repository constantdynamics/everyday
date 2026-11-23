package nl.constantdynamics.dagelijks.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * [FileTree] on a folder the user granted through the Storage Access Framework.
 *
 * Talks to DocumentsContract directly: one query lists a whole folder with names,
 * sizes and dates, where the DocumentFile helper would need a query per file. Ids
 * are document ids. Whatever the storage provider throws is turned into an
 * [IOException], so callers have one kind of failure to deal with.
 */
class SafTree(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : FileTree {

    override val pickedFolderId: String = DocumentsContract.getTreeDocumentId(treeUri)

    fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    override fun nameOf(id: String): String? = try {
        node(id)?.name
    } catch (_: IOException) {
        null
    }

    override fun list(directoryId: String): List<Node> = guarded("list $directoryId") {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
        val cursor = resolver.query(childrenUri, PROJECTION, null, null, null)
            ?: throw IOException("The folder could not be read")
        cursor.use {
            val nodes = ArrayList<Node>(it.count)
            while (it.moveToNext()) {
                nodes += Node(
                    id = it.getString(0),
                    name = it.getString(1).orEmpty(),
                    isDirectory = it.getString(2) == Document.MIME_TYPE_DIR,
                    size = if (it.isNull(3)) 0 else it.getLong(3),
                    lastModified = if (it.isNull(4)) 0 else it.getLong(4),
                )
            }
            nodes
        }
    }

    override fun createDirectory(parentId: String, name: String): Node =
        create(parentId, Document.MIME_TYPE_DIR, name)

    override fun createFile(parentId: String, name: String, mimeType: String): Node =
        create(parentId, mimeType, name)

    private fun create(parentId: String, mimeType: String, name: String): Node = guarded("create $name") {
        val created = DocumentsContract.createDocument(resolver, documentUri(parentId), mimeType, name)
            ?: throw IOException("Could not create $name")
        node(DocumentsContract.getDocumentId(created)) ?: throw IOException("Could not find $name after creating it")
    }

    override fun openInput(id: String): InputStream = guarded("read $id") {
        resolver.openInputStream(documentUri(id)) ?: throw FileNotFoundException(id)
    }

    // "wt": write and truncate. Plain "w" leaves old bytes behind when the new content is shorter.
    override fun openOutput(id: String): OutputStream = guarded("write $id") {
        resolver.openOutputStream(documentUri(id), "wt") ?: throw FileNotFoundException(id)
    }

    override fun delete(id: String): Boolean = try {
        DocumentsContract.deleteDocument(resolver, documentUri(id))
    } catch (_: Exception) {
        false
    }

    override fun rename(id: String, newName: String): Node? = try {
        DocumentsContract.renameDocument(resolver, documentUri(id), newName)
            ?.let { node(DocumentsContract.getDocumentId(it)) }
    } catch (_: Exception) {
        null
    }

    override fun location(id: String): String = documentUri(id).toString()

    private fun node(id: String): Node? = guarded("look up $id") {
        resolver.query(documentUri(id), PROJECTION, null, null, null)?.use {
            if (!it.moveToFirst()) return@use null
            Node(
                id = it.getString(0),
                name = it.getString(1).orEmpty(),
                isDirectory = it.getString(2) == Document.MIME_TYPE_DIR,
                size = if (it.isNull(3)) 0 else it.getLong(3),
                lastModified = if (it.isNull(4)) 0 else it.getLong(4),
            )
        }
    }

    private inline fun <T> guarded(what: String, block: () -> T): T = try {
        block()
    } catch (failure: IOException) {
        throw failure
    } catch (failure: Exception) {
        // SecurityException (access revoked), IllegalArgumentException (gone), ...
        throw IOException("Storage error during: $what", failure)
    }

    companion object {
        /** The phone's own storage (internal storage, SD card, USB stick). Never a cloud service. */
        const val LOCAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
