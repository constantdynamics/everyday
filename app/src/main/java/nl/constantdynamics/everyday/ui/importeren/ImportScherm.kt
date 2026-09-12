package nl.constantdynamics.everyday.ui.importeren

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.ImportUitkomst
import nl.constantdynamics.everyday.ui.korteDatum
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val MAXIMAAL_AANTAL = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScherm(
    container: AppContainer,
    serieId: Long,
    terug: () -> Unit,
) {
    val viewModel: ImportViewModel = viewModel(
        key = "import-$serieId",
        factory = ImportViewModel.factory(container, serieId),
    )
    val serie by viewModel.serie.collectAsStateWithLifecycle()
    var gestart by remember { mutableStateOf(false) }

    val kiezer = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAXIMAAL_AANTAL),
    ) { uris -> viewModel.start(uris) }

    LaunchedEffect(serie?.id) {
        if (!gestart && serie != null) {
            gestart = true
            kiezer.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importeren in ${serie?.naam ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = terug) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
            )
        },
    ) { ruimte ->
        Column(
            modifier = Modifier.fillMaxSize().padding(ruimte).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val stand = viewModel.stand) {
                ImportStand.Kiezen -> Text(
                    text = "Kies de foto's die je wilt toevoegen.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                is ImportStand.Bezig -> {
                    Text(
                        text = "Bezig met kopiëren… ${stand.gedaan} van ${stand.totaal}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (stand.totaal == 0) 0f else stand.gedaan.toFloat() / stand.totaal
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }

                is ImportStand.VraagDatum -> {
                    Text(
                        text = "Bezig met kopiëren… ${stand.gedaan} van ${stand.totaal}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    DatumVraag(
                        bestandsnaam = stand.kandidaat.bestandsnaam,
                        voorstel = stand.voorstel,
                        kies = viewModel::beantwoordDatum,
                        onzeker = viewModel::markeerDatumOnzeker,
                    )
                }

                is ImportStand.Klaar -> {
                    Overzicht(stand.uitkomst)
                    Button(onClick = terug, modifier = Modifier.padding(top = 24.dp)) {
                        Text("Klaar")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatumVraag(
    bestandsnaam: String,
    voorstel: LocalDate,
    kies: (LocalDate) -> Unit,
    onzeker: () -> Unit,
) {
    val staat = rememberDatePickerState(
        initialSelectedDateMillis = voorstel.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onzeker,
        confirmButton = {
            TextButton(
                onClick = {
                    val gekozen = staat.selectedDateMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?: voorstel
                    kies(gekozen)
                },
            ) { Text("Deze datum gebruiken") }
        },
        dismissButton = {
            TextButton(onClick = onzeker) { Text("Datum onzeker") }
        },
    ) {
        Column {
            Text(
                text = "Van deze foto is geen opnamedatum bekend.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (bestandsnaam.isNotBlank()) {
                Text(
                    text = bestandsnaam,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            DatePicker(state = staat, title = null)
        }
    }
}

@Composable
private fun Overzicht(uitkomst: ImportUitkomst) {
    val regels = buildList {
        add("${uitkomst.toegevoegd} toegevoegd")
        if (uitkomst.overgeslagenAlsDubbel > 0) {
            add("${uitkomst.overgeslagenAlsDubbel} overgeslagen omdat ze er al stonden")
        }
        if (uitkomst.zonderZekereDatum > 0) {
            add("${uitkomst.zonderZekereDatum} met een onzekere datum")
        }
        if (uitkomst.mislukt > 0) add("${uitkomst.mislukt} niet gelukt")
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Klaar met importeren", style = MaterialTheme.typography.titleMedium)
        regels.forEach { regel ->
            Text(regel, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (uitkomst.zonderZekereDatum > 0) {
            Text(
                text = "Foto's met een onzekere datum staan in het dagdetail met het label \"datum onzeker\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
