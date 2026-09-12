package nl.constantdynamics.everyday.ui.dag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.FotoRepository
import nl.constantdynamics.everyday.data.SerieRepository
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import java.time.LocalDate

class DagViewModel(
    private val serieId: Long,
    val dag: LocalDate,
    serieRepository: SerieRepository,
    private val fotoRepository: FotoRepository,
) : ViewModel() {

    val serie: StateFlow<SerieEntiteit?> = serieRepository.serie(serieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Alle foto's van deze dag, oudste eerst. */
    val fotos: StateFlow<List<FotoEntiteit>?> = fotoRepository.fotosOpDag(serieId, dag)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Gevuld zodra er handmatig een andere foto dan de laatste is gekozen. */
    val handmatigGekozen: StateFlow<Long?> = fotoRepository.gekozenFotoId(serieId, dag)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Zonder handmatige keuze is de laatste foto van de dag de foto-van-de-dag. */
    val standaardFotoId: StateFlow<Long?> = fotoRepository.fotosOpDag(serieId, dag)
        .map { fotos -> fotos.lastOrNull()?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _verwijderd = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val verwijderd = _verwijderd.asSharedFlow()

    fun kiesAlsFotoVanDeDag(fotoId: Long) {
        viewModelScope.launch { fotoRepository.kiesFotoVanDeDag(serieId, dag, fotoId) }
    }

    fun zetTerugNaarStandaard() {
        viewModelScope.launch { fotoRepository.zetTerugNaarStandaard(serieId, dag) }
    }

    fun verwijder(fotoId: Long) {
        viewModelScope.launch {
            fotoRepository.verwijder(fotoId)
            _verwijderd.tryEmit(fotoId)
        }
    }

    fun herstel(fotoId: Long) {
        viewModelScope.launch { fotoRepository.herstel(fotoId) }
    }

    companion object {
        fun factory(container: AppContainer, serieId: Long, dag: LocalDate) = viewModelFactory {
            initializer {
                DagViewModel(serieId, dag, container.serieRepository, container.fotoRepository)
            }
        }
    }
}
