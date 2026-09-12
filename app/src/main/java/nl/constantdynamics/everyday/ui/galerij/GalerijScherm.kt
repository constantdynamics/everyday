package nl.constantdynamics.everyday.ui.galerij

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.toonUri
import nl.constantdynamics.everyday.ui.maandJaar
import nl.constantdynamics.everyday.ui.onderdelen.FotoBeeld
import nl.constantdynamics.everyday.ui.onderdelen.LegeStaat

private const val KOLOMMEN = 3

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalerijScherm(
    container: AppContainer,
    serieId: Long,
    terug: () -> Unit,
    naarOpname: (Long) -> Unit,
    naarDag: (java.time.LocalDate) -> Unit,
    naarImporteren: () -> Unit,
    naarTimelapse: () -> Unit,
) {
    val viewModel: GalerijViewModel = viewModel(
        key = "galerij-$serieId",
        factory = GalerijViewModel.factory(container, serieId),
    )
    val serie by viewModel.serie.collectAsStateWithLifecycle()
    val maanden by viewModel.maanden.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(serie?.naam ?: "") },
                navigationIcon = {
                    IconButton(onClick = terug) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
                actions = {
                    IconButton(onClick = naarImporteren) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Foto's importeren")
                    }
                    IconButton(onClick = naarTimelapse) {
                        Icon(Icons.Filled.Movie, contentDescription = "Timelapse maken")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { naarOpname(serieId) }) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Foto maken")
            }
        },
    ) { ruimte ->
        val groepen = maanden
        when {
            groepen == null -> Box(Modifier.fillMaxSize().padding(ruimte))
            groepen.isEmpty() -> LegeStaat(
                titel = "Nog geen foto's",
                uitleg = "Maak je eerste foto met de cameraknop, of voeg bestaande foto's toe met de knop rechtsboven.",
                modifier = Modifier.fillMaxSize().padding(ruimte),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = ruimte.calculateTopPadding(),
                    bottom = ruimte.calculateBottomPadding() + 88.dp,
                ),
            ) {
                groepen.forEach { groep ->
                    stickyHeader(key = "kop-${groep.maand}") {
                        Text(
                            text = maandJaar(groep.eersteDag),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    val rijen = groep.fotos.chunked(KOLOMMEN)
                    items(count = rijen.size, key = { index -> "rij-${groep.maand}-$index" }) { index ->
                        DagRij(fotos = rijen[index], openDag = naarDag)
                    }
                }
            }
        }
    }
}

@Composable
private fun DagRij(fotos: List<FotoEntiteit>, openDag: (java.time.LocalDate) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        fotos.forEach { foto ->
            Box(modifier = Modifier.weight(1f).clickable { openDag(foto.dagSleutel) }) {
                FotoBeeld(
                    uri = foto.toonUri(),
                    maxZijde = 480,
                    beschrijving = "Foto van ${foto.dagSleutel}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)),
                )
                Text(
                    text = foto.dagSleutel.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        // Laatste rij aanvullen zodat de tegels even breed blijven.
        repeat(KOLOMMEN - fotos.size) {
            Column(modifier = Modifier.weight(1f)) {}
        }
    }
}
