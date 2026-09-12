package nl.constantdynamics.everyday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            CompositionLocalProvider(LocalFotoLader provides container.fotoLader) {
                EverydayThema(themakeuze = themakeuze) {
                    EverydayApp(container = container)
                }
            }
        }
    }
}
