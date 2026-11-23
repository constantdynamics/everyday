package nl.constantdynamics.dagelijks.core

import kotlin.math.roundToLong

/**
 * Timing of the timelapse video.
 *
 * The user picks a speed in photos per second. A video with very few frames per
 * second upsets some encoders and players, so every photo is simply repeated for a
 * whole number of video frames: each photo stays on screen exactly equally long,
 * and the video itself always runs at 16 to 30 frames per second.
 */
object TimelapseMath {

    const val MAX_VIDEO_FPS = 30

    fun clampSpeed(photosPerSecond: Int): Int =
        photosPerSecond.coerceIn(ProjectSettings.MIN_TIMELAPSE_FPS, ProjectSettings.MAX_TIMELAPSE_FPS)

    /** How many video frames each photo is shown for. */
    fun framesPerPhoto(photosPerSecond: Int): Int = MAX_VIDEO_FPS / clampSpeed(photosPerSecond)

    /** Frame rate of the video file. */
    fun videoFps(photosPerSecond: Int): Int = clampSpeed(photosPerSecond) * framesPerPhoto(photosPerSecond)

    fun totalFrames(photoCount: Int, photosPerSecond: Int): Int =
        photoCount.coerceAtLeast(0) * framesPerPhoto(photosPerSecond)

    /** Which photo a given video frame shows. */
    fun photoIndexForFrame(frame: Int, photosPerSecond: Int): Int = frame / framesPerPhoto(photosPerSecond)

    /** Moment a video frame appears, in microseconds from the start. */
    fun presentationTimeMicros(frame: Int, photosPerSecond: Int): Long =
        (frame * 1_000_000.0 / videoFps(photosPerSecond)).roundToLong()

    fun durationSeconds(photoCount: Int, photosPerSecond: Int): Double =
        photoCount.coerceAtLeast(0).toDouble() / clampSpeed(photosPerSecond)

    /** Bits per second for good quality at the given frame size. */
    fun bitRate(width: Int, height: Int): Int =
        (width.toLong() * height * 8).coerceIn(4_000_000L, 24_000_000L).toInt()
}
