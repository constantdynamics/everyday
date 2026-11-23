package nl.constantdynamics.dagelijks.camera

import android.content.Context
import android.util.Rational
import android.view.Window
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.Lens
import nl.constantdynamics.dagelijks.core.PixelRect
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A picture straight from the camera, before it is cut and turned upright. */
class RawCapture(
    val jpeg: ByteArray,
    /** The part of the picture that the preview showed, in the picture's own coordinates. */
    val cropRect: PixelRect?,
    /** How far the picture must be turned clockwise to stand upright. */
    val rotationDegrees: Int,
)

/** How the flash button behaves for the camera in use. */
enum class FlashKind {
    /** A real flash LED. */
    LED,

    /** No LED (most front cameras): the screen itself lights up white for a moment. */
    SCREEN,
    NONE,
}

class CameraUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Everything CameraX in one place.
 *
 * The essential part is the shared [ViewPort]: preview and photo are bound together
 * in one [UseCaseGroup] with the project's aspect ratio, so CameraX works out a
 * matching cut for both. What the preview shows is exactly what ends up in the
 * photo, and that is what makes the overlay of the previous photo line up.
 */
class CameraSession(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var flashKind = FlashKind.NONE
    private val captureExecutor = Executors.newSingleThreadExecutor { Thread(it, "foto") }

    /**
     * Starts the camera.
     *
     * @param rotation the current rotation of the display (Surface.ROTATION_*); the
     *   aspect ratio is interpreted as seen on a display in that rotation
     */
    suspend fun bind(
        owner: LifecycleOwner,
        previewView: PreviewView,
        window: Window?,
        lens: Lens,
        aspectRatio: AspectRatio,
        rotation: Int,
    ): FlashKind {
        val cameraProvider = provider ?: awaitProvider().also { provider = it }
        val selector = when (lens) {
            Lens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            Lens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        if (!cameraProvider.hasCamera(selector)) {
            throw CameraUnavailableException("This phone has no camera on that side")
        }

        // Both streams use the sensor's own 4:3 shape, so they cover the same field of
        // view; the view port then cuts the project's ratio out of both in the same way.
        val preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build(),
            )
            .setTargetRotation(rotation)
            .build()

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build(),
            )
            .setTargetRotation(rotation)
            .setJpegQuality(100)
            // Always off to begin with; the screen switches it on for one session at most.
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()

        val viewPort = ViewPort.Builder(Rational(aspectRatio.width, aspectRatio.height), rotation)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()
        val useCases = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .setViewPort(viewPort)
            .build()

        try {
            cameraProvider.unbindAll()
            val bound = cameraProvider.bindToLifecycle(owner, selector, useCases)
            preview.surfaceProvider = previewView.surfaceProvider
            camera = bound
            imageCapture = capture

            flashKind = when {
                bound.cameraInfo.hasFlashUnit() -> FlashKind.LED
                lens == Lens.FRONT && window != null -> {
                    previewView.setScreenFlashWindow(window)
                    capture.screenFlash = previewView.screenFlash
                    if (capture.screenFlash != null) FlashKind.SCREEN else FlashKind.NONE
                }
                else -> FlashKind.NONE
            }
            return flashKind
        } catch (failure: Exception) {
            imageCapture = null
            camera = null
            throw CameraUnavailableException("The camera could not be started", failure)
        }
    }

    /** Switches the flash for the photos that follow. */
    fun setFlash(on: Boolean) {
        val capture = imageCapture ?: return
        capture.flashMode = when {
            !on -> ImageCapture.FLASH_MODE_OFF
            flashKind == FlashKind.LED -> ImageCapture.FLASH_MODE_ON
            flashKind == FlashKind.SCREEN -> ImageCapture.FLASH_MODE_SCREEN
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    /** Takes one picture and returns it in memory. */
    suspend fun capture(): RawCapture = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resumeWithException(CameraUnavailableException("The camera is not ready yet"))
            return@suspendCancellableCoroutine
        }
        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val result = runCatching {
                        image.use {
                            val buffer = it.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val crop = it.cropRect
                            RawCapture(
                                jpeg = bytes,
                                cropRect = PixelRect(crop.left, crop.top, crop.right, crop.bottom),
                                rotationDegrees = it.imageInfo.rotationDegrees,
                            )
                        }
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            },
        )
    }

    fun unbind() {
        runCatching { provider?.unbindAll() }
        imageCapture = null
        camera = null
    }

    /** Call when the camera screen is left for good. */
    fun release() {
        unbind()
        captureExecutor.shutdown()
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (failure: Exception) {
                    continuation.resumeWithException(CameraUnavailableException("No camera available", failure))
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }
}
