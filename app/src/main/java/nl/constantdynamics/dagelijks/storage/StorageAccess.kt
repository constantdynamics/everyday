package nl.constantdynamics.dagelijks.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Remembers which folder the user chose and keeps the access to it.
 *
 * This is the only thing the app stores privately; everything else lives in the
 * chosen folder itself. After a reinstall this memory is gone, the user picks the
 * folder once more, and all projects are back.
 */
class StorageAccess(private val context: Context) {

    private val preferences = context.getSharedPreferences("opslag", Context.MODE_PRIVATE)

    data class Opened(val tree: FileTree, val rootId: String)

    sealed interface PickResult {
        data class Success(val opened: Opened) : PickResult

        /** A cloud drive or another app's storage was picked instead of the phone itself. */
        data object NotLocalStorage : PickResult
        data class Failed(val cause: Exception) : PickResult
    }

    /** The folder chosen earlier, when the app still has access to it. */
    suspend fun openStored(): Opened? = withContext(Dispatchers.IO) {
        val treeUri = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse) ?: return@withContext null
        val rootId = preferences.getString(KEY_ROOT_ID, null) ?: return@withContext null
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        if (!granted) return@withContext null
        val tree = SafTree(context.contentResolver, treeUri)
        // The folder may have been deleted or renamed from a computer in the meantime.
        if (tree.nameOf(rootId) == null) return@withContext null
        Opened(tree, rootId)
    }

    /** Takes a folder straight from the system folder picker into use. */
    suspend fun adopt(pickedTreeUri: Uri): PickResult = withContext(Dispatchers.IO) {
        if (pickedTreeUri.authority != SafTree.LOCAL_STORAGE_AUTHORITY) return@withContext PickResult.NotLocalStorage
        try {
            val resolver = context.contentResolver
            resolver.takePersistableUriPermission(pickedTreeUri, ACCESS_FLAGS)
            val tree = SafTree(resolver, pickedTreeUri)
            val rootId = RootResolver.resolve(tree)
            RootResolver.ensureNoMedia(tree, rootId)

            val previous = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)
            preferences.edit {
                putString(KEY_TREE_URI, pickedTreeUri.toString())
                putString(KEY_ROOT_ID, rootId)
            }
            if (previous != null && previous != pickedTreeUri) {
                runCatching { resolver.releasePersistableUriPermission(previous, ACCESS_FLAGS) }
            }
            PickResult.Success(Opened(tree, rootId))
        } catch (failure: Exception) {
            PickResult.Failed(failure)
        }
    }

    /** A readable description of where the photos are, for example "Documents/Everyday". */
    fun describeLocation(opened: Opened): String {
        val path = opened.rootId.substringAfter(':', opened.rootId)
        val volume = opened.rootId.substringBefore(':', "")
        return when {
            path.isEmpty() -> opened.tree.nameOf(opened.rootId).orEmpty()
            volume == "primary" || volume.isEmpty() -> path
            else -> "$path (geheugenkaart)"
        }
    }

    companion object {
        private const val KEY_TREE_URI = "map_uri"
        private const val KEY_ROOT_ID = "hoofdmap_id"

        private const val ACCESS_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** Where the folder picker opens: the Documents folder of the phone's own storage. */
        val suggestedStartFolder: Uri =
            DocumentsContract.buildDocumentUri(SafTree.LOCAL_STORAGE_AUTHORITY, "primary:Documents")
    }
}
