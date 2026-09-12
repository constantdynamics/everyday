package nl.constantdynamics.everyday.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import nl.constantdynamics.everyday.data.db.FotoDao
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import nl.constantdynamics.everyday.data.media.MediaOpslag
import nl.constantdynamics.everyday.data.opslag.Instellingen
import nl.constantdynamics.everyday.kern.Bron
import nl.constantdynamics.everyday.kern.Dagindeling
import java.time.Instant
import java.time.LocalDate

class FotoRepository(
    private val fotoDao: FotoDao,
    private val mediaOpslag: MediaOpslag,
    private val instellingen: Instellingen,
) {

    fun fotosVanDeDag(serieId: Long): Flow<List<FotoEntiteit>> = fotoDao.fotosVanDeDag(serieId)

    fun fotosOpDag(serieId: Long, dag: LocalDate): Flow<List<FotoEntiteit>> =
        fotoDao.fotosOpDag(serieId, dag)

    fun laatsteFoto(serieId: Long): Flow<FotoEntiteit?> = fotoDao.laatsteFoto(serieId)

    /**
     * Legt een zojuist opgeslagen opname vast in de administratie. Het bestand staat
     * op dat moment al op schijf; hier lezen we alleen terug wat er werkelijk staat.
     */
    suspend fun registreerOpname(serie: SerieEntiteit, uri: Uri, moment: Instant): Long {
        mediaOpslag.verwijderGpsUitExif(uri)
        val afmeting = mediaOpslag.afmetingen(uri)
        val bestandsnaam = mediaOpslag.werkelijkeBestandsnaam(uri)
            ?: mediaOpslag.bestandsnaam(serie.mapNaam, moment)
        return fotoDao.voegToe(
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
    }
}

/** De te tonen versie van een foto: bewerkt als die bestaat, anders het origineel. */
fun FotoEntiteit.toonUri(): Uri = Uri.parse(bewerktUri ?: origineelUri)
