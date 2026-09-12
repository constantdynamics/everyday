package nl.constantdynamics.everyday.ui.timelapse

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.FotoRepository
import nl.constantdynamics.everyday.data.SerieRepository
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import nl.constantdynamics.everyday.data.video.TimelapseMaker
import nl.constantdynamics.everyday.kern.TimelapseInstellingen

sealed interface RenderStand {
    data object Instellen : RenderStand
    data class Bezig(val voortgang: Float) : RenderStand
    data class Klaar(val uri: Uri) : RenderStand
    data class Mislukt(val bericht: String) : RenderStand
}

class TimelapseViewModel(
    private val serieId: Long,
    serieRepository: SerieRepository,
    fotoRepository: FotoRepository,
    private val timelapseMaker: TimelapseMaker,
) : ViewModel() {

    val serie: StateFlow<SerieEntiteit?> = serieRepository.serie(serieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** De foto-van-de-dag per dag, oudste eerst: dat is de volgorde van de video. */
    val fotos: StateFlow<List<FotoEntiteit>?> = fotoRepository.fotosVanDeDag(serieId)
        .map { lijst -> lijst.sortedBy { it.dagSleutel } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var instellingen by mutableStateOf(TimelapseInstellingen())
        private set

    var stand by mutableStateOf<RenderStand>(RenderStand.Instellen)
        private set

    private var renderTaak: Job? = null

    fun wijzig(nieuw: TimelapseInstellingen) {
        instellingen = nieuw
    }

    fun render() {
        val serieNu = serie.value ?: return
        val lijst = fotos.value.orEmpty()
        if (lijst.isEmpty() || renderTaak?.isActive == true) return

        stand = RenderStand.Bezig(0f)
        renderTaak = viewModelScope.launch {
            val uitkomst = runCatching {
                timelapseMaker.maak(serieNu, lijst, instellingen) { deel ->
                    stand = RenderStand.Bezig(deel)
                }
            }
            stand = when {
                uitkomst.isSuccess && uitkomst.getOrNull() != null ->
                    RenderStand.Klaar(uitkomst.getOrThrow()!!)
                uitkomst.isSuccess ->
                    RenderStand.Mislukt("De video kon niet worden aangemaakt.")
                else ->
                    RenderStand.Mislukt(
                        uitkomst.exceptionOrNull()?.message
                            ?: "Er ging iets mis tijdens het renderen.",
                    )
            }
        }
    }

    fun annuleer() {
        renderTaak?.cancel()
        renderTaak = null
        stand = RenderStand.Instellen
    }

    fun terugNaarInstellen() {
        stand = RenderStand.Instellen
    }

    override fun onCleared() {
        renderTaak?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer, serieId: Long) = viewModelFactory {
            initializer {
                TimelapseViewModel(
                    serieId = serieId,
                    serieRepository = container.serieRepository,
                    fotoRepository = container.fotoRepository,
                    timelapseMaker = container.timelapseMaker,
                )
            }
        }
    }
}
