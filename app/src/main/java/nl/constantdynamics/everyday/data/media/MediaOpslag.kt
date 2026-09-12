package nl.constantdynamics.everyday.data.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Afmeting(val breedte: Int, val hoogte: Int)

/**
 * Alles wat met bestanden op het toestel te maken heeft. Foto's gaan als gewone JPG
 * naar Pictures/Everyday/<mapNaam>/ zodat de galerij ze ziet. Er wordt nooit een
 * bestand overschreven en er worden nooit GPS-gegevens weggeschreven.
 */
class MediaOpslag(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    fun relatiefPad(mapNaam: String): String = "${Environment.DIRECTORY_PICTURES}/$HOOFDMAP/$mapNaam"

    fun zichtbaarPad(mapNaam: String): String = "Pictures/$HOOFDMAP/$mapNaam"

    /** Bijvoorbeeld: mees_2026-09-11_074512.jpg — sorteert chronologisch op naam. */
    fun bestandsnaam(mapNaam: String, moment: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        mapNaam + "_" + BESTANDSNAAM_OPMAAK.format(moment.atZone(zone)) + ".jpg"

    fun opnameOpties(mapNaam: String, bestandsnaam: String, moment: Instant): ImageCapture.OutputFileOptions {
        val waarden = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, bestandsnaam)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relatiefPad(mapNaam))
            put(MediaStore.Images.Media.DATE_TAKEN, moment.toEpochMilli())
        }
        return ImageCapture.OutputFileOptions
            .Builder(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, waarden)
            .build()
    }

    /**
     * MediaStore kan een naam aanpassen bij botsing, dus we lezen terug hoe het
     * bestand daadwerkelijk heet in plaats van aan te nemen wat we hebben gevraagd.
     */
    suspend fun werkelijkeBestandsnaam(uri: Uri): String? = withContext(Dispatchers.IO) {
        resolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use { rij ->
            if (rij.moveToFirst()) rij.getString(0) else null
        }
    }

    /** Afmetingen zoals de foto getoond wordt, dus met de EXIF-oriëntatie meegerekend. */
    suspend fun afmetingen(uri: Uri): Afmeting? = withContext(Dispatchers.IO) {
        val opties = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opties) }
        }
        if (opties.outWidth <= 0 || opties.outHeight <= 0) return@withContext null
        val draaiing = draaiingUitExif(uri)
        if (draaiing == 90 || draaiing == 270) {
            Afmeting(opties.outHeight, opties.outWidth)
        } else {
            Afmeting(opties.outWidth, opties.outHeight)
        }
    }

    suspend fun draaiingUitExif(uri: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees }
        }.getOrNull() ?: 0
    }

    /**
     * Vangnet: CameraX schrijft zelf geen locatie weg zolang we die niet meegeven,
     * maar we controleren het bestand alsnog en halen alles wat op GPS lijkt eruit.
     */
    suspend fun verwijderGpsUitExif(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openFileDescriptor(uri, "rw")?.use { beschrijver ->
                val exif = ExifInterface(beschrijver.fileDescriptor)
                var gewijzigd = false
                for (label in GPS_LABELS) {
                    if (exif.getAttribute(label) != null) {
                        exif.setAttribute(label, null)
                        gewijzigd = true
                    }
                }
                if (gewijzigd) exif.saveAttributes()
                gewijzigd
            } ?: false
        }.getOrDefault(false)
    }

    /** Verwijdert het bestand definitief uit MediaStore. */
    suspend fun verwijderBestand(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    /** Leest het opnamemoment uit EXIF; wordt gebruikt bij importeren. */
    suspend fun momentUitExif(uri: Uri, zone: ZoneId = ZoneId.systemDefault()): Instant? =
        withContext(Dispatchers.IO) {
            runCatching {
                resolver.openInputStream(uri)?.use { stroom ->
                    val exif = ExifInterface(stroom)
                    val tekst = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    tekst?.let {
                        java.time.LocalDateTime.parse(it, EXIF_OPMAAK).atZone(zone).toInstant()
                    }
                }
            }.getOrNull()
        }

    companion object {
        const val HOOFDMAP = "Everyday"

        private val BESTANDSNAAM_OPMAAK: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

        private val EXIF_OPMAAK: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

        private val GPS_LABELS = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_VERSION_ID,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_DIFFERENTIAL,
        )
    }
}
