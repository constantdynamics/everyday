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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import nl.constantdynamics.dagelijks.core.Days
import nl.constantdynamics.dagelijks.core.DutchText
import nl.constantdynamics.dagelijks.storage.Photo
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.Format
import nl.constantdynamics.dagelijks.ui.LocalServices
import nl.constantdynamics.dagelijks.ui.components.AppBackground
import nl.constantdynamics.dagelijks.ui.components.AppButton
import nl.constantdynamics.dagelijks.ui.components.AppCard
import nl.constantdynamics.dagelijks.ui.components.AppDialog
import nl.constantdynamics.dagelijks.ui.components.AppIconButton
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.AppTextField
import nl.constantdynamics.dagelijks.ui.components.ButtonKind
import nl.constantdynamics.dagelijks.ui.components.DisplayTitle
import nl.constantdynamics.dagelijks.ui.components.Hairline
import nl.constantdynamics.dagelijks.ui.components.PhotoImage
import nl.constantdynamics.dagelijks.ui.components.Pill
import nl.constantdynamics.dagelijks.ui.components.PillTone
import nl.constantdynamics.dagelijks.ui.components.ScreenHeader
import nl.constantdynamics.dagelijks.ui.components.SectionLabel
import nl.constantdynamics.dagelijks.ui.components.panel
import nl.constantdynamics.dagelijks.ui.components.rememberToday
import nl.constantdynamics.dagelijks.ui.theme.AppColors
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
    val today = rememberToday()
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

    AppBackground(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (ratio.isLandscape) 2 else 3),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "kop", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    ScreenHeader(title = project.settings.name, onBack = onBack) {
                        Spacer(Modifier.width(10.dp))
                        AppIconButton(AppIcons.Edit, "Project aanpassen", onClick = { showSettings = true })
                    }
                    Spacer(Modifier.height(22.dp))
                    Hairline()
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (project.photos.isEmpty()) "Nog geen foto's" else DutchText.photoCount(project.photos.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            project.photos.firstOrNull()?.let { first ->
                                Text(
                                    "sinds ${Format.date(first.date)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppColors.TextMuted,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        if (takenToday) {
                            Pill("Vandaag gelukt", tone = PillTone.Done, icon = AppIcons.Check)
                        } else {
                            Pill("Vandaag nog niet")
                        }
                    }
                    Hairline()
                    Spacer(Modifier.height(18.dp))

                    // One hot button: today's photo while it still has to be taken, the timelapse after that.
                    val photoButton: @Composable (ButtonKind) -> Unit = { kind ->
                        AppButton(
                            text = if (takenToday) "Foto van vandaag opnieuw maken" else "Maak de foto van vandaag",
                            onClick = onTakePhoto,
                            icon = AppIcons.Camera,
                            kind = kind,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val timelapseButton: @Composable (ButtonKind) -> Unit = { kind ->
                        AppButton(
                            text = "Maak timelapse",
                            onClick = onTimelapse,
                            icon = AppIcons.Film,
                            kind = kind,
                            enabled = project.photos.isNotEmpty() || project.timelapses.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (takenToday) {
                            timelapseButton(ButtonKind.Primary)
                            photoButton(ButtonKind.Secondary)
                        } else {
                            photoButton(ButtonKind.Primary)
                            timelapseButton(ButtonKind.Secondary)
                        }
                        if (project.photos.isEmpty()) {
                            AppCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(
                                    "Hier komen je foto's te staan, één per dag. Maak de eerste!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.TextMuted,
                                )
                            }
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
                    is GalleryItem.Month -> SectionLabel(
                        text = Format.month(item.month.atDay(1)),
                        rule = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
                    )

                    is GalleryItem.Picture -> PhotoTile(
                        repository = repository,
                        photo = item.photo,
                        ratio = ratio.value,
                        isToday = item.photo.date == today,
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

/** One photo in the grid with its date under it; today's date lights up. */
@Composable
private fun PhotoTile(repository: ProjectRepository, photo: Photo, ratio: Float, isToday: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier.clip(shape).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PhotoImage(
            repository = repository,
            photo = photo,
            maxSide = 360,
            contentDescription = "Foto van ${Format.date(photo.date)}",
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(shape),
        )
        Text(
            text = Format.shortDate(photo.date),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) AppColors.Amber else AppColors.TextMuted,
            maxLines = 1,
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
        AppDialog(
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
                .panel(corner = 24.dp, edge = AppColors.Outline)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DisplayTitle("Project aanpassen", style = MaterialTheme.typography.headlineMedium)

            Text("Naam", style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
            AppTextField(state = nameField, modifier = Modifier.fillMaxWidth())

            Text("Camera", style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
            LensChoice(lens, onChange = { lens = it })

            Text("Vorm van de foto", style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
            AspectRatioChoice(aspectRatio, onChange = { aspectRatio = it }, enabled = project.canChangeAspectRatio)
            if (!project.canChangeAspectRatio) {
                Text(
                    "De vorm ligt vast omdat er al foto's zijn. Anders past de vorige foto niet meer over de nieuwe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
            if (deleteFailed) {
                Text("Het verwijderen is niet gelukt.", style = MaterialTheme.typography.bodyMedium, color = AppColors.Coral)
            }

            Spacer(Modifier.height(2.dp))
            AppButton("Opslaan", onClick = ::save, icon = AppIcons.Check, modifier = Modifier.fillMaxWidth())
            AppButton("Annuleren", onClick = onClose, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Dit project verwijderen",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.Coral,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { confirmDelete = true }.padding(10.dp),
                )
            }
        }
    }
}
