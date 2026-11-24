package nl.constantdynamics.dagelijks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.constantdynamics.dagelijks.FolderState
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.components.AppBackground
import nl.constantdynamics.dagelijks.ui.components.rememberScreenScope
import nl.constantdynamics.dagelijks.ui.screens.AboutScreen
import nl.constantdynamics.dagelijks.ui.screens.CameraScreen
import nl.constantdynamics.dagelijks.ui.screens.GalleryScreen
import nl.constantdynamics.dagelijks.ui.screens.HomeScreen
import nl.constantdynamics.dagelijks.ui.screens.NewProjectScreen
import nl.constantdynamics.dagelijks.ui.screens.PlayerScreen
import nl.constantdynamics.dagelijks.ui.screens.TimelapseScreen
import nl.constantdynamics.dagelijks.ui.screens.ViewerScreen
import nl.constantdynamics.dagelijks.ui.screens.WelcomeScreen

/** The screens of the app. Projects are referred to by their folder name. */
sealed interface Screen {
    data object Home : Screen
    data object NewProject : Screen
    data object About : Screen
    data class Gallery(val project: String) : Screen

    /** [replaceDate] is set when an existing photo is being replaced by a new one. */
    data class Camera(val project: String, val replaceDate: LocalDate? = null) : Screen
    data class Viewer(val project: String, val date: LocalDate) : Screen
    data class Timelapse(val project: String) : Screen
    data class Player(val project: String, val fileName: String) : Screen

    fun encode(): String = when (this) {
        Home -> "home"
        NewProject -> "new"
        About -> "about"
        is Gallery -> listOf("gallery", project).joinToString(SEPARATOR)
        is Camera -> listOf("camera", project, replaceDate?.toString().orEmpty()).joinToString(SEPARATOR)
        is Viewer -> listOf("viewer", project, date.toString()).joinToString(SEPARATOR)
        is Timelapse -> listOf("timelapse", project).joinToString(SEPARATOR)
        is Player -> listOf("player", project, fileName).joinToString(SEPARATOR)
    }

    companion object {
        private const val SEPARATOR = "\u0000"

        fun decode(text: String): Screen {
            val parts = text.split(SEPARATOR)
            return runCatching {
                when (parts[0]) {
                    "new" -> NewProject
                    "about" -> About
                    "gallery" -> Gallery(parts[1])
                    "camera" -> Camera(parts[1], parts[2].takeIf { it.isNotEmpty() }?.let(LocalDate::parse))
                    "viewer" -> Viewer(parts[1], LocalDate.parse(parts[2]))
                    "timelapse" -> Timelapse(parts[1])
                    "player" -> Player(parts[1], parts[2])
                    else -> Home
                }
            }.getOrDefault(Home)
        }
    }
}

/**
 * A plain back stack; with eight screens a navigation library would be overkill.
 *
 * [onLeft] is told about every screen that is no longer anywhere on the stack, so that
 * what it remembered (the page the viewer was on, a half-filled form) can be thrown
 * away. Opened again later, such a screen starts from scratch.
 */
class Navigator(
    private val stack: MutableList<Screen>,
    private val onLeft: (Screen) -> Unit = {},
) {
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1
    val screens: List<Screen> get() = stack

    fun open(screen: Screen) {
        if (current != screen) stack.add(screen)
    }

    fun back() {
        if (canGoBack) left(listOf(stack.removeAt(stack.lastIndex)))
    }

    /** Swaps the current screen for another, so going back skips it. */
    fun replace(screen: Screen) {
        val previous = stack[stack.lastIndex]
        stack[stack.lastIndex] = screen
        left(listOf(previous))
    }

    /** Leaves every screen that belongs to a project that no longer exists. */
    fun closeProject(project: String) {
        val gone = stack.filter { it.belongsTo(project) }
        stack.removeAll(gone)
        if (stack.isEmpty()) stack.add(Screen.Home)
        left(gone)
    }

    private fun left(screens: List<Screen>) {
        screens.distinct().filter { it !in stack }.forEach(onLeft)
    }

    private fun Screen.belongsTo(project: String): Boolean = when (this) {
        is Screen.Gallery -> this.project == project
        is Screen.Camera -> this.project == project
        is Screen.Viewer -> this.project == project
        is Screen.Timelapse -> this.project == project
        is Screen.Player -> this.project == project
        else -> false
    }
}

@Composable
private fun rememberNavigator(onLeft: (Screen) -> Unit): Navigator {
    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<Screen>, String>(
            save = { list -> list.map { it.encode() } },
            restore = { saved -> mutableStateListOf<Screen>().apply { addAll(saved.map(Screen::decode)) } },
        ),
    ) { mutableStateListOf<Screen>(Screen.Home) }
    return remember(stack) { Navigator(stack, onLeft) }
}

@Composable
fun DagelijksRoot(folderState: StateFlow<FolderState>) {
    val folder by folderState.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    when (val state = folder) {
        FolderState.Loading -> AppBackground(Modifier.fillMaxSize()) {}
        FolderState.Missing -> WelcomeScreen()
        is FolderState.Ready -> MainNavigation(state.repository, state.location)
    }
}

@Composable
private fun MainNavigation(repository: ProjectRepository, location: String) {
    val stateHolder = rememberSaveableStateHolder()
    // The screens that are on screen right now, a closing one still fading out included.
    // What such a screen remembers is only thrown away once it is really gone; thrown away
    // earlier, it would also stop being saved while it is still shown.
    val composed = remember { mutableSetOf<String>() }
    val navigator = rememberNavigator { left ->
        val key = left.encode()
        if (key !in composed) stateHolder.removeState(key)
    }
    val scope = rememberScreenScope()
    val projects by repository.projects.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)

    // Photos may have been added or removed from a computer while the app was away.
    // If the whole folder turns out to be gone, the app asks for it again.
    val services = LocalServices.current
    LifecycleResumeEffect(repository) {
        scope.launch {
            if (runCatching { repository.refresh() }.isFailure) services.recheckFolder()
        }
        onPauseOrDispose {}
    }

    BackHandler(enabled = navigator.canGoBack) { navigator.back() }

    AnimatedContent(
        targetState = navigator.current,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "scherm",
    ) { screen ->
        val key = screen.encode()
        DisposableEffect(key) {
            composed += key
            onDispose {
                composed -= key
                if (screen !in navigator.screens) stateHolder.removeState(key)
            }
        }
        stateHolder.SaveableStateProvider(key) {
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.Home -> HomeScreen(
                        repository = repository,
                        projects = projects,
                        onOpenProject = { navigator.open(Screen.Gallery(it.folderName)) },
                        onTakePhoto = { navigator.open(Screen.Camera(it.folderName)) },
                        onNewProject = { navigator.open(Screen.NewProject) },
                        onAbout = { navigator.open(Screen.About) },
                    )

                    Screen.NewProject -> NewProjectScreen(
                        repository = repository,
                        onCreated = {
                            // Only when the form is still what the user is looking at; whoever
                            // went back in the meantime finds the new project in the list.
                            if (navigator.current == Screen.NewProject) navigator.replace(Screen.Gallery(it.folderName))
                        },
                        onBack = navigator::back,
                    )

                    Screen.About -> AboutScreen(location = location, onBack = navigator::back)

                    is Screen.Gallery -> WithProject(projects, screen.project, navigator) { project ->
                        GalleryScreen(
                            repository = repository,
                            project = project,
                            onBack = navigator::back,
                            onTakePhoto = { navigator.open(Screen.Camera(project.folderName)) },
                            onOpenPhoto = { navigator.open(Screen.Viewer(project.folderName, it.date)) },
                            onTimelapse = { navigator.open(Screen.Timelapse(project.folderName)) },
                            onDeleted = { navigator.closeProject(project.folderName) },
                        )
                    }

                    is Screen.Camera -> WithProject(projects, screen.project, navigator) { project ->
                        CameraScreen(
                            repository = repository,
                            project = project,
                            replaceDate = screen.replaceDate,
                            onClose = navigator::back,
                        )
                    }

                    is Screen.Viewer -> WithProject(projects, screen.project, navigator) { project ->
                        ViewerScreen(
                            repository = repository,
                            project = project,
                            initialDate = screen.date,
                            onBack = navigator::back,
                            onReplace = { navigator.open(Screen.Camera(project.folderName, it)) },
                        )
                    }

                    is Screen.Timelapse -> WithProject(projects, screen.project, navigator) { project ->
                        TimelapseScreen(
                            repository = repository,
                            project = project,
                            onBack = navigator::back,
                            onPlay = { navigator.open(Screen.Player(project.folderName, it.name)) },
                        )
                    }

                    is Screen.Player -> WithProject(projects, screen.project, navigator) { project ->
                        PlayerScreen(
                            repository = repository,
                            project = project,
                            fileName = screen.fileName,
                            onBack = navigator::back,
                        )
                    }
                }
            }
        }
    }
}

/** Shows a project screen, and steps back when the project turns out to be gone. */
@Composable
private fun WithProject(
    projects: List<Project>?,
    folderName: String,
    navigator: Navigator,
    content: @Composable (Project) -> Unit,
) {
    val project = projects?.firstOrNull { it.folderName == folderName }
    when {
        project != null -> content(project)
        projects == null -> AppBackground(Modifier.fillMaxSize()) {}
        else -> LaunchedEffect(folderName) { navigator.closeProject(folderName) }
    }
}
