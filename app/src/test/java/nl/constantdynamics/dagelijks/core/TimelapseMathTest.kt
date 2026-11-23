package nl.constantdynamics.dagelijks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelapseMathTest {

    @Test
    fun `speed stays between 1 and 30 photos per second`() {
        assertEquals(1, TimelapseMath.clampSpeed(0))
        assertEquals(1, TimelapseMath.clampSpeed(-5))
        assertEquals(10, TimelapseMath.clampSpeed(10))
        assertEquals(30, TimelapseMath.clampSpeed(99))
    }

    @Test
    fun `the video always runs at 16 to 30 frames per second`() {
        for (speed in 1..30) {
            val fps = TimelapseMath.videoFps(speed)
            assertTrue("speed $speed gives $fps fps", fps in 16..30)
            assertEquals(0, fps % speed)
        }
    }

    @Test
    fun `every photo is on screen for the same whole number of frames`() {
        assertEquals(30, TimelapseMath.framesPerPhoto(1))
        assertEquals(3, TimelapseMath.framesPerPhoto(10))
        assertEquals(2, TimelapseMath.framesPerPhoto(15))
        assertEquals(1, TimelapseMath.framesPerPhoto(16))
        assertEquals(1, TimelapseMath.framesPerPhoto(30))
    }

    @Test
    fun `frames map back to photos in order and each photo appears`() {
        for (speed in listOf(1, 7, 10, 24, 30)) {
            val photos = 37
            val frames = TimelapseMath.totalFrames(photos, speed)
            val shown = (0 until frames).map { TimelapseMath.photoIndexForFrame(it, speed) }
            assertEquals((0 until photos).toList(), shown.distinct())
            assertEquals(shown.sorted(), shown)
            assertTrue(shown.groupingBy { it }.eachCount().values.all { it == TimelapseMath.framesPerPhoto(speed) })
        }
    }

    @Test
    fun `the length of the video is the number of photos divided by the speed`() {
        assertEquals(36.5, TimelapseMath.durationSeconds(365, 10), 1e-9)
        assertEquals(365.0, TimelapseMath.durationSeconds(365, 1), 1e-9)
        assertEquals(0.0, TimelapseMath.durationSeconds(0, 10), 1e-9)
    }

    @Test
    fun `frame times add up to the length of the video`() {
        for (speed in listOf(1, 7, 10, 30)) {
            val photos = 365
            val frames = TimelapseMath.totalFrames(photos, speed)
            val endMicros = TimelapseMath.presentationTimeMicros(frames, speed)
            assertEquals(TimelapseMath.durationSeconds(photos, speed), endMicros / 1_000_000.0, 0.001)
            assertEquals(0L, TimelapseMath.presentationTimeMicros(0, speed))
        }
    }

    @Test
    fun `frame times never go backwards`() {
        var previous = -1L
        for (frame in 0 until 500) {
            val time = TimelapseMath.presentationTimeMicros(frame, 7)
            assertTrue(time > previous)
            previous = time
        }
    }

    @Test
    fun `the bit rate grows with the frame size within sensible limits`() {
        assertTrue(TimelapseMath.bitRate(1080, 1920) > TimelapseMath.bitRate(1080, 1080))
        assertTrue(TimelapseMath.bitRate(64, 64) >= 4_000_000)
        assertTrue(TimelapseMath.bitRate(8000, 8000) <= 24_000_000)
    }
}
