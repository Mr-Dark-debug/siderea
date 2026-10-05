package io.github.mrdarkdebug.siderea.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Remembers the camera's last-used state (lens, format, exposure settings, aids) as one opaque JSON blob.
 * The blob's schema belongs to the camera feature, so this module only stores and returns text.
 */
class CameraStateRepository(
    private val store: DataStore<Preferences>,
) {
    val json: Flow<String?> =
        store.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { it[KEY] }

    suspend fun save(json: String) {
        store.edit { it[KEY] = json }
    }

    private companion object {
        val KEY = stringPreferencesKey("camera_state_v1")
    }
}
