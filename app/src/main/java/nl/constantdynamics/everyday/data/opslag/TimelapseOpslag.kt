package nl.constantdynamics.everyday.data.opslag

import nl.constantdynamics.everyday.kern.Beeldverhouding
import nl.constantdynamics.everyday.kern.DatumOpmaak
import nl.constantdynamics.everyday.kern.Overgang
import nl.constantdynamics.everyday.kern.Passing
import nl.constantdynamics.everyday.kern.Resolutie
import nl.constantdynamics.everyday.kern.Snijpunt
import nl.constantdynamics.everyday.kern.StempelPositie
import nl.constantdynamics.everyday.kern.TimelapseInstellingen
import org.json.JSONObject

/**
 * De laatst gebruikte timelapse-instellingen worden per serie onthouden. Eén klein
 * JSON-blokje per serie is genoeg; dat scheelt een tiental losse sleutels.
 */
object TimelapseOpslag {

    fun naarTekst(instellingen: TimelapseInstellingen): String = JSONObject().apply {
        put("fps", instellingen.fps)
        put("beeldverhouding", instellingen.beeldverhouding.name)
        put("resolutie", instellingen.resolutie.name)
        put("passing", instellingen.passing.name)
        put("snijpunt", instellingen.snijpunt.name)
        put("overgang", instellingen.overgang.name)
        put("overgangsduur", instellingen.overgangsduurSeconden.toDouble())
        put("stempelPositie", instellingen.stempelPositie.name)
        put("datumOpmaak", instellingen.datumOpmaak.name)
        put("stempelGrootte", instellingen.stempelGrootte.toDouble())
        instellingen.muziekUri?.let { put("muziekUri", it) }
        put("muziekVolume", instellingen.muziekVolume.toDouble())
    }.toString()

    fun uitTekst(tekst: String?): TimelapseInstellingen {
        if (tekst.isNullOrBlank()) return TimelapseInstellingen()
        return runCatching {
            val obj = JSONObject(tekst)
            val standaard = TimelapseInstellingen()
            TimelapseInstellingen(
                fps = obj.optInt("fps", standaard.fps),
                beeldverhouding = obj.enumOf("beeldverhouding", standaard.beeldverhouding) {
                    Beeldverhouding.valueOf(it)
                },
                resolutie = obj.enumOf("resolutie", standaard.resolutie) { Resolutie.valueOf(it) },
                passing = obj.enumOf("passing", standaard.passing) { Passing.valueOf(it) },
                snijpunt = obj.enumOf("snijpunt", standaard.snijpunt) { Snijpunt.valueOf(it) },
                overgang = obj.enumOf("overgang", standaard.overgang) { Overgang.valueOf(it) },
                overgangsduurSeconden = obj
                    .optDouble("overgangsduur", standaard.overgangsduurSeconden.toDouble())
                    .toFloat(),
                stempelPositie = obj.enumOf("stempelPositie", standaard.stempelPositie) {
                    StempelPositie.valueOf(it)
                },
                datumOpmaak = obj.enumOf("datumOpmaak", standaard.datumOpmaak) {
                    DatumOpmaak.valueOf(it)
                },
                stempelGrootte = obj
                    .optDouble("stempelGrootte", standaard.stempelGrootte.toDouble())
                    .toFloat(),
                muziekUri = obj.optString("muziekUri").ifBlank { null },
                muziekVolume = obj
                    .optDouble("muziekVolume", standaard.muziekVolume.toDouble())
                    .toFloat(),
            )
        }.getOrDefault(TimelapseInstellingen())
    }

    private inline fun <T> JSONObject.enumOf(sleutel: String, terugval: T, maak: (String) -> T): T {
        val tekst = optString(sleutel)
        if (tekst.isBlank()) return terugval
        return runCatching { maak(tekst) }.getOrDefault(terugval)
    }
}
