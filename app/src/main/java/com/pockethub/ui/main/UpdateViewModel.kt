package com.pockethub.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.SettingsRepository
import com.pockethub.data.remote.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns in-app auto-update detection. Triggered automatically a short delay after
 * launch and manually from Settings. Exposes the latest known [UpdateChecker.UpdateInfo]
 * plus a loading flag for manual checks.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updater: UpdateChecker,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** The repo to check against — public PocketHub repository. */
    private val owner = "wochatchat"
    private val repo = "PocketHub"

    /** Minimum gap between two automatic background checks (24h). */
    private val autoCheckIntervalMs = 24L * 60 * 60 * 1000

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpdateAvailable(val info: UpdateChecker.UpdateInfo) : State
        data object UpToDate : State
        data object Error : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Run an automatic check, throttled by [autoCheckIntervalMs]. Pre-releases are
     * skipped — only stable releases prompt the dialog. Ignored versions are respected.
     */
    fun maybeAutoCheck() {
        viewModelScope.launch {
            val lastMs = settings.getLastUpdateCheckMs()
            if (System.currentTimeMillis() - lastMs < autoCheckIntervalMs) return@launch
            settings.markUpdateCheckedNow()
            runCheck(includePre = false)
        }
    }

    /** Force an immediate check (from Settings → "Check for updates"). */
    fun manualCheck() {
        _state.value = State.Checking
        viewModelScope.launch {
            settings.markUpdateCheckedNow()
            runCheck(includePre = false)
        }
    }

    private suspend fun runCheck(includePre: Boolean) {
        val info = updater.fetchLatest(owner, repo, includePre)
        if (info == null) {
            _state.value = State.Error
            return
        }
        val ignored = settings.ignoredUpdateVersion.first()
        if (updater.isNewer(info) && info.latestVersionName != ignored) {
            _state.value = State.UpdateAvailable(info)
        } else {
            _state.value = State.UpToDate
        }
    }

    /** User dismissed the dialog for this version; persist so we don't nag. */
    fun ignoreVersion(version: String) {
        viewModelScope.launch {
            settings.setIgnoredUpdateVersion(version)
            _state.value = State.Idle
        }
    }

    /** User dismissed the dialog without ignoring; just close it. */
    fun dismiss() {
        _state.value = State.Idle
    }

    companion object {
        const val REPO_OWNER = "wochatchat"
        const val REPO_NAME = "PocketHub"
    }
}
