package nl.constantdynamics.everyday.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import nl.constantdynamics.everyday.kern.Bron
import nl.constantdynamics.everyday.kern.LensRichting
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "serie",
    indices = [Index(value = ["mapNaam"], unique = true)],
)
data class SerieEntiteit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Vrij te wijzigen weergavenaam. */
    val naam: String,
    /** Mapnaam op schijf. Ligt vast vanaf het aanmaken en verandert nooit meer. */
    val mapNaam: String,
    val aangemaaktOp: Instant,
    val lensRichting: LensRichting,
    val volgorde: Int,
    val gearchiveerd: Boolean = false,
    val verwijderdOp: Instant? = null,
)

@Entity(
    tableName = "foto",
    foreignKeys = [
        ForeignKey(
            entity = SerieEntiteit::class,
            parentColumns = ["id"],
            childColumns = ["serieId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["serieId", "dagSleutel"]), Index(value = ["serieId", "gemaaktOp"])],
)
data class FotoEntiteit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serieId: Long,
    val gemaaktOp: Instant,
    val dagSleutel: LocalDate,
    val bron: Bron,
    /** MediaStore-uri van het originele bestand. Wordt nooit overschreven. */
    val origineelUri: String,
    val bestandsnaam: String,
    val breedte: Int,
    val hoogte: Int,
    /** Bewerking: hele kwartslagen. */
    val rotatie: Int = 0,
    /** Bewerking: vrije hoek voor rechtzetten, in graden. */
    val rechtzetHoek: Float = 0f,
    val cropL: Float? = null,
    val cropT: Float? = null,
    val cropR: Float? = null,
    val cropB: Float? = null,
    /** Gerenderde afgeleide na bewerking; null betekent onbewerkt. */
    val bewerktUri: String? = null,
    /** Bij import zonder betrouwbare datum. */
    val datumOnzeker: Boolean = false,
    /** Gevuld zolang de foto in de prullenbak zit. */
    val verwijderdOp: Instant? = null,
)

/**
 * Alleen aanwezig als er handmatig een andere foto dan de laatste van die dag is
 * gekozen. Geen rij betekent: de laatste foto van die dag is de foto-van-de-dag.
 */
@Entity(
    tableName = "dagkeuze",
    primaryKeys = ["serieId", "dagSleutel"],
)
data class DagKeuzeEntiteit(
    val serieId: Long,
    val dagSleutel: LocalDate,
    val fotoId: Long,
)

/**
 * Eén nog uit te voeren kopie naar de backupmap. Een mislukte kopie blijft staan en
 * wordt opnieuw geprobeerd; een foto gaat er dus nooit door verloren en de app
 * wacht nergens op.
 */
@Entity(
    tableName = "backuptaak",
    indices = [Index(value = ["bronUri"], unique = true)],
)
data class BackupTaakEntiteit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serieMapNaam: String,
    val bestandsnaam: String,
    val bronUri: String,
    /** Leeg voor originelen, "bewerkt" voor een gerenderde afgeleide. */
    val submap: String? = null,
    val aangemaaktOp: Instant,
    val pogingen: Int = 0,
)
