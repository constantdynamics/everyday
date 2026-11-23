package nl.constantdynamics.dagelijks.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import nl.constantdynamics.dagelijks.core.Days
import nl.constantdynamics.dagelijks.core.DutchText
import nl.constantdynamics.dagelijks.storage.Photo
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.Format
import nl.constantdynamics.dagelijks.ui.LocalServices
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.PhotoImage
import nl.constantdynamics.dagelijks.ui.components.Pill
import nl.constantdynamics.dagelijks.ui.components.RetroBackground
import nl.constantdynamics.dagelijks.ui.components.RetroButton
import nl.constantdynamics.dagelijks.ui.components.RetroCard
import nl.constantdynamics.dagelijks.ui.components.RetroDialog
import nl.constantdynamics.dagelijks.ui.components.RetroIconButton
import nl.constantdynamics.dagelijks.ui.components.RetroTextField
import nl.constantdynamics.dagelijks.ui.components.ScreenHeader
import nl.constantdynamics.dagelijks.ui.components.retroBox
import nl.constantdynamics.dagelijks.ui.theme.RetroColors
import nl.constantdynamics.dagelijks.ui.theme.RetroType
import java.time.LocalDate
import java.time.YearMonth

/** What the gallery grid shows: month headings with the photos of that month under them. */
private sealed interface GalleryItem {
    data class Month(val month: YearMonth) : GalleryItem
    data class Picture(val photo: Photo) : GalleryItem
}

/**
 * All photos of one project in a grid, newest first, grouped by month. From here the
 * user takes today's photo, opens a photo, makes a timelapse or changes the project.
 */
@Composable
fun GalleryScreen(
    repository: ProjectRepository,
    project: Project,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onOpenPhoto: (Photo) -> Unit,
    onTimelapse: () -> Unit,
    onDeleted: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val takenToday = Days.hasPhotoOn(project.dates, today)
    val ratio = project.settings.aspectRatio

    val items = remember(project.photos) {
        buildList {
            project.photos.asReversed()
                .groupBy { YearMonth.from(it.date) }
                .forEach { (month, photos) ->
                    add(GalleryItem.Month(month))
                    photos.forEach { add(GalleryItem.Picture(it)) }
                }
        }
    }

    RetroBackground(Modifier.fillMaxSize(), sunburst = false) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (ratio.isLandscape) 2 else 3),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "kop", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ScreenHeader(title = project.settings.name, onBack = onBack, fontSize = 32.sp) {
                        Spacer(Modifier.width(8.dp))
                        RetroIconButton(AppIcons.Edit, "Project aanpassen", onClick = { showSettings = true }, size = 44.dp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = when (val first = project.photos.firstOrNull()) {
                                null -> "Nog geen foto's"
                                else -> "${DutchText.photoCount(project.photos.size)} sinds ${Format.date(first.date)}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (takenToday) {
                            Pill("Vandaag gelukt", icon = AppIcons.Check, color = RetroColors.Cream)
                        }
                    }
                    RetroButton(
                        text = if (takenToday) "Foto van vandaag opnieuw" else "Maak de foto van vandaag",
                        onClick = onTakePhoto,
                        icon = AppIcons.Camera,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RetroButton(
                        text = "Maak timelapse",
                        onClick = onTimelapse,
                        icon = AppIcons.Film,
                        color = RetroColors.Mustard,
                        contentColor = RetroColors.Navy,
                        enabled = project.photos.isNotEmpty() || project.timelapses.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (project.photos.isEmpty()) {
                        RetroCard(Modifier.fillMaxWidth()) {
                            Text(
                                "Hier komen je foto's te staan, één per dag. Maak de eerste!",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            items(
                items = items,
                key = { item ->
                    when (item) {
                        is GalleryItem.Month -> "maand-${item.month}"
                        is GalleryItem.Picture -> item.photo.date.toString()
                    }
                },
                span = { item -> if (item is GalleryItem.Month) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            ) { item ->
                when (item) {
                    is GalleryItem.Month -> Text(
                        text = Format.month(item.month.atDay(1)),
                        style = RetroType.scriptLarge,
                        color = RetroColors.Navy,
                        modifier = Modifier.padding(top = 10.dp, start = 2.dp),
                    )

                    is GalleryItem.Picture -> PhotoTile(
                        repository = repository,
                        photo = item.photo,
                        ratio = ratio.value,
                        onClick = { onOpenPhoto(item.photo) },
                    )
                }
            }
        }
    }

    if (showSettings) {
        ProjectSettingsDialog(
            repository = repository,
            project = project,
            onClose = { showSettings = false },
            onDeleted = onDeleted,
        )
    }
}

/** One photo in the grid, framed like a small print with its date on the edge. */
@Composable
private fun PhotoTile(repository: ProjectRepository, photo: Photo, ratio: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .retroBox(fill = Color.White, corner = 6.dp, border = 2.dp, shadow = DpOffset(3.dp, 3.dp))
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhotoImage(
            repository = repository,
            photo = photo,
            maxSide = 360,
            contentDescription = "Foto van ${Format.date(photo.date)}",
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(2.dp)),
        )
        Text(
            text = Format.shortDate(photo.date),
            style = MaterialTheme.typography.labelMedium,
            color = RetroColors.Navy,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Rename the project, switch camera, change the shape (only while there are no
 * photos yet) or delete the whole project.
 */
@Composable
private fun ProjectSettingsDialog(
    repository: ProjectRepository,
    project: Project,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
) {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    val nameField = rememberTextFieldState(project.settings.name)
    var lens by rememberSaveable { mutableStateOf(project.settings.lens) }
    var aspectRatio by rememberSaveable { mutableStateOf(project.settings.aspectRatio) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleteFailed by rememberSaveable { mutableStateOf(false) }

    fun save() {
        val newName = nameField.text.toString().trim().ifEmpty { project.settings.name }
        val newLens = lens
        val newRatio = aspectRatio
        services.scope.launch {
            runCatching {
                repository.updateSettings(project.folderName) { it.copy(name = newName, lens = newLens, aspectRatio = newRatio) }
            }
        }
        onClose()
    }

    if (confirmDelete) {
        RetroDialog(
            title = "Project verwijderen?",
            onDismiss = { confirmDelete = false },
            confirmText = "Ja, verwijder alles",
            onConfirm = {
                scope.launch {
                    val deleted = runCatching { repository.deleteProject(project.folderName) }
                    if (deleted.isSuccess) onDeleted() else deleteFailed = true
                    confirmDelete = false
                }
            },
            dismissText = "Nee, bewaar het project",
        ) {
            Text(
                "“${project.settings.name}” wordt met alle ${DutchText.photoCount(project.photos.size)} en timelapses van je telefoon verwijderd. " +
                    "Dit kan niet ongedaan worden gemaakt.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .retroBox(fill = RetroColors.Cream, corner = 24.dp, shadow = DpOffset(6.dp, 7.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Project aanpassen", style = MaterialTheme.typography.headlineMedium)

            Text("Naam", style = MaterialTheme.typography.titleSmall)
            RetroTextField(state = nameField, modifier = Modifier.fillMaxWidth())

            Text("Camera", style = MaterialTheme.typography.titleSmall)
            LensChoice(lens, onChange = { lens = it })

            Text("Vorm van de foto", style = MaterialTheme.typography.titleSmall)
            AspectRatioChoice(aspectRatio, onChange = { aspectRatio = it }, enabled = project.canChangeAspectRatio)
            if (!project.canChangeAspectRatio) {
                Text(
                    "De vorm ligt vast omdat er al foto's zijn. Anders past de vorige foto niet meer over de nieuwe.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (deleteFailed) {
                Text("Het verwijderen is niet gelukt.", style = MaterialTheme.typography.bodyMedium, color = RetroColors.CoralDark)
            }

            Spacer(Modifier.height(2.dp))
            RetroButton("Opslaan", onClick = ::save, icon = AppIcons.Check, modifier = Modifier.fillMaxWidth())
            RetroButton(
                "Annuleren",
                onClick = onClose,
                color = RetroColors.Cream,
                contentColor = RetroColors.Navy,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Dit project verwijderen",
                    style = MaterialTheme.typography.labelLarge,
                    color = RetroColors.CoralDark,
                    modifier = Modifier.clickable { confirmDelete = true }.padding(10.dp),
                )
            }
        }
    }
}
