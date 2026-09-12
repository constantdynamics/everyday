package nl.constantdynamics.everyday.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.constantdynamics.everyday.data.backup.BackupBeheer
import nl.constantdynamics.everyday.data.db.FotoDao
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import nl.constantdynamics.everyday.data.media.MediaOpslag
import nl.constantdynamics.everyday.data.opslag.Instellingen
import nl.constantdynamics.everyday.kern.Bron
import nl.constantdynamics.everyday.kern.Dagindeling
import nl.constantdynamics.everyday.kern.datumUitBestandsnaam
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Wat we van een gekozen foto weten vóórdat hij wordt geïmporteerd. */
data class ImportKandidaat(
    val uri: Uri,
    val bestandsnaam: String,
    val moment: Instant?,
    val terugvalMoment: Instant,
    val herkomstVanDatum: DatumHerkomst,
) {
    val datumIsZeker: Boolean get() = herkomstVanDatum != DatumHerkomst.ONBEKEND
}

enum class DatumHerkomst { EXIF, BESTANDSNAAM, ONBEKEND }

data class ImportUitkomst(
    val toegevoegd: Int = 0,
    val overgeslagenAlsDubbel: Int = 0,
    val zonderZekereDatum: Int = 0,
    val mislukt: Int = 0,
)

/**
 * Importeren gaat via de systeem-fotokiezer, dus zonder opslagpermissie: de app
 * krijgt alleen de foto's die je zelf aantikt. Elk bestand wordt gekopieerd naar de
 * seriemap, want de oorspronkelijke foto kan verdwijnen.
 */
class ImportBeheer(
    private val context: Context,
    private val fotoDao: FotoDao,
    private val mediaOpslag: MediaOpslag,
    private val instellingen: Instellingen,
    private val backupBeheer: BackupBeheer,
) {

    /** Bepaalt per gekozen foto wat we van de datum weten, zonder al iets te kopiëren. */
    suspend fun verken(uris: List<Uri>, zone: ZoneId = ZoneId.systemDefault()): List<ImportKandidaat> =
        withContext(Dispatchers.IO) {
            uris.map { uri ->
                val beschrijving = mediaOpslag.beschrijving(uri)
                val naam = beschrijving?.naam.orEmpty()
                val uitExif = mediaOpslag.momentUitExif(uri, zone)
                val uitNaam = datumUitBestandsnaam(naam)?.atZone(zone)?.toInstant()
                val moment = uitExif ?: uitNaam
                ImportKandidaat(
                    uri = uri,
                    bestandsnaam = naam,
                    moment = moment,
                    terugvalMoment = mediaOpslag.bestandsMoment(uri) ?: Instant.now(),
                    herkomstVanDatum = when {
                        uitExif != null -> DatumHerkomst.EXIF
                        uitNaam != null -> DatumHerkomst.BESTANDSNAAM
                        else -> DatumHerkomst.ONBEKEND
                    },
                )
            }
        }

    /**
     * Kopieert één foto naar de serie. [moment] is de definitieve datum; met
     * [datumOnzeker] wordt die zichtbaar als gok gemarkeerd.
     */
    suspend fun importeer(
        serie: SerieEntiteit,
        kandidaat: ImportKandidaat,
        moment: Instant,
        datumOnzeker: Boolean,
    ): ImportUitkomst = withContext(Dispatchers.IO) {
        val afmeting = mediaOpslag.afmetingen(kandidaat.uri)
        if (afmeting == null) return@withContext ImportUitkomst(mislukt = 1)

        val dubbel = fotoDao.zoekDubbele(serie.id, moment, afmeting.breedte, afmeting.hoogte)
        if (dubbel != null) return@withContext ImportUitkomst(overgeslagenAlsDubbel = 1)

        val bestandsnaam = mediaOpslag.bestandsnaam(serie.mapNaam, moment)
        val nieuweUri = mediaOpslag.schrijfJpeg(
            mapNaam = serie.mapNaam,
            bestandsnaam = bestandsnaam,
            moment = moment,
        ) { uitvoer ->
            context.contentResolver.openInputStream(kandidaat.uri).use { invoer ->
                requireNotNull(invoer) { "De gekozen foto kon niet worden gelezen" }
                invoer.copyTo(uitvoer)
            }
        } ?: return@withContext ImportUitkomst(mislukt = 1)

        mediaOpslag.verwijderGpsUitExif(nieuweUri)
        val werkelijkeNaam = mediaOpslag.werkelijkeBestandsnaam(nieuweUri) ?: bestandsnaam

        fotoDao.voegToe(
            FotoEntiteit(
                serieId = serie.id,
                gemaaktOp = moment,
                dagSleutel = dagSleutelVoor(moment),
                bron = Bron.IMPORT,
                origineelUri = nieuweUri.toString(),
                bestandsnaam = werkelijkeNaam,
                breedte = afmeting.breedte,
                hoogte = afmeting.hoogte,
                datumOnzeker = datumOnzeker,
            ),
        )
        backupBeheer.zetInWachtrij(serie.mapNaam, werkelijkeNaam, nieuweUri.toString())
        ImportUitkomst(toegevoegd = 1, zonderZekereDatum = if (datumOnzeker) 1 else 0)
    }

    private suspend fun dagSleutelVoor(moment: Instant): LocalDate =
        Dagindeling.dagSleutel(moment, instellingen.huidigDagStartUur())

    companion object {
        fun momentVanDatum(datum: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Instant =
            LocalDateTime.of(datum, java.time.LocalTime.NOON).atZone(zone).toInstant()
    }
}

operator fun ImportUitkomst.plus(ander: ImportUitkomst) = ImportUitkomst(
    toegevoegd = toegevoegd + ander.toegevoegd,
    overgeslagenAlsDubbel = overgeslagenAlsDubbel + ander.overgeslagenAlsDubbel,
    zonderZekereDatum = zonderZekereDatum + ander.zonderZekereDatum,
    mislukt = mislukt + ander.mislukt,
)
