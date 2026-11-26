package nl.constantdynamics.dagelijks.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.launch
import nl.constantdynamics.dagelijks.storage.Photo
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.Format
import nl.constantdynamics.dagelijks.ui.components.AppBackground
import nl.constantdynamics.dagelijks.ui.components.AppButton
import nl.constantdynamics.dagelijks.ui.components.AppDialog
import nl.constantdynamics.dagelijks.ui.components.AppIconButton
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.ButtonKind
import nl.constantdynamics.dagelijks.ui.components.PhotoImage
import nl.constantdynamics.dagelijks.ui.components.rememberScreenScope
import nl.constantdynamics.dagelijks.ui.theme.AppColors

/**
 * One photo, large. Swipe sideways to walk through the days; pinch to zoom in.
 * From here a photo can be deleted or replaced by a new one, always after a
 * confirmation.
 */
@Composable
fun ViewerScreen(
    repository: ProjectRepository,
    project: Project,
    initialDate: LocalDate,
    onBack: () -> Unit,
    onReplace: (LocalDate) -> Unit,
) {
    val scope = rememberScreenScope()
    val photos = project.photos
    if (photos.isEmpty()) {
        // The last photo was deleted.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val startIndex = remember { photos.indexOfFirst { it.date == initialDate }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startIndex) { photos.size }
    val current: Photo = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]
    var askDelete by rememberSaveable { mutableStateOf(false) }
    var askReplace by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }

    AppBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(AppIcons.Back, "Terug", onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(Format.dateWithWeekday(current.date), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Foto ${pager.currentPage + 1} van ${photos.size} · ${project.settings.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextMuted,
                        maxLines = 1,
                    )
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp),
                pageSpacing = 16.dp,
                key = { photos[it].date.toString() },
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ZoomablePhoto(repository, photos[page], project.settings.aspectRatio.value)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(
                    text = "Verwijder",
                    onClick = { askDelete = true },
                    icon = AppIcons.Delete,
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "Vervang",
                    onClick = { askReplace = true },
                    icon = AppIcons.Retake,
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (askDelete) {
        AppDialog(
            title = "Foto verwijderen?",
            onDismiss = { askDelete = false },
            confirmText = "Ja, verwijder",
            onConfirm = {
                askDelete = false
                scope.launch {
                    failed = runCatching { repository.deletePhoto(project.folderName, current.date) }.isFailure
                }
            },
            dismissText = "Nee, bewaar de foto",
        ) {
            Text(
                "De foto van ${Format.date(current.date)} wordt van je telefoon verwijderd. Dit kan niet ongedaan worden gemaakt.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    if (askReplace) {
        AppDialog(
            title = "Foto vervangen?",
            onDismiss = { askReplace = false },
            confirmText = "Maak een nieuwe foto",
            onConfirm = {
                askReplace = false
                onReplace(current.date)
            },
            dismissText = "Annuleren",
        ) {
            Text(
                "Je maakt nu een nieuwe foto voor ${Format.date(current.date)}. De oude foto verdwijnt pas als je de nieuwe hebt goedgekeurd.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    if (failed) {
        AppDialog(
            title = "Dat ging niet goed",
            onDismiss = { failed = false },
            confirmText = "Oké",
            onConfirm = { failed = false },
            dismissText = null,
            confirmKind = ButtonKind.Secondary,
        ) {
            Text("De foto kon niet worden verwijderd.", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * A photo with softly rounded corners. Pinch to zoom in, drag to look around, double tap
 * to zoom in or back out. While it is not zoomed, a sideways swipe is left alone so
 * it turns the page to the next day.
 */
@Composable
private fun ZoomablePhoto(repository: ProjectRepository, photo: Photo, ratio: Float) {
    var scale by remember(photo.date) { mutableFloatStateOf(1f) }
    var offset by remember(photo.date) { mutableStateOf(Offset.Zero) }

    val shape = RoundedCornerShape(12.dp)
    Box(modifier = Modifier.border(1.dp, AppColors.Hairline, shape)) {
        Box(
            modifier = Modifier
                .aspectRatio(ratio)
                .clip(shape)
                .pointerInput(photo.date) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        },
                    )
                }
                .pointerInput(photo.date) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers > 1 || scale > 1f) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                                // Never further than the picture's own edge.
                                val limitX = size.width * (scale - 1f) / 2f
                                val limitY = size.height * (scale - 1f) / 2f
                                val pan = event.calculatePan()
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-limitX, limitX),
                                    (offset.y + pan.y).coerceIn(-limitY, limitY),
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            PhotoImage(
                repository = repository,
                photo = photo,
                maxSide = 2048,
                contentScale = ContentScale.Crop,
                contentDescription = "Foto van ${Format.date(photo.date)}",
                placeholder = AppColors.Surface,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
        }
    }
}
