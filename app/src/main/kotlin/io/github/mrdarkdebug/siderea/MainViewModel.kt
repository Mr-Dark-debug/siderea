package io.github.mrdarkdebug.siderea

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mrdarkdebug.siderea.core.data.settings.AppSettings
import io.github.mrdarkdebug.siderea.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Activity-level state: the settings that change how the whole app looks. */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** Null until the first read, so the UI can avoid flashing the wrong theme. */
        val settings: StateFlow<AppSettings?> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
    }
