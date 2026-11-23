package nl.constantdynamics.dagelijks.framework

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import nl.constantdynamics.dagelijks.ui.components.RetroDialog
import nl.constantdynamics.dagelijks.ui.screens.AboutScreen
import nl.constantdynamics.dagelijks.ui.screens.CameraLayout
import nl.constantdynamics.dagelijks.ui.screens.GalleryScreen
import nl.constantdynamics.dagelijks.ui.screens.HomeScreen
import nl.constantdynamics.dagelijks.ui.screens.NewProjectScreen
import nl.constantdynamics.dagelijks.ui.screens.TimelapseScreen
import nl.constantdynamics.dagelijks.ui.screens.ViewerScreen
import nl.constantdynamics.dagelijks.ui.screens.WelcomeScreen
import nl.constantdynamics.dagelijks.ui.theme.DagelijksTheme
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

/**
 * Renders every screen with realistic content and saves it as a picture. Besides
 * proving that each screen can be drawn without a phone, the pictures are how the
 * design is reviewed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = PHONE_UPRIGHT)
class ScreensTest {

    @get:Rule(order = 0)
    val registerActivity = RegisterComposeActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var repository: ProjectRepository
    private lateinit var services: AppServices
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        val tree = LocalFileTree(temporary.newFolder("Dagelijks"))
        repository = ProjectRepository(tree, tree.pickedFolderId, Dispatchers.Unconfined, photoSize = ImageLoader::readSize)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        services = AppServices(
            scope = scope,
            imageLoader = ImageLoader(temporary.newFolder("cache"), Dispatchers.Unconfined),
            timelapseRenderer = TimelapseRenderer(ApplicationProvider.getApplicationContext(), scope),
            chooseFolder = { StorageAccess.PickResult.Failed(IllegalStateException("not in a test")) },
        )
        runBlocking {
            repository.createProject("Mijn gezicht", Lens.FRONT, AspectRatio.PORTRAIT_3_4)
            // Forty days, with a few missed ones, and today's photo already taken.
            (0L..44L).filter { it % 9 != 4L }.forEach { daysAgo ->
                savePhoto("Mijn gezicht", today.minusDays(daysAgo), 450, 600)
            }
            repository.createProject("De boom in de tuin", Lens.BACK, AspectRatio.LANDSCAPE_16_9)
            (1L..12L).forEach { daysAgo -> savePhoto("De boom in de tuin", today.minusDays(daysAgo), 640, 360) }
            repository.createProject("Ochtendlicht", Lens.BACK, AspectRatio.SQUARE)
            repository.refresh()
        }
    }

    private suspend fun savePhoto(project: String, day: LocalDate, width: Int, height: Int) {
        val file = File(temporary.root, "bron-$project-$day.jpg")
        file.writeBytes(Fixtures.jpeg(Fixtures.dailyPhoto(width, height, day), quality = 85))
        repository.savePhoto(project, day, file)
        file.delete()
    }

    private fun project(name: String): Project = repository.project(name)!!

    private fun show(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalServices provides services) {
                DagelijksTheme { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().saveScreenshot(name)
    }

    @Test
    fun welcome() {
        show("01-welkom") { WelcomeScreen() }
        compose.onNodeWithText("Kies een map").assertIsDisplayed()
    }

    @Test
    fun homeWithoutProjects() {
        show("02-start-leeg") { HomeScreen(repository, emptyList(), {}, {}, {}, {}) }
        compose.onNodeWithText("Nog geen projecten").assertIsDisplayed()
    }

    @Test
    fun home() {
        show("03-start") { HomeScreen(repository, repository.projects.value, {}, {}, {}, {}) }
        compose.onNodeWithText("Mijn gezicht").assertIsDisplayed()
        compose.onNodeWithText("Vandaag gelukt").assertIsDisplayed()
    }

    @Test
    fun newProject() {
        show("04-nieuw-project") { NewProjectScreen(repository, {}, {}) }
        compose.onNodeWithText("Maak project").assertIsDisplayed()
    }

    @Test
    fun gallery() {
        show("05-galerij") { GalleryScreen(repository, project("Mijn gezicht"), {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Maak timelapse").assertIsDisplayed()
    }

    @Test
    fun galleryOfLandscapeProject() {
        show("06-galerij-liggend") { GalleryScreen(repository, project("De boom in de tuin"), {}, {}, {}, {}, {}) }
    }

    @Test
    fun galleryWithoutPhotos() {
        show("07-galerij-leeg") { GalleryScreen(repository, project("Ochtendlicht"), {}, {}, {}, {}, {}) }
    }

    @Test
    fun viewer() {
        show("08-foto") { ViewerScreen(repository, project("Mijn gezicht"), today, {}, {}) }
        compose.onNodeWithText("Verwijder").assertIsDisplayed()
    }

    @Test
    fun timelapse() {
        show("09-timelapse") { TimelapseScreen(repository, project("Mijn gezicht"), {}, {}) }
        compose.onNodeWithText("Maak timelapse").assertExists()
    }

    @Test
    fun about() {
        show("10-over") { AboutScreen("Documents/Dagelijks", {}) }
    }

    @Test
    fun cameraWithOverlay() {
        show("11-camera") { Camera(AspectRatio.PORTRAIT_3_4, overlay = true) }
    }

    @Test
    fun cameraTall() {
        show("12-camera-9x16") { Camera(AspectRatio.PORTRAIT_9_16, overlay = true, flashOn = true) }
    }

    @Test
    fun cameraSquareFirstPhoto() {
        show("13-camera-eerste-foto") { Camera(AspectRatio.SQUARE, overlay = false) }
        compose.onNodeWithText("Je eerste foto!", substring = true).assertIsDisplayed()
    }

    @Test
    fun cameraAfterSaving() {
        show("14-camera-opgeslagen") { Camera(AspectRatio.PORTRAIT_3_4, overlay = true, saved = true) }
        compose.onNodeWithText("Klaar").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE_SIDEWAYS)
    fun cameraLandscape() {
        show("15-camera-liggend") { Camera(AspectRatio.LANDSCAPE_16_9, overlay = true) }
    }

    @Test
    @Config(qualifiers = PHONE_SIDEWAYS)
    fun cameraLandscapeAfterSaving() {
        show("16-camera-liggend-opgeslagen") { Camera(AspectRatio.LANDSCAPE_16_9, overlay = true, saved = true) }
    }

    @Test
    fun replaceQuestion() {
        compose.setContent {
            DagelijksTheme {
                RetroDialog(
                    title = "Foto vervangen?",
                    onDismiss = {},
                    confirmText = "Ja, vervang",
                    onConfirm = {},
                    dismissText = "Nee, bewaar de oude",
                ) {
                    Text(
                        "Er is al een foto van vandaag. Wil je die vervangen door de foto die je net hebt gemaakt? De oude foto wordt dan verwijderd.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNode(isDialog()).saveScreenshot("17-vraag-vervangen")
        compose.onNodeWithText("Ja, vervang").assertIsDisplayed()
    }

    /** The camera screen with a still picture standing in for the live camera image. */
    @Composable
    private fun Camera(ratio: AspectRatio, overlay: Boolean, saved: Boolean = false, flashOn: Boolean = false) {
        val size = if (ratio.isLandscape) 960 to 540 else 600 to (600 / ratio.value).toInt()
        val live: Bitmap = Fixtures.dailyPhoto(size.first, size.second, today, offset = 0.5f)
        val previous: Bitmap = Fixtures.dailyPhoto(size.first, size.second, today.minusDays(1))
        CameraLayout(
            aspectRatio = ratio,
            overlay = previous.takeIf { overlay },
            overlayPercent = 35,
            overlayMirrored = false,
            overlayHidden = false,
            onOverlayPercentChange = {},
            onOverlayPercentChangeFinished = {},
            onHoldOverlayAway = {},
            picture = live.takeIf { saved },
            blink = 0f,
            saved = saved,
            busy = false,
            shutterEnabled = true,
            flashKind = FlashKind.LED,
            flashOn = flashOn,
            onToggleFlash = {},
            onShutter = {},
            onClose = {},
            onRetake = {},
            note = null,
            showFirstPhotoHint = !overlay,
        ) {
            Image(live.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
