package nl.constantdynamics.everyday.ui.start

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.db.SerieOverzichtRij
import nl.constantdynamics.everyday.ui.aantalFotos
import nl.constantdynamics.everyday.ui.korteDatum
import nl.constantdynamics.everyday.ui.onderdelen.FotoBeeld
import nl.constantdynamics.everyday.ui.onderdelen.LegeStaat
import nl.constantdynamics.everyday.ui.onderdelen.NaamDialoog
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScherm(
    container: AppContainer,
    naarOpname: (Long) -> Unit,
    naarGalerij: (Long) -> Unit,
) {
    val viewModel: StartViewModel = viewModel(factory = StartViewModel.factory(container))
    val series by viewModel.series.collectAsStateWithLifecycle()
    var toonNieuweSerie by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("everyday") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { toonNieuweSerie = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nieuwe serie")
            }
        },
    ) { ruimte ->
        val rijen = series
        when {
            rijen == null -> Box(Modifier.fillMaxSize().padding(ruimte))
            rijen.isEmpty() -> LegeStaat(
                titel = "Nog geen series",
                uitleg = "Maak een serie aan voor het onderwerp dat je elke dag fotografeert.",
                modifier = Modifier.fillMaxSize().padding(ruimte),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = ruimte.calculateTopPadding() + 8.dp,
                    bottom = ruimte.calculateBottomPadding() + 88.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rijen, key = { it.serie.id }) { rij ->
                    SerieKaart(
                        rij = rij,
                        openGalerij = { naarGalerij(rij.serie.id) },
                        openCamera = { naarOpname(rij.serie.id) },
                    )
                }
            }
        }
    }

    if (toonNieuweSerie) {
        NaamDialoog(
            titel = "Nieuwe serie",
            uitleg = "Geef de serie een naam. De mapnaam op je toestel wordt hiervan afgeleid en verandert daarna niet meer.",
            beginwaarde = "",
            bevestigLabel = "Aanmaken",
            sluit = { toonNieuweSerie = false },
            bevestig = { naam ->
                toonNieuweSerie = false
                viewModel.maakSerie(naam) { nieuweId -> naarOpname(nieuweId) }
            },
        )
    }
}

@Composable
private fun SerieKaart(
    rij: SerieOverzichtRij,
    openGalerij: () -> Unit,
    openCamera: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = openGalerij)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FotoBeeld(
                uri = rij.omslagUri?.let(Uri::parse),
                maxZijde = 320,
                beschrijving = "Laatste foto van ${rij.serie.naam}",
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rij.serie.naam,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(aantalFotos(rij.aantalFotos))
                        rij.laatsteMoment?.let { append(" · laatste ").append(korteDatum(it)) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = openCamera) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Foto maken van ${rij.serie.naam}")
            }
        }
    }
}
