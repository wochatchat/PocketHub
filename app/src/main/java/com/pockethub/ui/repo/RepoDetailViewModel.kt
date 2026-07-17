package com.pockethub.ui.repo

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.CachedRepository
import com.pockethub.data.remote.GitHubApi
import com.pockethub.data.remote.GoogleTranslate
import com.pockethub.data.remote.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RepoTab { OVERVIEW, CODE, ISSUES, PRS, RELEASES, COMMITS, WORKFLOWS }

@HiltViewModel
class RepoDetailViewModel @Inject constructor(
    private val api: GitHubApi,
    private val cache: CachedRepository,
    private val history: com.pockethub.data.remote.HistoryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _repo = MutableStateFlow<Repository?>(null)
    val repo: StateFlow<Repository?> = _repo

    private val _issues = MutableStateFlow<List<Issue>>(emptyList())
    val issues: StateFlow<List<Issue>> = _issues

    private val _pulls = MutableStateFlow<List<Issue>>(emptyList())
    val pulls: StateFlow<List<Issue>> = _pulls

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _releases = MutableStateFlow<List<GitHubApi.Release>>(emptyList())
    val releases: StateFlow<List<GitHubApi.Release>> = _releases

    private val _workflowRuns = MutableStateFlow<List<GitHubApi.WorkflowRun>>(emptyList())
    val workflowRuns: StateFlow<List<GitHubApi.WorkflowRun>> = _workflowRuns

    private val _workflows = MutableStateFlow<List<GitHubApi.Workflow>>(emptyList())
    val workflows: StateFlow<List<GitHubApi.Workflow>> = _workflows

    private val _isLoadingWorkflows = MutableStateFlow(false)
    val isLoadingWorkflows: StateFlow<Boolean> = _isLoadingWorkflows.asStateFlow()

    private val _isDispatching = MutableStateFlow(false)
    val isDispatching: StateFlow<Boolean> = _isDispatching.asStateFlow()

    private val _dispatchMessage = MutableStateFlow<String?>(null)
    val dispatchMessage: StateFlow<String?> = _dispatchMessage.asStateFlow()

    private val _readme = MutableStateFlow<String?>(null)
    val readme: StateFlow<String?> = _readme

    // ── Translation state ─────────────────────────────────────
    private val _translatedReadme = MutableStateFlow<String?>(null)
    val translatedReadme: StateFlow<String?> = _translatedReadme

    private val _showTranslated = MutableStateFlow(false)
    val showTranslated: StateFlow<Boolean> = _showTranslated

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    val translateTarget: StateFlow<String?> = settings.translateTarget
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isStarred = MutableStateFlow(false)
    val isStarred: StateFlow<Boolean> = _isStarred.asStateFlow()

    private val _isForking = MutableStateFlow(false)
    val isForking: StateFlow<Boolean> = _isForking.asStateFlow()

    private val _forkMessage = MutableStateFlow<String?>(null)
    val forkMessage: StateFlow<String?> = _forkMessage.asStateFlow()

    var currentTab = MutableStateFlow(RepoTab.OVERVIEW)
    private var loadedOwner: String? = null
    private var loadedRepo: String? = null

    fun loadRepo(owner: String, repo: String) {
        if (loadedOwner == owner && loadedRepo == repo && _repo.value != null) return
        loadedOwner = owner; loadedRepo = repo
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                _repo.update { cache.getRepository(owner, repo) }
                history.recordVisit(owner, repo)
                loadReadme(owner, repo)
                checkStar(owner, repo)
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "加载仓库失败" }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    private fun loadReadme(owner: String, repo: String): Job = viewModelScope.launch {
        try {
            val resp = cache.getReadme(owner, repo)
            val markdown = if (resp.encoding == "base64" && resp.content.isNotBlank()) {
                decodeBase64(resp.content)
            } else {
                resp.content
            }
            _readme.update { markdown }
        } catch (_: Exception) {
            _readme.update { null }
        }
    }

    private fun checkStar(owner: String, repo: String) = viewModelScope.launch {
        try {
            val resp = api.checkStarred(owner, repo)
            _isStarred.update { resp.isSuccessful }
        } catch (_: Exception) {
            _isStarred.update { false }
        }
    }

    fun toggleStar(owner: String, repo: String) {
        viewModelScope.launch {
            try {
                if (_isStarred.value) {
                    api.unstar(owner, repo)
                    _isStarred.update { false }
                } else {
                    api.star(owner, repo)
                    _isStarred.update { true }
                }
                cache.invalidateRepo(owner, repo)
            } catch (_: Exception) {}
        }
    }

    fun loadIssues(owner: String, repo: String, state: String = "open") {
        viewModelScope.launch {
            try {
                val all = cache.getIssues(owner, repo, state = state)
                _issues.update { all.filter { it.pullRequest == null } }
            } catch (e: Exception) {
                _issues.update { emptyList() }
                _error.update { e.localizedMessage ?: "加载 Issues 失败" }
            }
        }
    }

    fun loadPulls(owner: String, repo: String, state: String = "open") {
        viewModelScope.launch {
            try {
                val all = cache.getIssues(owner, repo, state = state)
                _pulls.update { all.filter { it.pullRequest != null } }
            } catch (e: Exception) {
                _pulls.update { emptyList() }
                _error.update { e.localizedMessage ?: "加载 PR 失败" }
            }
        }
    }

    fun loadReleases(owner: String, repo: String) {
        viewModelScope.launch {
            try {
                _releases.update { cache.getReleases(owner, repo) }
            } catch (e: Exception) {
                _releases.update { emptyList() }
                _error.update { e.localizedMessage ?: "加载 Releases 失败" }
            }
        }
    }

    fun loadWorkflowRuns(owner: String, repo: String, branch: String? = null) {
        viewModelScope.launch {
            try {
                val resp = api.getWorkflowRuns(owner, repo, branch = branch)
                _workflowRuns.update { resp.runs }
            } catch (e: Exception) {
                _workflowRuns.update { emptyList() }
                _error.update { e.localizedMessage ?: "加载 Workflows 失败" }
            }
        }
    }

    /** Load workflow definitions so the user can pick one to dispatch manually. */
    fun loadWorkflows(owner: String, repo: String) {
        viewModelScope.launch {
            if (_isLoadingWorkflows.value) return@launch
            _isLoadingWorkflows.update { true }
            try {
                val resp = api.getWorkflows(owner, repo)
                _workflows.update { resp.workflows.filter { it.state == "active" && it.deletedAt == null } }
            } catch (e: Exception) {
                _workflows.update { emptyList() }
                _dispatchMessage.update { e.localizedMessage ?: "加载工作流失败" }
            } finally {
                _isLoadingWorkflows.update { false }
            }
        }
    }

    /** Trigger a `workflow_dispatch` event for the given workflow on the given ref. */
    fun dispatchWorkflow(owner: String, repo: String, workflowId: Long, ref: String) {
        viewModelScope.launch {
            if (_isDispatching.value) return@launch
            _isDispatching.update { true }
            _dispatchMessage.update { null }
            try {
                val resp = api.dispatchWorkflow(owner, repo, workflowId, GitHubApi.WorkflowDispatchRequest(ref = ref))
                if (resp.isSuccessful) {
                    _dispatchMessage.update { "已触发：将在几秒后出现新一次运行" }
                } else {
                    val err = resp.errorBody()?.string()
                    val reason = when (resp.code()) {
                        403 -> "无权限：需要对此仓库有 write 权限"
                        404 -> "工作流或仓库不存在或无 Actions 权限"
                        422 -> "触发失败：该 workflow 可能没有声明 `on: workflow_dispatch`，或 ref 不存在"
                        else -> "触发失败 (${resp.code()}): ${err?.take(200)}"
                    }
                    _dispatchMessage.update { reason }
                }
            } catch (e: Exception) {
                _dispatchMessage.update { e.localizedMessage ?: "触发工作流失败" }
            } finally {
                _isDispatching.update { false }
            }
        }
    }

    fun clearDispatchMessage() {
        _dispatchMessage.update { null }
    }

    fun fork(owner: String, repo: String) {
        viewModelScope.launch {
            if (_isForking.value) return@launch
            _isForking.update { true }
            try {
                val resp = api.forkRepository(owner, repo)
                if (resp.isSuccessful) {
                    _forkMessage.update { "已 fork 到当前账号" }
                } else {
                    _forkMessage.update { "Fork 失败: ${resp.code()}" }
                }
            } catch (e: Exception) {
                _forkMessage.update { e.localizedMessage ?: "Fork 失败" }
            } finally {
                _isForking.update { false }
            }
        }
    }

    fun clearForkMessage() {
        _forkMessage.update { null }
    }

    // ── Translation ──────────────────────────────────────────

    /** Toggle between original and translated README. Triggers translation if needed. */
    fun toggleTranslation() {
        val target = translateTarget.value ?: return
        if (_showTranslated.value) {
            // Switch back to original
            _showTranslated.update { false }
            return
        }
        // If already translated, just switch
        if (_translatedReadme.value != null) {
            _showTranslated.update { true }
            return
        }
        // Need to translate first
        val original = _readme.value ?: return
        viewModelScope.launch {
            _isTranslating.update { true }
            try {
                val lang = if (target == "zh") "zh-CN" else "en"
                val translated = GoogleTranslate.translate(original, lang)
                _translatedReadme.update { translated }
                _showTranslated.update { true }
            } catch (_: Exception) {
                // On failure, stay on original
            } finally {
                _isTranslating.update { false }
            }
        }
    }

    fun decodeBase64(b64: String): String {
        return try {
            val cleaned = b64.replace("\n", "")
            String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            b64
        }
    }
}
