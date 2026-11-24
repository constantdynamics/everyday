package nl.constantdynamics.dagelijks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.constantdynamics.dagelijks.core.Days
import nl.constantdynamics.dagelijks.core.DutchText
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.components.AppBackground
import nl.constantdynamics.dagelijks.ui.components.AppButton
import nl.constantdynamics.dagelijks.ui.components.AppCard
import nl.constantdynamics.dagelijks.ui.components.AppIconButton
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.ButtonKind
import nl.constantdynamics.dagelijks.ui.components.DisplayTitle
import nl.constantdynamics.dagelijks.ui.components.IconButtonKind
import nl.constantdynamics.dagelijks.ui.components.PhotoImage
import nl.constantdynamics.dagelijks.ui.components.Pill
import nl.constantdynamics.dagelijks.ui.components.PillTone
import nl.constantdynamics.dagelijks.ui.components.SectionLabel
import nl.constantdynamics.dagelijks.ui.components.TitleRule
import nl.constantdynamics.dagelijks.ui.components.rememberToday
import nl.constantdynamics.dagelijks.ui.theme.AppColors
import nl.constantdynamics.dagelijks.ui.theme.AppType
import java.time.LocalDate

/**
 * The start screen: every project with its latest photo, how many photos it has and
 * whether today's photo has been taken. One tap on the camera button starts shooting.
 */
@Composable
fun HomeScreen(
    repository: ProjectRepository,
    projects: List<Project>?,
    onOpenProject: (Project) -> Unit,
    onTakePhoto: (Project) -> Unit,
    onNewProject: () -> Unit,
    onAbout: () -> Unit,
) {
    val today = rememberToday()

    AppBackground(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "kop") {
                Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(
                            text = "elke dag een foto",
                            color = AppColors.TextMuted,
                            style = AppType.tagline,
                            modifier = Modifier.weight(1f),
                        )
                        AppIconButton(icon = AppIcons.Info, description = "Over deze app", onClick = onAbout)
                    }
                    Spacer(Modifier.height(18.dp))
                    DisplayTitle("Everyday", style = AppType.appTitle, color = AppColors.Amber)
                    Spacer(Modifier.height(24.dp))
                    TitleRule()
                }
            }

            when {
                projects == null -> Unit

                projects.isEmpty() -> item(key = "leeg") {
                    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Nog geen projecten", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Een project is één onderwerp dat je elke dag fotografeert. " +
                                    "Bijvoorbeeld “Mijn gezicht” met de selfiecamera, of “De boom in de tuin”.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppColors.TextMuted,
                            )
                        }
                    }
                }

                else -> items(projects, key = { "project-${it.folderName}" }) { project ->
                    ProjectCard(
                        repository = repository,
                        project = project,
                        today = today,
                        onOpen = { onOpenProject(project) },
                        onTakePhoto = { onTakePhoto(project) },
                    )
                }
            }

            if (projects != null) {
                item(key = "nieuw") {
                    AppButton(
                        text = "Nieuw project",
                        onClick = onNewProject,
                        icon = AppIcons.Add,
                        // With no project yet this is the one thing to do; after that it steps back.
                        kind = if (projects.isEmpty()) ButtonKind.Primary else ButtonKind.Secondary,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    repository: ProjectRepository,
    project: Project,
    today: LocalDate,
    onOpen: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    val takenToday = Days.hasPhotoOn(project.dates, today)
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen),
        contentPadding = PaddingValues(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LatestPhoto(repository, project, Modifier.width(if (project.settings.aspectRatio.isLandscape) 96.dp else 78.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    project.settings.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (project.photos.isEmpty()) "Nog geen foto's" else DutchText.photoCount(project.photos.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextMuted,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
                if (takenToday) {
                    Pill("Vandaag gelukt", tone = PillTone.Done, icon = AppIcons.Check)
                } else {
                    Pill("Vandaag nog niet")
                }
            }
            Spacer(Modifier.width(10.dp))
            AppIconButton(
                icon = AppIcons.Camera,
                description = "Maak de foto van vandaag voor ${project.settings.name}",
                onClick = onTakePhoto,
                // The hot colour only while today's photo still has to be taken.
                kind = if (takenToday) IconButtonKind.Outlined else IconButtonKind.Primary,
                size = 52.dp,
            )
        }
    }
}

/** The latest photo of the project; a camera pictogram while the project is still empty. */
@Composable
private fun LatestPhoto(repository: ProjectRepository, project: Project, modifier: Modifier = Modifier) {
    val ratio = project.settings.aspectRatio
    val shape = RoundedCornerShape(10.dp)
    val latest = project.latestPhoto
    if (latest != null) {
        PhotoImage(
            repository = repository,
            photo = latest,
            maxSide = 360,
            contentDescription = "Laatste foto van ${project.settings.name}",
            modifier = modifier.aspectRatio(ratio.value).clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .aspectRatio(ratio.value)
                .clip(shape)
                .background(AppColors.Raised)
                .border(1.dp, AppColors.Hairline, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppIcons.Camera,
                contentDescription = null,
                tint = AppColors.Outline,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
