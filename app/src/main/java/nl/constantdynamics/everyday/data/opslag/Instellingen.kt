package nl.constantdynamics.everyday.data.opslag

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class Themakeuze { LICHT, DONKER, SYSTEEM }

private val Context.instellingenStore: DataStore<Preferences> by preferencesDataStore(name = "instellingen")

class Instellingen(private val context: Context) {

    /** Uur waarop een nieuwe dag begint. 0 = gewone kalenderdag. */
    val dagStartUur: Flow<Int> =
        context.instellingenStore.data.map { it[SLEUTEL_DAG_START_UUR] ?: 0 }

    /** Standaarddekking van de ghost overlay, 0f..1f. */
    val ghostDekking: Flow<Float> =
        context.instellingenStore.data.map { it[SLEUTEL_GHOST_DEKKING] ?: 0.4f }

    val thema: Flow<Themakeuze> = context.instellingenStore.data.map { voorkeuren ->
        voorkeuren[SLEUTEL_THEMA]?.let { runCatching { Themakeuze.valueOf(it) }.getOrNull() }
            ?: Themakeuze.SYSTEEM
    }

    suspend fun huidigDagStartUur(): Int = dagStartUur.first()

    suspend fun zetDagStartUur(uur: Int) {
        context.instellingenStore.edit { it[SLEUTEL_DAG_START_UUR] = uur.coerceIn(0, 12) }
    }

    suspend fun zetGhostDekking(dekking: Float) {
        context.instellingenStore.edit { it[SLEUTEL_GHOST_DEKKING] = dekking.coerceIn(0f, 1f) }
    }

    suspend fun zetThema(keuze: Themakeuze) {
        context.instellingenStore.edit { it[SLEUTEL_THEMA] = keuze.name }
    }

    private companion object {
        val SLEUTEL_DAG_START_UUR = intPreferencesKey("dag_start_uur")
        val SLEUTEL_GHOST_DEKKING = floatPreferencesKey("ghost_dekking")
        val SLEUTEL_THEMA = stringPreferencesKey("thema")
    }
}
