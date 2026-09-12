package nl.constantdynamics.everyday

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import nl.constantdynamics.everyday.data.opslag.Themakeuze
import nl.constantdynamics.everyday.ui.EverydayApp
import nl.constantdynamics.everyday.ui.LocalFotoLader
import nl.constantdynamics.everyday.ui.thema.EverydayThema

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EverydayApplication).container

        setContent {
            val themakeuze by container.instellingen.thema.collectAsState(initial = Themakeuze.SYSTEEM)

            // De backupmap kan op een SD-kaart staan. Zodra die terugkomt, of zodra de
            // app weer op de voorgrond staat, wordt de achterstand stil ingelopen.
            BackupInhalen(container)

            CompositionLocalProvider(LocalFotoLader provides container.fotoLader) {
                EverydayThema(themakeuze = themakeuze) {
                    EverydayApp(container = container)
                }
            }
        }
    }
}

@Composable
private fun BackupInhalen(container: AppContainer) {
    val context = LocalContext.current
    val levensloopEigenaar = LocalLifecycleOwner.current

    DisposableEffect(levensloopEigenaar) {
        val waarnemer = LifecycleEventObserver { _, gebeurtenis ->
            if (gebeurtenis == Lifecycle.Event.ON_RESUME) {
                container.backupBeheer.verwerkWachtrij()
            }
        }
        levensloopEigenaar.lifecycle.addObserver(waarnemer)

        val ontvanger = object : BroadcastReceiver() {
            override fun onReceive(ontvangenIn: Context?, bericht: Intent?) {
                container.backupBeheer.verwerkWachtrij()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addDataScheme("file")
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                ontvanger,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        onDispose {
            levensloopEigenaar.lifecycle.removeObserver(waarnemer)
            runCatching { context.unregisterReceiver(ontvanger) }
        }
    }
}
