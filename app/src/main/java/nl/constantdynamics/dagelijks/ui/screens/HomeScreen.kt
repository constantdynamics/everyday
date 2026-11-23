package nl.constantdynamics.dagelijks.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.constantdynamics.dagelijks.core.Days
import nl.constantdynamics.dagelijks.core.DutchText
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.PhotoImage
import nl.constantdynamics.dagelijks.ui.components.Pill
import nl.constantdynamics.dagelijks.ui.components.RetroBackground
import nl.constantdynamics.dagelijks.ui.components.RetroButton
import nl.constantdynamics.dagelijks.ui.components.RetroCard
import nl.constantdynamics.dagelijks.ui.components.RetroIconButton
import nl.constantdynamics.dagelijks.ui.components.RetroTitle
import nl.constantdynamics.dagelijks.ui.components.Ribbon
import nl.constantdynamics.dagelijks.ui.components.rememberToday
import nl.constantdynamics.dagelijks.ui.components.retroBox
import nl.constantdynamics.dagelijks.ui.theme.RetroColors
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

    RetroBackground(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "kop") {
                Box(Modifier.fillMaxWidth()) {
                    RetroIconButton(
                        icon = AppIcons.Info,
                        description = "Over deze app",
                        onClick = onAbout,
                        size = 44.dp,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Ribbon("elke dag een foto")
                        Spacer(Modifier.height(8.dp))
                        RetroTitle("Dagelijks", fontSize = 58.sp, depth = 7.dp, outlineWidth = 2.5.dp)
                    }
                }
            }

            when {
                projects == null -> Unit

                projects.isEmpty() -> item(key = "leeg") {
                    RetroCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Nog geen projecten", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Een project is één onderwerp dat je elke dag fotografeert. " +
                                    "Bijvoorbeeld “Mijn gezicht” met de selfiecamera, of “De boom in de tuin”.",
                                style = MaterialTheme.typography.bodyLarge,
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
                    RetroButton(
                        text = "Nieuw project",
                        onClick = onNewProject,
                        icon = AppIcons.Add,
                        color = RetroColors.Mustard,
                        contentColor = RetroColors.Navy,
                        modifier = Modifier.fillMaxWidth(),
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
    RetroCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LatestPhoto(repository, project, Modifier.width(if (project.settings.aspectRatio.isLandscape) 112.dp else 84.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    project.settings.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = RetroColors.Navy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (project.photos.isEmpty()) "Nog geen foto's" else DutchText.photoCount(project.photos.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RetroColors.Navy.copy(alpha = 0.85f),
                )
                if (takenToday) {
                    Pill("Vandaag gelukt", icon = AppIcons.Check, color = RetroColors.Mint)
                } else {
                    Pill("Vandaag nog niet", color = RetroColors.Mustard)
                }
            }
            Spacer(Modifier.width(10.dp))
            RetroIconButton(
                icon = AppIcons.Camera,
                description = "Maak de foto van vandaag voor ${project.settings.name}",
                onClick = onTakePhoto,
                color = RetroColors.Coral,
                contentColor = RetroColors.Cream,
                size = 60.dp,
            )
        }
    }
}

/** The latest photo as a little print, slightly askew; a camera pictogram while the project is empty. */
@Composable
private fun LatestPhoto(repository: ProjectRepository, project: Project, modifier: Modifier = Modifier) {
    val ratio = project.settings.aspectRatio
    Box(
        modifier = modifier
            .rotate(-3f)
            .retroBox(fill = Color.White, corner = 6.dp, border = 2.dp, shadow = DpOffset(3.dp, 3.dp))
            .padding(5.dp),
    ) {
        val shape = RoundedCornerShape(2.dp)
        val latest = project.latestPhoto
        if (latest != null) {
            PhotoImage(
                repository = repository,
                photo = latest,
                maxSide = 360,
                contentDescription = "Laatste foto van ${project.settings.name}",
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio.value).clip(shape),
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(ratio.value).clip(shape).retroBox(
                    fill = RetroColors.CreamDark,
                    corner = 2.dp,
                    border = 0.dp,
                    shadow = DpOffset(0.dp, 0.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Camera,
                    contentDescription = null,
                    tint = RetroColors.Navy.copy(alpha = 0.45f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
