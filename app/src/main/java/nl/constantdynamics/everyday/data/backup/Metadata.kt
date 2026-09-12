package nl.constantdynamics.everyday.data.backup

import nl.constantdynamics.everyday.data.db.DagKeuzeEntiteit
import nl.constantdynamics.everyday.data.db.FotoEntiteit
import nl.constantdynamics.everyday.data.db.SerieEntiteit
import nl.constantdynamics.everyday.kern.Bron
import nl.constantdynamics.everyday.kern.LensRichting
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

data class MetadataSerie(
    val naam: String,
    val mapNaam: String,
    val aangemaaktOp: Instant,
    val lensRichting: LensRichting,
    val volgorde: Int,
    val gearchiveerd: Boolean,
)

data class MetadataFoto(
    val serieMapNaam: String,
    val bestandsnaam: String,
    val gemaaktOp: Instant,
    val dagSleutel: LocalDate,
    val bron: Bron,
    val breedte: Int,
    val hoogte: Int,
    val datumOnzeker: Boolean,
    val rotatie: Int,
    val rechtzetHoek: Float,
    val cropL: Float?,
    val cropT: Float?,
    val cropR: Float?,
    val cropB: Float?,
    val heeftBewerking: Boolean,
)

data class MetadataKeuze(
    val serieMapNaam: String,
    val dagSleutel: LocalDate,
    val bestandsnaam: String,
)

data class MetadataInhoud(
    val series: List<MetadataSerie>,
    val fotos: List<MetadataFoto>,
    val keuzes: List<MetadataKeuze>,
)

/**
 * De hele administratie in één bestand naast de foto's. Alles verwijst naar mappen
 * en bestandsnamen, nooit naar database-ids of uri's, zodat de backupmap in zijn
 * eentje genoeg is om alles terug te halen — ook op een ander toestel.
 */
object Metadata {

    const val BESTANDSNAAM = "everyday-metadata.json"
    private const val VERSIE = 1

    fun maak(
        series: List<SerieEntiteit>,
        fotos: List<FotoEntiteit>,
        keuzes: List<DagKeuzeEntiteit>,
    ): String {
        val mapNaamPerSerie = series.associate { it.id to it.mapNaam }
        val fotoPerId = fotos.associateBy { it.id }

        val serieLijst = JSONArray()
        series.filter { it.verwijderdOp == null }.forEach { serie ->
            serieLijst.put(
                JSONObject().apply {
                    put("naam", serie.naam)
                    put("mapNaam", serie.mapNaam)
                    put("aangemaaktOp", serie.aangemaaktOp.toString())
                    put("lensRichting", serie.lensRichting.name)
                    put("volgorde", serie.volgorde)
                    put("gearchiveerd", serie.gearchiveerd)
                },
            )
        }

        val fotoLijst = JSONArray()
        fotos.filter { it.verwijderdOp == null }.forEach { foto ->
            val mapNaam = mapNaamPerSerie[foto.serieId] ?: return@forEach
            fotoLijst.put(
                JSONObject().apply {
                    put("serie", mapNaam)
                    put("bestandsnaam", foto.bestandsnaam)
                    put("gemaaktOp", foto.gemaaktOp.toString())
                    put("dagSleutel", foto.dagSleutel.toString())
                    put("bron", foto.bron.name)
                    put("breedte", foto.breedte)
                    put("hoogte", foto.hoogte)
                    put("datumOnzeker", foto.datumOnzeker)
                    put("rotatie", foto.rotatie)
                    put("rechtzetHoek", foto.rechtzetHoek.toDouble())
                    foto.cropL?.let { put("cropL", it.toDouble()) }
                    foto.cropT?.let { put("cropT", it.toDouble()) }
                    foto.cropR?.let { put("cropR", it.toDouble()) }
                    foto.cropB?.let { put("cropB", it.toDouble()) }
                    put("heeftBewerking", foto.bewerktUri != null)
                },
            )
        }

        val keuzeLijst = JSONArray()
        keuzes.forEach { keuze ->
            val mapNaam = mapNaamPerSerie[keuze.serieId] ?: return@forEach
            val foto = fotoPerId[keuze.fotoId] ?: return@forEach
            keuzeLijst.put(
                JSONObject().apply {
                    put("serie", mapNaam)
                    put("dagSleutel", keuze.dagSleutel.toString())
                    put("bestandsnaam", foto.bestandsnaam)
                },
            )
        }

        return JSONObject().apply {
            put("versie", VERSIE)
            put("geschrevenOp", Instant.now().toString())
            put("series", serieLijst)
            put("fotos", fotoLijst)
            put("dagkeuzes", keuzeLijst)
        }.toString(2)
    }

    fun lees(tekst: String): MetadataInhoud? = runCatching {
        val wortel = JSONObject(tekst)

        val series = wortel.optJSONArray("series").objecten().mapNotNull { obj ->
            MetadataSerie(
                naam = obj.optString("naam").ifBlank { return@mapNotNull null },
                mapNaam = obj.optString("mapNaam").ifBlank { return@mapNotNull null },
                aangemaaktOp = obj.momentOf("aangemaaktOp", Instant.now()),
                lensRichting = runCatching { LensRichting.valueOf(obj.optString("lensRichting")) }
                    .getOrDefault(LensRichting.ACHTER),
                volgorde = obj.optInt("volgorde", 0),
                gearchiveerd = obj.optBoolean("gearchiveerd", false),
            )
        }

        val fotos = wortel.optJSONArray("fotos").objecten().mapNotNull { obj ->
            val dag = runCatching { LocalDate.parse(obj.optString("dagSleutel")) }.getOrNull()
                ?: return@mapNotNull null
            MetadataFoto(
                serieMapNaam = obj.optString("serie").ifBlank { return@mapNotNull null },
                bestandsnaam = obj.optString("bestandsnaam").ifBlank { return@mapNotNull null },
                gemaaktOp = obj.momentOf("gemaaktOp", Instant.now()),
                dagSleutel = dag,
                bron = runCatching { Bron.valueOf(obj.optString("bron")) }.getOrDefault(Bron.CAMERA),
                breedte = obj.optInt("breedte", 0),
                hoogte = obj.optInt("hoogte", 0),
                datumOnzeker = obj.optBoolean("datumOnzeker", false),
                rotatie = obj.optInt("rotatie", 0),
                rechtzetHoek = obj.optDouble("rechtzetHoek", 0.0).toFloat(),
                cropL = obj.floatOf("cropL"),
                cropT = obj.floatOf("cropT"),
                cropR = obj.floatOf("cropR"),
                cropB = obj.floatOf("cropB"),
                heeftBewerking = obj.optBoolean("heeftBewerking", false),
            )
        }

        val keuzes = wortel.optJSONArray("dagkeuzes").objecten().mapNotNull { obj ->
            val dag = runCatching { LocalDate.parse(obj.optString("dagSleutel")) }.getOrNull()
                ?: return@mapNotNull null
            MetadataKeuze(
                serieMapNaam = obj.optString("serie").ifBlank { return@mapNotNull null },
                dagSleutel = dag,
                bestandsnaam = obj.optString("bestandsnaam").ifBlank { return@mapNotNull null },
            )
        }

        MetadataInhoud(series, fotos, keuzes)
    }.getOrNull()

    private fun JSONArray?.objecten(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun JSONObject.momentOf(sleutel: String, terugval: Instant): Instant =
        runCatching { Instant.parse(optString(sleutel)) }.getOrDefault(terugval)

    private fun JSONObject.floatOf(sleutel: String): Float? =
        if (has(sleutel)) optDouble(sleutel).takeIf { !it.isNaN() }?.toFloat() else null
}
