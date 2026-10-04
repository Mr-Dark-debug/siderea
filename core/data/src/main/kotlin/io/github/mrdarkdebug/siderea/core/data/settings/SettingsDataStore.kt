package io.github.mrdarkdebug.siderea.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.sideriaSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "siderea_settings")

/** The one process-wide settings DataStore. */
fun Context.settingsDataStore(): DataStore<Preferences> = sideriaSettingsStore
