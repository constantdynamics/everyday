package nl.constantdynamics.dagelijks.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.constantdynamics.dagelijks.core.AspectRatio
import nl.constantdynamics.dagelijks.core.Lens
import nl.constantdynamics.dagelijks.storage.Project
import nl.constantdynamics.dagelijks.storage.ProjectRepository
import nl.constantdynamics.dagelijks.ui.components.AppBackground
import nl.constantdynamics.dagelijks.ui.components.AppButton
import nl.constantdynamics.dagelijks.ui.components.AppCard
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.AppTextField
import nl.constantdynamics.dagelijks.ui.components.ChoiceTile
import nl.constantdynamics.dagelijks.ui.components.FittedText
import nl.constantdynamics.dagelijks.ui.components.NoticeCard
import nl.constantdynamics.dagelijks.ui.components.ScreenHeader
import nl.constantdynamics.dagelijks.ui.components.rememberScreenScope
import nl.constantdynamics.dagelijks.ui.theme.AppColors

@Composable
fun NewProjectScreen(
    repository: ProjectRepository,
    onCreated: (Project) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberScreenScope()
    val nameField = rememberTextFieldState()
    val name = nameField.text.toString()
    var lens by rememberSaveable { mutableStateOf(Lens.FRONT) }
    var aspectRatio by rememberSaveable { mutableStateOf(AspectRatio.DEFAULT) }
    // Not saved: when this screen is rebuilt, the work it was waiting for is no longer its own.
    var busy by remember { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    // No walking away in the split second that the project is being made.
    BackHandler(enabled = busy) {}

    fun create() {
        if (busy || name.isBlank()) return
        busy = true
        failed = false
        scope.launch {
            val created = runCatching { repository.createProject(name, lens, aspectRatio) }
            busy = false
            created.onSuccess(onCreated).onFailure { failed = true }
        }
    }

    AppBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(title = "Nieuw project", onBack = { if (!busy) onBack() }, modifier = Modifier.padding(bottom = 6.dp))

            AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hoe heet je project?", style = MaterialTheme.typography.titleMedium)
                    AppTextField(
                        state = nameField,
                        placeholder = "Bijvoorbeeld: Mijn gezicht",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Welke camera?", style = MaterialTheme.typography.titleMedium)
                    LensChoice(lens, onChange = { lens = it })
                }
            }

            AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Welke vorm krijgt de foto?", style = MaterialTheme.typography.titleMedium)
                    AspectRatioChoice(aspectRatio, onChange = { aspectRatio = it })
                    Text(
                        "Na je eerste foto ligt de vorm vast. Alleen dan blijft de vorige foto precies over de nieuwe passen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextMuted,
                    )
                }
            }

            if (failed) {
                NoticeCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Het project kon niet worden aangemaakt. Controleer of er nog ruimte is op je telefoon en probeer het opnieuw.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            AppButton(
                text = if (busy) "Even geduld…" else "Maak project",
                onClick = ::create,
                icon = AppIcons.Check,
                enabled = name.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Front or back camera. */
@Composable
fun LensChoice(selected: Lens, onChange: (Lens) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceTile(selected == Lens.FRONT, onClick = { onChange(Lens.FRONT) }, modifier = Modifier.weight(1f)) {
            FittedText("Voorkant", style = MaterialTheme.typography.titleMedium)
            FittedText("selfie", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        }
        ChoiceTile(selected == Lens.BACK, onClick = { onChange(Lens.BACK) }, modifier = Modifier.weight(1f)) {
            FittedText("Achterkant", style = MaterialTheme.typography.titleMedium)
            FittedText("gewone camera", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        }
    }
}

/** The four shapes, each drawn in its real proportions. */
@Composable
fun AspectRatioChoice(
    selected: AspectRatio,
    onChange: (AspectRatio) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AspectRatio.entries.forEach { ratio ->
            ChoiceTile(
                selected = selected == ratio,
                onClick = { onChange(ratio) },
                enabled = enabled || selected == ratio,
                modifier = Modifier.weight(1f),
            ) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            // Fit the shape into the square, like a little picture frame.
                            val scale = minOf(size.width / ratio.width, size.height / ratio.height)
                            val shape = Size(ratio.width * scale, ratio.height * scale)
                            val origin = Offset((size.width - shape.width) / 2, (size.height - shape.height) / 2)
                            val radius = CornerRadius(3.dp.toPx())
                            val line = 1.5.dp.toPx()
                            drawRoundRect(AppColors.Surface, origin, shape, radius)
                            drawRoundRect(
                                color = if (selected == ratio) AppColors.Amber else AppColors.Outline,
                                topLeft = origin + Offset(line / 2, line / 2),
                                size = Size(shape.width - line, shape.height - line),
                                cornerRadius = radius,
                                style = Stroke(line),
                            )
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                FittedText(ratio.id, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                FittedText(
                    text = when (ratio) {
                        AspectRatio.PORTRAIT_9_16 -> "hoog"
                        AspectRatio.PORTRAIT_3_4 -> "staand"
                        AspectRatio.SQUARE -> "vierkant"
                        AspectRatio.LANDSCAPE_16_9 -> "liggend"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
