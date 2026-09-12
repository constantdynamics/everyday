package nl.constantdynamics.everyday.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Eén regel op het startscherm: de serie plus wat samenvattende cijfers. */
data class SerieOverzichtRij(
    @Embedded val serie: SerieEntiteit,
    val aantalFotos: Int,
    val laatsteMoment: Instant?,
    val omslagUri: String?,
)

@Dao
interface SerieDao {

    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM foto AS f
              WHERE f.serieId = s.id AND f.verwijderdOp IS NULL) AS aantalFotos,
            (SELECT MAX(f.gemaaktOp) FROM foto AS f
              WHERE f.serieId = s.id AND f.verwijderdOp IS NULL) AS laatsteMoment,
            (SELECT COALESCE(f.bewerktUri, f.origineelUri) FROM foto AS f
              WHERE f.serieId = s.id AND f.verwijderdOp IS NULL
              ORDER BY f.gemaaktOp DESC, f.id DESC LIMIT 1) AS omslagUri
        FROM serie AS s
        WHERE s.verwijderdOp IS NULL AND s.gearchiveerd = 0
        ORDER BY s.volgorde ASC, s.id ASC
        """
    )
    fun overzicht(): Flow<List<SerieOverzichtRij>>

    @Query("SELECT * FROM serie WHERE id = :serieId AND verwijderdOp IS NULL")
    fun serie(serieId: Long): Flow<SerieEntiteit?>

    @Query("SELECT * FROM serie WHERE id = :serieId")
    suspend fun serieEenmalig(serieId: Long): SerieEntiteit?

    @Query("SELECT EXISTS(SELECT 1 FROM serie WHERE mapNaam = :mapNaam)")
    suspend fun mapNaamBestaat(mapNaam: String): Boolean

    @Query("SELECT COALESCE(MAX(volgorde), -1) + 1 FROM serie")
    suspend fun volgendeVolgorde(): Int

    @Insert
    suspend fun voegToe(serie: SerieEntiteit): Long

    @Update
    suspend fun werkBij(serie: SerieEntiteit)
}

@Dao
interface FotoDao {

    @Insert
    suspend fun voegToe(foto: FotoEntiteit): Long

    @Update
    suspend fun werkBij(foto: FotoEntiteit)

    @Query("SELECT * FROM foto WHERE id = :fotoId")
    suspend fun fotoEenmalig(fotoId: Long): FotoEntiteit?

    /** De allerlaatst gemaakte foto van een serie: de bron voor de ghost overlay. */
    @Query(
        """
        SELECT * FROM foto
        WHERE serieId = :serieId AND verwijderdOp IS NULL
        ORDER BY gemaaktOp DESC, id DESC LIMIT 1
        """
    )
    fun laatsteFoto(serieId: Long): Flow<FotoEntiteit?>

    /**
     * Eén foto per dag: de handmatig gekozen foto als die er is en niet verwijderd is,
     * anders de laatste foto van die dag. Dagen zonder foto komen niet voor.
     */
    @Query(
        """
        SELECT f.* FROM foto AS f
        WHERE f.serieId = :serieId
          AND f.verwijderdOp IS NULL
          AND f.id = (
              SELECT COALESCE(
                  (SELECT k.fotoId FROM dagkeuze AS k
                    INNER JOIN foto AS kf ON kf.id = k.fotoId AND kf.verwijderdOp IS NULL
                   WHERE k.serieId = f.serieId AND k.dagSleutel = f.dagSleutel),
                  (SELECT f2.id FROM foto AS f2
                    WHERE f2.serieId = f.serieId AND f2.dagSleutel = f.dagSleutel
                      AND f2.verwijderdOp IS NULL
                    ORDER BY f2.gemaaktOp DESC, f2.id DESC LIMIT 1)
              )
          )
        ORDER BY f.dagSleutel DESC
        """
    )
    fun fotosVanDeDag(serieId: Long): Flow<List<FotoEntiteit>>

    /** Alle foto's van één dag, oudste eerst. */
    @Query(
        """
        SELECT * FROM foto
        WHERE serieId = :serieId AND dagSleutel = :dag AND verwijderdOp IS NULL
        ORDER BY gemaaktOp ASC, id ASC
        """
    )
    fun fotosOpDag(serieId: Long, dag: LocalDate): Flow<List<FotoEntiteit>>
}

@Dao
interface DagKeuzeDao {

    @Upsert
    suspend fun zet(keuze: DagKeuzeEntiteit)

    @Query("DELETE FROM dagkeuze WHERE serieId = :serieId AND dagSleutel = :dag")
    suspend fun wis(serieId: Long, dag: LocalDate)

    @Query("SELECT * FROM dagkeuze WHERE serieId = :serieId AND dagSleutel = :dag")
    suspend fun keuze(serieId: Long, dag: LocalDate): DagKeuzeEntiteit?
}
