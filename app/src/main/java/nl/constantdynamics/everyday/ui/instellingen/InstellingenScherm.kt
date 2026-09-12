package nl.constantdynamics.everyday.ui.instellingen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.backup.BackupStatus
import nl.constantdynamics.everyday.data.media.MediaOpslag
import nl.constantdynamics.everyday.data.opslag.Themakeuze
import nl.constantdynamics.everyday.ui.aantalFotos
import nl.constantdynamics.everyday.ui.korteDatum
import nl.constantdynamics.everyday.ui.tijdstip
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstellingenScherm(
    container: AppContainer,
    terug: () -> Unit,
) {
    val viewModel: InstellingenViewModel = viewModel(factory = InstellingenViewModel.factory(container))
    val status by viewModel.backupStatus.collectAsStateWithLifecycle()
    val ghostDekking by viewModel.ghostDekking.collectAsStateWithLifecycle()
    val thema by viewModel.thema.collectAsStateWithLifecycle()
    val dagStartUur by viewModel.dagStartUur.collectAsStateWithLifecycle()
    val meldingen = remember { SnackbarHostState() }

    val mapKiezer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) viewModel.kiesBackupMap(uri) }

    LaunchedEffect(Unit) {
        viewModel.meldingen.collect { meldingen.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(meldingen) },
        topBar = {
            TopAppBar(
                title = { Text("Instellingen") },
                navigationIcon = {
                    IconButton(onClick = terug) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
            )
        },
    ) { ruimte ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ruimte)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (viewModel.bezig) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Kop("Back-up")
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Elke nieuwe of gewijzigde foto wordt automatisch naar deze map gekopieerd. " +
                            "Lukt dat niet, dan blijft het werk in de wachtrij staan en wordt het later ingehaald.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BackupStand(status)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { mapKiezer.launch(null) },
                            enabled = !viewModel.bezig,
                        ) {
                            Text(if (status?.mapIngesteld == true) "Map wijzigen" else "Map kiezen")
                        }
                        OutlinedButton(
                            onClick = viewModel::kopieerAllesOpnieuw,
                            enabled = !viewModel.bezig && status?.mapIngesteld == true,
                        ) {
                            Text("Alles opnieuw kopiëren")
                        }
                    }
                }
            }

            Kop("Herstellen")
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Na een de-installatie of een nieuw toestel raakt de app de administratie kwijt, " +
                            "maar de backupmap heeft alles nog. Hiermee lees je series, foto's en dagkeuzes terug.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = viewModel::herstelUitBackupmap,
                        enabled = !viewModel.bezig && status?.mapIngesteld == true,
                    ) {
                        Text("Herstellen uit backupmap")
                    }
                }
            }

            Kop("Opnemen")
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Standaarddekking van de vorige foto", style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = ghostDekking,
                            onValueChange = viewModel::zetGhostDekking,
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(ghostDekking * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }

            Kop("Dagindeling")
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (dagStartUur == 0) {
                            "Een dag loopt van 00:00 tot 24:00"
                        } else {
                            "Een dag loopt van %02d:00 tot %02d:00 de volgende dag".format(dagStartUur, dagStartUur)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Met een latere dagstart hoort een foto van na middernacht nog bij de dag ervoor. " +
                            "Dit geldt voor nieuwe foto's; de dag van bestaande foto's verandert niet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = dagStartUur.toFloat(),
                        onValueChange = { viewModel.zetDagStartUur(it.roundToInt()) },
                        valueRange = 0f..12f,
                        steps = 11,
                    )
                }
            }

            Kop("Weergave")
            Card {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Themakeuze.entries.forEach { keuze ->
                        FilterChip(
                            selected = thema == keuze,
                            onClick = { viewModel.zetThema(keuze) },
                            label = {
                                Text(
                                    when (keuze) {
                                        Themakeuze.LICHT -> "Licht"
                                        Themakeuze.DONKER -> "Donker"
                                        Themakeuze.SYSTEEM -> "Systeem"
                                    },
                                )
                            },
                        )
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "Je foto's staan in ${MediaOpslag.HOOFDMAP_ZICHTBAAR} en zijn zichtbaar voor je galerij. " +
                    "Wil je niet dat ze naar de cloud gaan, zet dan de back-up van Google Foto's uit voor die map.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Kop(tekst: String) {
    Text(
        text = tekst,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun BackupStand(status: BackupStatus?) {
    val regels = buildList {
        if (status == null || !status.mapIngesteld) {
            add("Nog geen backupmap gekozen.")
        } else {
            add(
                status.laatsteGeslaagdeKopie?.let {
                    "Laatste geslaagde kopie: ${korteDatum(it)} om ${tijdstip(it)}"
                } ?: "Nog niets gekopieerd.",
            )
            if (status.aantalInWachtrij > 0) {
                val wachtrij = "In de wachtrij: ${aantalFotos(status.aantalInWachtrij)}"
                add(
                    status.oudsteInWachtrij?.let { "$wachtrij, oudste van ${korteDatum(it)}" }
                        ?: wachtrij,
                )
            } else {
                add("Niets in de wachtrij.")
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        regels.forEach { regel ->
            Text(regel, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
