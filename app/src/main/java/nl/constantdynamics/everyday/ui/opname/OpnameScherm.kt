package nl.constantdynamics.everyday.ui.opname

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.media.cameraAanbieder
import nl.constantdynamics.everyday.data.toonUri
import nl.constantdynamics.everyday.kern.LensRichting
import nl.constantdynamics.everyday.ui.onderdelen.FotoBeeld
import nl.constantdynamics.everyday.ui.onderdelen.LegeStaat

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
        runCatching {
            cameraAanbieder.unbindAll()
            cameraAanbieder.bindToLifecycle(levensloopEigenaar, kiezer, voorbeeld, opnemer)
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
                    AndroidView(
                        factory = { voorbeeldWeergave },
                        modifier = Modifier.fillMaxSize(),
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

@Composable
private fun Balk(
    bezig: Boolean,
    laatsteFotoUri: android.net.Uri?,
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
