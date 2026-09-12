package nl.constantdynamics.everyday.ui.dag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.toonUri
import nl.constantdynamics.everyday.ui.deelFoto
import nl.constantdynamics.everyday.ui.langeDatum
import nl.constantdynamics.everyday.ui.onderdelen.FotoBeeld
import nl.constantdynamics.everyday.ui.tijdstip
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DagScherm(
    container: AppContainer,
    serieId: Long,
    dag: LocalDate,
    terug: () -> Unit,
    naarOpname: (Long) -> Unit,
    naarBewerken: (Long) -> Unit,
) {
    val viewModel: DagViewModel = viewModel(
        key = "dag-$serieId-$dag",
        factory = DagViewModel.factory(container, serieId, dag),
    )
    val context = LocalContext.current
    val fotos by viewModel.fotos.collectAsStateWithLifecycle()
    val handmatigGekozen by viewModel.handmatigGekozen.collectAsStateWithLifecycle()
    val standaardFotoId by viewModel.standaardFotoId.collectAsStateWithLifecycle()
    val meldingen = remember { SnackbarHostState() }
    var teVerwijderen by remember { mutableStateOf<FotoEntiteit?>(null) }

    LaunchedEffect(Unit) {
        viewModel.verwijderd.collect { fotoId ->
            val antwoord = meldingen.showSnackbar(
                message = "Foto naar de prullenbak",
                actionLabel = "Ongedaan maken",
                withDismissAction = true,
            )
            if (antwoord == SnackbarResult.ActionPerformed) viewModel.herstel(fotoId)
        }
    }

    // Als de laatste foto van deze dag verdwijnt, valt er niets meer te tonen.
    val lijst = fotos
    LaunchedEffect(lijst) {
        if (lijst != null && lijst.isEmpty()) terug()
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(meldingen) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = langeDatum(dag).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = terug) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { ruimte ->
        if (lijst.isNullOrEmpty()) {
            Box(Modifier.fillMaxSize().padding(ruimte))
            return@Scaffold
        }

        val pagerStaat = rememberPagerState(initialPage = lijst.lastIndex) { lijst.size }
        val huidige = lijst.getOrNull(pagerStaat.currentPage) ?: lijst.last()
        val isFotoVanDeDag = huidige.id == (handmatigGekozen ?: standaardFotoId)
        val isStandaard = handmatigGekozen == null && huidige.id == standaardFotoId

        Column(
            modifier = Modifier.fillMaxSize().padding(top = ruimte.calculateTopPadding()),
        ) {
            HorizontalPager(
                state = pagerStaat,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { bladzijde ->
                val foto = lijst[bladzijde]
                FotoBeeld(
                    uri = foto.toonUri(),
                    maxZijde = 1600,
                    beschrijving = "Foto van ${tijdstip(foto.gemaaktOp)}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Onderregel(
                foto = huidige,
                positie = pagerStaat.currentPage + 1,
                aantal = lijst.size,
                isFotoVanDeDag = isFotoVanDeDag,
                isStandaard = isStandaard,
                wisselFotoVanDeDag = {
                    if (isFotoVanDeDag && handmatigGekozen != null) {
                        viewModel.zetTerugNaarStandaard()
                    } else {
                        viewModel.kiesAlsFotoVanDeDag(huidige.id)
                    }
                },
                deel = { deelFoto(context, huidige.toonUri()) },
                bewerk = { naarBewerken(huidige.id) },
                vervang = { naarOpname(serieId) },
                verwijder = { teVerwijderen = huidige },
            )
        }
    }

    teVerwijderen?.let { foto ->
        AlertDialog(
            onDismissRequest = { teVerwijderen = null },
            title = { Text("Foto verwijderen?") },
            text = {
                Text(
                    "De foto gaat naar de prullenbak en verdwijnt na 30 dagen definitief. " +
                        "Tot die tijd kun je hem terugzetten.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.verwijder(foto.id)
                    teVerwijderen = null
                }) { Text("Verwijderen") }
            },
            dismissButton = {
                TextButton(onClick = { teVerwijderen = null }) { Text("Annuleren") }
            },
        )
    }
}

@Composable
private fun Onderregel(
    foto: FotoEntiteit,
    positie: Int,
    aantal: Int,
    isFotoVanDeDag: Boolean,
    isStandaard: Boolean,
    wisselFotoVanDeDag: () -> Unit,
    deel: () -> Unit,
    bewerk: () -> Unit,
    vervang: () -> Unit,
    verwijder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append(tijdstip(foto.gemaaktOp))
                    if (aantal > 1) append(" · foto $positie van $aantal")
                    if (foto.bewerktUri != null) append(" · bewerkt")
                    if (foto.datumOnzeker) append(" · datum onzeker")
                },
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }

        if (isFotoVanDeDag) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (isStandaard) "standaard" else "gekozen als foto van de dag",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = wisselFotoVanDeDag) {
                Icon(
                    imageVector = if (isFotoVanDeDag) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFotoVanDeDag) {
                        "Terug naar de standaardkeuze"
                    } else {
                        "Kies als foto van de dag"
                    },
                    tint = Color.White,
                )
            }
            IconButton(onClick = deel) {
                Icon(Icons.Filled.Share, contentDescription = "Delen", tint = Color.White)
            }
            IconButton(onClick = bewerk) {
                Icon(Icons.Filled.Tune, contentDescription = "Bewerken", tint = Color.White)
            }
            IconButton(onClick = vervang) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Vervangen", tint = Color.White)
            }
            IconButton(onClick = verwijder) {
                Icon(Icons.Filled.Delete, contentDescription = "Verwijderen", tint = Color.White)
            }
        }
    }
}
