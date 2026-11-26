package nl.constantdynamics.dagelijks

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import nl.constantdynamics.dagelijks.ui.DagelijksRoot
import nl.constantdynamics.dagelijks.ui.LocalServices
import nl.constantdynamics.dagelijks.ui.LocalShutterKeys
import nl.constantdynamics.dagelijks.ui.ShutterKeys
import nl.constantdynamics.dagelijks.ui.theme.DagelijksTheme

class MainActivity : ComponentActivity() {

    private val shutterKeys = ShutterKeys()

    /** The whole interface is Dutch, whatever language the phone is set to. */
    @SuppressLint("AppBundleLocaleChanges") // installed as a plain APK, never split by language
    override fun attachBaseContext(newBase: Context) {
        // Only the language is laid down here. Everything else (how the phone is held,
        // the font size, the screen size) keeps following the phone; a full copy of the
        // configuration would freeze all of that at the moment the app was opened.
        val dutchOnly = Configuration().apply { setLocale(DagelijksApp.DUTCH) }
        super.attachBaseContext(newBase.createConfigurationContext(dutchOnly))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            // Every screen is dark, so the clock and icons in the bars are always light.
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as DagelijksApp).container
        setContent {
            CompositionLocalProvider(
                LocalServices provides container.services,
                LocalShutterKeys provides shutterKeys,
            ) {
                DagelijksTheme {
                    DagelijksRoot(container.folder)
                }
            }
        }
    }

    /**
     * While the camera is open, the volume buttons take the photo instead of changing
     * the volume. A bluetooth selfie remote works the same way: it poses as a volume
     * button (or as an Enter key).
     */
    @SuppressLint("RestrictedApi") // overriding Activity.dispatchKeyEvent is ordinary, public Android
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (shutterKeys.handle(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
