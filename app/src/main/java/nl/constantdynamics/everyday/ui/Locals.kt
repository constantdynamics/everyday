package nl.constantdynamics.everyday.ui

import androidx.compose.runtime.staticCompositionLocalOf
import nl.constantdynamics.everyday.data.media.FotoLader

val LocalFotoLader = staticCompositionLocalOf<FotoLader> {
    error("Er is geen FotoLader beschikbaar")
}
