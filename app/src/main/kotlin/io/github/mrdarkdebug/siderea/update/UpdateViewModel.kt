package io.github.mrdarkdebug.siderea.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateState(
    val automatic: Boolean = true,
    val checking: Boolean = false,
    val candidate: AppUpdate? = null,
    val progress: Float? = null,
    val readyVersion: String? = null,
    val message: String? = null,
    val showPrompt: Boolean = false,
)

@HiltViewModel
// IO, package parsing and activity launch failures must all become retryable UI states.
@Suppress("TooGenericExceptionCaught")
class UpdateViewModel
    @Inject
    constructor(
        private val repository: UpdateRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(UpdateState(automatic = repository.prefs.getBoolean("automatic", true)))
        val state = mutable.asStateFlow()
        private var job: Job? = null

        fun foreground() {
            if (job?.isActive == true) return
            job =
                viewModelScope.launch {
                    if (repository.pending() != null) {
                        mutable.update { it.copy(readyVersion = repository.prefs.getString("pending_version", null)) }
                    }
                    val last = repository.prefs.getLong("last_check", 0)
                    if (state.value.automatic &&
                        System.currentTimeMillis() - last >= CHECK_INTERVAL_MS
                    ) {
                        checkNow(automatic = true)
                    }
                }
        }

        fun setAutomatic(enabled: Boolean) {
            repository.prefs
                .edit()
                .putBoolean("automatic", enabled)
                .apply()
            mutable.update { it.copy(automatic = enabled) }
            if (enabled) foreground()
        }

        fun check() {
            if (job?.isActive == true) return
            job = viewModelScope.launch { checkNow(automatic = false) }
        }

        private suspend fun checkNow(automatic: Boolean) {
            mutable.update { it.copy(checking = true, message = null) }
            try {
                val candidate = repository.check()
                repository.prefs
                    .edit()
                    .putLong("last_check", System.currentTimeMillis())
                    .apply()
                mutable.update {
                    it.copy(
                        checking = false,
                        candidate = candidate,
                        message = if (candidate == null) "You're up to date" else null,
                    )
                }
                if (candidate != null && state.value.readyVersion != candidate.version && automatic &&
                    repository.unmetered()
                ) {
                    downloadNow(candidate)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(checking = false, message = e.message ?: "Couldn't check for updates.") }
            }
        }

        fun download() {
            val candidate = state.value.candidate ?: return
            if (job?.isActive == true) return
            job = viewModelScope.launch { downloadNow(candidate) }
        }

        private suspend fun downloadNow(candidate: AppUpdate) {
            mutable.update { it.copy(progress = 0f, message = null) }
            try {
                repository.download(candidate) { progress -> mutable.update { it.copy(progress = progress) } }
                mutable.update { it.copy(progress = null, readyVersion = candidate.version, showPrompt = true) }
            } catch (e: CancellationException) {
                mutable.update { it.copy(progress = null, message = "Download cancelled") }
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(progress = null, message = e.message ?: "Couldn't download the update.") }
            }
        }

        fun cancel() {
            job?.cancel()
        }

        fun dismiss() {
            mutable.update { it.copy(showPrompt = false) }
        }

        fun install(context: Context) {
            if (job?.isActive == true) return
            job =
                viewModelScope.launch {
                    try {
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                            mutable.update {
                                it.copy(
                                    message = "Allow Siderea to install updates, then tap Install again.",
                                )
                            }
                            return@launch
                        }
                        val file = repository.installerFile()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                clipData = ClipData.newRawUri("Siderea update", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                        )
                        dismiss()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        mutable.update {
                            it.copy(
                                readyVersion = null,
                                showPrompt = false,
                                message = e.message ?: "Couldn't open Android's installer. Check for updates to retry.",
                            )
                        }
                    }
                }
        }

        private companion object {
            const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        }
    }
