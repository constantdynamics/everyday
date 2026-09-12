package nl.constantdynamics.everyday.ui.onderdelen

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import nl.constantdynamics.everyday.data.media.FotoLader
import nl.constantdynamics.everyday.ui.LocalFotoLader

/**
 * Toont een lokale foto. Laadt asynchroon en verkleind, zodat een raster met
 * honderden foto's soepel blijft scrollen.
 */
@Composable
fun FotoBeeld(
    uri: Uri?,
    maxZijde: Int,
    beschrijving: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    achtergrond: Color = Color.Black,
) {
    val lader: FotoLader = LocalFotoLader.current
    var bitmap by remember(uri, maxZijde) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri, maxZijde) {
        bitmap = uri?.let { lader.laad(it, maxZijde) }
    }

    Box(modifier = modifier.background(achtergrond)) {
        val huidige = bitmap
        if (huidige != null) {
            Image(
                bitmap = huidige.asImageBitmap(),
                contentDescription = beschrijving,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
