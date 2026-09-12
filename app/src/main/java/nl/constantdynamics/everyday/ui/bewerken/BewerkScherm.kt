package nl.constantdynamics.everyday.ui.bewerken

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.kern.Bewerking
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MINIMALE_UITSNEDE = 0.1f

private enum class Greep { GEEN, VERPLAATS, LINKSBOVEN, RECHTSBOVEN, LINKSONDER, RECHTSONDER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BewerkScherm(
    container: AppContainer,
    fotoId: Long,
    terug: () -> Unit,
) {
    val viewModel: BewerkViewModel = viewModel(
        key = "bewerk-$fotoId",
        factory = BewerkViewModel.factory(container, fotoId),
    )
    val foto by viewModel.foto.collectAsStateWithLifecycle()
    val meldingen = remember { SnackbarHostState() }
    var toonHerstelVraag by remember { mutableStateOf(false) }

    LaunchedEffect(foto?.id) {
        foto?.let { viewModel.neemBestaandeOver(it) }
    }
    LaunchedEffect(foto?.origineelUri, viewModel.kwartslagen, viewModel.rechtzetHoek) {
        val origineel = foto?.origineelUri ?: return@LaunchedEffect
        viewModel.vernieuwVoorbeeld(android.net.Uri.parse(origineel))
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(meldingen) },
        topBar = {
            TopAppBar(
                title = { Text("Bewerken") },
                navigationIcon = {
                    IconButton(onClick = terug) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
                actions = {
                    if (foto?.bewerktUri != null) {
                        TextButton(
                            onClick = { toonHerstelVraag = true },
                            enabled = !viewModel.bezig,
                        ) { Text("Herstel origineel", color = Color.White) }
                    }
                    TextButton(
                        onClick = {
                            viewModel.bewaar { gelukt -> if (gelukt) terug() }
                        },
                        enabled = !viewModel.bezig,
                    ) { Text("Bewaren", color = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { ruimte ->
        Column(
            modifier = Modifier.fillMaxSize().padding(top = ruimte.calculateTopPadding()),
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val beeld = viewModel.voorbeeld
                if (beeld != null) {
                    // Het vlak krijgt precies de verhouding van het beeld, zodat de
                    // uitsnede in dezelfde coördinaten ligt als het beeld zelf.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(beeld.width.toFloat() / beeld.height.toFloat()),
                    ) {
                        Image(
                            bitmap = beeld.asImageBitmap(),
                            contentDescription = "Voorbeeld van de bewerking",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        UitsnedeLaag(
                            uitsnede = viewModel.uitsnede,
                            wijzig = viewModel::zetUitsnede,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Regelaars(
                rechtzetHoek = viewModel.rechtzetHoek,
                zetHoek = viewModel::zetRechtzetHoek,
                draaiLinks = viewModel::draaiLinks,
                draaiRechts = viewModel::draaiRechts,
                zetUitsnedeTerug = viewModel::zetUitsnedeTerug,
                uitsnedeIsVolledig = viewModel.uitsnede == Rect(0f, 0f, 1f, 1f),
            )
        }
    }

    if (toonHerstelVraag) {
        AlertDialog(
            onDismissRequest = { toonHerstelVraag = false },
            title = { Text("Origineel herstellen?") },
            text = {
                Text(
                    "De bewerkte versie wordt verwijderd en overal wordt weer het " +
                        "origineel getoond. Het originele bestand is nooit gewijzigd.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    toonHerstelVraag = false
                    viewModel.herstelOrigineel { terug() }
                }) { Text("Herstellen") }
            },
            dismissButton = {
                TextButton(onClick = { toonHerstelVraag = false }) { Text("Annuleren") }
            },
        )
    }
}

@Composable
private fun UitsnedeLaag(
    uitsnede: Rect,
    wijzig: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val huidige by rememberUpdatedState(uitsnede)
    var greep by remember { mutableStateOf(Greep.GEEN) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { punt ->
                    greep = bepaalGreep(punt, huidige, Size(size.width.toFloat(), size.height.toFloat()))
                },
                onDragEnd = { greep = Greep.GEEN },
                onDragCancel = { greep = Greep.GEEN },
                onDrag = { verandering, sleep ->
                    verandering.consume()
                    wijzig(
                        pasAan(
                            huidige,
                            greep,
                            Offset(sleep.x / size.width, sleep.y / size.height),
                        ),
                    )
                },
            )
        },
    ) {
        val vlak = Rect(
            left = uitsnede.left * size.width,
            top = uitsnede.top * size.height,
            right = uitsnede.right * size.width,
            bottom = uitsnede.bottom * size.height,
        )
        val sluier = Color.Black.copy(alpha = 0.55f)
        drawRect(sluier, size = Size(size.width, vlak.top))
        drawRect(sluier, topLeft = Offset(0f, vlak.bottom), size = Size(size.width, size.height - vlak.bottom))
        drawRect(sluier, topLeft = Offset(0f, vlak.top), size = Size(vlak.left, vlak.height))
        drawRect(
            sluier,
            topLeft = Offset(vlak.right, vlak.top),
            size = Size(size.width - vlak.right, vlak.height),
        )

        drawRect(
            color = Color.White,
            topLeft = Offset(vlak.left, vlak.top),
            size = Size(vlak.width, vlak.height),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
        )

        // Derdenlijnen helpen bij het uitlijnen.
        val lijn = Color.White.copy(alpha = 0.35f)
        for (deel in 1..2) {
            val x = vlak.left + vlak.width * deel / 3f
            val y = vlak.top + vlak.height * deel / 3f
            drawLine(lijn, Offset(x, vlak.top), Offset(x, vlak.bottom), strokeWidth = 1f)
            drawLine(lijn, Offset(vlak.left, y), Offset(vlak.right, y), strokeWidth = 1f)
        }

        val greepLengte = 28f
        val dikte = 5f
        listOf(
            Offset(vlak.left, vlak.top) to Pair(1f, 1f),
            Offset(vlak.right, vlak.top) to Pair(-1f, 1f),
            Offset(vlak.left, vlak.bottom) to Pair(1f, -1f),
            Offset(vlak.right, vlak.bottom) to Pair(-1f, -1f),
        ).forEach { (hoek, richting) ->
            drawLine(
                Color.White,
                hoek,
                Offset(hoek.x + greepLengte * richting.first, hoek.y),
                strokeWidth = dikte,
            )
            drawLine(
                Color.White,
                hoek,
                Offset(hoek.x, hoek.y + greepLengte * richting.second),
                strokeWidth = dikte,
            )
        }
    }
}

private fun bepaalGreep(punt: Offset, uitsnede: Rect, vlak: Size): Greep {
    if (vlak.width <= 0f || vlak.height <= 0f) return Greep.GEEN
    val raakAfstand = minOf(vlak.width, vlak.height) * 0.12f
    val hoeken = listOf(
        Greep.LINKSBOVEN to Offset(uitsnede.left * vlak.width, uitsnede.top * vlak.height),
        Greep.RECHTSBOVEN to Offset(uitsnede.right * vlak.width, uitsnede.top * vlak.height),
        Greep.LINKSONDER to Offset(uitsnede.left * vlak.width, uitsnede.bottom * vlak.height),
        Greep.RECHTSONDER to Offset(uitsnede.right * vlak.width, uitsnede.bottom * vlak.height),
    )
    val dichtstbij = hoeken.minByOrNull { (_, hoek) -> (hoek - punt).getDistance() }
    if (dichtstbij != null && (dichtstbij.second - punt).getDistance() <= raakAfstand) {
        return dichtstbij.first
    }
    val binnen = punt.x in (uitsnede.left * vlak.width)..(uitsnede.right * vlak.width) &&
        punt.y in (uitsnede.top * vlak.height)..(uitsnede.bottom * vlak.height)
    return if (binnen) Greep.VERPLAATS else Greep.GEEN
}

private fun pasAan(uitsnede: Rect, greep: Greep, sleep: Offset): Rect = when (greep) {
    Greep.GEEN -> uitsnede
    Greep.VERPLAATS -> {
        val dx = sleep.x.coerceIn(-uitsnede.left, 1f - uitsnede.right)
        val dy = sleep.y.coerceIn(-uitsnede.top, 1f - uitsnede.bottom)
        Rect(uitsnede.left + dx, uitsnede.top + dy, uitsnede.right + dx, uitsnede.bottom + dy)
    }
    Greep.LINKSBOVEN -> uitsnede.copy(
        left = (uitsnede.left + sleep.x).coerceIn(0f, uitsnede.right - MINIMALE_UITSNEDE),
        top = (uitsnede.top + sleep.y).coerceIn(0f, uitsnede.bottom - MINIMALE_UITSNEDE),
    )
    Greep.RECHTSBOVEN -> uitsnede.copy(
        right = (uitsnede.right + sleep.x).coerceIn(uitsnede.left + MINIMALE_UITSNEDE, 1f),
        top = (uitsnede.top + sleep.y).coerceIn(0f, uitsnede.bottom - MINIMALE_UITSNEDE),
    )
    Greep.LINKSONDER -> uitsnede.copy(
        left = (uitsnede.left + sleep.x).coerceIn(0f, uitsnede.right - MINIMALE_UITSNEDE),
        bottom = (uitsnede.bottom + sleep.y).coerceIn(uitsnede.top + MINIMALE_UITSNEDE, 1f),
    )
    Greep.RECHTSONDER -> uitsnede.copy(
        right = (uitsnede.right + sleep.x).coerceIn(uitsnede.left + MINIMALE_UITSNEDE, 1f),
        bottom = (uitsnede.bottom + sleep.y).coerceIn(uitsnede.top + MINIMALE_UITSNEDE, 1f),
    )
}

@Composable
private fun Regelaars(
    rechtzetHoek: Float,
    zetHoek: (Float) -> Unit,
    draaiLinks: () -> Unit,
    draaiRechts: () -> Unit,
    zetUitsnedeTerug: () -> Unit,
    uitsnedeIsVolledig: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Rechtzetten",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(96.dp),
            )
            Slider(
                value = rechtzetHoek,
                onValueChange = zetHoek,
                valueRange = -Bewerking.MAX_RECHTZET_HOEK..Bewerking.MAX_RECHTZET_HOEK,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
            Text(
                text = "${if (rechtzetHoek >= 0) "+" else ""}${rechtzetHoek.roundToInt()}°",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = draaiLinks) {
                Icon(Icons.Filled.RotateLeft, contentDescription = "Kwartslag naar links", tint = Color.White)
            }
            IconButton(onClick = draaiRechts) {
                Icon(Icons.Filled.RotateRight, contentDescription = "Kwartslag naar rechts", tint = Color.White)
            }
            IconButton(onClick = zetUitsnedeTerug, enabled = !uitsnedeIsVolledig) {
                Icon(
                    Icons.Filled.Crop,
                    contentDescription = "Uitsnede terugzetten",
                    tint = if (uitsnedeIsVolledig) Color.Gray else Color.White,
                )
            }
        }

        if (abs(rechtzetHoek) >= 0.01f) {
            Text(
                text = "Bij rechtzetten wordt automatisch zo min mogelijk weggesneden, zodat er geen lege hoeken overblijven.",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
