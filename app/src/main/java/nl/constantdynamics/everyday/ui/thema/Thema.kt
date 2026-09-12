package nl.constantdynamics.everyday.ui.thema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import nl.constantdynamics.everyday.data.opslag.Themakeuze

@Composable
fun EverydayThema(
    themakeuze: Themakeuze = Themakeuze.SYSTEEM,
    content: @Composable () -> Unit,
) {
    val donker = when (themakeuze) {
        Themakeuze.LICHT -> false
        Themakeuze.DONKER -> true
        Themakeuze.SYSTEEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val kleuren = if (donker) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    MaterialTheme(colorScheme = kleuren, content = content)
}
