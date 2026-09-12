package nl.constantdynamics.everyday.data.backup

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import nl.constantdynamics.everyday.data.db.BackupTaakDao
import nl.constantdynamics.everyday.data.db.BackupTaakEntiteit
import nl.constantdynamics.everyday.data.db.DagKeuzeDao
import nl.constantdynamics.everyday.data.db.FotoDao
import nl.constantdynamics.everyday.data.db.SerieDao
import nl.constantdynamics.everyday.data.opslag.Instellingen
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

data class BackupStatus(
    val mapIngesteld: Boolean,
    val aantalInWachtrij: Int,
    val oudsteInWachtrij: Instant?,
    val laatsteGeslaagdeKopie: Instant?,
) {
    /**
     * Pas als de achterstand een paar dagen oud is, is er iets aan de hand dat je
     * wilt weten. Daaronder wordt er stil ingelopen.
     */
    fun verdientAandacht(nu: Instant = Instant.now()): Boolean {
        val oudste = oudsteInWachtrij ?: return false
        return oudste.isBefore(nu.minus(DREMPEL))
    }

    companion object {
        val DREMPEL: java.time.Duration = java.time.Duration.ofDays(3)
    }
}

/**
 * Houdt de backupmap bij en werkt de kopieerwachtrij af. Een mislukte kopie mag
 * nooit een foto kosten en nooit de app ophouden: het werk blijft in de wachtrij en
 * wordt opnieuw geprobeerd bij de volgende appstart en na de volgende geslaagde kopie.
 */
class BackupBeheer(
    private val backupTaakDao: BackupTaakDao,
    private val serieDao: SerieDao,
    private val fotoDao: FotoDao,
    private val dagKeuzeDao: DagKeuzeDao,
    private val backupOpslag: BackupOpslag,
    private val instellingen: Instellingen,
    private val scope: CoroutineScope,
) {

    val status: Flow<BackupStatus> = combine(
        instellingen.backupMapUri,
        backupTaakDao.aantalInWachtrij(),
        backupTaakDao.oudsteInWachtrij(),
        instellingen.laatsteGeslaagdeKopie,
    ) { mapUri, aantal, oudste, laatste ->
        BackupStatus(
            mapIngesteld = mapUri != null,
            aantalInWachtrij = aantal,
            oudsteInWachtrij = oudste,
            laatsteGeslaagdeKopie = laatste,
        )
    }

    private val bezig = AtomicBoolean(false)

    suspend fun kiesMap(uri: Uri): Boolean {
        val gelukt = backupOpslag.onthoudMap(uri)
        if (gelukt) {
            kopieerAllesOpnieuw()
            verwerkWachtrij()
        }
        return gelukt
    }

    suspend fun zetInWachtrij(
        serieMapNaam: String,
        bestandsnaam: String,
        bronUri: String,
        submap: String? = null,
    ) {
        backupTaakDao.voegToe(
            BackupTaakEntiteit(
                serieMapNaam = serieMapNaam,
                bestandsnaam = bestandsnaam,
                bronUri = bronUri,
                submap = submap,
                aangemaaktOp = Instant.now(),
            ),
        )
    }

    /** Zet alles wat de app kent opnieuw in de wachtrij; al gekopieerde bestanden worden overgeslagen. */
    suspend fun kopieerAllesOpnieuw() {
        val series = serieDao.alles().associateBy { it.id }
        for (foto in fotoDao.alles()) {
            if (foto.verwijderdOp != null) continue
            val serie = series[foto.serieId] ?: continue
            zetInWachtrij(serie.mapNaam, foto.bestandsnaam, foto.origineelUri)
            foto.bewerktUri?.let {
                zetInWachtrij(serie.mapNaam, foto.bestandsnaam, it, submap = SUBMAP_BEWERKT)
            }
        }
    }

    /** Werkt de wachtrij af op de achtergrond. Meerdere aanroepen tegelijk doen geen kwaad. */
    fun verwerkWachtrij() {
        if (!bezig.compareAndSet(false, true)) return
        scope.launch {
            try {
                if (!backupOpslag.bereikbaar()) return@launch
                var ietsGelukt = false
                while (true) {
                    val taken = backupTaakDao.wachtrij(PER_RONDE)
                    if (taken.isEmpty()) break
                    var voortgang = false
                    for (taak in taken) {
                        if (backupOpslag.kopieer(taak)) {
                            backupTaakDao.verwijder(taak.id)
                            ietsGelukt = true
                            voortgang = true
                        } else {
                            backupTaakDao.tekPoging(taak.id)
                        }
                    }
                    // Lukt er in een hele ronde niets, dan is de map weg of vol:
                    // stoppen en het bij de volgende gelegenheid opnieuw proberen.
                    if (!voortgang) break
                }
                if (ietsGelukt) {
                    instellingen.zetLaatsteGeslaagdeKopie(Instant.now())
                    werkMetadataBij()
                }
            } finally {
                bezig.set(false)
            }
        }
    }

    /** Herschrijft everyday-metadata.json; het bestand is klein, dus dat mag elke keer. */
    suspend fun werkMetadataBij(): Boolean {
        val inhoud = Metadata.maak(
            series = serieDao.alles(),
            fotos = fotoDao.alles(),
            keuzes = dagKeuzeDao.alles(),
        )
        return backupOpslag.schrijfMetadata(inhoud)
    }

    companion object {
        const val SUBMAP_BEWERKT = "bewerkt"
        private const val PER_RONDE = 25
    }
}
