package io.github.mrdarkdebug.siderea.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mrdarkdebug.siderea.core.data.settings.AppSettings
import io.github.mrdarkdebug.siderea.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            repository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

        fun setRedMode(enabled: Boolean) {
            viewModelScope.launch { repository.setRedMode(enabled) }
        }

        fun setHapticsEnabled(enabled: Boolean) {
            viewModelScope.launch { repository.setHapticsEnabled(enabled) }
        }

        fun reset() {
            viewModelScope.launch { repository.reset() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
