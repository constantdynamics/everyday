package nl.constantdynamics.everyday.ui.importeren

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.data.ImportBeheer
import nl.constantdynamics.everyday.data.ImportKandidaat
import nl.constantdynamics.everyday.data.ImportUitkomst
import nl.constantdynamics.everyday.data.SerieRepository
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import nl.constantdynamics.everyday.data.plus
import java.time.LocalDate
import java.time.ZoneId

sealed interface ImportStand {
    data object Kiezen : ImportStand
    data class Bezig(val gedaan: Int, val totaal: Int) : ImportStand
    data class VraagDatum(
        val kandidaat: ImportKandidaat,
        val voorstel: LocalDate,
        val gedaan: Int,
        val totaal: Int,
    ) : ImportStand
    data class Klaar(val uitkomst: ImportUitkomst) : ImportStand
}

class ImportViewModel(
    private val serieId: Long,
    serieRepository: SerieRepository,
    private val importBeheer: ImportBeheer,
) : ViewModel() {

    val serie: StateFlow<SerieEntiteit?> = serieRepository.serie(serieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var stand by mutableStateOf<ImportStand>(ImportStand.Kiezen)
        private set

    private var antwoord: CompletableDeferred<LocalDate?>? = null

    fun start(uris: List<Uri>) {
        if (uris.isEmpty()) {
            stand = ImportStand.Klaar(ImportUitkomst())
            return
        }
        val huidigeSerie = serie.value
        viewModelScope.launch {
            stand = ImportStand.Bezig(0, uris.size)
            val doelSerie = huidigeSerie ?: serie.value ?: run {
                stand = ImportStand.Klaar(ImportUitkomst(mislukt = uris.size))
                return@launch
            }
            val kandidaten = importBeheer.verken(uris)
            var uitkomst = ImportUitkomst()
            val zone = ZoneId.systemDefault()

            kandidaten.forEachIndexed { index, kandidaat ->
                stand = ImportStand.Bezig(index, kandidaten.size)
                var datumOnzeker = false
                val moment = kandidaat.moment ?: run {
                    // Geen betrouwbare datum: vragen, met de bestandsdatum voorgevuld.
                    val wachten = CompletableDeferred<LocalDate?>()
                    antwoord = wachten
                    stand = ImportStand.VraagDatum(
                        kandidaat = kandidaat,
                        voorstel = kandidaat.terugvalMoment.atZone(zone).toLocalDate(),
                        gedaan = index,
                        totaal = kandidaten.size,
                    )
                    val gekozen = wachten.await()
                    stand = ImportStand.Bezig(index, kandidaten.size)
                    if (gekozen == null) {
                        datumOnzeker = true
                        kandidaat.terugvalMoment
                    } else {
                        ImportBeheer.momentVanDatum(gekozen, zone)
                    }
                }
                uitkomst += importBeheer.importeer(doelSerie, kandidaat, moment, datumOnzeker)
            }

            stand = ImportStand.Klaar(uitkomst)
        }
    }

    fun beantwoordDatum(datum: LocalDate) {
        antwoord?.complete(datum)
        antwoord = null
    }

    fun markeerDatumOnzeker() {
        antwoord?.complete(null)
        antwoord = null
    }

    override fun onCleared() {
        antwoord?.complete(null)
        antwoord = null
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer, serieId: Long) = viewModelFactory {
            initializer {
                ImportViewModel(serieId, container.serieRepository, container.importBeheer)
            }
        }
    }
}
