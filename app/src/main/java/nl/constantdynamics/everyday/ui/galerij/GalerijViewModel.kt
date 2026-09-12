package nl.constantdynamics.everyday.ui.galerij

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.FotoRepository
import nl.constantdynamics.everyday.data.SerieRepository
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import java.time.LocalDate
import java.time.YearMonth

data class MaandGroep(val maand: YearMonth, val fotos: List<FotoEntiteit>) {
    val eersteDag: LocalDate get() = maand.atDay(1)
}

class GalerijViewModel(
    serieId: Long,
    serieRepository: SerieRepository,
    fotoRepository: FotoRepository,
) : ViewModel() {

    val serie: StateFlow<SerieEntiteit?> = serieRepository.serie(serieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Foto-van-de-dag, nieuwste boven, gegroepeerd per maand. Lege dagen bestaan niet. */
    val maanden: StateFlow<List<MaandGroep>?> = fotoRepository.fotosVanDeDag(serieId)
        .map { fotos ->
            fotos.groupBy { YearMonth.from(it.dagSleutel) }
                .map { (maand, lijst) -> MaandGroep(maand, lijst) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(container: AppContainer, serieId: Long) = viewModelFactory {
            initializer {
                GalerijViewModel(serieId, container.serieRepository, container.fotoRepository)
            }
        }
    }
}
