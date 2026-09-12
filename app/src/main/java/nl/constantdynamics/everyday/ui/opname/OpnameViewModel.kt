package nl.constantdynamics.everyday.ui.opname

import androidx.camera.core.ImageCapture
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.FotoRepository
import nl.constantdynamics.everyday.data.SerieRepository
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import nl.constantdynamics.everyday.data.media.MediaOpslag
import nl.constantdynamics.everyday.data.media.maakFoto
import nl.constantdynamics.everyday.kern.LensRichting
import java.time.Instant

class OpnameViewModel(
    private val serieId: Long,
    private val serieRepository: SerieRepository,
    private val fotoRepository: FotoRepository,
    private val mediaOpslag: MediaOpslag,
) : ViewModel() {

    val serie: StateFlow<SerieEntiteit?> = serieRepository.serie(serieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** De allerlaatst gemaakte foto van deze serie: bron voor de ghost overlay. */
    val laatsteFoto: StateFlow<FotoEntiteit?> = fotoRepository.laatsteFoto(serieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var bezig by mutableStateOf(false)
        private set

    private val _meldingen = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val meldingen = _meldingen.asSharedFlow()

    fun wisselLens() {
        val huidige = serie.value ?: return
        val nieuwe = when (huidige.lensRichting) {
            LensRichting.VOOR -> LensRichting.ACHTER
            LensRichting.ACHTER -> LensRichting.VOOR
        }
        viewModelScope.launch { serieRepository.zetLensRichting(serieId, nieuwe) }
    }

    fun maakFoto(opnemer: ImageCapture) {
        val huidige = serie.value ?: return
        if (bezig) return
        bezig = true
        viewModelScope.launch {
            val moment = Instant.now()
            val bestandsnaam = mediaOpslag.bestandsnaam(huidige.mapNaam, moment)
            val resultaat = runCatching {
                val uri = opnemer.maakFoto(
                    mediaOpslag.opnameOpties(huidige.mapNaam, bestandsnaam, moment),
                    Dispatchers.IO.asExecutor(),
                )
                fotoRepository.registreerOpname(huidige, uri, moment)
            }
            bezig = false
            _meldingen.tryEmit(
                if (resultaat.isSuccess) "Foto opgeslagen" else "Opslaan mislukt. Probeer het opnieuw.",
            )
        }
    }

    companion object {
        fun factory(container: AppContainer, serieId: Long) = viewModelFactory {
            initializer {
                OpnameViewModel(
                    serieId = serieId,
                    serieRepository = container.serieRepository,
                    fotoRepository = container.fotoRepository,
                    mediaOpslag = container.mediaOpslag,
                )
            }
        }
    }
}
