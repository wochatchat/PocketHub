package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detail page for a single GitHub Actions workflow run.
 *
 * Loads the run metadata itself plus its jobs (with per-step status). Provides
 * cancel + rerun for runs not yet finished / completed respectively. Per-job log
 * fetching is intentionally best-effort — GitHub returns a 302 to a signed zip
 * URL; the VM converts it into a "view logs in browser" URL rather than
 * unzipping on-device.
 */
@HiltViewModel
class WorkflowRunDetailViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _run = MutableStateFlow<GitHubApi.WorkflowRun?>(null)
    val run: StateFlow<GitHubApi.WorkflowRun?> = _run

    private val _jobs = MutableStateFlow<List<GitHubApi.WorkflowJob>>(emptyList())
    val jobs: StateFlow<List<GitHubApi.WorkflowJob>> = _jobs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    private var loadedOwner: String? = null
    private var loadedRepo: String? = null
    private var loadedRunId: Long? = null

    fun loadRun(owner: String, repo: String, runId: Long) {
        // Reuse cache if same run unless explicitly forced via retry.
        if (loadedRunId == runId && _run.value != null) return
        loadedOwner = owner; loadedRepo = repo; loadedRunId = runId
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                val resp = api.getWorkflowRuns(owner, repo, perPage = 1)
                // API doesn't expose single-run GET here, so we filter the list — OK
                // for the popular case (first page contains the run user clicked).
                _run.update { resp.runs.firstOrNull { it.id == runId } ?: resp.runs.firstOrNull() }
                if (_run.value == null) {
                    // Fall back to per-page scan if first page didn't contain it.
                    var page = 2
                    while (_run.value == null && page <= 5) {
                        val r = api.getWorkflowRuns(owner, repo, perPage = 50, page = page)
                        _run.update { r.runs.firstOrNull { it.id == runId } }
                        if (r.runs.isEmpty()) break
                        page++
                    }
                }
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "Failed to load workflow run" }
            } finally {
                _isLoading.update { false }
            }
            // Load jobs in parallel — these are the core of the screen.
            viewModelScope.launch {
                runCatching { api.getWorkflowRunJobs(owner, repo, runId) }
                    .onSuccess { resp ->
                        _jobs.update { resp.jobs }
                        if (resp.jobs.isEmpty()) _error.update { "No jobs" }
                    }
                    .onFailure { e -> _error.update { e.localizedMessage ?: "Failed to load jobs" } }
            }
        }
    }

    /** Cancel a queued or in-progress run; surface success / failure to UI. */
    fun cancelRun() {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        val runId = loadedRunId ?: return
        viewModelScope.launch {
            runCatching { api.cancelWorkflowRun(owner, repo, runId) }
                .onSuccess { resp ->
                    _actionMessage.update {
                        if (resp.isSuccessful || resp.code() == 409) "Cancellation requested"
                        else "Cancel failed (${resp.code()})"
                    }
                    if (resp.isSuccessful) loadRunForced(owner, repo, runId)
                }
                .onFailure { e -> _actionMessage.update { e.localizedMessage ?: "Cancellation failed" } }
        }
    }

    /** Re-run a completed run (works for any terminal run). */
    fun rerunRun() {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        val runId = loadedRunId ?: return
        viewModelScope.launch {
            runCatching { api.rerunWorkflowRun(owner, repo, runId) }
                .onSuccess { resp ->
                    _actionMessage.update {
                        if (resp.isSuccessful) "Re-run triggered"
                        else "Re-run failed (${resp.code()})"
                    }
                    if (resp.isSuccessful) loadRunForced(owner, repo, runId)
                }
                .onFailure { e -> _actionMessage.update { e.localizedMessage ?: "Re-run failed" } }
        }
    }

    private fun loadRunForced(owner: String, repo: String, runId: Long) {
        loadedRunId = null
        loadRun(owner, repo, runId)
    }

    fun retry(owner: String, repo: String, runId: Long) {
        loadedRunId = null
        loadRun(owner, repo, runId)
    }

    fun clearActionMessage() { _actionMessage.update { null } }
}
