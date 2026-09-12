package nl.constantdynamics.everyday.ui.bewerken

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.FotoRepository
import nl.constantdynamics.everyday.data.bewerking
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.media.Bewerker
import nl.constantdynamics.everyday.kern.Bewerking

class BewerkViewModel(
    private val fotoId: Long,
    private val fotoRepository: FotoRepository,
    private val bewerker: Bewerker,
) : ViewModel() {

    val foto: StateFlow<FotoEntiteit?> = fotoRepository.foto(fotoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var kwartslagen by mutableIntStateOf(0)
        private set
    var rechtzetHoek by mutableFloatStateOf(0f)
        private set
    var uitsnede by mutableStateOf(Rect(0f, 0f, 1f, 1f))
        private set

    var voorbeeld by mutableStateOf<Bitmap?>(null)
        private set
    var bezig by mutableStateOf(false)
        private set

    private var beginwaardenGezet = false

    /** Eenmalig de bestaande bewerking overnemen zodra de foto bekend is. */
    fun neemBestaandeOver(foto: FotoEntiteit) {
        if (beginwaardenGezet) return
        beginwaardenGezet = true
        val bestaande = foto.bewerking()
        kwartslagen = bestaande.kwartslagen
        rechtzetHoek = bestaande.rechtzetHoek
        uitsnede = Rect(bestaande.links, bestaande.boven, bestaande.rechts, bestaande.onder)
    }

    fun draaiLinks() {
        kwartslagen = kwartslagen - 1
        uitsnede = Rect(0f, 0f, 1f, 1f)
    }

    fun draaiRechts() {
        kwartslagen = kwartslagen + 1
        uitsnede = Rect(0f, 0f, 1f, 1f)
    }

    fun zetRechtzetHoek(hoek: Float) {
        rechtzetHoek = hoek.coerceIn(-Bewerking.MAX_RECHTZET_HOEK, Bewerking.MAX_RECHTZET_HOEK)
    }

    fun zetUitsnede(nieuw: Rect) {
        uitsnede = nieuw
    }

    fun zetUitsnedeTerug() {
        uitsnede = Rect(0f, 0f, 1f, 1f)
    }

    fun huidigeBewerking(): Bewerking = Bewerking(
        kwartslagen = kwartslagen,
        rechtzetHoek = rechtzetHoek,
        links = uitsnede.left,
        boven = uitsnede.top,
        rechts = uitsnede.right,
        onder = uitsnede.bottom,
    )

    /** Het beeld waarop je de uitsnede aanwijst: gedraaid en rechtgezet, nog niet bijgesneden. */
    fun vernieuwVoorbeeld(origineelUri: Uri) {
        viewModelScope.launch {
            val nieuw = bewerker.rechtgezet(
                uri = origineelUri,
                kwartslagen = kwartslagen,
                rechtzetHoek = rechtzetHoek,
                maxZijde = Bewerker.VOORBEELD_ZIJDE,
            )
            val oud = voorbeeld
            voorbeeld = nieuw
            if (oud != null && oud !== nieuw) oud.recycle()
        }
    }

    fun bewaar(klaar: (Boolean) -> Unit) {
        if (bezig) return
        bezig = true
        viewModelScope.launch {
            val gelukt = runCatching {
                fotoRepository.bewaarBewerking(fotoId, huidigeBewerking())
            }.getOrDefault(false)
            bezig = false
            klaar(gelukt)
        }
    }

    fun herstelOrigineel(klaar: () -> Unit) {
        if (bezig) return
        bezig = true
        viewModelScope.launch {
            runCatching { fotoRepository.herstelOrigineel(fotoId) }
            bezig = false
            klaar()
        }
    }

    override fun onCleared() {
        voorbeeld?.recycle()
        voorbeeld = null
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer, fotoId: Long) = viewModelFactory {
            initializer {
                BewerkViewModel(fotoId, container.fotoRepository, container.bewerker)
            }
        }
    }
}
