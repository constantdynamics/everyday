package nl.constantdynamics.dagelijks.storage

import nl.constantdynamics.dagelijks.core.PhotoNames
import nl.constantdynamics.dagelijks.core.ProjectSettingsJson

/**
 * Decides which folder becomes the app's main folder after the user picked one.
 *
 * The main folder must be a dedicated folder. Android silently removes a .nomedia
 * file from the standard top-level folders (Documents, Pictures, ...), which would
 * make every photo show up in the gallery and in Google Photos after all. So unless
 * the picked folder is clearly ours already, a folder named "Everyday" inside it is
 * used, and created when needed.
 *
 * That also makes the first start forgiving: picking "Documents", picking a freshly
 * made "Everyday" folder, or picking either of them again after a reinstall all end
 * up at the same place.
 */
object RootResolver {

    const val MAIN_FOLDER_NAME = "Everyday"

    fun resolve(tree: FileTree): String {
        val picked = tree.pickedFolderId
        val children = tree.list(picked)

        // Picked the main folder itself, whatever it is called now: it holds projects.
        if (children.any { it.isDirectory && isProjectFolder(tree, it) }) return picked

        if (tree.nameOf(picked).equals(MAIN_FOLDER_NAME, ignoreCase = true)) return picked

        val existing = children.firstOrNull { it.isDirectory && it.name.equals(MAIN_FOLDER_NAME, ignoreCase = true) }
        return (existing ?: tree.createDirectory(picked, MAIN_FOLDER_NAME)).id
    }

    /** Makes sure gallery apps and Google Photos skip the folder. */
    fun ensureNoMedia(tree: FileTree, rootId: String) {
        if (tree.child(rootId, PhotoNames.NO_MEDIA_FILE) == null) {
            tree.createFile(rootId, PhotoNames.NO_MEDIA_FILE, "application/octet-stream")
        }
    }

    private fun isProjectFolder(tree: FileTree, folder: Node): Boolean {
        val settingsFile = tree.child(folder.id, PhotoNames.PROJECT_FILE) ?: return false
        if (settingsFile.isDirectory) return false
        return try {
            val text = tree.openInput(settingsFile.id).use { it.readBytes().toString(Charsets.UTF_8) }
            ProjectSettingsJson.decode(text, folder.name) != null
        } catch (_: Exception) {
            false
        }
    }
}
