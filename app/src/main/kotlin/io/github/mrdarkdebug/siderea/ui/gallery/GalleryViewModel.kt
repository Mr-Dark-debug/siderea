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
    val busy: Boolean = false,
    val notice: String? = null,
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
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
                        mutable.value = mutable.value.copy(photos = repository.list(), loading = false, error = null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        mutable.value = mutable.value.copy(loading = false, error = "Couldn't load photos. Try again.")
                    }
                }
        }

        fun delete(
            photos: List<GalleryPhoto>,
            onDone: () -> Unit,
        ) {
            if (state.value.busy) return
            viewModelScope.launch {
                mutable.value = mutable.value.copy(busy = true, notice = null)
                var deleted = 0
                var failure: String? = null
                try {
                    photos.forEach { photo ->
                        try {
                            repository.delete(photo)
                            deleted++
                        } catch (
                            e: CancellationException,
                        ) {
                            throw e
                        } catch (e: Exception) {
                            failure = e.message ?: "Couldn't delete this photo."
                        }
                    }
                    mutable.value =
                        mutable.value.copy(
                            photos = repository.list(),
                            notice =
                                failure ?: "Deleted $deleted ${if (deleted == 1) "photo" else "photos"}",
                        )
                    onDone()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutable.value = mutable.value.copy(notice = e.message ?: "Couldn't refresh the gallery. Try again.")
                    onDone()
                } finally {
                    mutable.value = mutable.value.copy(busy = false)
                }
            }
        }

        fun clearNotice() {
            mutable.value = mutable.value.copy(notice = null)
        }

        private companion object {
            const val SCAN_SETTLE_MS = 250L
        }
    }
