package nl.constantdynamics.dagelijks.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import nl.constantdynamics.dagelijks.image.ImageLoader
import nl.constantdynamics.dagelijks.storage.StorageAccess
import nl.constantdynamics.dagelijks.timelapse.TimelapseRenderer

/**
 * What the screens need from the app as a whole. Handing it to them as one small
 * object (instead of the Application) keeps every screen easy to render in a test.
 */
class AppServices(
    /** For work that must finish even when the user leaves the screen that started it. */
    val scope: CoroutineScope,
    val imageLoader: ImageLoader,
    val timelapseRenderer: TimelapseRenderer,
    /** Takes the folder the user picked in the system folder picker into use. */
    val chooseFolder: suspend (Uri) -> StorageAccess.PickResult,
)

val LocalServices = staticCompositionLocalOf<AppServices> { error("No AppServices provided") }

val LocalShutterKeys = staticCompositionLocalOf { ShutterKeys() }

/**
 * Routes hardware keys to the camera screen. Only while a screen has registered a
 * listener are the keys taken over; everywhere else the volume buttons simply
 * change the volume.
 */
class ShutterKeys {

    private var listener: (() -> Unit)? = null

    fun register(onShutter: () -> Unit) {
        listener = onShutter
    }

    fun unregister(onShutter: () -> Unit) {
        if (listener === onShutter) listener = null
    }

    /** True when the key was used as a shutter and must not reach the system. */
    fun handle(event: KeyEvent): Boolean {
        val current = listener ?: return false
        if (event.keyCode !in KEY_CODES) return false
        // Fire once per press; swallow the repeats and the release as well.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) current()
        return true
    }

    companion object {
        val KEY_CODES = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_HEADSETHOOK,
        )
    }
}

/** Makes the volume buttons (and a selfie remote) call [onShutter] while this is on screen. */
@Composable
fun ShutterKeyHandler(enabled: Boolean, onShutter: () -> Unit) {
    val keys = LocalShutterKeys.current
    val latest by rememberUpdatedState(onShutter)
    DisposableEffect(keys, enabled) {
        val callback = { latest() }
        if (enabled) keys.register(callback)
        onDispose { keys.unregister(callback) }
    }
}
