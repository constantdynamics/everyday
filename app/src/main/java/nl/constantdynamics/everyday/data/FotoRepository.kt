package nl.constantdynamics.everyday.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import nl.constantdynamics.everyday.data.db.DagKeuzeDao
import nl.constantdynamics.everyday.data.db.DagKeuzeEntiteit
import nl.constantdynamics.everyday.data.db.FotoDao
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import nl.constantdynamics.everyday.data.backup.BackupBeheer
import nl.constantdynamics.everyday.data.backup.BackupOpslag
import nl.constantdynamics.everyday.data.media.GhostCache
import nl.constantdynamics.everyday.data.media.MediaOpslag
import nl.constantdynamics.everyday.data.opslag.Instellingen
import nl.constantdynamics.everyday.kern.Bron
import nl.constantdynamics.everyday.kern.Dagindeling
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class FotoRepository(
    private val fotoDao: FotoDao,
    private val dagKeuzeDao: DagKeuzeDao,
    private val mediaOpslag: MediaOpslag,
    private val instellingen: Instellingen,
    private val ghostCache: GhostCache,
    private val serieDao: nl.constantdynamics.everyday.data.db.SerieDao,
    private val backupBeheer: BackupBeheer,
    private val backupOpslag: BackupOpslag,
) {

    fun fotosVanDeDag(serieId: Long): Flow<List<FotoEntiteit>> = fotoDao.fotosVanDeDag(serieId)

    fun fotosOpDag(serieId: Long, dag: LocalDate): Flow<List<FotoEntiteit>> =
        fotoDao.fotosOpDag(serieId, dag)

    fun laatsteFoto(serieId: Long): Flow<FotoEntiteit?> = fotoDao.laatsteFoto(serieId)

    fun gekozenFotoId(serieId: Long, dag: LocalDate): Flow<Long?> =
        dagKeuzeDao.gekozenFotoId(serieId, dag)

    /**
     * Legt een zojuist opgeslagen opname vast in de administratie. Het bestand staat
     * op dat moment al op schijf; hier lezen we alleen terug wat er werkelijk staat.
     */
    suspend fun registreerOpname(serie: SerieEntiteit, uri: Uri, moment: Instant): Long {
        mediaOpslag.verwijderGpsUitExif(uri)
        val afmeting = mediaOpslag.afmetingen(uri)
        val bestandsnaam = mediaOpslag.werkelijkeBestandsnaam(uri)
            ?: mediaOpslag.bestandsnaam(serie.mapNaam, moment)
        val id = fotoDao.voegToe(
            FotoEntiteit(
                serieId = serie.id,
                gemaaktOp = moment,
                dagSleutel = Dagindeling.dagSleutel(moment, instellingen.huidigDagStartUur()),
                bron = Bron.CAMERA,
                origineelUri = uri.toString(),
                bestandsnaam = bestandsnaam,
                breedte = afmeting?.breedte ?: 0,
                hoogte = afmeting?.hoogte ?: 0,
            )
        )
        // Vast klaarzetten voor de ghost overlay van de volgende opname.
        ghostCache.ghost(id, uri, GHOST_ORIGINEEL)
        // De kopie naar de backupmap mag mislukken; dan blijft hij in de wachtrij staan.
        backupBeheer.zetInWachtrij(serie.mapNaam, bestandsnaam, uri.toString())
        backupBeheer.verwerkWachtrij()
        return id
    }

    /** Handmatig een andere foto dan de laatste tot foto-van-de-dag maken. */
    suspend fun kiesFotoVanDeDag(serieId: Long, dag: LocalDate, fotoId: Long) {
        dagKeuzeDao.zet(DagKeuzeEntiteit(serieId = serieId, dagSleutel = dag, fotoId = fotoId))
    }

    /** Terug naar de standaard: de laatste foto van die dag. */
    suspend fun zetTerugNaarStandaard(serieId: Long, dag: LocalDate) {
        dagKeuzeDao.wis(serieId, dag)
    }

    /** Zachte verwijdering: het bestand blijft staan tot de prullenbak wordt geleegd. */
    suspend fun verwijder(fotoId: Long) {
        fotoDao.markeerVerwijderd(fotoId, Instant.now())
    }

    suspend fun herstel(fotoId: Long) {
        fotoDao.herstelUitPrullenbak(fotoId)
    }

    /**
     * Ruimt foto's op die lang genoeg in de prullenbak zitten. Draait bij het starten
     * van de app, zodat verwijderen zelf nooit hoeft te wachten.
     */
    suspend fun ruimPrullenbakOp(bewaartermijn: Duration = STANDAARD_BEWAARTERMIJN) {
        val grens = Instant.now().minus(bewaartermijn)
        for (foto in fotoDao.verlopenInPrullenbak(grens)) {
            // In de backupmap wordt de kopie niet gewist maar verplaatst: een backup
            // die zelf dingen weggooit is geen backup.
            serieDao.serieEenmalig(foto.serieId)?.let { serie ->
                backupOpslag.verplaatsNaarVerwijderd(serie.mapNaam, foto.bestandsnaam)
            }
            mediaOpslag.verwijderBestand(Uri.parse(foto.origineelUri))
            foto.bewerktUri?.let { mediaOpslag.verwijderBestand(Uri.parse(it)) }
            dagKeuzeDao.wisVoorFoto(foto.id)
            fotoDao.wisDefinitief(foto.id)
        }
    }

    suspend fun ghostVoor(foto: FotoEntiteit) =
        ghostCache.ghost(foto.id, foto.toonUri(), foto.ghostVersie())

    companion object {
        val STANDAARD_BEWAARTERMIJN: Duration = Duration.ofDays(30)
        private const val GHOST_ORIGINEEL = "origineel"
    }
}

/** De te tonen versie van een foto: bewerkt als die bestaat, anders het origineel. */
fun FotoEntiteit.toonUri(): Uri = Uri.parse(bewerktUri ?: origineelUri)

/** Verandert zodra de bewerking verandert, zodat de ghost-cache vanzelf verjaart. */
fun FotoEntiteit.ghostVersie(): String =
    if (bewerktUri == null) "origineel" else "bewerkt${bewerktUri.hashCode()}"
