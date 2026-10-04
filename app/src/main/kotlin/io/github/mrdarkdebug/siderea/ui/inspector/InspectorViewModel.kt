package io.github.mrdarkdebug.siderea.ui.inspector

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityJson
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityReport
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityRepository
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityState
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilitySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

data class InspectorState(
    val loading: Boolean = true,
    val report: CapabilityReport? = null,
    val error: String? = null,
    /** Camera ids whose detail is open. */
    val expanded: Set<String> = emptySet(),
)

sealed interface InspectorIntent {
    data object Refresh : InspectorIntent

    data class ToggleCamera(
        val id: String,
    ) : InspectorIntent

    data object CopyJson : InspectorIntent

    data object ShareReport : InspectorIntent
}

/** One-shot things the screen must do exactly once: they are not state. */
sealed interface InspectorEffect {
    data class CopyToClipboard(
        val json: String,
    ) : InspectorEffect

    data class Share(
        val uri: Uri,
    ) : InspectorEffect

    data object ShareFailed : InspectorEffect
}

@HiltViewModel
class InspectorViewModel
    @Inject
    constructor(
        private val capabilities: CapabilityRepository,
        private val exporter: ReportExporter,
    ) : ViewModel() {
        /** Null until the user toggles a card: then the default (first camera open) no longer applies. */
        private val expandedOverride = MutableStateFlow<Set<String>?>(null)

        val state: StateFlow<InspectorState> =
            combine(capabilities.state, expandedOverride) { capability, override ->
                when (capability) {
                    CapabilityState.Loading -> {
                        InspectorState(loading = true, expanded = override.orEmpty())
                    }

                    is CapabilityState.Failed -> {
                        InspectorState(loading = false, error = capability.message)
                    }

                    is CapabilityState.Ready -> {
                        InspectorState(
                            loading = false,
                            report = capability.report,
                            expanded = override ?: defaultExpanded(capability.report),
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InspectorState())

        private val effectChannel = Channel<InspectorEffect>(Channel.BUFFERED)
        val effects: Flow<InspectorEffect> = effectChannel.receiveAsFlow()

        init {
            viewModelScope.launch { capabilities.ensureLoaded() }
        }

        fun onIntent(intent: InspectorIntent) {
            when (intent) {
                InspectorIntent.Refresh -> {
                    viewModelScope.launch { capabilities.refresh() }
                }

                is InspectorIntent.ToggleCamera -> {
                    toggle(intent.id)
                }

                InspectorIntent.CopyJson -> {
                    withReport { report ->
                        effectChannel.send(InspectorEffect.CopyToClipboard(encode(report)))
                    }
                }

                InspectorIntent.ShareReport -> {
                    withReport { report ->
                        val json = encode(report)
                        val label = CapabilitySummary.from(report).deviceName
                        val effect =
                            try {
                                InspectorEffect.Share(exporter.writeForSharing(json, label))
                            } catch (_: IOException) {
                                InspectorEffect.ShareFailed
                            }
                        effectChannel.send(effect)
                    }
                }
            }
        }

        private fun toggle(id: String) {
            val report = (capabilities.state.value as? CapabilityState.Ready)?.report
            expandedOverride.update { current ->
                val open = current ?: report?.let(::defaultExpanded).orEmpty()
                if (id in open) open - id else open + id
            }
        }

        private fun withReport(block: suspend (CapabilityReport) -> Unit) {
            val report = (capabilities.state.value as? CapabilityState.Ready)?.report ?: return
            viewModelScope.launch { block(report) }
        }

        /** JSON of a big report is built off the main thread. */
        private suspend fun encode(report: CapabilityReport): String =
            withContext(Dispatchers.Default) { CapabilityJson.encode(report) }

        private fun defaultExpanded(report: CapabilityReport): Set<String> =
            report.cameras
                .firstOrNull()
                ?.let { setOf(it.id) }
                .orEmpty()

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
