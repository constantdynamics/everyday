package nl.constantdynamics.dagelijks.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.constantdynamics.dagelijks.storage.StorageAccess
import nl.constantdynamics.dagelijks.ui.LocalServices
import nl.constantdynamics.dagelijks.ui.components.AppBackground
import nl.constantdynamics.dagelijks.ui.components.AppButton
import nl.constantdynamics.dagelijks.ui.components.AppCard
import nl.constantdynamics.dagelijks.ui.components.AppIcons
import nl.constantdynamics.dagelijks.ui.components.ButtonKind
import nl.constantdynamics.dagelijks.ui.components.ScreenHeader
import nl.constantdynamics.dagelijks.ui.components.SectionLabel
import nl.constantdynamics.dagelijks.ui.theme.AppColors

/** Where the photos are, how to back them up, what the app can and cannot do, and the licences. */
@Composable
fun AboutScreen(location: String, onBack: () -> Unit) {
    val services = LocalServices.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var problem by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(PickLocalFolder()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            problem = when (services.chooseFolder(uri)) {
                is StorageAccess.PickResult.Success -> null
                StorageAccess.PickResult.NotLocalStorage -> "Dat is geen map op je telefoon zelf. Er is niets veranderd."
                is StorageAccess.PickResult.Failed -> "Die map kon niet worden gebruikt. Er is niets veranderd."
            }
        }
    }

    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    AppBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(title = "Over de app", onBack = onBack, modifier = Modifier.padding(bottom = 6.dp))

            Section("Waar staan je foto's?") {
                Paragraph("In de map “$location” op je telefoon. Elk project heeft daar een eigen map, met één foto per dag: 2026-11-23.jpg, 2026-11-24.jpg, enzovoort.")
                Paragraph("In de map staat een bestandje “.nomedia”. Daardoor slaan je galerij en Google Foto's de map over, en wordt er niets gesynchroniseerd.")
                problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Coral) }
                AppButton(
                    "Andere map kiezen",
                    onClick = {
                        runCatching { picker.launch(StorageAccess.suggestedStartFolder) }
                            .onFailure { problem = "De mappenkiezer van je telefoon kon niet worden geopend." }
                    },
                    icon = AppIcons.Folder,
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Reservekopie maken") {
                Paragraph("Sluit je telefoon met een usb-kabel aan op je computer en kies op je telefoon “Bestandsoverdracht”. Kopieer daarna de hele map “$location” naar je computer.")
                Paragraph("Na een nieuwe installatie van de app kies je dezelfde map opnieuw. Al je projecten, foto's en instellingen zijn dan meteen terug.")
            }

            Section("Privacy") {
                Paragraph("Deze app kan niet op internet: hij heeft daar geen toestemming voor en vraagt die ook nooit. De enige toestemming is die voor de camera.")
                Paragraph("Er zijn geen accounts, geen advertenties, geen statistieken en geen meldingen. In je foto's wordt alleen de datum en tijd bewaard, geen locatie.")
            }

            Section("Licenties") {
                Paragraph("Lettertypen: Unbounded (The Unbounded Project Authors) en Outfit (The Outfit Project Authors), allebei onder de SIL Open Font License 1.1.")
                Paragraph("Bouwstenen: AndroidX, Jetpack Compose en CameraX van Google en Kotlin van JetBrains, Apache-licentie 2.0.")
                Paragraph("Versie $version")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(title)
            content()
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}
