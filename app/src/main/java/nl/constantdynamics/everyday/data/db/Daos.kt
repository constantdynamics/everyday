package nl.constantdynamics.everyday.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query("SELECT * FROM serie WHERE mapNaam = :mapNaam LIMIT 1")
    suspend fun serieOpMapNaam(mapNaam: String): SerieEntiteit?

    @Query("SELECT COALESCE(MAX(volgorde), -1) + 1 FROM serie")
    suspend fun volgendeVolgorde(): Int

    @Query("SELECT * FROM serie")
    suspend fun alles(): List<SerieEntiteit>

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

    @Query("SELECT * FROM foto WHERE id = :fotoId")
    fun fotoStroom(fotoId: Long): Flow<FotoEntiteit?>

    @Query("SELECT * FROM foto")
    suspend fun alles(): List<FotoEntiteit>

    @Query("SELECT * FROM foto WHERE serieId = :serieId AND verwijderdOp IS NULL")
    suspend fun alleVanSerie(serieId: Long): List<FotoEntiteit>

    @Query("SELECT * FROM foto WHERE serieId = :serieId AND bestandsnaam = :bestandsnaam LIMIT 1")
    suspend fun fotoOpBestandsnaam(serieId: Long, bestandsnaam: String): FotoEntiteit?

    /** Voor het herkennen van dubbelen bij importeren: zelfde tijdstip en zelfde grootte. */
    @Query(
        """
        SELECT * FROM foto
        WHERE serieId = :serieId AND gemaaktOp = :moment AND breedte = :breedte AND hoogte = :hoogte
        LIMIT 1
        """
    )
    suspend fun zoekDubbele(serieId: Long, moment: Instant, breedte: Int, hoogte: Int): FotoEntiteit?

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

    @Query("UPDATE foto SET verwijderdOp = :moment WHERE id = :fotoId")
    suspend fun markeerVerwijderd(fotoId: Long, moment: Instant)

    @Query("UPDATE foto SET verwijderdOp = NULL WHERE id = :fotoId")
    suspend fun herstelUitPrullenbak(fotoId: Long)

    /** Foto's die lang genoeg in de prullenbak zitten om definitief te mogen verdwijnen. */
    @Query("SELECT * FROM foto WHERE verwijderdOp IS NOT NULL AND verwijderdOp < :grens")
    suspend fun verlopenInPrullenbak(grens: Instant): List<FotoEntiteit>

    @Query("DELETE FROM foto WHERE id = :fotoId")
    suspend fun wisDefinitief(fotoId: Long)

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
interface BackupTaakDao {

    /** Dezelfde bron twee keer in de wachtrij zetten heeft geen zin. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun voegToe(taak: BackupTaakEntiteit): Long

    @Query("SELECT * FROM backuptaak ORDER BY aangemaaktOp ASC, id ASC LIMIT :maximaal")
    suspend fun wachtrij(maximaal: Int): List<BackupTaakEntiteit>

    @Query("DELETE FROM backuptaak WHERE id = :taakId")
    suspend fun verwijder(taakId: Long)

    @Query("UPDATE backuptaak SET pogingen = pogingen + 1 WHERE id = :taakId")
    suspend fun tekPoging(taakId: Long)

    @Query("SELECT COUNT(*) FROM backuptaak")
    fun aantalInWachtrij(): Flow<Int>

    @Query("SELECT MIN(aangemaaktOp) FROM backuptaak")
    fun oudsteInWachtrij(): Flow<Instant?>

    @Query("DELETE FROM backuptaak")
    suspend fun leeg()
}

@Dao
interface DagKeuzeDao {

    @Upsert
    suspend fun zet(keuze: DagKeuzeEntiteit)

    @Query("DELETE FROM dagkeuze WHERE serieId = :serieId AND dagSleutel = :dag")
    suspend fun wis(serieId: Long, dag: LocalDate)

    /** Opruimen wanneer de gekozen foto definitief verdwijnt. */
    @Query("DELETE FROM dagkeuze WHERE fotoId = :fotoId")
    suspend fun wisVoorFoto(fotoId: Long)

    @Query("SELECT fotoId FROM dagkeuze WHERE serieId = :serieId AND dagSleutel = :dag")
    fun gekozenFotoId(serieId: Long, dag: LocalDate): Flow<Long?>

    @Query("SELECT * FROM dagkeuze")
    suspend fun alles(): List<DagKeuzeEntiteit>
}
