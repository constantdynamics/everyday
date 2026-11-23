package nl.constantdynamics.dagelijks.framework

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.Shadows.shadowOf
import java.io.File

/** A phone held upright, and the same phone on its side. */
const val PHONE_UPRIGHT = "w411dp-h891dp-420dpi"
const val PHONE_SIDEWAYS = "w891dp-h411dp-land-420dpi"

/**
 * Makes the plain ComponentActivity that Compose tests run in known to the simulated
 * package manager, so no test-only activity has to be added to the app itself.
 */
class RegisterComposeActivityRule : TestWatcher() {
    override fun starting(description: Description?) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application.packageManager).addActivityIfNotPresent(
            ComponentName(application.packageName, ComponentActivity::class.java.name),
        )
    }
}

/** Renders a node to a PNG under app/build/schermafbeeldingen, for a human (or a model) to look at. */
fun SemanticsNodeInteraction.saveScreenshot(name: String): Bitmap {
    val bitmap = captureToImage().asAndroidBitmap()
    val directory = File(System.getProperty("dagelijks.screenshots") ?: "build/schermafbeeldingen")
    directory.mkdirs()
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return bitmap
}
