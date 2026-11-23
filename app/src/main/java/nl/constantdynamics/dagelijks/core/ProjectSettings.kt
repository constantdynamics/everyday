package nl.constantdynamics.dagelijks.core

import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Everything a project remembers. It is written to project.json inside the project
 * folder, so picking the folder again after a reinstall brings it all back.
 */
data class ProjectSettings(
    val name: String,
    val lens: Lens,
    val aspectRatio: AspectRatio,
    /** How visible the previous photo is over the camera image: 0 (hidden) to 100 (opaque). */
    val overlayPercent: Int = DEFAULT_OVERLAY_PERCENT,
    /** Photos per second in the timelapse. */
    val timelapseFps: Int = DEFAULT_TIMELAPSE_FPS,
    val createdOn: LocalDate? = null,
) {
    companion object {
        const val DEFAULT_OVERLAY_PERCENT = 35
        const val DEFAULT_TIMELAPSE_FPS = 10
        const val MIN_TIMELAPSE_FPS = 1
        const val MAX_TIMELAPSE_FPS = 30
    }
}

object ProjectSettingsJson {

    private const val FORMAT_VERSION = 1

    private const val KEY_VERSION = "versie"
    private const val KEY_NAME = "naam"
    private const val KEY_LENS = "camera"
    private const val KEY_ASPECT = "beeldverhouding"
    private const val KEY_OVERLAY = "overlayZichtbaarheid"
    private const val KEY_FPS = "timelapseFotosPerSeconde"
    private const val KEY_CREATED = "aangemaakt"

    fun encode(settings: ProjectSettings): String {
        val json = JSONObject()
        json.put(KEY_VERSION, FORMAT_VERSION)
        json.put(KEY_NAME, settings.name)
        json.put(KEY_LENS, settings.lens.id)
        json.put(KEY_ASPECT, settings.aspectRatio.id)
        json.put(KEY_OVERLAY, settings.overlayPercent.coerceIn(0, 100))
        json.put(KEY_FPS, settings.timelapseFps.coerceIn(ProjectSettings.MIN_TIMELAPSE_FPS, ProjectSettings.MAX_TIMELAPSE_FPS))
        settings.createdOn?.let { json.put(KEY_CREATED, it.toString()) }
        return json.toString(2) + "\n"
    }

    /**
     * Reads project.json. Returns null when the file is unreadable or misses the
     * parts that cannot be guessed (camera and aspect ratio); the caller then
     * reconstructs the project from the photos instead of losing it.
     */
    fun decode(text: String, fallbackName: String): ProjectSettings? {
        val json = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return null
        }
        val lens = Lens.fromId(json.optString(KEY_LENS, "")) ?: return null
        val aspectRatio = AspectRatio.fromId(json.optString(KEY_ASPECT, "")) ?: return null
        val name = json.optString(KEY_NAME, "").trim().ifEmpty { fallbackName }
        val created = try {
            json.optString(KEY_CREATED, "").takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        }
        return ProjectSettings(
            name = name,
            lens = lens,
            aspectRatio = aspectRatio,
            overlayPercent = json.optInt(KEY_OVERLAY, ProjectSettings.DEFAULT_OVERLAY_PERCENT).coerceIn(0, 100),
            timelapseFps = json.optInt(KEY_FPS, ProjectSettings.DEFAULT_TIMELAPSE_FPS)
                .coerceIn(ProjectSettings.MIN_TIMELAPSE_FPS, ProjectSettings.MAX_TIMELAPSE_FPS),
            createdOn = created,
        )
    }
}
