package io.github.mrdarkdebug.siderea.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** User preferences. Grows with each milestone; every field has a safe default. */
data class AppSettings(
    /** Pure-red night-vision UI. */
    val redMode: Boolean = false,
    val hapticsEnabled: Boolean = true,
)

/** Typed access to the settings DataStore. */
class SettingsRepository(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> =
        store.data
            // An unreadable settings file must never stop the camera app from opening: fall back to defaults.
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs ->
                AppSettings(
                    redMode = prefs[RED_MODE] ?: AppSettings().redMode,
                    hapticsEnabled = prefs[HAPTICS] ?: AppSettings().hapticsEnabled,
                )
            }

    suspend fun setRedMode(enabled: Boolean) {
        store.edit { it[RED_MODE] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        store.edit { it[HAPTICS] = enabled }
    }

    /** Restores every setting to its default. */
    suspend fun reset() {
        store.edit { it.clear() }
    }

    private companion object {
        val RED_MODE = booleanPreferencesKey("red_mode")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
    }
}
