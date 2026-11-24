package nl.constantdynamics.dagelijks.ui.components

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalDate
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.constantdynamics.dagelijks.storage.Photo
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.LocalServices
import nl.constantdynamics.dagelijks.ui.theme.AppColors

/**
 * Today's date, refreshed whenever the app comes back to the foreground. Without that
 * an app left open overnight would keep saying that today's photo has been taken.
 */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose {}
    }
    return today
}

/**
 * Keeps the display on while [enabled], for as long as the caller is on screen.
 *
 * During the cross-fade from one screen to the next both are on screen for a moment.
 * So the requests are counted: the display may only dim again when the last one let go.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        if (enabled) ScreenOnRequests.add(view)
        onDispose { if (enabled) ScreenOnRequests.remove(view) }
    }
}

/** Only touched from the main thread, like everything else about a view. */
private object ScreenOnRequests {
    private val counts = WeakHashMap<View, Int>()

    fun add(view: View) {
        counts[view] = (counts[view] ?: 0) + 1
        view.keepScreenOn = true
    }

    fun remove(view: View) {
        val left = (counts[view] ?: 1) - 1
        if (left > 0) {
            counts[view] = left
        } else {
            counts.remove(view)
            view.keepScreenOn = false
        }
    }
}

/** Loads a photo at a suitable size; null while it is still loading. */
@Composable
fun rememberPhotoBitmap(repository: ProjectRepository, photo: Photo?, maxSide: Int): Bitmap? {
    val loader = LocalServices.current.imageLoader
    val bitmap by produceState(
        initialValue = photo?.let { loader.cached(it.cacheKey, maxSide) },
        photo?.cacheKey,
        maxSide,
    ) {
        val loaded = photo?.let { loader.load(it.cacheKey, maxSide) { repository.openPhoto(it) } }
        // The loading happened elsewhere; the screen hears about it at the main thread.
        withContext(Dispatchers.Main.immediate) { value = loaded }
    }
    return bitmap
}

/**
 * A scope for work that a screen starts. Whatever that work waits for (the storage, the
 * camera), it carries on at the main thread afterwards, which is where everything that
 * touches the screen belongs. On a phone that is what happens anyway; saying so here
 * makes it hold wherever the app runs, the automated tests included.
 */
@Composable
fun rememberScreenScope(): CoroutineScope = rememberCoroutineScope { Dispatchers.Main.immediate }

/** A photo from the user's folder, with a calm placeholder until it has loaded. */
@Composable
fun PhotoImage(
    repository: ProjectRepository,
    photo: Photo?,
    maxSide: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    placeholder: Color = AppColors.Raised,
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

/** Top of a screen: back button, the title in display letters, and optional actions. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(AppIcons.Back, description = "Terug", onClick = onBack)
        Spacer(Modifier.width(14.dp))
        DisplayTitle(text = title, maxLines = 2, modifier = Modifier.weight(1f))
        actions()
    }
}
