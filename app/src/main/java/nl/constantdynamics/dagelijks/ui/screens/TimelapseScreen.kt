package nl.constantdynamics.dagelijks.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.constantdynamics.dagelijks.core.Days
import nl.constantdynamics.dagelijks.core.DutchText
import nl.constantdynamics.dagelijks.core.ProjectSettings
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.storage.Timelapse
import nl.constantdynamics.dagelijks.timelapse.RenderState
import nl.constantdynamics.dagelijks.timelapse.TimelapseRequest
import nl.constantdynamics.dagelijks.ui.Format
import nl.constantdynamics.dagelijks.ui.LocalServices
import nl.constantdynamics.dagelijks.ui.components.AppBackground
import nl.constantdynamics.dagelijks.ui.components.AppButton
import nl.constantdynamics.dagelijks.ui.components.AppCard
import nl.constantdynamics.dagelijks.ui.components.AppDialog
import nl.constantdynamics.dagelijks.ui.components.AppIconButton
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.AppSlider
import nl.constantdynamics.dagelijks.ui.components.ButtonKind
import nl.constantdynamics.dagelijks.ui.components.ChoiceTile
import nl.constantdynamics.dagelijks.ui.components.DisplayTitle
import nl.constantdynamics.dagelijks.ui.components.NoticeCard
import nl.constantdynamics.dagelijks.ui.components.ScreenHeader
import nl.constantdynamics.dagelijks.ui.components.SectionLabel
import nl.constantdynamics.dagelijks.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

object TimelapseTags {
    const val START = "maak-timelapse"
    const val PROGRESS = "voortgang"
    const val DONE = "timelapse-klaar"
}

/**
 * Settings for the timelapse (speed, period, date stamp), the render itself with a
 * progress bar, and the videos made earlier. Everything happens on the phone; there
 * is no share button, the video simply lands in the project's "timelapses" folder.
 */
@Composable
fun TimelapseScreen(
    repository: ProjectRepository,
    project: Project,
    onBack: () -> Unit,
    onPlay: (Timelapse) -> Unit,
) {
    val services = LocalServices.current
    val renderer = services.timelapseRenderer
    val renderState by renderer.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    var speed by rememberSaveable { mutableIntStateOf(project.settings.timelapseFps) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var fromText by rememberSaveable { mutableStateOf<String?>(null) }
    var toText by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Timelapse?>(null) }

    val firstDate = project.photos.firstOrNull()?.date
    val lastDate = project.photos.lastOrNull()?.date
    val from = fromText?.let(LocalDate::parse)
    val to = toText?.let(LocalDate::parse)
    val chosen = remember(project.photos, from, to) {
        val days = Days.inPeriod(project.dates, from, to).toSet()
        project.photos.filter { it.date in days }
    }

    val running = renderState as? RenderState.Running
    val runningHere = running?.projectFolderName == project.folderName
    val size = project.settings.aspectRatio.videoSize

    // Rendering continues while this screen is visible even if nobody touches the phone.
    DisposableEffect(view, running != null) {
        view.keepScreenOn = running != null
        onDispose { view.keepScreenOn = false }
    }

    fun start() {
        if (chosen.isEmpty() || renderer.isRunning) return
        val chosenSpeed = speed
        services.scope.launch {
            runCatching { repository.updateSettings(project.folderName) { it.copy(timelapseFps = chosenSpeed) } }
        }
        renderer.start(
            TimelapseRequest(
                projectFolderName = project.folderName,
                photos = chosen,
                aspectRatio = project.settings.aspectRatio,
                photosPerSecond = chosenSpeed,
                showDate = showDate,
            ),
            repository,
        )
    }

    AppBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(title = "Timelapse", onBack = onBack, modifier = Modifier.padding(bottom = 6.dp))

            if (project.photos.isEmpty()) {
                AppCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Er zijn nog geen foto's om een timelapse van te maken.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextMuted,
                    )
                }
            } else {
                AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Snelheid", style = MaterialTheme.typography.titleMedium)
                        Text(DutchText.photosPerSecond(speed), style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
                        AppSlider(
                            value = speed.toFloat(),
                            onValueChange = { speed = it.roundToInt() },
                            valueRange = ProjectSettings.MIN_TIMELAPSE_FPS.toFloat()..ProjectSettings.MAX_TIMELAPSE_FPS.toFloat(),
                            steps = ProjectSettings.MAX_TIMELAPSE_FPS - ProjectSettings.MIN_TIMELAPSE_FPS - 1,
                            description = "Snelheid in foto's per seconde",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Periode", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ChoiceTile(selected = from != null, onClick = { pickingFrom = true }, modifier = Modifier.weight(1f)) {
                                Text("Van", style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted)
                                Text(Format.date(from ?: firstDate!!), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            }
                            ChoiceTile(selected = to != null, onClick = { pickingTo = true }, modifier = Modifier.weight(1f)) {
                                Text("Tot en met", style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted)
                                Text(Format.date(to ?: lastDate!!), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            }
                        }
                        if (from != null || to != null) {
                            TextButton(onClick = { fromText = null; toText = null }) {
                                Text("Gebruik weer alle foto's", style = MaterialTheme.typography.labelLarge, color = AppColors.Amber)
                            }
                        }
                    }
                }

                AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Datum in beeld", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ChoiceTile(selected = !showDate, onClick = { showDate = false }, modifier = Modifier.weight(1f)) {
                                Text("Zonder", style = MaterialTheme.typography.titleSmall)
                            }
                            ChoiceTile(selected = showDate, onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                                Text("Met datum", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }

                NoticeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DisplayTitle(
                            text = if (chosen.isEmpty()) "Geen foto's in deze periode" else DutchText.timelapseSummary(chosen.size, speed),
                            style = MaterialTheme.typography.headlineSmall,
                            color = AppColors.Amber,
                            maxLines = 2,
                        )
                        Text(
                            "MP4-video van ${size.width} × ${size.height} pixels. Hij komt in de map “timelapses” van dit project.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                when {
                    runningHere -> RenderProgress(running, onCancel = renderer::cancel)

                    running != null -> AppCard(Modifier.fillMaxWidth()) {
                        Text(
                            "Er wordt al een timelapse gemaakt voor een ander project. Wacht tot die klaar is.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    else -> AppButton(
                        text = "Maak timelapse",
                        onClick = ::start,
                        icon = AppIcons.Film,
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag(TimelapseTags.START),
                    )
                }
            }

            if (project.timelapses.isNotEmpty()) {
                SectionLabel("Je timelapses", rule = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                project.timelapses.forEach { timelapse ->
                    TimelapseRow(timelapse, onPlay = { onPlay(timelapse) }, onDelete = { deleting = timelapse })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    // --- Result of a render --------------------------------------------------------------
    when (val state = renderState) {
        is RenderState.Finished -> if (state.projectFolderName == project.folderName) {
            AppDialog(
                title = "Je timelapse is klaar!",
                onDismiss = renderer::acknowledge,
                confirmText = "Bekijk",
                onConfirm = {
                    renderer.acknowledge()
                    onPlay(state.timelapse)
                },
                dismissText = "Sluiten",
                modifier = Modifier.testTag(TimelapseTags.DONE),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${DutchText.photoCount(state.photosUsed)} in een video van ${state.size.width} × ${state.size.height} pixels " +
                            "(${Format.fileSize(state.timelapse.size)}).",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text("Opgeslagen als:\ntimelapses/${state.timelapse.name}", style = MaterialTheme.typography.bodyMedium)
                    if (state.photosSkipped > 0) {
                        Text(
                            "${DutchText.photoCount(state.photosSkipped)} kon niet worden gelezen en is overgeslagen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Coral,
                        )
                    }
                }
            }
        }

        is RenderState.Failed -> if (state.projectFolderName == project.folderName) {
            AppDialog(
                title = "Dat ging niet goed",
                onDismiss = renderer::acknowledge,
                confirmText = "Oké",
                onConfirm = renderer::acknowledge,
                dismissText = null,
                confirmKind = ButtonKind.Secondary,
            ) {
                Text(
                    "De timelapse kon niet worden gemaakt. Controleer of er genoeg ruimte vrij is op je telefoon en probeer het opnieuw." +
                        (state.reason?.let { "\n\n($it)" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        else -> Unit
    }

    deleting?.let { timelapse ->
        AppDialog(
            title = "Timelapse verwijderen?",
            onDismiss = { deleting = null },
            confirmText = "Ja, verwijder",
            onConfirm = {
                deleting = null
                services.scope.launch { runCatching { repository.deleteTimelapse(project.folderName, timelapse) } }
            },
            dismissText = "Nee, bewaar hem",
        ) {
            Text(
                "De video “${timelapse.name}” wordt verwijderd. Je foto's blijven staan, dus je kunt hem altijd opnieuw maken.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    if (pickingFrom && firstDate != null && lastDate != null) {
        DayPicker(
            initial = from ?: firstDate,
            earliest = firstDate,
            latest = to ?: lastDate,
            onPicked = { fromText = it.takeIf { day -> day > firstDate }?.toString() },
            onClose = { pickingFrom = false },
        )
    }
    if (pickingTo && firstDate != null && lastDate != null) {
        DayPicker(
            initial = to ?: lastDate,
            earliest = from ?: firstDate,
            latest = lastDate,
            onPicked = { toText = it.takeIf { day -> day < lastDate }?.toString() },
            onClose = { pickingTo = false },
        )
    }
}

@Composable
private fun RenderProgress(state: RenderState.Running, onCancel: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("even geduld")
            Text(
                text = if (state.saving) "De video wordt opgeslagen…" else "Foto ${state.photosDone} van ${state.photosTotal}",
                style = MaterialTheme.typography.titleMedium,
            )
            ProgressBar(state.progress, Modifier.fillMaxWidth().testTag(TimelapseTags.PROGRESS))
            Text(
                "Laat de app open tot de video klaar is. Het scherm blijft vanzelf aan.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
            )
            AppButton(
                "Annuleren",
                onClick = onCancel,
                kind = ButtonKind.Secondary,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(8.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) }
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(AppColors.Track, cornerRadius = radius)
                val filled = (size.width * progress.coerceIn(0f, 1f)).coerceAtLeast(size.height)
                drawRoundRect(AppColors.Amber, size = Size(filled, size.height), cornerRadius = radius)
            },
    )
}

@Composable
private fun TimelapseRow(timelapse: Timelapse, onPlay: () -> Unit, onDelete: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(AppIcons.Play, "Bekijk ${timelapse.name}", onClick = onPlay)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    timelapse.name.removeSuffix(".mp4"),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(Format.fileSize(timelapse.size), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            }
            Spacer(Modifier.width(8.dp))
            AppIconButton(AppIcons.Delete, "Verwijder ${timelapse.name}", onClick = onDelete)
        }
    }
}

/** The Material date picker, limited to the days between the first and last photo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(
    initial: LocalDate,
    earliest: LocalDate,
    latest: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    fun LocalDate.toMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.coerceIn(earliest, latest).toMillis(),
        yearRange = earliest.year..latest.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toDate() in earliest..latest
            override fun isSelectableYear(year: Int): Boolean = year in earliest.year..latest.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPicked(it.toDate()) }
                    onClose()
                },
            ) { Text("Kies", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Annuleren", style = MaterialTheme.typography.labelLarge) }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
