package nl.constantdynamics.everyday.ui.instellingen

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.backup.BackupBeheer
import nl.constantdynamics.everyday.data.backup.BackupStatus
import nl.constantdynamics.everyday.data.backup.HerstelBeheer
import nl.constantdynamics.everyday.data.SerieRepository
import nl.constantdynamics.everyday.data.media.MediaOpslag
import nl.constantdynamics.everyday.data.opslag.Instellingen
import nl.constantdynamics.everyday.data.opslag.Themakeuze

data class SerieOpslag(val naam: String, val mapNaam: String, val bytes: Long)

class InstellingenViewModel(
    private val instellingen: Instellingen,
    private val backupBeheer: BackupBeheer,
    private val herstelBeheer: HerstelBeheer,
    private val serieRepository: SerieRepository,
    private val mediaOpslag: MediaOpslag,
) : ViewModel() {

    val backupStatus: StateFlow<BackupStatus?> = backupBeheer.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ghostDekking: StateFlow<Float> = instellingen.ghostDekking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.4f)

    val thema: StateFlow<Themakeuze> = instellingen.thema
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Themakeuze.SYSTEEM)

    val dagStartUur: StateFlow<Int> = instellingen.dagStartUur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var bezig by mutableStateOf(false)
        private set

    /** Opslaggebruik per serie; wordt bij het openen van dit scherm opgehaald. */
    var opslagPerSerie by mutableStateOf<List<SerieOpslag>?>(null)
        private set

    init {
        vernieuwOpslaggebruik()
    }

    fun vernieuwOpslaggebruik() {
        viewModelScope.launch {
            opslagPerSerie = runCatching {
                serieRepository.alleSeries().map { serie ->
                    SerieOpslag(
                        naam = serie.naam,
                        mapNaam = serie.mapNaam,
                        bytes = mediaOpslag.opslaggebruik(serie.mapNaam),
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    private val _meldingen = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val meldingen = _meldingen.asSharedFlow()

    fun kiesBackupMap(uri: Uri) {
        viewModelScope.launch {
            bezig = true
            val gelukt = backupBeheer.kiesMap(uri)
            bezig = false
            _meldingen.tryEmit(
                if (gelukt) {
                    "Backupmap ingesteld. Alles wordt op de achtergrond gekopieerd."
                } else {
                    "Die map kon niet worden gebruikt. Kies een map waarin de app mag schrijven."
                },
            )
        }
    }

    fun kopieerAllesOpnieuw() {
        viewModelScope.launch {
            bezig = true
            backupBeheer.kopieerAllesOpnieuw()
            backupBeheer.verwerkWachtrij()
            bezig = false
            _meldingen.tryEmit("Alles staat weer in de wachtrij.")
        }
    }

    fun herstelUitBackupmap() {
        viewModelScope.launch {
            bezig = true
            val resultaat = runCatching { herstelBeheer.herstel() }.getOrNull()
            bezig = false
            _meldingen.tryEmit(
                when {
                    resultaat == null ->
                        "Geen leesbare everyday-metadata.json in de backupmap gevonden."
                    !resultaat.erIsIetsGedaan ->
                        "Niets te herstellen: alles uit de backupmap staat al in de app."
                    else -> buildString {
                        append("Hersteld: ${resultaat.fotosToegevoegd} foto's")
                        if (resultaat.seriesToegevoegd > 0) {
                            append(" in ${resultaat.seriesToegevoegd} nieuwe series")
                        }
                        if (resultaat.overgeslagen > 0) append(", ${resultaat.overgeslagen} overgeslagen")
                        if (resultaat.mislukt > 0) append(", ${resultaat.mislukt} niet teruggevonden")
                        append(".")
                    }
                },
            )
        }
    }

    fun zetGhostDekking(dekking: Float) {
        viewModelScope.launch { instellingen.zetGhostDekking(dekking) }
    }

    fun zetThema(keuze: Themakeuze) {
        viewModelScope.launch { instellingen.zetThema(keuze) }
    }

    fun zetDagStartUur(uur: Int) {
        viewModelScope.launch { instellingen.zetDagStartUur(uur) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                InstellingenViewModel(
                    instellingen = container.instellingen,
                    backupBeheer = container.backupBeheer,
                    herstelBeheer = container.herstelBeheer,
                    serieRepository = container.serieRepository,
                    mediaOpslag = container.mediaOpslag,
                )
            }
        }
    }
}
