package nl.constantdynamics.dagelijks.framework

import android.app.Application
import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import nl.constantdynamics.dagelijks.camera.FlashKind
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.Lens
import nl.constantdynamics.dagelijks.image.ImageLoader
import nl.constantdynamics.dagelijks.storage.LocalFileTree
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.storage.StorageAccess
import nl.constantdynamics.dagelijks.timelapse.TimelapseRenderer
import nl.constantdynamics.dagelijks.ui.AppServices
import nl.constantdynamics.dagelijks.ui.LocalServices
import nl.constantdynamics.dagelijks.ui.ShutterKeys
import nl.constantdynamics.dagelijks.ui.components.RetroSlider
import nl.constantdynamics.dagelijks.ui.screens.CameraLayout
import nl.constantdynamics.dagelijks.ui.screens.CameraTags
import nl.constantdynamics.dagelijks.ui.screens.HomeScreen
import nl.constantdynamics.dagelijks.ui.screens.NewProjectScreen
import nl.constantdynamics.dagelijks.ui.screens.OVERLAY_SLIDER_DESCRIPTION
import nl.constantdynamics.dagelijks.ui.screens.TimelapseScreen
import nl.constantdynamics.dagelijks.ui.screens.ViewerScreen
import nl.constantdynamics.dagelijks.ui.theme.DagelijksTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** What the screens do when they are used: buttons, questions, keys. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = PHONE_UPRIGHT)
class BehaviourTest {

    @get:Rule(order = 0)
    val registerActivity = RegisterComposeActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var root: File
    private lateinit var repository: ProjectRepository
    private lateinit var services: AppServices
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        root = temporary.newFolder("Dagelijks")
        val tree = LocalFileTree(root)
        repository = ProjectRepository(tree, tree.pickedFolderId, Dispatchers.Unconfined, photoSize = ImageLoader::readSize)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        services = AppServices(
            scope = scope,
            imageLoader = ImageLoader(temporary.newFolder("cache"), Dispatchers.Unconfined),
            timelapseRenderer = TimelapseRenderer(ApplicationProvider.getApplicationContext(), scope),
            chooseFolder = { StorageAccess.PickResult.Failed(IllegalStateException("not in a test")) },
        )
        runBlocking { repository.refresh() }
    }

    private fun addProject(name: String, lens: Lens, ratio: AspectRatio, vararg daysAgo: Long): Project = runBlocking {
        repository.createProject(name, lens, ratio)
        daysAgo.forEach { ago ->
            val day = today.minusDays(ago)
            val file = File(temporary.root, "bron.jpg")
            file.writeBytes(Fixtures.jpeg(Fixtures.dailyPhoto(300, 400, day), quality = 80))
            repository.savePhoto(name, day, file)
        }
        repository.project(name)!!
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalServices provides services) {
                DagelijksTheme { content() }
            }
        }
        compose.waitForIdle()
    }

    // --- Volume buttons as shutter -----------------------------------------------------------

    private fun press(code: Int, repeat: Int = 0) = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, repeat)
    private fun release(code: Int) = KeyEvent(0L, 0L, KeyEvent.ACTION_UP, code, 0)

    @Test
    fun `volume up and volume down both take a photo while the camera is open`() {
        val keys = ShutterKeys()
        var photos = 0
        keys.register { photos++ }

        assertTrue(keys.handle(press(KeyEvent.KEYCODE_VOLUME_UP)))
        assertTrue(keys.handle(release(KeyEvent.KEYCODE_VOLUME_UP)))
        assertTrue(keys.handle(press(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertTrue(keys.handle(release(KeyEvent.KEYCODE_VOLUME_DOWN)))

        assertEquals(2, photos)
    }

    @Test
    fun `holding the button down takes one photo, not a burst`() {
        val keys = ShutterKeys()
        var photos = 0
        keys.register { photos++ }

        keys.handle(press(KeyEvent.KEYCODE_VOLUME_UP))
        repeat(5) { assertTrue(keys.handle(press(KeyEvent.KEYCODE_VOLUME_UP, repeat = it + 1))) }
        keys.handle(release(KeyEvent.KEYCODE_VOLUME_UP))

        assertEquals(1, photos)
    }

    @Test
    fun `a bluetooth remote that sends Enter works too, other keys are left alone`() {
        val keys = ShutterKeys()
        var photos = 0
        keys.register { photos++ }

        assertTrue(keys.handle(press(KeyEvent.KEYCODE_ENTER)))
        assertFalse(keys.handle(press(KeyEvent.KEYCODE_BACK)))
        assertFalse(keys.handle(press(KeyEvent.KEYCODE_A)))
        assertEquals(1, photos)
    }

    @Test
    fun `outside the camera the volume buttons just change the volume`() {
        val keys = ShutterKeys()
        assertFalse(keys.handle(press(KeyEvent.KEYCODE_VOLUME_UP)))

        var photos = 0
        val listener = { photos++; Unit }
        keys.register(listener)
        keys.unregister(listener)
        assertFalse(keys.handle(press(KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertEquals(0, photos)
    }

    // --- Camera controls ---------------------------------------------------------------------

    @Composable
    private fun Camera(
        flashKind: FlashKind = FlashKind.LED,
        flashOn: Boolean = false,
        withOverlay: Boolean = true,
        overlayPercent: Int = 35,
        onToggleFlash: () -> Unit = {},
        onShutter: () -> Unit = {},
        onOverlayPercentChange: (Int) -> Unit = {},
    ) {
        CameraLayout(
            aspectRatio = AspectRatio.PORTRAIT_3_4,
            overlay = Fixtures.quarters(300, 400).takeIf { withOverlay },
            overlayPercent = overlayPercent,
            overlayMirrored = false,
            overlayHidden = false,
            onOverlayPercentChange = onOverlayPercentChange,
            onOverlayPercentChangeFinished = {},
            onHoldOverlayAway = {},
            picture = null,
            blink = 0f,
            saved = false,
            busy = false,
            shutterEnabled = true,
            flashKind = flashKind,
            flashOn = flashOn,
            onToggleFlash = onToggleFlash,
            onShutter = onShutter,
            onClose = {},
            onRetake = {},
            note = null,
            showFirstPhotoHint = !withOverlay,
        ) {}
    }

    @Test
    fun `the big shutter button takes the photo`() {
        var photos = 0
        show { Camera(onShutter = { photos++ }) }
        compose.onNodeWithTag(CameraTags.SHUTTER).performClick()
        assertEquals(1, photos)
    }

    @Test
    fun `the flash button says it is off and toggles on a tap`() {
        var flashOn by mutableStateOf(false)
        show { Camera(flashOn = flashOn, onToggleFlash = { flashOn = !flashOn }) }

        compose.onNodeWithContentDescription("Flitser staat uit. Tik om aan te zetten.").assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertTrue(flashOn)
        compose.onNodeWithContentDescription("Flitser staat aan. Tik om uit te zetten.").assertIsDisplayed()
    }

    @Test
    fun `a camera without flash has no flash button`() {
        show { Camera(flashKind = FlashKind.NONE) }
        compose.onNodeWithTag(CameraTags.FLASH).assertDoesNotExist()
    }

    @Test
    fun `the overlay slider runs from 0 to 100 percent`() {
        var percent by mutableStateOf(35)
        show { Camera(overlayPercent = percent, onOverlayPercentChange = { percent = it }) }

        compose.onNodeWithText("35%").assertIsDisplayed()
        compose.onNodeWithContentDescription(OVERLAY_SLIDER_DESCRIPTION).performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        compose.waitForIdle()
        assertEquals(80, percent)
        compose.onNodeWithText("80%").assertIsDisplayed()

        compose.onNodeWithContentDescription(OVERLAY_SLIDER_DESCRIPTION).performSemanticsAction(SemanticsActions.SetProgress) { it(250f) }
        compose.waitForIdle()
        assertEquals(100, percent)
    }

    @Test
    fun `on the first day there is no overlay and no slider, only an explanation`() {
        show { Camera(withOverlay = false) }
        compose.onNodeWithTag(CameraTags.OVERLAY).assertDoesNotExist()
        compose.onNodeWithContentDescription(OVERLAY_SLIDER_DESCRIPTION).assertDoesNotExist()
        compose.onNodeWithText("Je eerste foto!", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a slider with steps only lands on whole values`() {
        var value by mutableFloatStateOf(10f)
        show {
            RetroSlider(
                value = value,
                onValueChange = { value = it },
                valueRange = 1f..30f,
                steps = 28,
                description = "snelheid",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        compose.onNodeWithContentDescription("snelheid").performSemanticsAction(SemanticsActions.SetProgress) { it(12.4f) }
        compose.waitForIdle()
        assertEquals(12f, value, 0.001f)
    }

    // --- Projects ----------------------------------------------------------------------------

    @Test
    fun `making a project with the back camera and a square shape`() {
        var created: Project? = null
        show { NewProjectScreen(repository, onCreated = { created = it }, onBack = {}) }

        compose.onNode(hasSetTextAction()).performTextInput("De boom in de tuin")
        compose.onNodeWithText("Achterkant").performClick()
        compose.onNodeWithText("1:1").performClick()
        compose.onNodeWithText("Maak project").performClick()
        compose.waitForIdle()

        assertNotNull(created)
        val stored = repository.project("De boom in de tuin")!!
        assertEquals(Lens.BACK, stored.settings.lens)
        assertEquals(AspectRatio.SQUARE, stored.settings.aspectRatio)
        assertTrue(File(root, "De boom in de tuin/project.json").isFile)
    }

    @Test
    fun `a project without a name cannot be made`() {
        var created: Project? = null
        show { NewProjectScreen(repository, onCreated = { created = it }, onBack = {}) }
        compose.onNodeWithText("Maak project").performClick()
        compose.waitForIdle()
        assertNull(created)
        assertTrue(repository.projects.value!!.isEmpty())
    }

    @Test
    fun `the start screen tells per project whether today's photo is there`() {
        addProject("Gezicht", Lens.FRONT, AspectRatio.PORTRAIT_3_4, 0, 1, 2)
        addProject("Tuin", Lens.BACK, AspectRatio.PORTRAIT_3_4, 1, 2)
        var opened: String? = null
        var camera: String? = null
        val projects = repository.projects.value
        show {
            HomeScreen(
                repository = repository,
                projects = projects,
                onOpenProject = { opened = it.folderName },
                onTakePhoto = { camera = it.folderName },
                onNewProject = {},
                onAbout = {},
            )
        }

        compose.onNodeWithText("Vandaag gelukt").assertIsDisplayed()
        compose.onNodeWithText("Vandaag nog niet").assertIsDisplayed()
        compose.onNodeWithText("3 foto's").assertIsDisplayed()
        compose.onNodeWithText("2 foto's").assertIsDisplayed()

        compose.onNodeWithContentDescription("Maak de foto van vandaag voor Tuin").performClick()
        assertEquals("Tuin", camera)
        compose.onNodeWithText("Gezicht").performClick()
        assertEquals("Gezicht", opened)
    }

    // --- Deleting needs a yes ------------------------------------------------------------------

    @Test
    fun `a photo is only deleted after confirming`() {
        val project = addProject("Tuin", Lens.BACK, AspectRatio.PORTRAIT_3_4, 0, 1)
        show { ViewerScreen(repository, repository.project("Tuin")!!, today, onBack = {}, onReplace = {}) }
        assertEquals(2, project.photos.size)

        compose.onNodeWithText("Verwijder").performClick()
        compose.onNodeWithText("Foto verwijderen?").assertIsDisplayed()
        compose.onNodeWithText("Nee, bewaar de foto").performClick()
        compose.waitForIdle()
        assertEquals(2, repository.project("Tuin")!!.photos.size)

        compose.onNodeWithText("Verwijder").performClick()
        compose.onNodeWithText("Ja, verwijder").performClick()
        compose.waitForIdle()
        assertEquals(listOf(today.minusDays(1)), repository.project("Tuin")!!.dates)
    }

    @Test
    fun `replacing a photo asks first and then opens the camera for that day`() {
        addProject("Tuin", Lens.BACK, AspectRatio.PORTRAIT_3_4, 0, 1)
        var replacing: LocalDate? = null
        show { ViewerScreen(repository, repository.project("Tuin")!!, today.minusDays(1), onBack = {}, onReplace = { replacing = it }) }

        compose.onNodeWithText("Vervang").performClick()
        compose.onNodeWithText("Foto vervangen?").assertIsDisplayed()
        assertNull(replacing)
        compose.onNodeWithText("Maak een nieuwe foto").performClick()
        assertEquals(today.minusDays(1), replacing)
    }

    // --- Timelapse settings --------------------------------------------------------------------

    @Test
    fun `the timelapse screen tells how long the video will be`() {
        addProject("Tuin", Lens.BACK, AspectRatio.PORTRAIT_3_4, *(0L..29L).toList().toLongArray())
        show { TimelapseScreen(repository, repository.project("Tuin")!!, onBack = {}, onPlay = {}) }

        compose.onNodeWithText("10 foto's per seconde").assertIsDisplayed()
        compose.onNodeWithText("30 foto's ≈ 3 seconden").assertExists()

        compose.onNodeWithContentDescription("Snelheid in foto's per seconde")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.waitForIdle()
        compose.onNodeWithText("30 foto's ≈ 1 seconde").assertExists()
    }
}
