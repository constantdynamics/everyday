package nl.constantdynamics.everyday.data

import kotlinx.coroutines.flow.Flow
import nl.constantdynamics.everyday.data.db.SerieDao
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import nl.constantdynamics.everyday.data.db.SerieOverzichtRij
import nl.constantdynamics.everyday.kern.LensRichting
import nl.constantdynamics.everyday.kern.maakMapNaam
import nl.constantdynamics.everyday.kern.maakUniekeMapNaam
import java.time.Instant

class SerieRepository(private val serieDao: SerieDao) {

    fun overzicht(): Flow<List<SerieOverzichtRij>> = serieDao.overzicht()

    fun serie(serieId: Long): Flow<SerieEntiteit?> = serieDao.serie(serieId)

    suspend fun serieEenmalig(serieId: Long): SerieEntiteit? = serieDao.serieEenmalig(serieId)

    suspend fun alleSeries(): List<SerieEntiteit> =
        serieDao.alles().filter { it.verwijderdOp == null }

    suspend fun maakSerie(naam: String): Long {
        val schoon = naam.trim()
        val mapNaam = maakUniekeMapNaam(maakMapNaam(schoon)) { serieDao.mapNaamBestaat(it) }
        return serieDao.voegToe(
            SerieEntiteit(
                naam = schoon,
                mapNaam = mapNaam,
                aangemaaktOp = Instant.now(),
                lensRichting = LensRichting.ACHTER,
                volgorde = serieDao.volgendeVolgorde(),
            )
        )
    }

    /** Hernoemen raakt alleen de weergavenaam; de mapnaam en bestandspaden blijven staan. */
    suspend fun hernoem(serieId: Long, nieuweNaam: String) {
        val serie = serieDao.serieEenmalig(serieId) ?: return
        serieDao.werkBij(serie.copy(naam = nieuweNaam.trim()))
    }

    suspend fun zetLensRichting(serieId: Long, richting: LensRichting) {
        val serie = serieDao.serieEenmalig(serieId) ?: return
        if (serie.lensRichting == richting) return
        serieDao.werkBij(serie.copy(lensRichting = richting))
    }

    suspend fun zetGearchiveerd(serieId: Long, gearchiveerd: Boolean) {
        val serie = serieDao.serieEenmalig(serieId) ?: return
        serieDao.werkBij(serie.copy(gearchiveerd = gearchiveerd))
    }
}
