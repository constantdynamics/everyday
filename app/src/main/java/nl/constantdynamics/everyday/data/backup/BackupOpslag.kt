package nl.constantdynamics.everyday.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.constantdynamics.everyday.data.db.BackupTaakEntiteit
import nl.constantdynamics.everyday.data.opslag.Instellingen

/**
 * De backupmap die jij zelf kiest. Kan op interne opslag of op een SD-kaart staan;
 * valt die laatste weg, dan is de map simpelweg onbereikbaar en blijft het werk in
 * de wachtrij staan.
 */
class BackupOpslag(
    private val context: Context,
    private val instellingen: Instellingen,
) {

    private val resolver get() = context.contentResolver

    /** Onthoudt de gekozen map en vraagt blijvende toegang aan. */
    suspend fun onthoudMap(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            instellingen.zetBackupMapUri(uri.toString())
            true
        }.getOrDefault(false)
    }

    suspend fun vergeetMap() {
        instellingen.zetBackupMapUri(null)
    }

    /**
     * De backupmap, of null als er geen is gekozen, de toestemming is ingetrokken of
     * het volume op dit moment niet is aangekoppeld.
     */
    suspend fun map(): DocumentFile? = withContext(Dispatchers.IO) {
        val tekst = instellingen.huidigeBackupMapUri() ?: return@withContext null
        val uri = runCatching { Uri.parse(tekst) }.getOrNull() ?: return@withContext null
        val magSchrijven = resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        if (!magSchrijven) return@withContext null
        runCatching { DocumentFile.fromTreeUri(context, uri) }
            .getOrNull()
            ?.takeIf { it.exists() && it.canWrite() }
    }

    suspend fun bereikbaar(): Boolean = map() != null

    /** Kopieert één bestand. Bestaat het al in de backupmap, dan is het werk klaar. */
    suspend fun kopieer(taak: BackupTaakEntiteit): Boolean = withContext(Dispatchers.IO) {
        val wortel = map() ?: return@withContext false
        val serieMap = mapOfMaak(wortel, taak.serieMapNaam) ?: return@withContext false
        val doelMap = taak.submap?.let { mapOfMaak(serieMap, it) } ?: serieMap
        val bestaand = doelMap.findFile(taak.bestandsnaam)
        if (bestaand != null && bestaand.length() > 0L) return@withContext true

        val doel = bestaand ?: doelMap.createFile("image/jpeg", taak.bestandsnaam)
        ?: return@withContext false
        runCatching {
            resolver.openInputStream(Uri.parse(taak.bronUri)).use { bron ->
                requireNotNull(bron)
                resolver.openOutputStream(doel.uri, "wt").use { doelStroom ->
                    requireNotNull(doelStroom)
                    bron.copyTo(doelStroom)
                }
            }
            true
        }.getOrElse {
            // Een half geschreven bestand is erger dan geen bestand.
            runCatching { doel.delete() }
            false
        }
    }

    suspend fun schrijfMetadata(inhoud: String): Boolean = withContext(Dispatchers.IO) {
        val wortel = map() ?: return@withContext false
        val doel = wortel.findFile(Metadata.BESTANDSNAAM)
            ?: wortel.createFile("application/json", Metadata.BESTANDSNAAM)
            ?: return@withContext false
        runCatching {
            resolver.openOutputStream(doel.uri, "wt").use { stroom ->
                requireNotNull(stroom)
                stroom.write(inhoud.toByteArray())
            }
            true
        }.getOrDefault(false)
    }

    suspend fun leesMetadata(): String? = withContext(Dispatchers.IO) {
        val wortel = map() ?: return@withContext null
        val bestand = wortel.findFile(Metadata.BESTANDSNAAM) ?: return@withContext null
        runCatching {
            resolver.openInputStream(bestand.uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
    }

    /** Zoekt een bestand in de backupmap terug, voor herstellen. */
    suspend fun zoekBestand(serieMapNaam: String, bestandsnaam: String, submap: String? = null): Uri? =
        withContext(Dispatchers.IO) {
            val wortel = map() ?: return@withContext null
            val serieMap = wortel.findFile(serieMapNaam)?.takeIf { it.isDirectory }
                ?: return@withContext null
            val doelMap = submap?.let { serieMap.findFile(it)?.takeIf { m -> m.isDirectory } }
                ?: serieMap
            doelMap.findFile(bestandsnaam)?.takeIf { it.isFile }?.uri
        }

    /** Verplaatst een definitief verwijderde kopie naar _verwijderd, in plaats van hem te wissen. */
    suspend fun verplaatsNaarVerwijderd(serieMapNaam: String, bestandsnaam: String): Boolean =
        withContext(Dispatchers.IO) {
            val wortel = map() ?: return@withContext false
            val serieMap = wortel.findFile(serieMapNaam)?.takeIf { it.isDirectory }
                ?: return@withContext false
            val bron = serieMap.findFile(bestandsnaam) ?: return@withContext true
            val prullenbak = mapOfMaak(wortel, MAP_VERWIJDERD) ?: return@withContext false
            val doel = prullenbak.findFile(bestandsnaam)
                ?: prullenbak.createFile("image/jpeg", bestandsnaam)
                ?: return@withContext false
            runCatching {
                resolver.openInputStream(bron.uri).use { invoer ->
                    requireNotNull(invoer)
                    resolver.openOutputStream(doel.uri, "wt").use { uitvoer ->
                        requireNotNull(uitvoer)
                        invoer.copyTo(uitvoer)
                    }
                }
                bron.delete()
                true
            }.getOrDefault(false)
        }

    private fun mapOfMaak(ouder: DocumentFile, naam: String): DocumentFile? =
        ouder.findFile(naam)?.takeIf { it.isDirectory } ?: ouder.createDirectory(naam)

    companion object {
        const val MAP_VERWIJDERD = "_verwijderd"
    }
}
