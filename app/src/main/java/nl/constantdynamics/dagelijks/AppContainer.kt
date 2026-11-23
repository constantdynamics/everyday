package nl.constantdynamics.dagelijks

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.constantdynamics.dagelijks.image.ImageLoader
import nl.constantdynamics.dagelijks.storage.FileTree
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.storage.RootResolver
import nl.constantdynamics.dagelijks.storage.StorageAccess
import nl.constantdynamics.dagelijks.timelapse.TimelapseRenderer
import nl.constantdynamics.dagelijks.ui.AppServices

/** Where the app stands with regard to the photo folder. */
sealed interface FolderState {
    /** Still checking whether a folder was chosen before. */
    data object Loading : FolderState

    /** No folder yet, or the access to it is gone (for example after a reinstall). */
    data object Missing : FolderState

    data class Ready(val repository: ProjectRepository, val location: String) : FolderState
}

/**
 * The few long-lived objects of the app, created by hand. With so little to wire
 * up, a dependency injection framework would only add another library.
 */
class AppContainer(private val application: Application) {

    /** For work that must finish even when the user leaves the screen that started it. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val imageLoader = ImageLoader(application.cacheDir)
    val timelapseRenderer = TimelapseRenderer(application, scope)

    private val storageAccess = StorageAccess(application)

    /** The part of this container that the screens get to see. */
    val services = AppServices(
        scope = scope,
        imageLoader = imageLoader,
        timelapseRenderer = timelapseRenderer,
        chooseFolder = ::chooseFolder,
        recheckFolder = ::reloadFolder,
    )
    private val folderState = MutableStateFlow<FolderState>(FolderState.Loading)
    val folder: StateFlow<FolderState> = folderState.asStateFlow()

    init {
        scope.launch {
            val opened = runCatching { storageAccess.openStored() }.getOrNull()
            // A test may already have put its own folder in place.
            if (folderState.value is FolderState.Loading) {
                if (opened == null) folderState.value = FolderState.Missing else use(opened)
            }
            runCatching { imageLoader.trimDiskCache() }
        }
    }

    /**
     * Looks again whether the folder chosen earlier is still there and still
     * accessible, the way a freshly started app does. When it is gone (deleted or
     * renamed from a computer, or the app was reinstalled) the app asks for it again.
     */
    suspend fun reloadFolder() {
        val opened = runCatching { storageAccess.openStored() }.getOrNull()
        if (opened == null) folderState.value = FolderState.Missing else use(opened)
    }

    /** Called with the result of the system folder picker. */
    suspend fun chooseFolder(pickedTreeUri: Uri): StorageAccess.PickResult {
        val result = storageAccess.adopt(pickedTreeUri)
        if (result is StorageAccess.PickResult.Success) use(result.opened)
        return result
    }

    private suspend fun use(opened: StorageAccess.Opened) {
        val repository = ProjectRepository(
            tree = opened.tree,
            rootId = opened.rootId,
            photoSize = ImageLoader::readSize,
        )
        folderState.value = FolderState.Ready(repository, storageAccess.describeLocation(opened))
        runCatching { repository.refresh() }
    }

    /** Lets automated tests run the whole app on a folder of their own, without the system folder picker. */
    suspend fun useTreeForTesting(tree: FileTree, location: String = "testmap") {
        val rootId = RootResolver.resolve(tree)
        RootResolver.ensureNoMedia(tree, rootId)
        val repository = ProjectRepository(tree = tree, rootId = rootId, photoSize = ImageLoader::readSize)
        folderState.value = FolderState.Ready(repository, location)
        repository.refresh()
    }
}
