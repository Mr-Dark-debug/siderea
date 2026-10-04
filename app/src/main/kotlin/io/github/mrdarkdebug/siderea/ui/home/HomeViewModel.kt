package io.github.mrdarkdebug.siderea.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityRepository
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityState
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilitySummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val summary: CapabilitySummary,
    ) : HomeUiState

    data class Failed(
        val message: String,
    ) : HomeUiState
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val capabilities: CapabilityRepository,
    ) : ViewModel() {
        val state: StateFlow<HomeUiState> =
            capabilities.state
                .map { state ->
                    when (state) {
                        CapabilityState.Loading -> HomeUiState.Loading
                        is CapabilityState.Ready -> HomeUiState.Ready(CapabilitySummary.from(state.report))
                        is CapabilityState.Failed -> HomeUiState.Failed(state.message)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState.Loading)

        init {
            viewModelScope.launch { capabilities.ensureLoaded() }
        }

        fun retry() {
            viewModelScope.launch { capabilities.refresh() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
