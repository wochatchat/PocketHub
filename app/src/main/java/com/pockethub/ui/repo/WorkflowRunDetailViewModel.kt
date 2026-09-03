package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import com.pockethub.util.userMessage
import androidx.lifecycle.viewModelScope
import com.pockethub.data.download.ArtifactExtractor
import com.pockethub.data.download.DownloadManager
import com.pockethub.data.local.DownloadEntity
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Detail page for a single GitHub Actions workflow run.
 *
 * Loads the run metadata itself plus its jobs (with per-step status). Provides
 * cancel + rerun for runs not yet finished / completed respectively. Per-job log
 * fetching is intentionally best-effort — GitHub returns a 302 to a signed zip
 * URL; the VM converts it into a "view logs in browser" URL rather than
 * unzipping on-device.
 *
 * Also surfaces the run's build artifacts: the artifact list comes from
 * `GET …/actions/runs/{id}/artifacts` (everything a workflow uploaded via
 * `actions/upload-artifact`, regardless of format). Downloading goes through
 * [DownloadManager] so it reuses the queue / progress / retry UI; once the zip
 * lands, [ArtifactExtractor] unpacks it and the run screen shows the file list.
 */
@HiltViewModel
class WorkflowRunDetailViewModel @Inject constructor(
    private val issueReporter: com.pockethub.data.reporting.IssueReporter,
    private val api: GitHubApi,
    private val downloadManager: DownloadManager,
    private val extractor: ArtifactExtractor,
) : ViewModel() {

    /** One artifact as shown on the run detail screen (download/extract state attached). */
    data class ArtifactUi(
        val artifact: GitHubApi.Artifact,
        val download: DownloadEntity? = null,
        val extractedFiles: List<ArtifactExtractor.ExtractedFile>? = null,
        val extracting: Boolean = false,
        val extractError: String? = null,
    )

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

    private val _artifacts = MutableStateFlow<List<ArtifactUi>>(emptyList())
    val artifacts: StateFlow<List<ArtifactUi>> = _artifacts.asStateFlow()

    private val _artifactsLoading = MutableStateFlow(false)
    val artifactsLoading: StateFlow<Boolean> = _artifactsLoading.asStateFlow()

    private val _artifactsError = MutableStateFlow<String?>(null)
    val artifactsError: StateFlow<String?> = _artifactsError

    private var loadedOwner: String? = null
    private var loadedRepo: String? = null
    private var loadedRunId: Long? = null

    private var artifactsOwner: String? = null
    private var artifactsRepo: String? = null
    private var artifactsRunId: Long? = null

    init {
        // Mirror DownloadManager state into each artifact card and auto-extract
        // once the zip download completes.
        viewModelScope.launch {
            downloadManager.allFlow().collect { downloads ->
                val byUrl = downloads.associateBy { it.url }
                _artifacts.update { list ->
                    list.map { ui -> attachDownload(ui, byUrl[ui.artifact.archiveDownloadUrl]) }
                }
            }
        }
    }

    /**
     * Attach the current download state to an artifact card. A DONE entry whose
     * extraction dir still exists is rehydrated straight from disk (no
     * re-download / re-extract); a DONE zip that was never extracted triggers
     * extraction. This is what lets card state survive screen re-entry.
     */
    private suspend fun attachDownload(ui: ArtifactUi, dl: DownloadEntity?): ArtifactUi {
        if (dl == null) return ui.copy(download = null)
        if (dl.status != "DONE") return ui.copy(download = dl)
        if (ui.extractedFiles != null || ui.extracting) return ui.copy(download = dl)
        val owner = artifactsOwner ?: return ui.copy(download = dl)
        val repo = artifactsRepo ?: return ui.copy(download = dl)
        val destDir = File(
            downloadManager.dirFor("$owner/$repo"),
            "extracted/${ui.artifact.name}-${ui.artifact.id}",
        )
        val restored = extractor.listExtracted(destDir)
        if (restored != null) {
            return ui.copy(download = dl, extracting = false, extractedFiles = restored)
        }
        triggerExtract(ui, dl)
        return ui.copy(download = dl)
    }

    /**
     * One-shot reconciliation after the artifact list loads. The downloads Room
     * Flow only emits on table changes, so on a revisit (where it fired before
     * the artifact list arrived) the DONE rows would otherwise never reach the
     * cards — they'd show stale download buttons whose taps are silently
     * no-opped by DownloadManager's dedupe.
     */
    private suspend fun syncDownloadState() {
        val byUrl = downloadManager.allFlow().first().associateBy { it.url }
        _artifacts.update { list ->
            list.map { ui -> attachDownload(ui, byUrl[ui.artifact.archiveDownloadUrl]) }
        }
    }

    fun loadRun(owner: String, repo: String, runId: Long) {
        // Reuse cache if same run unless explicitly forced via retry.
        if (loadedRunId == runId && _run.value != null) return
        loadedOwner = owner; loadedRepo = repo; loadedRunId = runId
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                // Exact single-run fetch. (An earlier implementation pulled the
                // 1-item runs list and fell back to whichever run came back —
                // that displayed the wrong run whenever the clicked run wasn't
                // the repo's newest.)
                _run.update { api.getWorkflowRun(owner, repo, runId) }
            } catch (e: Exception) {
                issueReporter.reportError("WorkflowRunDetail", "loadRun", e)
                _error.update { e.userMessage("Failed to load workflow run") }
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
                    .onFailure { e -> _error.update { e.userMessage("Failed to load jobs") } }
            }
        }
    }

    fun loadArtifacts(owner: String, repo: String, runId: Long) {
        if (artifactsRunId == runId && _artifacts.value.isNotEmpty()) return
        artifactsOwner = owner; artifactsRepo = repo; artifactsRunId = runId
        viewModelScope.launch {
            _artifactsLoading.update { true }
            _artifactsError.update { null }
            try {
                // Paginate to avoid dropping artifacts when a run uploads more
                // than one page worth (API caps per_page at 100, guard at 5 pages).
                val all = mutableListOf<GitHubApi.Artifact>()
                var page = 1
                while (true) {
                    val resp = api.getWorkflowRunArtifacts(owner, repo, runId, perPage = 100, page = page)
                    all += resp.artifacts
                    if (resp.artifacts.size < 100 || page >= 5) break
                    page++
                }
                _artifacts.update { all.map { ArtifactUi(artifact = it) } }
                syncDownloadState()
            } catch (e: Exception) {
                issueReporter.reportError("WorkflowRunDetail", "loadArtifacts", e)
                _artifactsError.update { e.userMessage("Failed to load artifacts") }
            } finally {
                _artifactsLoading.update { false }
            }
        }
    }

    /** Queue an artifact zip download through the shared download manager. */
    fun downloadArtifact(owner: String, repo: String, runId: Long, artifact: GitHubApi.Artifact) {
        if (artifact.expired) return
        val safeName = artifact.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        viewModelScope.launch {
            val existing = downloadManager.byUrl(artifact.archiveDownloadUrl)
            // Already downloaded: don't enqueue a second copy — rehydrate the
            // card (file list / retry) instead of a silent no-op when
            // DownloadManager's dedupe swallows the duplicate enqueue.
            if (existing?.status == "DONE") {
                val dl = existing.takeIf { File(existing.localPath).exists() }
                    ?: existing.copy(status = "FAILED", errorMsg = "File deleted")
                _artifacts.update { list ->
                    list.map { ui ->
                        if (ui.artifact.archiveDownloadUrl == artifact.archiveDownloadUrl) attachDownload(ui, dl)
                        else ui
                    }
                }
                if (dl.status == "DONE") return@launch
            }
            downloadManager.enqueue(
                DownloadManager.EnqueueRequest(
                    url = artifact.archiveDownloadUrl,
                    fileName = "$safeName-${artifact.id}.zip",
                    contentType = "application/zip",
                    sizeBytes = artifact.sizeInBytes,
                    repoKey = "$owner/$repo",
                    releaseTag = "run #$runId",
                )
            )
        }
    }

    /** Re-download an artifact whose download failed earlier. */
    fun retryArtifactDownload(artifact: GitHubApi.Artifact) {
        val dl = _artifacts.value.firstOrNull { it.artifact.id == artifact.id }?.download ?: return
        viewModelScope.launch { downloadManager.retry(dl.url) }
    }

    /** Re-run just the artifact list fetch. */
    fun retryArtifacts(owner: String, repo: String, runId: Long) {
        artifactsRunId = null
        loadArtifacts(owner, repo, runId)
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
                .onFailure { e -> _actionMessage.update { e.userMessage("Cancellation failed") } }
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
                .onFailure { e -> _actionMessage.update { e.userMessage("Re-run failed") } }
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

    /**
     * Unpack a finished artifact zip into
     * `…/PocketHub/{owner}/{repo}/extracted/{artifactName}-{id}/` so its files
     * stay reachable via FileProvider (APK install / ACTION_VIEW).
     */
    private fun triggerExtract(ui: ArtifactUi, download: DownloadEntity) {
        val owner = artifactsOwner ?: return
        val repo = artifactsRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val zipFile = File(download.localPath)
                val destDir = File(
                    downloadManager.dirFor("$owner/$repo"),
                    "extracted/${ui.artifact.name}-${ui.artifact.id}",
                )
                destDir.deleteRecursively()
                extractor.extract(zipFile, destDir)
            }.onSuccess { files ->
                _artifacts.update { list ->
                    list.map {
                        if (it.artifact.id == ui.artifact.id) it.copy(extracting = false, extractedFiles = files, extractError = null)
                        else it
                    }
                }
            }.onFailure { e ->
                issueReporter.reportError("WorkflowRunDetail", "extractArtifact", e)
                _artifacts.update { list ->
                    list.map {
                        if (it.artifact.id == ui.artifact.id) it.copy(extracting = false, extractError = e.userMessage("Extraction failed"))
                        else it
                    }
                }
            }
        }
    }
}
