package nl.constantdynamics.dagelijks.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.constantdynamics.dagelijks.storage.StorageAccess
import nl.constantdynamics.dagelijks.ui.LocalServices
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.RetroBackground
import nl.constantdynamics.dagelijks.ui.components.RetroButton
import nl.constantdynamics.dagelijks.ui.components.RetroCard
import nl.constantdynamics.dagelijks.ui.components.RetroTitle
import nl.constantdynamics.dagelijks.ui.components.Ribbon
import nl.constantdynamics.dagelijks.ui.theme.RetroColors

/**
 * The system folder picker, limited to the phone's own storage: cloud drives are
 * not offered, because nothing of this app may leave the phone.
 */
class PickLocalFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).apply {
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
}

/**
 * Shown on the very first start, and again after a reinstall: the user points out
 * (or creates) the folder where the photos live.
 */
@Composable
fun WelcomeScreen() {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(PickLocalFolder()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        problem = null
        scope.launch {
            problem = when (services.chooseFolder(uri)) {
                is StorageAccess.PickResult.Success -> null
                StorageAccess.PickResult.NotLocalStorage ->
                    "Dat is geen map op je telefoon zelf. Kies een map in de interne opslag, bijvoorbeeld in Documenten."
                is StorageAccess.PickResult.Failed ->
                    "Die map kon niet worden gebruikt. Probeer een andere map, bijvoorbeeld in Documenten."
            }
            busy = false
        }
    }

    RetroBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Ribbon("elke dag een foto")
            Spacer(Modifier.height(10.dp))
            RetroTitle("Everyday", fontSize = 60.sp, depth = 7.dp, outlineWidth = 2.5.dp)
            Spacer(Modifier.height(22.dp))

            RetroCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Waar mogen je foto's staan?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = RetroColors.Navy,
                    )
                    Text(
                        "Je foto's komen als gewone bestanden in een map op je telefoon. " +
                            "Zo kun je ze met een usb-kabel naar je computer kopiëren, en blijven ze bewaard " +
                            "als je de app ooit verwijdert.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Step(1, "Tik hieronder op “Kies een map”.")
                    Step(2, "Maak een nieuwe map met de naam Dagelijks en open die. Of kies gewoon Documenten; dan maakt de app de map Dagelijks zelf aan.")
                    Step(3, "Tik op “Deze map gebruiken” en daarna op “Toestaan”.")
                    Text(
                        "Had je de app al eerder? Kies dan dezelfde map als toen: al je projecten en foto's komen vanzelf terug.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RetroColors.Navy.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            RetroButton(
                text = if (busy) "Even geduld…" else "Kies een map",
                onClick = {
                    // A phone without the system folder picker is rare, but it must not crash the app.
                    runCatching { picker.launch(StorageAccess.suggestedStartFolder) }
                        .onFailure { problem = "Je telefoon heeft geen mappenkiezer. Zonder kan de app geen map gebruiken." }
                },
                icon = AppIcons.Folder,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            problem?.let {
                Spacer(Modifier.height(16.dp))
                RetroCard(Modifier.fillMaxWidth(), fill = RetroColors.Mustard) {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "Deze app heeft geen toegang tot internet. Je foto's verlaten je telefoon alleen als jij ze zelf kopieert.",
                style = MaterialTheme.typography.bodyMedium,
                color = RetroColors.Navy,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = RetroColors.Cream,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .size(30.dp)
                .drawBehind {
                    drawCircle(RetroColors.Coral)
                    drawCircle(RetroColors.Navy, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
                .padding(top = 3.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 3.dp))
    }
}
