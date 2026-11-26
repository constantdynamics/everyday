package nl.constantdynamics.dagelijks.ui.screens

import android.media.AudioManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.components.AppIconButton
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.KeepScreenOn
import nl.constantdynamics.dagelijks.ui.theme.AppColors

/**
 * Plays a timelapse inside the app, with Android's own video player. The video keeps
 * repeating; tap it for the pause and scrub controls.
 */
@Composable
fun PlayerScreen(
    repository: ProjectRepository,
    project: Project,
    fileName: String,
    onBack: () -> Unit,
) {
    val timelapse = project.timelapses.firstOrNull { it.name == fileName }
    if (timelapse == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val uri = remember(timelapse.nodeId) { repository.locationOf(timelapse).toUri() }
    var failed by remember { mutableStateOf(false) }
    KeepScreenOn()

    Box(Modifier.fillMaxSize().background(AppColors.Ground)) {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (failed) {
                Text(
                    "Deze video kon niet worden afgespeeld.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.Text,
                    textAlign = TextAlign.Center,
                )
            } else {
                AndroidView(
                    modifier = Modifier.aspectRatio(project.settings.aspectRatio.value),
                    factory = { context ->
                        VideoView(context).apply {
                            // A timelapse has no sound, so music from another app keeps playing.
                            setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
                            setMediaController(MediaController(context).also { it.setAnchorView(this) })
                            setOnPreparedListener { player ->
                                player.isLooping = true
                                start()
                            }
                            setOnErrorListener { _, _, _ ->
                                failed = true
                                true
                            }
                            setVideoURI(uri)
                        }
                    },
                    onRelease = { it.stopPlayback() },
                )
            }
            AppIconButton(
                AppIcons.Close,
                "Sluiten",
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}
