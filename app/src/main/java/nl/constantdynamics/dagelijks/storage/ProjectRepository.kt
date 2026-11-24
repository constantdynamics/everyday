package nl.constantdynamics.dagelijks.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.FolderNames
import nl.constantdynamics.dagelijks.core.Lens
import nl.constantdynamics.dagelijks.core.PhotoNames
import nl.constantdynamics.dagelijks.core.PixelSize
import nl.constantdynamics.dagelijks.core.ProjectSettings
import nl.constantdynamics.dagelijks.core.ProjectSettingsJson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/** One daily photo. */
data class Photo(
    val date: LocalDate,
    val nodeId: String,
    val size: Long,
    val lastModified: Long,
) {
    /** Changes whenever the file's content changes, so caches never show a stale picture. */
    val cacheKey: String get() = "$nodeId|$size|$lastModified"
}

/** A rendered timelapse video in the project's "timelapses" folder. */
data class Timelapse(
    val name: String,
    val nodeId: String,
    val size: Long,
    val lastModified: Long,
)

/**
 * A project as found on disk. [folderName] is unique within the main folder and
 * serves as the project's identity; it survives a reinstall.
 */
data class Project(
    val folderName: String,
    val folderId: String,
    val settings: ProjectSettings,
    /** Oldest first. */
    val photos: List<Photo>,
    /** Newest first. */
    val timelapses: List<Timelapse>,
) {
    val dates: List<LocalDate> get() = photos.map { it.date }
    val latestPhoto: Photo? get() = photos.lastOrNull()
    fun photoOn(date: LocalDate): Photo? = photos.firstOrNull { it.date == date }

    /** Fixed after the first photo, otherwise the overlay would no longer line up. */
    val canChangeAspectRatio: Boolean get() = photos.isEmpty()
}

/**
 * All reading and writing of projects and photos. The folder on disk is the single
 * source of truth: nothing here is kept in a database, so whatever is in the folder
 * is what the app shows, also after a reinstall or after copying files by hand.
 */
class ProjectRepository(
    private val tree: FileTree,
    private val rootId: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val today: () -> LocalDate = LocalDate::now,
    /** Reads the pixel size of a photo; only needed to rebuild a lost project.json. */
    private val photoSize: (InputStream) -> PixelSize? = { null },
) {

    private val mutex = Mutex()
    private val state = MutableStateFlow<List<Project>?>(null)

    /** Null until the folder has been read for the first time. */
    val projects: StateFlow<List<Project>?> = state.asStateFlow()

    fun project(folderName: String): Project? = state.value?.firstOrNull { it.folderName == folderName }

    /** Reads the whole folder again. */
    suspend fun refresh(): List<Project> = withContext(io) {
        mutex.withLock {
            RootResolver.ensureNoMedia(tree, rootId)
            val found = tree.list(rootId)
                .filter { it.isDirectory }
                .mapNotNull { folder -> runCatching { readProject(folder) }.getOrNull() }
                .sortedBy { it.settings.name.lowercase() }
            state.value = found
            found
        }
    }

    suspend fun createProject(name: String, lens: Lens, aspectRatio: AspectRatio): Project = withContext(io) {
        mutex.withLock {
            val displayName = name.trim().ifEmpty { throw IllegalArgumentException("A project needs a name") }
            RootResolver.ensureNoMedia(tree, rootId)
            val existingNames = tree.list(rootId).map { it.name }
            val folderName = FolderNames.uniqueFolderName(FolderNames.safeFolderName(displayName), existingNames)
            val folder = tree.createDirectory(rootId, folderName)
            RootResolver.ensureNoMedia(tree, folder.id)
            val settings = ProjectSettings(
                name = displayName,
                lens = lens,
                aspectRatio = aspectRatio,
                createdOn = today(),
            )
            writeSettings(folder.id, settings)
            val project = readProject(folder) ?: throw IOException("The new project could not be read back")
            publish(project)
            project
        }
    }

    /**
     * Changes a project's settings. The aspect ratio only changes while the project
     * has no photos yet.
     */
    suspend fun updateSettings(folderName: String, change: (ProjectSettings) -> ProjectSettings): Project =
        withContext(io) {
            mutex.withLock {
                val current = rereadProject(folderName)
                var updated = change(current.settings)
                if (!current.canChangeAspectRatio) updated = updated.copy(aspectRatio = current.settings.aspectRatio)
                if (updated.name.isBlank()) updated = updated.copy(name = current.settings.name)
                if (updated != current.settings) writeSettings(current.folderId, updated)
                val project = current.copy(settings = updated)
                publish(project)
                project
            }
        }

    /**
     * Stores [jpeg] as the photo of [date]. The new photo is first written completely
     * under a temporary name; only then is an existing photo of that day removed and does
     * the new one get its final name. So a failure half-way never costs a photo, and a
     * file named after a day is always a whole photo.
     */
    suspend fun savePhoto(folderName: String, date: LocalDate, jpeg: File): Project = withContext(io) {
        mutex.withLock {
            val project = rereadProject(folderName)
            val finalName = PhotoNames.fileNameFor(date)
            val replaced = tree.list(project.folderId)
                .filter { !it.isDirectory && PhotoNames.dateFromFileName(it.name) == date }
            val stored = writeSafely(project.folderId, finalName, "image/jpeg", jpeg, replaced)
            if (stored.name != finalName) {
                // The storage chose another name; do not leave a stray file behind.
                tree.delete(stored.id)
                throw IOException("Could not store the photo as $finalName")
            }
            republish(folderName)
        }
    }

    suspend fun deletePhoto(folderName: String, date: LocalDate): Project = withContext(io) {
        mutex.withLock {
            val project = rereadProject(folderName)
            tree.list(project.folderId)
                .filter { !it.isDirectory && PhotoNames.dateFromFileName(it.name) == date }
                .forEach { tree.delete(it.id) }
            republish(folderName)
        }
    }

    /** Removes the project folder with all its photos and videos. */
    suspend fun deleteProject(folderName: String) = withContext(io) {
        mutex.withLock {
            val project = rereadProject(folderName)
            if (!tree.delete(project.folderId)) throw IOException("Could not delete the project folder")
            state.value = state.value?.filterNot { it.folderName == folderName }
        }
    }

    /**
     * Copies a finished video into the project's timelapses folder. A video with the same
     * name is replaced, but only once the new one has arrived completely.
     */
    suspend fun saveTimelapse(folderName: String, fileName: String, video: File): Timelapse = withContext(io) {
        mutex.withLock {
            val project = rereadProject(folderName)
            val folder = tree.child(project.folderId, PhotoNames.TIMELAPSE_FOLDER)?.takeIf { it.isDirectory }
                ?: tree.createDirectory(project.folderId, PhotoNames.TIMELAPSE_FOLDER)
            val replaced = tree.list(folder.id).filter { !it.isDirectory && it.name.equals(fileName, ignoreCase = true) }
            val created = writeSafely(folder.id, fileName, "video/mp4", video, replaced)
            val updated = republish(folderName)
            updated.timelapses.firstOrNull { it.nodeId == created.id }
                ?: Timelapse(created.name, created.id, video.length(), System.currentTimeMillis())
        }
    }

    suspend fun deleteTimelapse(folderName: String, timelapse: Timelapse): Project = withContext(io) {
        mutex.withLock {
            tree.delete(timelapse.nodeId)
            republish(folderName)
        }
    }

    /** Blocking; call from a background thread. */
    fun openPhoto(photo: Photo): InputStream = tree.openInput(photo.nodeId)

    fun locationOf(timelapse: Timelapse): String = tree.location(timelapse.nodeId)

    // ---------------------------------------------------------------------------------------

    private fun rereadProject(folderName: String): Project {
        val folder = tree.list(rootId).firstOrNull { it.isDirectory && it.name == folderName }
            ?: throw IOException("Project folder not found: $folderName")
        return readProject(folder) ?: throw IOException("Not a project folder: $folderName")
    }

    private fun republish(folderName: String): Project = rereadProject(folderName).also(::publish)

    private fun publish(project: Project) {
        val others = state.value.orEmpty().filterNot { it.folderName == project.folderName }
        state.value = (others + project).sortedBy { it.settings.name.lowercase() }
    }

    /** Reads one folder; null when it is not a project of this app. */
    private fun readProject(folder: Node): Project? {
        var children = tree.list(folder.id)
        if (settleTemporaryFiles(children) { PhotoNames.dateFromFileName(it) }) children = tree.list(folder.id)

        val photos = children
            // An empty file is what is left of a save that never got going; it is no photo.
            .filter { !it.isDirectory && it.size > 0 }
            .mapNotNull { node -> PhotoNames.dateFromFileName(node.name)?.let { Photo(it, node.id, node.size, node.lastModified) } }
            .groupBy { it.date }
            // 2026-11-23.jpg and 2026-11-23.jpeg side by side: the newest one counts.
            .map { (_, sameDay) -> sameDay.maxBy { it.lastModified } }
            .sortedBy { it.date }

        val settingsNode = children.firstOrNull { !it.isDirectory && it.name.equals(PhotoNames.PROJECT_FILE, ignoreCase = true) }
        val settingsText = settingsNode?.let { node ->
            runCatching { tree.openInput(node.id).use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        }
        var settings = settingsText?.let { text ->
            runCatching { ProjectSettingsJson.decode(text, folder.name) }.getOrNull()
        }

        if (settings == null) {
            // No usable project.json. A folder without photos and without that file is
            // simply some other folder; anything else is a project that lost its settings.
            if (settingsNode == null && photos.isEmpty()) return null
            settings = reconstructSettings(folder.name, photos)
            when {
                // Nothing to lose: the file is missing or empty.
                settingsNode == null || settingsText?.isBlank() == true ->
                    runCatching { writeSettings(folder.id, settings, existing = settingsNode) }
                // It has content that cannot be understood, perhaps edited by hand. Keep
                // that next to a fresh file instead of wiping it, so it can be repaired.
                settingsText != null ->
                    if (tree.rename(settingsNode.id, PhotoNames.DAMAGED_PROJECT_FILE) != null) {
                        runCatching { writeSettings(folder.id, settings) }
                    }
                // It could not even be opened, which may well be over in a moment: the
                // settings above are used for now and the file is left alone.
            }
        }

        if (children.none { it.name.equals(PhotoNames.NO_MEDIA_FILE, ignoreCase = true) }) {
            // Also when the folder ends up somewhere else, galleries skip the photos in it.
            runCatching { tree.createFile(folder.id, PhotoNames.NO_MEDIA_FILE, "application/octet-stream") }
        }

        val timelapses = children
            .firstOrNull { it.isDirectory && it.name.equals(PhotoNames.TIMELAPSE_FOLDER, ignoreCase = true) }
            ?.let { directory ->
                val videos = tree.list(directory.id)
                if (settleTemporaryFiles(videos, ::videoIdentity)) tree.list(directory.id) else videos
            }
            .orEmpty()
            .filter { !it.isDirectory && videoIdentity(it.name) != null && it.size > 0 }
            .map { Timelapse(it.name, it.id, it.size, it.lastModified) }
            .sortedByDescending { it.lastModified }

        return Project(folder.name, folder.id, settings, photos, timelapses)
    }

    /** Videos are told apart by name, whatever the capitals; null for anything that is no video. */
    private fun videoIdentity(fileName: String): String? =
        fileName.lowercase().takeIf { it.endsWith(".mp4") && !it.startsWith(PhotoNames.TEMPORARY_PREFIX) }

    /**
     * Rebuilds settings for a project whose project.json is missing or damaged. The
     * aspect ratio follows from the photos; which camera was used cannot be told from
     * a photo, so that falls back to the back camera and can be corrected in the app.
     */
    private fun reconstructSettings(folderName: String, photos: List<Photo>): ProjectSettings {
        // The newest photo that can be read decides; one unreadable file must not turn
        // the project into another shape.
        val size = photos.asReversed().asSequence().take(PHOTOS_TRIED_FOR_SIZE)
            .mapNotNull { photo -> runCatching { tree.openInput(photo.nodeId).use(photoSize) }.getOrNull() }
            .firstOrNull()
        return ProjectSettings(
            name = folderName,
            lens = Lens.BACK,
            aspectRatio = size?.let { AspectRatio.closestTo(it.width, it.height) } ?: AspectRatio.DEFAULT,
            createdOn = photos.firstOrNull()?.date,
        )
    }

    private fun writeSettings(folderId: String, settings: ProjectSettings, existing: Node? = null) {
        val node = existing
            ?: tree.child(folderId, PhotoNames.PROJECT_FILE)?.takeUnless { it.isDirectory }
            ?: tree.createFile(folderId, PhotoNames.PROJECT_FILE, "application/json")
        tree.openOutput(node.id).use { output ->
            output.write(ProjectSettingsJson.encode(settings).toByteArray(Charsets.UTF_8))
            output.forceToDisk()
        }
    }

    /**
     * Stores [source] as [name], taking the place of [replaced].
     *
     * The bytes first go into a file with a temporary name. Only when that file is
     * complete are the files it replaces removed and does it get its final name. An
     * interruption at any point therefore leaves either the old file or the new one, and
     * [settleTemporaryFiles] finishes the last step when it was the only one missing.
     */
    private fun writeSafely(parentId: String, name: String, mimeType: String, source: File, replaced: List<Node>): Node {
        if (source.length() <= 0L) throw IOException("There is nothing to store as $name")

        // What an earlier, interrupted attempt for this very file left behind.
        tree.list(parentId)
            .filter { !it.isDirectory && PhotoNames.temporaryTarget(it.name)?.finalName == name }
            .forEach { tree.delete(it.id) }

        val temporary = writeNewFile(parentId, PhotoNames.temporaryNameFor(name, source.length()), mimeType, source)
        replaced.forEach { tree.delete(it.id) }
        val renamed = tree.rename(temporary.id, name)
        if (renamed != null && renamed.name == name) return renamed

        // Renaming is not available here: write it once more, under the final name.
        renamed?.let { tree.delete(it.id) }
        val created = writeNewFile(parentId, name, mimeType, source)
        tree.delete(temporary.id)
        return created
    }

    /** Creates a file, copies [source] into it and checks that everything arrived. */
    private fun writeNewFile(parentId: String, name: String, mimeType: String, source: File): Node {
        val created = tree.createFile(parentId, name, mimeType)
        try {
            source.inputStream().use { input ->
                tree.openOutput(created.id).use { output ->
                    input.copyTo(output, 256 * 1024)
                    output.forceToDisk()
                }
            }
            val written = tree.list(parentId).firstOrNull { it.id == created.id }
            if (written == null || written.size != source.length()) {
                throw IOException("File was not written completely: ${created.name}")
            }
            return written
        } catch (failure: Exception) {
            runCatching { tree.delete(created.id) }
            throw failure
        }
    }

    /**
     * Asks the phone to really put the bytes on its storage before the file is relied on.
     * Without this a file can turn out empty when the battery dies just after saving.
     */
    private fun OutputStream.forceToDisk() {
        flush()
        runCatching { (this as? FileOutputStream)?.fd?.sync() }
    }

    /**
     * Deals with what an interrupted save left behind in one folder.
     *
     * A temporary file that is complete, while nothing has its final name, is a save that
     * was stopped right before its last step: it gets its final name after all, so the
     * photo or video is not lost. Every other temporary file is removed once it is clearly
     * no longer in use.
     *
     * [identityOf] tells which files count as the same thing (a day for photos, a name
     * for videos) and returns null for names that do not belong in the folder.
     *
     * @return true when the folder changed and has to be listed again.
     */
    private fun settleTemporaryFiles(children: List<Node>, identityOf: (fileName: String) -> Any?): Boolean {
        val temporaries = children.filter { !it.isDirectory && it.name.startsWith(PhotoNames.TEMPORARY_PREFIX) }
        if (temporaries.isEmpty()) return false

        val finals = children.filter { !it.isDirectory && !it.name.startsWith(PhotoNames.TEMPORARY_PREFIX) }
        val taken = finals.filter { it.size > 0 }.mapNotNullTo(HashSet()) { identityOf(it.name) }
        val cutoff = System.currentTimeMillis() - STALE_AFTER_MILLIS
        var changed = false

        for (temporary in temporaries) {
            val target = PhotoNames.temporaryTarget(temporary.name)
            val identity = target?.let { identityOf(it.finalName) }
            val complete = target != null && temporary.size > 0 && temporary.size == target.size
            if (target != null && identity != null && complete && identity !in taken) {
                // An empty file with the same name would be in the way.
                finals.filter { it.size == 0L && identityOf(it.name) == identity }.forEach { runCatching { tree.delete(it.id) } }
                // When this fails the file stays as it is: it holds the only copy.
                if (tree.rename(temporary.id, target.finalName) != null) {
                    taken += identity
                    changed = true
                }
            } else if (temporary.lastModified in 1 until cutoff) {
                if (runCatching { tree.delete(temporary.id) }.getOrDefault(false)) changed = true
            }
        }
        return changed
    }

    private companion object {
        const val STALE_AFTER_MILLIS = 60 * 60 * 1000L
        const val PHOTOS_TRIED_FOR_SIZE = 8
    }
}
