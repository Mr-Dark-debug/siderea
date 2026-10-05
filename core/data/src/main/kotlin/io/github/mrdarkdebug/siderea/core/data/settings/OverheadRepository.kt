package io.github.mrdarkdebug.siderea.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The per-frame capture overhead measured on this phone for each photo format, so the minimum interval
 * can be shown as a measured number even before a new session has taken a single frame.
 */
class OverheadRepository(
    private val store: DataStore<Preferences>,
) {
    /** Format name (`JPEG`, `RAW`, `RAW_JPEG`) to measured overhead in milliseconds. */
    val measured: Flow<Map<String, Long>> =
        store.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs -> FORMATS.mapNotNull { name -> prefs[key(name)]?.let { name to it } }.toMap() }

    suspend fun save(
        formatName: String,
        overheadMs: Long,
    ) {
        store.edit { it[key(formatName)] = overheadMs }
    }

    private fun key(formatName: String) = longPreferencesKey("overhead_ms_$formatName")

    private companion object {
        val FORMATS = listOf("JPEG", "RAW", "RAW_JPEG")
    }
}
