package nl.constantdynamics.everyday

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.constantdynamics.everyday.data.FotoRepository
import nl.constantdynamics.everyday.data.SerieRepository
import nl.constantdynamics.everyday.data.db.EverydayDatabase
import nl.constantdynamics.everyday.data.media.FotoLader
import nl.constantdynamics.everyday.data.media.GhostCache
import nl.constantdynamics.everyday.data.media.MediaOpslag
import nl.constantdynamics.everyday.data.opslag.Instellingen

/**
 * Handgeschreven afhankelijkhedencontainer. Bewust geen DI-framework: één module,
 * een handvol objecten, en zo blijft de build vrij van annotatieverwerking.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Voor onderhoud dat losstaat van welk scherm dan ook. */
    val toepassingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: EverydayDatabase by lazy {
        Room.databaseBuilder(appContext, EverydayDatabase::class.java, "everyday.db").build()
    }

    val mediaOpslag: MediaOpslag by lazy { MediaOpslag(appContext) }

    val fotoLader: FotoLader by lazy { FotoLader(appContext) }

    val ghostCache: GhostCache by lazy { GhostCache(appContext, fotoLader) }

    val instellingen: Instellingen by lazy { Instellingen(appContext) }

    val serieRepository: SerieRepository by lazy { SerieRepository(database.serieDao()) }

    val fotoRepository: FotoRepository by lazy {
        FotoRepository(
            fotoDao = database.fotoDao(),
            dagKeuzeDao = database.dagKeuzeDao(),
            mediaOpslag = mediaOpslag,
            instellingen = instellingen,
            ghostCache = ghostCache,
        )
    }

    /** Wordt bij het starten van de app aangeroepen; mag rustig even duren. */
    fun startOnderhoud() {
        toepassingsScope.launch {
            runCatching { fotoRepository.ruimPrullenbakOp() }
        }
    }
}
