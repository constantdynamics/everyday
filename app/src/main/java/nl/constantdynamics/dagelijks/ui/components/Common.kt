package nl.constantdynamics.dagelijks.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.constantdynamics.dagelijks.storage.Photo
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.LocalServices
import nl.constantdynamics.dagelijks.ui.theme.RetroColors

/** Loads a photo at a suitable size; null while it is still loading. */
@Composable
fun rememberPhotoBitmap(repository: ProjectRepository, photo: Photo?, maxSide: Int): Bitmap? {
    val loader = LocalServices.current.imageLoader
    val bitmap by produceState(
        initialValue = photo?.let { loader.cached(it.cacheKey, maxSide) },
        photo?.cacheKey,
        maxSide,
    ) {
        value = photo?.let { loader.load(it.cacheKey, maxSide) { repository.openPhoto(it) } }
    }
    return bitmap
}

/** A photo from the user's folder, with a calm placeholder until it has loaded. */
@Composable
fun PhotoImage(
    repository: ProjectRepository,
    photo: Photo?,
    maxSide: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    placeholder: Color = RetroColors.CreamDark,
) {
    val bitmap = rememberPhotoBitmap(repository, photo, maxSide)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(placeholder))
    }
}

/** Top of a screen: back button, a title in display letters, and optional actions. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 34.sp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RetroIconButton(AppIcons.Back, description = "Terug", onClick = onBack)
        Spacer(Modifier.width(12.dp))
        RetroTitle(text = title, fontSize = fontSize, modifier = Modifier.weight(1f))
        actions()
    }
}
