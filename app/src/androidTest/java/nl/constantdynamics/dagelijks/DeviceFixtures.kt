package nl.constantdynamics.dagelijks

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.sqrt

/** What the tests on a real (emulated) phone share. */
object DeviceFixtures {

    private const val SCREENSHOT_FOLDER = "/sdcard/Download/dagelijks-test"

    val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * A slow emulator now and then puts a system message over the screen ("Pixel
     * Launcher isn't responding"). It takes the keyboard focus away from the app, so it
     * is waved away before anything that depends on focus.
     */
    fun dismissSystemDialogs() {
        repeat(3) {
            val wait = device.findObject(By.text(Pattern.compile("(?i)wait"))) ?: return
            wait.click()
            device.waitForIdle()
            Thread.sleep(500)
        }
    }

    /**
     * Takes a screenshot named after a test that failed, to see what was on screen, and
     * writes down why it failed: the reason, the texts on the screen at that moment and
     * what the app went through just before. All of that is logged here, at once, because
     * closing the app after a failure can go wrong as well and then hides the first cause.
     */
    class ScreenshotOnFailure : TestWatcher() {
        override fun starting(description: Description) {
            Trail.install()
            Trail.note("TEST ${description.methodName}")
        }

        override fun failed(e: Throwable?, description: Description) {
            Log.e(TAG, "MISLUKT ${description.methodName}: $e", e)
            logInPieces("OP HET SCHERM", textsOnScreen())
            logInPieces("VLAK ERVOOR", Trail.recent().joinToString(" ; "))
            screenshot("mislukt-" + description.methodName.take(40))
        }

        private fun logInPieces(label: String, text: String) {
            text.chunked(800).forEachIndexed { index, piece -> Log.e(TAG, "$label ${index + 1}: $piece") }
        }

        /** Every text and description that the phone's screen shows, whichever window it is in. */
        private fun textsOnScreen(): String = runCatching {
            val dump = ByteArrayOutputStream()
            device.dumpWindowHierarchy(dump)
            Regex("(?:text|content-desc)=\"([^\"]+)\"").findAll(dump.toString("UTF-8"))
                .map { it.groupValues[1] }.distinct().take(70).joinToString(" | ")
        }.getOrElse { "het scherm kon niet worden uitgelezen: $it" }
    }

    private const val TAG = "DagelijksTest"

    /**
     * The last things that happened to the app's screens and its configuration, kept in
     * memory and only written out when a test fails.
     */
    private object Trail {
        private val events = ArrayDeque<String>()
        private var installed = false
        private val clock = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT)

        @Synchronized
        fun note(what: String) {
            events.addLast("${clock.format(java.util.Date())} $what")
            while (events.size > 45) events.removeFirst()
        }

        @Synchronized
        fun recent(): List<String> = events.toList()

        @Synchronized
        fun install() {
            if (installed) return
            installed = true
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                private fun name(activity: Activity) =
                    activity.javaClass.simpleName + "@" + Integer.toHexString(System.identityHashCode(activity))

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                    note("${name(activity)} ${if (savedInstanceState == null) "created" else "CREATED AGAIN from saved state"} ${describe(activity.resources.configuration)}")

                override fun onActivityStarted(activity: Activity) = note("${name(activity)} started")
                override fun onActivityResumed(activity: Activity) = note("${name(activity)} resumed")
                override fun onActivityPaused(activity: Activity) = note("${name(activity)} paused")
                override fun onActivityStopped(activity: Activity) = note("${name(activity)} stopped")
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = note("${name(activity)} saved its state")
                override fun onActivityDestroyed(activity: Activity) = note("${name(activity)} destroyed")
            })
            app.registerComponentCallbacks(object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) = note("CONFIGURATION ${describe(newConfig)}")

                @Deprecated("Deprecated in the platform")
                override fun onLowMemory() = note("LOW MEMORY")
            })
        }

        private fun describe(c: Configuration): String =
            "[${c.screenWidthDp}x${c.screenHeightDp}dp orientation=${c.orientation} density=${c.densityDpi} font=${c.fontScale} " +
                "ui=${c.uiMode} keyboard=${c.keyboard}/${c.keyboardHidden}/${c.hardKeyboardHidden} nav=${c.navigation} locales=${c.locales.toLanguageTags()}]"
    }

    /** Saves what is on the phone's screen right now; the workflow collects these pictures afterwards. */
    fun screenshot(name: String) {
        device.executeShellCommand("mkdir -p $SCREENSHOT_FOLDER")
        device.executeShellCommand("screencap -p $SCREENSHOT_FOLDER/$name.png")
    }

    fun solidJpeg(width: Int, height: Int, colour: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
    }

    /** True when the two colours are the same apart from what video compression does to them. */
    fun roughlySame(a: Int, b: Int, tolerance: Int = 48): Boolean =
        abs(Color.red(a) - Color.red(b)) < tolerance &&
            abs(Color.green(a) - Color.green(b)) < tolerance &&
            abs(Color.blue(a) - Color.blue(b)) < tolerance

    // --- Looking inside a video file ---------------------------------------------------------

    class VideoFacts(
        val tracks: Int,
        val mime: String,
        val width: Int,
        val height: Int,
        val frames: Int,
        val durationMicros: Long,
    )

    fun inspectVideo(path: String): VideoFacts {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        try {
            val format = extractor.getTrackFormat(0)
            extractor.selectTrack(0)
            var frames = 0
            while (extractor.sampleTime >= 0) {
                frames++
                extractor.advance()
            }
            return VideoFacts(
                tracks = extractor.trackCount,
                mime = format.getString(MediaFormat.KEY_MIME).orEmpty(),
                width = format.getInteger(MediaFormat.KEY_WIDTH),
                height = format.getInteger(MediaFormat.KEY_HEIGHT),
                frames = frames,
                durationMicros = format.getLong(MediaFormat.KEY_DURATION),
            )
        } finally {
            extractor.release()
        }
    }

    /** The colour in the middle of the video picture at the given moment. */
    fun colourInVideo(path: String, timeMicros: Long): Int {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        try {
            val frame = retriever.getFrameAtTime(timeMicros, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: error("No video frame at $timeMicros")
            return frame.getPixel(frame.width / 2, frame.height / 2)
        } finally {
            retriever.release()
        }
    }

    // --- Comparing two pictures of the viewfinder --------------------------------------------

    /** A small grey version of (part of) a picture. */
    class Grey(val width: Int, val height: Int, val values: FloatArray)

    /**
     * Reduces the part of [bitmap] between the given fractions of its width and height
     * to a small grey picture of [width] pixels wide.
     */
    fun grey(
        bitmap: Bitmap,
        fromX: Float = 0.06f,
        toX: Float = 0.94f,
        fromY: Float = 0.06f,
        toY: Float = 0.94f,
        width: Int = 120,
    ): Grey {
        val left = (bitmap.width * fromX).toInt()
        val top = (bitmap.height * fromY).toInt()
        val partWidth = (bitmap.width * (toX - fromX)).toInt()
        val partHeight = (bitmap.height * (toY - fromY)).toInt()
        val part = Bitmap.createBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false), left, top, partWidth, partHeight)
        val height = (width.toFloat() * part.height / part.width).toInt()
        val small = Bitmap.createScaledBitmap(part, width, height, true)
        val values = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = small.getPixel(x, y)
                values[y * width + x] = 0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)
            }
        }
        return Grey(width, height, values)
    }

    /**
     * How alike two pictures are when the second is shifted by ([dx], [dy]): 1 is
     * identical apart from brightness and contrast, 0 is unrelated.
     */
    fun likeness(a: Grey, b: Grey, dx: Int = 0, dy: Int = 0): Double {
        var count = 0
        var sumA = 0.0
        var sumB = 0.0
        var sumAA = 0.0
        var sumBB = 0.0
        var sumAB = 0.0
        for (y in 0 until a.height) {
            val yb = y + dy
            if (yb < 0 || yb >= b.height) continue
            for (x in 0 until a.width) {
                val xb = x + dx
                if (xb < 0 || xb >= b.width) continue
                val va = a.values[y * a.width + x].toDouble()
                val vb = b.values[yb * b.width + xb].toDouble()
                count++
                sumA += va
                sumB += vb
                sumAA += va * va
                sumBB += vb * vb
                sumAB += va * vb
            }
        }
        if (count == 0) return 0.0
        val covariance = sumAB / count - (sumA / count) * (sumB / count)
        val varianceA = sumAA / count - (sumA / count) * (sumA / count)
        val varianceB = sumBB / count - (sumB / count) * (sumB / count)
        if (varianceA < 1e-6 || varianceB < 1e-6) return 0.0
        return covariance / sqrt(varianceA * varianceB)
    }

    class Alignment(val dx: Int, val dy: Int, val likeness: Double)

    /** The shift (within [reach] small pixels) at which the two pictures match best. */
    fun bestAlignment(a: Grey, b: Grey, reach: Int = 5): Alignment {
        var best = Alignment(0, 0, likeness(a, b))
        for (dy in -reach..reach) {
            for (dx in -reach..reach) {
                val score = likeness(a, b, dx, dy)
                if (score > best.likeness + 1e-9) best = Alignment(dx, dy, score)
            }
        }
        return best
    }
}
