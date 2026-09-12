package nl.constantdynamics.everyday.ui.timelapse

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.kern.Beeldverhouding
import nl.constantdynamics.everyday.kern.DatumOpmaak
import nl.constantdynamics.everyday.kern.Overgang
import nl.constantdynamics.everyday.kern.Passing
import nl.constantdynamics.everyday.kern.Resolutie
import nl.constantdynamics.everyday.kern.Snijpunt
import nl.constantdynamics.everyday.kern.StempelPositie
import nl.constantdynamics.everyday.kern.TimelapseInstellingen
import nl.constantdynamics.everyday.kern.duurSeconden
import nl.constantdynamics.everyday.kern.fpsVoorDuur
import nl.constantdynamics.everyday.kern.videoAfmeting
import nl.constantdynamics.everyday.ui.aantalFotos
import nl.constantdynamics.everyday.ui.deelVideo
import kotlin.math.roundToInt

private val MUZIEK_SOORTEN = arrayOf("audio/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelapseScherm(
    container: AppContainer,
    serieId: Long,
    terug: () -> Unit,
) {
    val viewModel: TimelapseViewModel = viewModel(
        key = "timelapse-$serieId",
        factory = TimelapseViewModel.factory(container, serieId),
    )
    val context = LocalContext.current
    val serie by viewModel.serie.collectAsStateWithLifecycle()
    val fotos by viewModel.fotos.collectAsStateWithLifecycle()
    val aantal = fotos?.size ?: 0

    val muziekKiezer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.kiesMuziek(uri) }

    HoudSchermAan(viewModel.stand is RenderStand.Bezig)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timelapse van ${serie?.naam ?: ""}") },
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
            when (val stand = viewModel.stand) {
                RenderStand.Instellen -> Instellen(
                    aantal = aantal,
                    instellingen = viewModel.instellingen,
                    muziekNaam = viewModel.muziekNaam,
                    wijzig = viewModel::wijzig,
                    kiesMuziek = { muziekKiezer.launch(MUZIEK_SOORTEN) },
                    wisMuziek = viewModel::wisMuziek,
                    render = viewModel::render,
                )

                is RenderStand.Bezig -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                ) {
                    Text("Bezig met renderen…", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { stand.voortgang },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${(stand.voortgang * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Blijf in de app; het scherm blijft zolang aan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = viewModel::annuleer) { Text("Annuleren") }
                }

                is RenderStand.Klaar -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                ) {
                    Text("De video staat klaar", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Je vindt hem in je galerij onder ${container.mediaOpslag.videoZichtbaarPad()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { deelVideo(context, stand.uri) }) { Text("Delen") }
                    OutlinedButton(onClick = viewModel::terugNaarInstellen) {
                        Text("Opnieuw met andere instellingen")
                    }
                }

                is RenderStand.Mislukt -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                ) {
                    Text("Renderen is niet gelukt", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stand.bericht,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = viewModel::terugNaarInstellen) { Text("Terug naar instellingen") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Instellen(
    aantal: Int,
    instellingen: TimelapseInstellingen,
    muziekNaam: String?,
    wijzig: (TimelapseInstellingen) -> Unit,
    kiesMuziek: () -> Unit,
    wisMuziek: () -> Unit,
    render: () -> Unit,
) {
    var viaLengte by remember { mutableStateOf(false) }
    val duur = duurSeconden(aantal, instellingen.fps)
    val (breedte, hoogte) = videoAfmeting(instellingen.beeldverhouding, instellingen.resolutie)

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${aantalFotos(aantal)} · ${instellingen.fps} fps · ${"%.1f".format(duur)} s",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${breedte}×$hoogte, één foto per dag",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Kop("Snelheid")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !viaLengte,
            onClick = { viaLengte = false },
            label = { Text("Per fps") },
        )
        FilterChip(
            selected = viaLengte,
            onClick = { viaLengte = true },
            label = { Text("Op lengte") },
        )
    }
    if (viaLengte) {
        Column {
            Text(
                text = "Totale lengte: ${"%.1f".format(duur)} seconden",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = duur.coerceIn(1f, 60f),
                onValueChange = { seconden ->
                    wijzig(instellingen.copy(fps = fpsVoorDuur(aantal, seconden)))
                },
                valueRange = 1f..60f,
            )
        }
    } else {
        KeuzeRij(
            opties = TimelapseInstellingen.KEUZE_FPS,
            gekozen = instellingen.fps,
            label = { "$it fps" },
            kies = { wijzig(instellingen.copy(fps = it)) },
        )
    }

    Kop("Beeldverhouding")
    KeuzeRij(
        opties = Beeldverhouding.entries,
        gekozen = instellingen.beeldverhouding,
        label = { it.omschrijving },
        kies = { wijzig(instellingen.copy(beeldverhouding = it)) },
    )

    Kop("Resolutie")
    KeuzeRij(
        opties = Resolutie.entries,
        gekozen = instellingen.resolutie,
        label = { it.omschrijving },
        kies = { wijzig(instellingen.copy(resolutie = it)) },
    )

    Kop("Passing")
    KeuzeRij(
        opties = Passing.entries,
        gekozen = instellingen.passing,
        label = { if (it == Passing.VULLEN) "Vullen" else "Passend" },
        kies = { wijzig(instellingen.copy(passing = it)) },
    )
    if (instellingen.passing == Passing.VULLEN) {
        Text(
            text = "Vullen snijdt weg wat niet past. Bij portretten zit het gezicht meestal wat hoger dan het midden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        KeuzeRij(
            opties = Snijpunt.entries,
            gekozen = instellingen.snijpunt,
            label = {
                when (it) {
                    Snijpunt.BOVEN -> "Boven"
                    Snijpunt.MIDDEN -> "Midden"
                    Snijpunt.ONDER -> "Onder"
                }
            },
            kies = { wijzig(instellingen.copy(snijpunt = it)) },
        )
    } else {
        Text(
            text = "Passend laat de hele foto zien, met zwarte balken waar hij niet past.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Kop("Overgang")
    KeuzeRij(
        opties = Overgang.entries,
        gekozen = instellingen.overgang,
        label = { if (it == Overgang.CUT) "Harde cut" else "Crossfade" },
        kies = { wijzig(instellingen.copy(overgang = it)) },
    )
    if (instellingen.overgang == Overgang.CROSSFADE) {
        Column {
            Text(
                text = "Overgangsduur: ${"%.2f".format(instellingen.overgangsduurSeconden)} s",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = instellingen.overgangsduurSeconden,
                onValueChange = { wijzig(instellingen.copy(overgangsduurSeconden = it)) },
                valueRange = 0.05f..1f,
            )
        }
    }

    Kop("Datumstempel")
    KeuzeRij(
        opties = StempelPositie.entries,
        gekozen = instellingen.stempelPositie,
        label = {
            when (it) {
                StempelPositie.GEEN -> "Uit"
                StempelPositie.LINKSBOVEN -> "Linksboven"
                StempelPositie.RECHTSBOVEN -> "Rechtsboven"
                StempelPositie.LINKSONDER -> "Linksonder"
                StempelPositie.RECHTSONDER -> "Rechtsonder"
                StempelPositie.ONDER_MIDDEN -> "Onder midden"
            }
        },
        kies = { wijzig(instellingen.copy(stempelPositie = it)) },
    )
    if (instellingen.stempelPositie != StempelPositie.GEEN) {
        KeuzeRij(
            opties = DatumOpmaak.entries,
            gekozen = instellingen.datumOpmaak,
            label = {
                when (it) {
                    DatumOpmaak.CIJFERS -> "11-09-2026"
                    DatumOpmaak.KORTE_MAAND -> "11 sep 2026"
                    DatumOpmaak.LANGE_MAAND -> "11 september 2026"
                    DatumOpmaak.ISO -> "2026-09-11"
                }
            },
            kies = { wijzig(instellingen.copy(datumOpmaak = it)) },
        )
        Column {
            Text(
                text = "Tekstgrootte: ${(instellingen.stempelGrootte * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = instellingen.stempelGrootte,
                onValueChange = { wijzig(instellingen.copy(stempelGrootte = it)) },
                valueRange = 0.5f..2f,
            )
        }
    }

    Kop("Muziek")
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = muziekNaam ?: "Geen muziek gekozen",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "De muziek wordt automatisch ingekort op de lengte van de video, " +
                    "met een fade-out van anderhalve seconde aan het eind.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = kiesMuziek) {
                    Text(if (instellingen.muziekUri == null) "Bestand kiezen" else "Ander bestand")
                }
                if (instellingen.muziekUri != null) {
                    OutlinedButton(onClick = wisMuziek) { Text("Verwijderen") }
                }
            }
            if (instellingen.muziekUri != null) {
                Text(
                    text = "Volume: ${(instellingen.muziekVolume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = instellingen.muziekVolume,
                    onValueChange = { wijzig(instellingen.copy(muziekVolume = it)) },
                    valueRange = 0f..1f,
                )
            }
        }
    }

    Button(
        onClick = render,
        enabled = aantal > 0,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    ) {
        Text(if (aantal > 0) "Video maken" else "Nog geen foto's in deze serie")
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> KeuzeRij(
    opties: List<T>,
    gekozen: T,
    label: (T) -> String,
    kies: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opties.forEach { optie ->
            FilterChip(
                selected = optie == gekozen,
                onClick = { kies(optie) },
                label = { Text(label(optie)) },
            )
        }
    }
}

/** Tijdens het renderen mag het scherm niet uitvallen; een foreground service is niet nodig. */
@Composable
private fun HoudSchermAan(actief: Boolean) {
    val context = LocalContext.current
    DisposableEffect(actief) {
        val venster = (context as? Activity)?.window
        if (actief) {
            venster?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            venster?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { venster?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
