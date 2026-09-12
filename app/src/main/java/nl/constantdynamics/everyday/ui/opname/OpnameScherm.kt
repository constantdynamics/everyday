package nl.constantdynamics.everyday.ui.opname

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.media.cameraAanbieder
import nl.constantdynamics.everyday.data.toonUri
import nl.constantdynamics.everyday.kern.LensRichting
import nl.constantdynamics.everyday.ui.onderdelen.FotoBeeld
import nl.constantdynamics.everyday.ui.onderdelen.LegeStaat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpnameScherm(
    container: AppContainer,
    serieId: Long,
    terug: () -> Unit,
    naarGalerij: (Long) -> Unit,
) {
    val viewModel: OpnameViewModel = viewModel(
        key = "opname-$serieId",
        factory = OpnameViewModel.factory(container, serieId),
    )
    val context = LocalContext.current
    val levensloopEigenaar = LocalLifecycleOwner.current
    val serie by viewModel.serie.collectAsStateWithLifecycle()
    val laatsteFoto by viewModel.laatsteFoto.collectAsStateWithLifecycle()
    val ghost by viewModel.ghost.collectAsStateWithLifecycle()
    val ghostDekking by viewModel.ghostDekking.collectAsStateWithLifecycle()
    val meldingen = remember { SnackbarHostState() }

    var heeftToestemming by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var toestemmingGevraagd by remember { mutableStateOf(false) }
    val toestemmingVrager = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { toegekend -> heeftToestemming = toegekend }

    LaunchedEffect(Unit) {
        if (!heeftToestemming && !toestemmingGevraagd) {
            toestemmingGevraagd = true
            toestemmingVrager.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.meldingen.collect { meldingen.showSnackbar(it) }
    }

    val opnemer = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val voorbeeldWeergave = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    var aanbieder by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var beeldVerhouding by remember { mutableStateOf<Float?>(null) }
    var overlayVerborgen by remember { mutableStateOf(false) }
    val lensRichting = serie?.lensRichting

    LaunchedEffect(heeftToestemming, lensRichting) {
        if (!heeftToestemming || lensRichting == null) return@LaunchedEffect
        val cameraAanbieder = runCatching { context.cameraAanbieder() }.getOrNull() ?: return@LaunchedEffect
        aanbieder = cameraAanbieder
        val voorbeeld = Preview.Builder().build()
        voorbeeld.setSurfaceProvider(voorbeeldWeergave.surfaceProvider)
        val kiezer = when (lensRichting) {
            LensRichting.VOOR -> CameraSelector.DEFAULT_FRONT_CAMERA
            LensRichting.ACHTER -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        val gebonden = runCatching {
            cameraAanbieder.unbindAll()
            cameraAanbieder.bindToLifecycle(levensloopEigenaar, kiezer, voorbeeld, opnemer)
        }.isSuccess
        if (!gebonden) return@LaunchedEffect

        // De beeldverhouding is pas bekend zodra de camera een surface heeft. We geven
        // het voorbeeld precies die verhouding, zodat de ghost overlay op exact
        // hetzelfde rechthoekje ligt als het live beeld.
        repeat(20) {
            val info = runCatching { voorbeeld.resolutionInfo }.getOrNull()
            if (info != null) {
                val gedraaid = info.rotationDegrees == 90 || info.rotationDegrees == 270
                val breedte = if (gedraaid) info.resolution.height else info.resolution.width
                val hoogte = if (gedraaid) info.resolution.width else info.resolution.height
                if (breedte > 0 && hoogte > 0) {
                    beeldVerhouding = breedte.toFloat() / hoogte.toFloat()
                    return@LaunchedEffect
                }
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose { aanbieder?.unbindAll() }
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(meldingen) },
        topBar = {
            TopAppBar(
                title = { Text(serie?.naam ?: "") },
                navigationIcon = {
                    IconButton(onClick = terug) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
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
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (heeftToestemming) {
                    Voorbeeld(
                        weergave = voorbeeldWeergave,
                        beeldVerhouding = beeldVerhouding,
                        ghost = ghost,
                        ghostDekking = if (overlayVerborgen) 0f else ghostDekking,
                        spiegelGhost = lensRichting == LensRichting.VOOR,
                        zetVerborgen = { overlayVerborgen = it },
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LegeStaat(
                            titel = "Geen toegang tot de camera",
                            uitleg = "everyday heeft de camera nodig om foto's te maken. Je kunt de toegang aanzetten bij de app-instellingen van je toestel.",
                        )
                        TextButton(onClick = { toestemmingVrager.launch(Manifest.permission.CAMERA) }) {
                            Text("Opnieuw vragen")
                        }
                    }
                }
            }

            if (ghost != null && heeftToestemming) {
                GhostRegelaar(
                    dekking = ghostDekking,
                    wijzig = viewModel::zetGhostDekking,
                )
            }

            Balk(
                bezig = viewModel.bezig,
                laatsteFotoUri = laatsteFoto?.toonUri(),
                opnameMogelijk = heeftToestemming && serie != null,
                maakFoto = { viewModel.maakFoto(opnemer) },
                wisselLens = viewModel::wisselLens,
                openGalerij = { naarGalerij(serieId) },
            )
        }
    }
}

/**
 * Het live beeld met daarover de vorige foto. Beide liggen in hetzelfde rechthoekje,
 * zodat een foto met een andere beeldverhouding gecentreerd wordt ingepast en nooit
 * wordt uitgerekt. Ingedrukt houden haalt de overlay even weg.
 */
@Composable
private fun Voorbeeld(
    weergave: PreviewView,
    beeldVerhouding: Float?,
    ghost: Bitmap?,
    ghostDekking: Float,
    spiegelGhost: Boolean,
    zetVerborgen: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .then(
                if (beeldVerhouding != null) {
                    Modifier.fillMaxWidth().aspectRatio(beeldVerhouding)
                } else {
                    Modifier.fillMaxSize()
                },
            )
            .pointerInput(ghost) {
                detectTapGestures(
                    onPress = {
                        if (ghost == null) return@detectTapGestures
                        zetVerborgen(true)
                        tryAwaitRelease()
                        zetVerborgen(false)
                    },
                )
            },
    ) {
        AndroidView(factory = { weergave }, modifier = Modifier.fillMaxSize())

        if (ghost != null && ghostDekking > 0f) {
            Image(
                bitmap = ghost.asImageBitmap(),
                contentDescription = "Vorige foto als hulplijn",
                contentScale = ContentScale.Fit,
                alpha = ghostDekking,
                modifier = Modifier
                    .fillMaxSize()
                    // De voorcamera toont een gespiegeld beeld; de opgeslagen foto is
                    // niet gespiegeld, dus de overlay wordt hier gespiegeld getoond.
                    .graphicsLayer { if (spiegelGhost) scaleX = -1f },
            )
        }
    }
}

@Composable
private fun GhostRegelaar(dekking: Float, wijzig: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.Layers,
            contentDescription = "Doorzichtigheid van de vorige foto",
            tint = Color.White,
        )
        Slider(
            value = dekking,
            onValueChange = wijzig,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
        Text(
            text = "${(dekking * 100).roundToInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
private fun Balk(
    bezig: Boolean,
    laatsteFotoUri: Uri?,
    opnameMogelijk: Boolean,
    maakFoto: () -> Unit,
    wisselLens: () -> Unit,
    openGalerij: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (laatsteFotoUri != null) {
                FotoBeeld(
                    uri = laatsteFotoUri,
                    maxZijde = 240,
                    beschrijving = "Naar de galerij",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = openGalerij),
                )
            }
        }

        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(width = 3.dp, color = Color.White, shape = CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (opnameMogelijk) Color.White else Color.DarkGray)
                    .clickable(enabled = opnameMogelijk && !bezig, onClick = maakFoto),
            )
            if (bezig) {
                CircularProgressIndicator(
                    modifier = Modifier.size(76.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = wisselLens) {
                Icon(
                    Icons.Filled.Cameraswitch,
                    contentDescription = "Wissel tussen voor- en achtercamera",
                    tint = Color.White,
                )
            }
        }
    }
}
