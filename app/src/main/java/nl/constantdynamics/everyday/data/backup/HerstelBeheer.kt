package nl.constantdynamics.everyday.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.constantdynamics.everyday.data.db.DagKeuzeDao
import nl.constantdynamics.everyday.data.db.DagKeuzeEntiteit
import nl.constantdynamics.everyday.data.db.FotoDao
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.db.SerieDao
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import nl.constantdynamics.everyday.data.media.MediaOpslag

data class HerstelResultaat(
    val seriesToegevoegd: Int = 0,
    val fotosToegevoegd: Int = 0,
    val overgeslagen: Int = 0,
    val mislukt: Int = 0,
) {
    val erIsIetsGedaan: Boolean get() = seriesToegevoegd > 0 || fotosToegevoegd > 0
}

/**
 * Haalt de administratie terug uit alleen de backupmap. Dat is het vangnet na een
 * de-installatie of een toestelwissel: de app verliest dan het eigendom van zijn
 * oude bestanden, maar de backupmap heeft alles nog.
 */
class HerstelBeheer(
    private val context: Context,
    private val serieDao: SerieDao,
    private val fotoDao: FotoDao,
    private val dagKeuzeDao: DagKeuzeDao,
    private val backupOpslag: BackupOpslag,
    private val mediaOpslag: MediaOpslag,
) {

    suspend fun herstel(): HerstelResultaat? = withContext(Dispatchers.IO) {
        val tekst = backupOpslag.leesMetadata() ?: return@withContext null
        val inhoud = Metadata.lees(tekst) ?: return@withContext null

        var seriesToegevoegd = 0
        var fotosToegevoegd = 0
        var overgeslagen = 0
        var mislukt = 0

        val serieIdPerMapNaam = mutableMapOf<String, Long>()
        for (serie in inhoud.series) {
            val bestaand = serieDao.serieOpMapNaam(serie.mapNaam)
            if (bestaand != null) {
                serieIdPerMapNaam[serie.mapNaam] = bestaand.id
                continue
            }
            val id = serieDao.voegToe(
                SerieEntiteit(
                    naam = serie.naam,
                    mapNaam = serie.mapNaam,
                    aangemaaktOp = serie.aangemaaktOp,
                    lensRichting = serie.lensRichting,
                    volgorde = serie.volgorde,
                    gearchiveerd = serie.gearchiveerd,
                ),
            )
            serieIdPerMapNaam[serie.mapNaam] = id
            seriesToegevoegd++
        }

        for (foto in inhoud.fotos) {
            val serieId = serieIdPerMapNaam[foto.serieMapNaam] ?: continue
            if (fotoDao.fotoOpBestandsnaam(serieId, foto.bestandsnaam) != null) {
                overgeslagen++
                continue
            }
            val bronUri = backupOpslag.zoekBestand(foto.serieMapNaam, foto.bestandsnaam)
            if (bronUri == null) {
                mislukt++
                continue
            }
            val nieuweUri = kopieerTerug(foto.serieMapNaam, foto.bestandsnaam, foto, bronUri)
            if (nieuweUri == null) {
                mislukt++
                continue
            }
            val bewerktUri = if (foto.heeftBewerking) {
                backupOpslag
                    .zoekBestand(foto.serieMapNaam, foto.bestandsnaam, BackupBeheer.SUBMAP_BEWERKT)
                    ?.let { kopieerTerug(foto.serieMapNaam, foto.bestandsnaam, foto, it, BackupBeheer.SUBMAP_BEWERKT) }
            } else {
                null
            }
            fotoDao.voegToe(
                FotoEntiteit(
                    serieId = serieId,
                    gemaaktOp = foto.gemaaktOp,
                    dagSleutel = foto.dagSleutel,
                    bron = foto.bron,
                    origineelUri = nieuweUri.toString(),
                    bestandsnaam = foto.bestandsnaam,
                    breedte = foto.breedte,
                    hoogte = foto.hoogte,
                    rotatie = foto.rotatie,
                    rechtzetHoek = foto.rechtzetHoek,
                    cropL = foto.cropL,
                    cropT = foto.cropT,
                    cropR = foto.cropR,
                    cropB = foto.cropB,
                    bewerktUri = bewerktUri?.toString(),
                    datumOnzeker = foto.datumOnzeker,
                ),
            )
            fotosToegevoegd++
        }

        for (keuze in inhoud.keuzes) {
            val serieId = serieIdPerMapNaam[keuze.serieMapNaam] ?: continue
            val foto = fotoDao.fotoOpBestandsnaam(serieId, keuze.bestandsnaam) ?: continue
            dagKeuzeDao.zet(
                DagKeuzeEntiteit(serieId = serieId, dagSleutel = keuze.dagSleutel, fotoId = foto.id),
            )
        }

        HerstelResultaat(seriesToegevoegd, fotosToegevoegd, overgeslagen, mislukt)
    }

    private suspend fun kopieerTerug(
        serieMapNaam: String,
        bestandsnaam: String,
        foto: MetadataFoto,
        bronUri: Uri,
        submap: String? = null,
    ): Uri? = mediaOpslag.schrijfJpeg(
        mapNaam = serieMapNaam,
        bestandsnaam = bestandsnaam,
        moment = foto.gemaaktOp,
        submap = submap,
    ) { uitvoer ->
        context.contentResolver.openInputStream(bronUri).use { invoer ->
            requireNotNull(invoer)
            invoer.copyTo(uitvoer)
        }
    }
}
