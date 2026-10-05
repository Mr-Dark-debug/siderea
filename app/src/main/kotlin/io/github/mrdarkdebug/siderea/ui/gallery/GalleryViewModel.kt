package io.github.mrdarkdebug.siderea.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryState(
    val photos: List<GalleryPhoto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class GalleryViewModel
    @Inject
    constructor(
        val repository: GalleryRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(GalleryState())
        val state = mutable.asStateFlow()
        private var refreshJob: Job? = null

        init {
            viewModelScope.launch {
                repository.changes().collectLatest {
                    delay(SCAN_SETTLE_MS)
                    refresh()
                }
            }
        }

        fun refresh() {
            refreshJob?.cancel()
            refreshJob =
                viewModelScope.launch {
                    mutable.value = mutable.value.copy(loading = true, error = null)
                    try {
                        mutable.value = GalleryState(repository.list(), loading = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        mutable.value = mutable.value.copy(loading = false, error = "Couldn't load photos. Try again.")
                    }
                }
        }

        private companion object {
            const val SCAN_SETTLE_MS = 250L
        }
    }
