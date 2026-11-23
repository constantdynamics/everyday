package nl.constantdynamics.dagelijks.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Surface
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.constantdynamics.dagelijks.camera.CameraSession
import nl.constantdynamics.dagelijks.camera.CameraUnavailableException
import nl.constantdynamics.dagelijks.camera.FlashKind
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.Days
import nl.constantdynamics.dagelijks.core.Mirroring
import nl.constantdynamics.dagelijks.image.ImageLoader
import nl.constantdynamics.dagelijks.image.PhotoProcessor
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.Format
import nl.constantdynamics.dagelijks.ui.LocalServices
import nl.constantdynamics.dagelijks.ui.ShutterKeyHandler
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.RetroButton
import nl.constantdynamics.dagelijks.ui.components.RetroDialog
import nl.constantdynamics.dagelijks.ui.components.RetroIconButton
import nl.constantdynamics.dagelijks.ui.components.RetroSlider
import nl.constantdynamics.dagelijks.ui.components.Ribbon
import nl.constantdynamics.dagelijks.ui.components.rememberPhotoBitmap
import nl.constantdynamics.dagelijks.ui.theme.RetroColors
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Test tags, so automated tests can find the important parts of the camera screen. */
object CameraTags {
    const val VIEWFINDER = "zoeker"
    const val SHUTTER = "sluiter"
    const val OVERLAY = "overlay"
    const val FLASH = "flits"
}

/** A photo that has been taken and prepared, but not stored yet. */
private class PendingPhoto(val file: File, val picture: Bitmap?, val date: LocalDate)

private sealed interface Phase {
    /** Live camera image; the shutter is available. */
    data object Aiming : Phase

    /** Shutter pressed: taking and preparing the photo. */
    data object Capturing : Phase

    /** There already is a photo for that day: ask before replacing it. */
    data class ConfirmReplace(val photo: PendingPhoto) : Phase
    data class Saving(val photo: PendingPhoto) : Phase
    data class Saved(val photo: PendingPhoto) : Phase
    data class Failed(val whileSaving: Boolean) : Phase
}

/**
 * The heart of the app: the live camera image with the previous photo of the project
 * lying over it, half transparent, so today's photo can be lined up exactly.
 *
 * The screen itself stays quiet and dark. The viewfinder has precisely the project's
 * aspect ratio and shows precisely what will be saved; the overlay fills that same
 * rectangle, so the two coincide. For the front camera the overlay is mirrored,
 * because the live image is mirrored there and the stored photo is not.
 */
@SuppressLint("SourceLockedOrientationActivity") // a daily photo needs the same orientation every day
@Composable
fun CameraScreen(
    repository: ProjectRepository,
    project: Project,
    replaceDate: LocalDate?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val services = LocalServices.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val settings = project.settings
    val aspectRatio = settings.aspectRatio
    val lens = settings.lens

    var phase by remember { mutableStateOf<Phase>(Phase.Aiming) }
    // The flash is off whenever this screen opens; switching it on lasts for this visit only.
    var flashOn by remember { mutableStateOf(false) }
    var flashKind by remember { mutableStateOf(FlashKind.NONE) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraProblem by remember { mutableStateOf(false) }
    var overlayPercent by remember(project.folderName) { mutableIntStateOf(settings.overlayPercent) }
    var overlayHeldAway by remember { mutableStateOf(false) }
    val blink = remember { Animatable(0f) }

    // --- Camera permission ---------------------------------------------------------------
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionAsked = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionRequest.launch(Manifest.permission.CAMERA)
    }

    // --- Window behaviour while the camera is open ------------------------------------------
    val wantsLandscape = aspectRatio.isLandscape
    DisposableEffect(activity, wantsLandscape) {
        // A landscape project is shot with the phone on its side; everything else upright.
        activity?.requestedOrientation = if (wantsLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    DisposableEffect(view, activity) {
        view.keepScreenOn = true
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            view.keepScreenOn = false
            bars?.isAppearanceLightStatusBars = true
            bars?.isAppearanceLightNavigationBars = true
        }
    }

    fun close() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onClose()
    }
    // The back gesture leaves the same way as the close button, turning the screen upright first.
    BackHandler(onBack = ::close)

    // --- Camera -----------------------------------------------------------------------------
    val session = remember { CameraSession(context.applicationContext) }
    val previewView = remember {
        PreviewView(context).apply {
            // A TextureView-based preview composes predictably with the overlay drawn on top of it.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(session) { onDispose { session.release() } }

    // The photo is turned upright according to the display's rotation, so the camera is
    // only started once the display has really reached the orientation this project is
    // shot in, and it is started again whenever the display turns.
    val displayRotation = rememberDisplayRotation()
    val orientation = LocalConfiguration.current.orientation
    var boundRotation by remember { mutableStateOf<Int?>(null) }
    var rebindRequests by remember { mutableIntStateOf(0) }
    LaunchedEffect(hasPermission, lens, aspectRatio, displayRotation, orientation, rebindRequests) {
        cameraReady = false
        boundRotation = null
        if (!hasPermission) return@LaunchedEffect
        val rotation = awaitDisplayRotation(view, wantsLandscape)
        try {
            flashKind = session.bind(lifecycleOwner, previewView, activity?.window, lens, aspectRatio, rotation)
            session.setFlash(flashOn)
            boundRotation = rotation
            cameraProblem = false
            cameraReady = true
        } catch (_: CameraUnavailableException) {
            cameraProblem = true
        }
    }
    LaunchedEffect(flashOn, cameraReady) {
        if (cameraReady) session.setFlash(flashOn)
    }

    // --- The previous photo as overlay ------------------------------------------------------
    val today = remember { LocalDate.now() }
    val targetDate = replaceDate ?: today
    val overlayPhoto = remember(project.photos, targetDate) {
        Days.overlayDateFor(project.dates, targetDate)?.let(project::photoOn)
    }
    val overlayBitmap = rememberPhotoBitmap(repository, overlayPhoto, maxSide = 1600)
    val photoOfTargetDayExists = project.photoOn(targetDate) != null

    fun storeOverlayPercent() {
        val value = overlayPercent
        services.scope.launch {
            runCatching { repository.updateSettings(project.folderName) { it.copy(overlayPercent = value) } }
        }
    }

    // --- Taking and storing a photo ---------------------------------------------------------
    fun save(photo: PendingPhoto) {
        phase = Phase.Saving(photo)
        // Stored from the app's own scope: leaving the screen must not interrupt the write.
        services.scope.launch {
            val stored = runCatching { repository.savePhoto(project.folderName, photo.date, photo.file) }
            photo.file.delete()
            phase = if (stored.isSuccess) Phase.Saved(photo) else Phase.Failed(whileSaving = true)
        }
    }

    fun shoot() {
        if (phase != Phase.Aiming || !cameraReady) return
        if (view.display?.rotation != boundRotation) {
            // The display turned after the camera was started: start it again first, so
            // the photo cannot come out lying on its side.
            rebindRequests++
            return
        }
        phase = Phase.Capturing
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            blink.snapTo(0.7f)
            blink.animateTo(0f, tween(220))
        }
        scope.launch {
            try {
                val raw = session.capture()
                val takenAt = LocalDateTime.now()
                val date = replaceDate ?: takenAt.toLocalDate()
                val pending = withContext(Dispatchers.Default) {
                    val directory = File(context.cacheDir, "opname").apply { mkdirs() }
                    val file = File(directory, "nieuw.jpg")
                    PhotoProcessor.process(raw.jpeg, raw.cropRect, raw.rotationDegrees, aspectRatio, takenAt, file)
                    PendingPhoto(file, ImageLoader.decodeScaled(file.readBytes(), 1600), date)
                }
                val alreadyThere = repository.project(project.folderName)?.photoOn(date) != null
                if (alreadyThere) phase = Phase.ConfirmReplace(pending) else save(pending)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                phase = Phase.Failed(whileSaving = false)
            }
        }
    }

    ShutterKeyHandler(enabled = phase is Phase.Aiming || phase is Phase.Capturing || phase is Phase.Saving, onShutter = ::shoot)

    // --- Layout -----------------------------------------------------------------------------
    val shownPicture = when (val current = phase) {
        is Phase.ConfirmReplace -> current.photo.picture
        is Phase.Saving -> current.photo.picture
        is Phase.Saved -> current.photo.picture
        else -> null
    }
    CameraLayout(
        aspectRatio = aspectRatio,
        overlay = overlayBitmap,
        overlayPercent = overlayPercent,
        overlayMirrored = Mirroring.isOverlayMirrored(lens),
        overlayHidden = overlayHeldAway,
        onOverlayPercentChange = { overlayPercent = it },
        onOverlayPercentChangeFinished = ::storeOverlayPercent,
        onHoldOverlayAway = { overlayHeldAway = it },
        picture = shownPicture,
        blink = blink.value,
        saved = phase is Phase.Saved,
        busy = phase is Phase.Capturing || phase is Phase.Saving,
        shutterEnabled = cameraReady && phase is Phase.Aiming,
        flashKind = flashKind,
        flashOn = flashOn,
        onToggleFlash = { flashOn = !flashOn },
        onShutter = ::shoot,
        onClose = ::close,
        onRetake = { phase = Phase.Aiming },
        note = when {
            !hasPermission || cameraProblem || phase is Phase.Saved -> null
            replaceDate != null -> "Je vervangt de foto van ${Format.date(replaceDate)}"
            photoOfTargetDayExists && phase is Phase.Aiming -> "Vandaag al gelukt. Een nieuwe foto vervangt die pas als jij dat goedvindt."
            else -> null
        },
        showFirstPhotoHint = overlayPhoto == null && hasPermission,
    ) {
        when {
            !hasPermission -> PermissionExplanation(
                asked = permissionAsked,
                onAsk = { permissionRequest.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )
            cameraProblem -> FinderMessage("De camera kon niet worden gestart. Sluit andere camera-apps en probeer het opnieuw.")
            else -> AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }
    }

    // --- Questions and problems -------------------------------------------------------------
    when (val current = phase) {
        is Phase.ConfirmReplace -> {
            val day = if (current.photo.date == today) "vandaag" else Format.date(current.photo.date)
            RetroDialog(
                title = "Foto vervangen?",
                onDismiss = {
                    current.photo.file.delete()
                    phase = Phase.Aiming
                },
                confirmText = "Ja, vervang",
                onConfirm = { save(current.photo) },
                dismissText = "Nee, bewaar de oude",
            ) {
                Text(
                    "Er is al een foto van $day. Wil je die vervangen door de foto die je net hebt gemaakt? De oude foto wordt dan verwijderd.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        is Phase.Failed -> RetroDialog(
            title = "Dat ging niet goed",
            onDismiss = { phase = Phase.Aiming },
            confirmText = "Oké",
            onConfirm = { phase = Phase.Aiming },
            dismissText = null,
            confirmColor = RetroColors.Mustard,
        ) {
            Text(
                text = if (current.whileSaving) {
                    "De foto kon niet worden opgeslagen. Controleer of er nog ruimte vrij is op je telefoon en of de map Dagelijks nog bestaat."
                } else {
                    "De foto kon niet worden gemaakt. Probeer het nog een keer."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        else -> Unit
    }
}

/**
 * Everything the camera screen shows, without the camera itself: [preview] is whatever
 * provides the live image. That keeps this layout renderable in a test, with a still
 * picture standing in for the camera.
 *
 * The viewfinder gets exactly the project's aspect ratio and is made as large as the
 * screen allows. The controls sit below it, or beside it when the phone is held
 * sideways for a landscape project.
 */
@Composable
fun CameraLayout(
    aspectRatio: AspectRatio,
    overlay: Bitmap?,
    overlayPercent: Int,
    overlayMirrored: Boolean,
    overlayHidden: Boolean,
    onOverlayPercentChange: (Int) -> Unit,
    onOverlayPercentChangeFinished: () -> Unit,
    onHoldOverlayAway: (Boolean) -> Unit,
    picture: Bitmap?,
    blink: Float,
    saved: Boolean,
    busy: Boolean,
    shutterEnabled: Boolean,
    flashKind: FlashKind,
    flashOn: Boolean,
    onToggleFlash: () -> Unit,
    onShutter: () -> Unit,
    onClose: () -> Unit,
    onRetake: () -> Unit,
    note: String?,
    showFirstPhotoHint: Boolean,
    preview: @Composable BoxScope.() -> Unit,
) {
    val overlayAlpha = if (overlayHidden || picture != null) 0f else overlayPercent / 100f
    val showOverlayControls = overlay != null && !saved

    Box(Modifier.fillMaxSize().background(RetroColors.CameraBackground)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val landscape = maxWidth > maxHeight
            val sidePanel = 76.dp
            val shutterPanel = 124.dp
            val bottomPanel = 176.dp
            val areaWidth = if (landscape) maxWidth - sidePanel - shutterPanel else maxWidth
            val areaHeight = if (landscape) maxHeight else maxHeight - bottomPanel
            val finderSize = fitInside(aspectRatio, areaWidth, areaHeight)
            val finderX = (if (landscape) sidePanel else 0.dp) + (areaWidth - finderSize.first) / 2
            val finderY = (areaHeight - finderSize.second) / 2

            Viewfinder(
                modifier = Modifier.offset(finderX, finderY).size(finderSize.first, finderSize.second),
                preview = preview,
                overlay = overlay,
                overlayAlpha = overlayAlpha,
                overlayMirrored = overlayMirrored,
                picture = picture,
                blink = blink,
                onHold = onHoldOverlayAway,
            ) {
                when {
                    saved -> Ribbon(
                        "opgeslagen!",
                        Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                        color = RetroColors.Mustard,
                        textColor = RetroColors.Navy,
                    )
                    note != null -> FinderNote(note)
                }
            }

            if (saved) {
                SavedControls(
                    landscape = landscape,
                    modifier = if (landscape) {
                        Modifier.align(Alignment.CenterEnd).width(shutterPanel + sidePanel).fillMaxHeight().padding(12.dp)
                    } else {
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomPanel).padding(horizontal = 20.dp)
                    },
                    onRetake = onRetake,
                    onDone = onClose,
                )
            } else if (landscape) {
                Column(
                    modifier = Modifier.align(Alignment.CenterStart).width(sidePanel).fillMaxHeight().padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (showOverlayControls) {
                        Text("$overlayPercent%", style = MaterialTheme.typography.labelLarge, color = RetroColors.Cream)
                        Spacer(Modifier.height(6.dp))
                        RetroSlider(
                            value = overlayPercent.toFloat(),
                            onValueChange = { onOverlayPercentChange(it.roundToInt()) },
                            valueRange = 0f..100f,
                            onValueChangeFinished = onOverlayPercentChangeFinished,
                            onDark = true,
                            description = OVERLAY_SLIDER_DESCRIPTION,
                            modifier = Modifier.weight(1f).vertical(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Icon(AppIcons.Layers, contentDescription = null, tint = RetroColors.Cream, modifier = Modifier.size(22.dp))
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).width(shutterPanel).fillMaxHeight().padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    RetroIconButton(AppIcons.Close, "Sluiten", onClick = onClose)
                    ShutterButton(enabled = shutterEnabled, busy = busy, onClick = onShutter)
                    FlashButton(flashKind, flashOn, onToggle = onToggleFlash)
                }
            } else {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomPanel).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (showOverlayControls) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Layers, contentDescription = null, tint = RetroColors.Cream, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            RetroSlider(
                                value = overlayPercent.toFloat(),
                                onValueChange = { onOverlayPercentChange(it.roundToInt()) },
                                valueRange = 0f..100f,
                                onValueChangeFinished = onOverlayPercentChangeFinished,
                                onDark = true,
                                description = OVERLAY_SLIDER_DESCRIPTION,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "$overlayPercent%",
                                style = MaterialTheme.typography.labelLarge,
                                color = RetroColors.Cream,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(46.dp),
                            )
                        }
                    } else if (showFirstPhotoHint) {
                        Text(
                            "Je eerste foto! Vanaf de volgende keer schijnt deze hier doorheen, zodat je precies hetzelfde kunt gaan staan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RetroColors.Cream,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FlashButton(flashKind, flashOn, onToggle = onToggleFlash)
                        ShutterButton(enabled = shutterEnabled, busy = busy, onClick = onShutter)
                        RetroIconButton(AppIcons.Close, "Sluiten", onClick = onClose)
                    }
                }
            }
        }
    }
}

const val OVERLAY_SLIDER_DESCRIPTION = "Zichtbaarheid van de vorige foto"

/** The largest size with the project's aspect ratio that fits in the given space. */
private fun fitInside(aspectRatio: AspectRatio, width: Dp, height: Dp): Pair<Dp, Dp> {
    val ratio = aspectRatio.value
    return if (width / height > ratio) Pair(height * ratio, height) else Pair(width, width / ratio)
}

/**
 * The rectangle in which the live image, the overlay and (after shooting) the result
 * are stacked. All three fill it completely, which is what makes them coincide.
 */
@Composable
private fun Viewfinder(
    modifier: Modifier,
    preview: @Composable BoxScope.() -> Unit,
    overlay: Bitmap?,
    overlayAlpha: Float,
    overlayMirrored: Boolean,
    picture: Bitmap?,
    blink: Float,
    onHold: (Boolean) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .testTag(CameraTags.VIEWFINDER)
            // The camera view draws the whole sensor image and counts on being cut off at
            // its edges. Compose does not do that for an embedded view by itself, so the
            // viewfinder cuts everything to its own rectangle: what shows is what is saved.
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Press and hold to look at the live image without the overlay for a moment.
                detectTapGestures(
                    onPress = {
                        onHold(true)
                        tryAwaitRelease()
                        onHold(false)
                    },
                )
            },
    ) {
        preview()
        if (overlay != null && overlayAlpha > 0f) {
            PreviousPhotoOverlay(overlay, overlayAlpha, overlayMirrored, Modifier.fillMaxSize())
        }
        if (picture != null) {
            Image(
                bitmap = picture.asImageBitmap(),
                contentDescription = "De foto die je net hebt gemaakt",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (blink > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = blink)))
        Box(Modifier.fillMaxSize().border(1.5.dp, RetroColors.Cream.copy(alpha = 0.55f)))
        content()
    }
}

/**
 * The previous photo, half transparent, to be laid exactly over the live image.
 *
 * [mirrored] must be true over the front camera's live image: that image is shown
 * mirrored while the stored photo is not, so the photo is flipped to lie the same
 * way round.
 */
@Composable
fun PreviousPhotoOverlay(photo: Bitmap, alpha: Float, mirrored: Boolean, modifier: Modifier = Modifier) {
    Image(
        bitmap = photo.asImageBitmap(),
        contentDescription = "De vorige foto, half doorzichtig over het camerabeeld",
        contentScale = ContentScale.Crop,
        alpha = alpha,
        modifier = modifier
            .testTag(CameraTags.OVERLAY)
            .graphicsLayer { scaleX = if (mirrored) -1f else 1f },
    )
}

@Composable
private fun BoxScope.FinderNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = RetroColors.Cream,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RetroColors.NavyDeep.copy(alpha = 0.78f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun BoxScope.FinderMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = RetroColors.Cream,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
    )
}

@Composable
private fun BoxScope.PermissionExplanation(
    asked: Boolean,
    onAsk: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Dagelijks heeft de camera nodig om foto's te maken. Dat is de enige toestemming die de app vraagt.",
            style = MaterialTheme.typography.bodyLarge,
            color = RetroColors.Cream,
            textAlign = TextAlign.Center,
        )
        RetroButton("Geef toegang tot de camera", onClick = onAsk, icon = AppIcons.Camera)
        if (asked) {
            Text(
                "Gebeurt er niets? Zet de camera dan aan bij de instellingen van de app.",
                style = MaterialTheme.typography.bodyMedium,
                color = RetroColors.Cream,
                textAlign = TextAlign.Center,
            )
            RetroButton("Open instellingen", onClick = onOpenSettings, color = RetroColors.Cream, contentColor = RetroColors.Navy)
        }
    }
}

/** The big round shutter button. */
@Composable
private fun ShutterButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(92.dp)
            .testTag(CameraTags.SHUTTER)
            .semantics { contentDescription = "Maak foto" }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                val radius = size.minDimension / 2
                val ring = 5.dp.toPx()
                drawCircle(RetroColors.Cream, radius - ring / 2, style = Stroke(ring))
                val face = (radius - ring - 5.dp.toPx()) * (if (pressed) 0.9f else 1f)
                drawCircle(if (enabled || busy) RetroColors.Coral else RetroColors.Coral.copy(alpha = 0.4f), face)
                drawCircle(RetroColors.NavyDeep, face, style = Stroke(2.5.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(color = RetroColors.Cream, strokeWidth = 4.dp, modifier = Modifier.size(40.dp))
    }
}

/** Off by default. Lights up mustard when switched on for this visit. */
@Composable
private fun FlashButton(kind: FlashKind, on: Boolean, onToggle: () -> Unit) {
    if (kind == FlashKind.NONE) {
        // Keeps the shutter centred when there is nothing to switch.
        Spacer(Modifier.size(56.dp))
        return
    }
    val what = if (kind == FlashKind.SCREEN) "Schermflits" else "Flitser"
    RetroIconButton(
        icon = if (on) AppIcons.FlashOn else AppIcons.FlashOff,
        description = if (on) "$what staat aan. Tik om uit te zetten." else "$what staat uit. Tik om aan te zetten.",
        onClick = onToggle,
        color = if (on) RetroColors.Mustard else RetroColors.Cream,
        modifier = Modifier.testTag(CameraTags.FLASH),
    )
}

@Composable
private fun SavedControls(landscape: Boolean, modifier: Modifier, onRetake: () -> Unit, onDone: () -> Unit) {
    if (landscape) {
        Column(modifier, verticalArrangement = Arrangement.Center) {
            RetroButton("Klaar", onClick = onDone, icon = AppIcons.Check, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            RetroButton("Opnieuw", onClick = onRetake, icon = AppIcons.Retake, color = RetroColors.Cream, contentColor = RetroColors.Navy, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RetroButton("Opnieuw", onClick = onRetake, icon = AppIcons.Retake, color = RetroColors.Cream, contentColor = RetroColors.Navy, modifier = Modifier.weight(1f))
            RetroButton("Klaar", onClick = onDone, icon = AppIcons.Check, modifier = Modifier.weight(1f))
        }
    }
}

/** Turns a horizontal control a quarter turn, for the slider beside a landscape viewfinder. */
private fun Modifier.vertical(): Modifier = this
    .graphicsLayer {
        rotationZ = 270f
        transformOrigin = TransformOrigin(0f, 0f)
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            ),
        )
        layout(placeable.height, placeable.width) { placeable.place(-placeable.width, 0) }
    }

/** The display's current rotation (Surface.ROTATION_*), kept up to date while on screen. */
@Composable
private fun rememberDisplayRotation(): Int {
    val context = LocalContext.current
    val view = LocalView.current
    var rotation by remember { mutableIntStateOf(view.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(view) {
        val manager = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                view.display?.let { rotation = it.rotation }
            }

            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        manager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        view.display?.let { rotation = it.rotation }
        onDispose { manager?.unregisterDisplayListener(listener) }
    }
    return rotation
}

/**
 * Waits until the display has turned to the wanted orientation and returns its
 * rotation at that moment. Size and rotation are read from the display together,
 * so they always belong to each other.
 *
 * Some devices (tablets, unfolded foldables) ignore a request to turn. After a few
 * seconds the display is therefore taken as it is.
 */
private suspend fun awaitDisplayRotation(view: View, landscape: Boolean, patienceMillis: Long = 3_000): Int {
    val started = SystemClock.elapsedRealtime()
    val size = Point()
    while (true) {
        val display = view.display
        val outOfPatience = SystemClock.elapsedRealtime() - started > patienceMillis
        if (display != null) {
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            if ((size.x > size.y) == landscape || outOfPatience) return display.rotation
        } else if (outOfPatience) {
            return Surface.ROTATION_0
        }
        delay(50)
    }
}
