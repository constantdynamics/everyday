package nl.constantdynamics.everyday.ui.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.SerieRepository
import nl.constantdynamics.everyday.data.backup.BackupBeheer
import nl.constantdynamics.everyday.data.backup.BackupStatus
import nl.constantdynamics.everyday.data.db.SerieOverzichtRij

class StartViewModel(
    private val serieRepository: SerieRepository,
    backupBeheer: BackupBeheer,
) : ViewModel() {

    val series: StateFlow<List<SerieOverzichtRij>?> = serieRepository.overzicht()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Alleen bedoeld om een achterstand te melden die echt aandacht verdient. */
    val backupStatus: StateFlow<BackupStatus?> = backupBeheer.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun maakSerie(naam: String, klaar: (Long) -> Unit = {}) {
        if (naam.isBlank()) return
        viewModelScope.launch { klaar(serieRepository.maakSerie(naam)) }
    }

    fun hernoem(serieId: Long, naam: String) {
        if (naam.isBlank()) return
        viewModelScope.launch { serieRepository.hernoem(serieId, naam) }
    }

    fun archiveer(serieId: Long) {
        viewModelScope.launch { serieRepository.zetGearchiveerd(serieId, true) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { StartViewModel(container.serieRepository, container.backupBeheer) }
        }
    }
}
