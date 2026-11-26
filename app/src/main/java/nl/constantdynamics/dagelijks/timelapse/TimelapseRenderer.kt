package nl.constantdynamics.dagelijks.timelapse

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.constantdynamics.dagelijks.WorkFiles
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.PhotoNames
import nl.constantdynamics.dagelijks.core.PixelSize
import nl.constantdynamics.dagelijks.core.TimelapseMath
import nl.constantdynamics.dagelijks.storage.Photo
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.storage.Timelapse
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/** What to render. */
data class TimelapseRequest(
    val projectFolderName: String,
    /** The photos to use, oldest first. */
    val photos: List<Photo>,
    val aspectRatio: AspectRatio,
    val photosPerSecond: Int,
    val showDate: Boolean,
)

sealed interface RenderState {
    data object Idle : RenderState

    data class Running(
        val projectFolderName: String,
        val photosDone: Int,
        val photosTotal: Int,
        /** True during the last step: copying the finished video into the project folder. */
        val saving: Boolean = false,
    ) : RenderState {
        val progress: Float get() = if (photosTotal == 0) 0f else photosDone.toFloat() / photosTotal
    }

    data class Finished(
        val projectFolderName: String,
        val timelapse: Timelapse,
        val photosUsed: Int,
        val photosSkipped: Int,
        val size: PixelSize,
    ) : RenderState

    /** [reason] is a Dutch sentence for the user, or null when there is nothing more to say. */
    data class Failed(val projectFolderName: String, val reason: String?) : RenderState
}

/**
 * Renders a timelapse completely on the phone.
 *
 * The work runs on a thread of its own and belongs to the app rather than to a
 * screen, so the interface stays responsive and walking around in the app does not
 * interrupt it. The video is first written to the app's private cache and only
 * copied into the project folder when it is complete: an interrupted render leaves
 * nothing behind there. (What stays in the cache when the app is stopped altogether
 * is removed the next time the app starts, see [WorkFiles].)
 */
class TimelapseRenderer(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val mutableState = MutableStateFlow<RenderState>(RenderState.Idle)
    val state: StateFlow<RenderState> = mutableState.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start(request: TimelapseRequest, repository: ProjectRepository) {
        if (isRunning) return
        mutableState.value = RenderState.Running(request.projectFolderName, 0, request.photos.size)
        job = scope.launch {
            try {
                mutableState.value = render(request, repository)
            } catch (cancelled: CancellationException) {
                mutableState.value = RenderState.Idle
                throw cancelled
            } catch (failure: Exception) {
                // Only a reason written for the user is passed on; the message of any other
                // failure is English and meant for a programmer.
                mutableState.value = RenderState.Failed(request.projectFolderName, (failure as? RenderProblem)?.message)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Back to rest, after the result or the error has been shown. */
    fun acknowledge() {
        if (!isRunning) mutableState.value = RenderState.Idle
    }

    private suspend fun render(request: TimelapseRequest, repository: ProjectRepository): RenderState {
        if (request.photos.isEmpty()) throw RenderProblem("Er zijn geen foto's in deze periode")
        val workFile = File(WorkFiles.timelapseFolder(context.cacheDir), "bezig.mp4")
        val thread = Executors.newSingleThreadExecutor { Thread(it, "timelapse") }.asCoroutineDispatcher()
        try {
            // 1080 pixels on the short side; a phone whose encoder cannot do that gets 720.
            var outcome: Outcome? = null
            var lastFailure: Exception? = null
            for (size in candidateSizes(request.aspectRatio)) {
                try {
                    outcome = withContext(thread) { encode(request, repository, workFile, size) }
                    break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: EncoderUnavailableException) {
                    lastFailure = failure
                }
            }
            val result = outcome ?: throw RenderProblem("Je telefoon kon de video niet coderen", lastFailure)

            mutableState.value = RenderState.Running(request.projectFolderName, request.photos.size, request.photos.size, saving = true)
            val fileName = PhotoNames.timelapseFileName(
                request.projectFolderName,
                request.photos.first().date,
                request.photos.last().date,
            )
            val saved = repository.saveTimelapse(request.projectFolderName, fileName, workFile)
            return RenderState.Finished(request.projectFolderName, saved, result.used, result.skipped, result.size)
        } finally {
            workFile.delete()
            thread.close()
        }
    }

    private class Outcome(val used: Int, val skipped: Int, val size: PixelSize)

    /** A failure whose message is Dutch and written for the user, so it can be shown as it is. */
    private class RenderProblem(message: String, cause: Throwable? = null) : IOException(message, cause)

    /** The encoder refused this frame size before a single picture was written. */
    private class EncoderUnavailableException(cause: Exception) : Exception(cause)

    /** Runs entirely on the render thread: the OpenGL context lives on that one thread. */
    private suspend fun encode(
        request: TimelapseRequest,
        repository: ProjectRepository,
        target: File,
        size: PixelSize,
    ): Outcome {
        target.delete()
        val speed = TimelapseMath.clampSpeed(request.photosPerSecond)
        val framesPerPhoto = TimelapseMath.framesPerPhoto(speed)
        val encoder = try {
            VideoEncoder(
                output = target,
                width = size.width,
                height = size.height,
                frameRate = TimelapseMath.videoFps(speed),
                bitRate = TimelapseMath.bitRate(size.width, size.height),
            )
        } catch (failure: Exception) {
            throw EncoderUnavailableException(failure)
        }
        val composer = FrameComposer(context, size.width, size.height, request.showDate)
        val outcome = try {
            var frame = 0
            var used = 0
            var skipped = 0
            request.photos.forEachIndexed { index, photo ->
                kotlin.coroutines.coroutineContext.ensureActive()
                val picture = runCatching {
                    val bytes = repository.openPhoto(photo).use { it.readBytes() }
                    FrameComposer.decodeForFrame(bytes, size.width, size.height)
                }.getOrNull()
                if (picture == null) {
                    // A damaged file must not ruin the whole video.
                    skipped++
                } else {
                    encoder.setPicture(composer.compose(picture, photo.date))
                    picture.recycle()
                    repeat(framesPerPhoto) {
                        encoder.writeFrame(TimelapseMath.presentationTimeMicros(frame, speed) * 1000L)
                        frame++
                    }
                    used++
                }
                mutableState.value = RenderState.Running(request.projectFolderName, index + 1, request.photos.size)
            }
            if (used == 0) throw RenderProblem("Geen enkele foto kon worden gelezen")
            encoder.finish()
            Outcome(used, skipped, size)
        } finally {
            encoder.close()
            composer.release()
        }
        // Android's video writer notes the Android version, and on some phones the make
        // and model, in the file. A photo carries nothing of the kind; neither does this.
        Mp4Metadata.strip(target)
        return outcome
    }

    private fun candidateSizes(aspectRatio: AspectRatio): List<PixelSize> {
        val full = aspectRatio.videoSize
        val reduced = PixelSize(full.width * 2 / 3, full.height * 2 / 3) // 1080 -> 720
        return listOf(full, reduced)
    }
}
