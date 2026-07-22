package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.GitHubApi
import com.pockethub.ui.components.CommentUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    private val api: GitHubApi,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val _issue = MutableStateFlow<Issue?>(null)
    val issue: StateFlow<Issue?> = _issue
    private val _comments = MutableStateFlow<List<GitHubApi.IssueComment>>(emptyList())
    val comments: StateFlow<List<GitHubApi.IssueComment>> = _comments
    private val _repositoryLabels = MutableStateFlow<List<Issue.Label>>(emptyList())
    val repositoryLabels: StateFlow<List<Issue.Label>> = _repositoryLabels
    private val _milestones = MutableStateFlow<List<Issue.Milestone>>(emptyList())
    val milestones: StateFlow<List<Issue.Milestone>> = _milestones
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isSendingComment = MutableStateFlow(false)
    val isSendingComment = _isSendingComment.asStateFlow()
    private val _isTogglingState = MutableStateFlow(false)
    val isTogglingState = _isTogglingState.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    /** Tracks per-comment reaction ownership so chips render as toggled. */
    private val _viewerReactions = MutableStateFlow<Map<Long, Map<String, Long>>>(emptyMap())
    val viewerReactions: StateFlow<Map<Long, Map<String, Long>>> = _viewerReactions

    /** Comments currently being mutated (saved/deleted/reacted) — disables chip menu. */
    private val _busyComments = MutableStateFlow<Set<Long>>(emptySet())
    val busyComments: StateFlow<Set<Long>> = _busyComments

    private val _currentLogin = MutableStateFlow<String?>(null)
    val currentLogin: StateFlow<String?> = _currentLogin.asStateFlow()

    private var loadedOwner: String? = null
    private var loadedRepo: String? = null
    private var loadedNumber: Int? = null

    init {
        viewModelScope.launch { _currentLogin.value = accounts.getActiveLogin().takeIf { it.isNotBlank() } }
    }

    fun loadIssue(owner: String, repo: String, number: Int) {
        if (loadedNumber == number && _issue.value != null) return
        loadedOwner = owner; loadedRepo = repo; loadedNumber = number
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try { _issue.value = api.getIssue(owner, repo, number) }
            catch (e: Exception) { _error.value = e.localizedMessage ?: "Failed to load issue" }
            finally { _isLoading.value = false }
        }
        viewModelScope.launch {
            runCatching { api.getIssueComments(owner, repo, number) }.onSuccess {
                _comments.value = it
            }
        }
        viewModelScope.launch { hydrateReactions(owner, repo) }
    }

    private suspend fun hydrateReactions(owner: String, repo: String) {
        val current = accounts.getActiveLogin()
        if (current.isBlank()) return
        val snap = _comments.value
        for (c in snap) {
            if (c.reactions == null || c.reactions.totalCount == 0) continue
            runCatching { api.listIssueCommentReactions(owner, repo, c.id) }.onSuccess { list ->
                val mine = list.filter { it.user?.login == current }.associate { it.content to it.id }
                if (mine.isNotEmpty()) _viewerReactions.update { it + (c.id to mine) }
            }
        }
    }

    fun loadMetadata(owner: String, repo: String) {
        if (_repositoryLabels.value.isNotEmpty() || _milestones.value.isNotEmpty()) return
        viewModelScope.launch { runCatching { api.getRepositoryLabels(owner, repo) }.onSuccess { _repositoryLabels.value = it } }
        viewModelScope.launch { runCatching { api.getRepositoryMilestones(owner, repo) }.onSuccess { _milestones.value = it } }
    }

    fun postComment(body: String, onSuccess: () -> Unit = {}) {
        val owner = loadedOwner ?: return; val repo = loadedRepo ?: return; val number = loadedNumber ?: return
        if (body.isBlank() || _isSendingComment.value) return
        viewModelScope.launch {
            _isSendingComment.value = true; _actionMessage.value = null
            try {
                val comment = api.createIssueComment(owner, repo, number, GitHubApi.CommentRequest(body))
                _comments.update { it + comment }; _issue.update { it?.copy(comments = it.comments + 1) }; onSuccess()
            } catch (e: Exception) { _actionMessage.value = e.localizedMessage ?: "Failed to post comment" }
            finally { _isSendingComment.value = false }
        }
    }

    fun editComment(commentId: Long, newBody: String) {
        val owner = loadedOwner ?: return; val repo = loadedRepo ?: return
        if (newBody.isBlank() || commentId in _busyComments.value) return
        viewModelScope.launch {
            _busyComments.update { it + commentId }; _actionMessage.value = null
            try {
                val updated = api.editIssueComment(owner, repo, commentId, GitHubApi.CommentRequest(newBody))
                _comments.update { list -> list.map { if (it.id == commentId) updated else it } }
                _actionMessage.value = "Comment updated"
            } catch (e: Exception) { _actionMessage.value = e.localizedMessage ?: "Failed to update comment" }
            finally { _busyComments.update { it - commentId } }
        }
    }

    fun deleteComment(commentId: Long) {
        val owner = loadedOwner ?: return; val repo = loadedRepo ?: return
        if (commentId in _busyComments.value) return
        viewModelScope.launch {
            _busyComments.update { it + commentId }; _actionMessage.value = null
            try {
                api.deleteIssueComment(owner, repo, commentId)
                _comments.update { list -> list.filter { it.id != commentId } }
                _issue.update { it?.copy(comments = (it.comments - 1).coerceAtLeast(0)) }
                _viewerReactions.update { it - commentId }
            } catch (e: Exception) { _actionMessage.value = e.localizedMessage ?: "Failed to delete comment" }
            finally { _busyComments.update { it - commentId } }
        }
    }

    fun toggleReaction(commentId: Long, content: GitHubApi.ReactionContent) {
        val owner = loadedOwner ?: return; val repo = loadedRepo ?: return
        if (commentId in _busyComments.value) return
        val mine = _viewerReactions.value[commentId]?.get(content.apiValue)
        viewModelScope.launch {
            toggleReactionInternal(owner, repo, commentId, content, mine)
        }
    }

    /** UI is composed of header + body (markdown) + reactions row + action chip menu. */
    private suspend fun toggleReactionInternal(
        owner: String, repo: String, commentId: Long,
        content: GitHubApi.ReactionContent, mine: Long?,
    ) {
        val local = _comments.value.firstOrNull { it.id == commentId } ?: return
        _busyComments.update { it + commentId }
        try {
            if (mine != null) {
                api.deleteIssueCommentReaction(owner, repo, commentId, mine)
                _viewerReactions.update { all -> all[commentId]?.let { m -> all + (commentId to (m - content.apiValue)) } ?: all }
                _comments.update { list -> list.map { if (it.id == commentId) decrement(it, content) else it } }
            } else {
                val created = api.createIssueCommentReaction(owner, repo, commentId, GitHubApi.ReactionRequest(content.apiValue))
                _viewerReactions.update { all -> all[commentId]?.let { m -> all + (commentId to (m + (content.apiValue to created.id))) } ?: all + (commentId to mapOf(content.apiValue to created.id)) }
                _comments.update { list -> list.map { if (it.id == commentId) increment(it, content) else it } }
            }
        } catch (e: Exception) {
            _actionMessage.value = e.localizedMessage ?: "Failed to toggle reaction"
        } finally {
            _busyComments.update { it - commentId }
        }
    }

    private fun increment(c: GitHubApi.IssueComment, content: GitHubApi.ReactionContent): GitHubApi.IssueComment {
        val r = (c.reactions ?: com.pockethub.data.model.Reactions())
        val rr = when (content) {
            GitHubApi.ReactionContent.PLUS_ONE -> r.copy(plusOne = r.plusOne + 1)
            GitHubApi.ReactionContent.MINUS_ONE -> r.copy(minusOne = r.minusOne + 1)
            GitHubApi.ReactionContent.LAUGH -> r.copy(laugh = r.laugh + 1)
            GitHubApi.ReactionContent.CONFUSED -> r.copy(confused = r.confused + 1)
            GitHubApi.ReactionContent.HEART -> r.copy(heart = r.heart + 1)
            GitHubApi.ReactionContent.HOORAY -> r.copy(hooray = r.hooray + 1)
            GitHubApi.ReactionContent.ROCKET -> r.copy(rocket = r.rocket + 1)
            GitHubApi.ReactionContent.EYES -> r.copy(eyes = r.eyes + 1)
        }
        return c.copy(reactions = rr)
    }

    private fun decrement(c: GitHubApi.IssueComment, content: GitHubApi.ReactionContent): GitHubApi.IssueComment {
        val r = (c.reactions ?: com.pockethub.data.model.Reactions())
        val rr = when (content) {
            GitHubApi.ReactionContent.PLUS_ONE -> r.copy(plusOne = (r.plusOne - 1).coerceAtLeast(0))
            GitHubApi.ReactionContent.MINUS_ONE -> r.copy(minusOne = (r.minusOne - 1).coerceAtLeast(0))
            GitHubApi.ReactionContent.LAUGH -> r.copy(laugh = (r.laugh - 1).coerceAtLeast(0))
            GitHubApi.ReactionContent.CONFUSED -> r.copy(confused = (r.confused - 1).coerceAtLeast(0))
            GitHubApi.ReactionContent.HEART -> r.copy(heart = (r.heart - 1).coerceAtLeast(0))
            GitHubApi.ReactionContent.HOORAY -> r.copy(hooray = (r.hooray - 1).coerceAtLeast(0))
            GitHubApi.ReactionContent.ROCKET -> r.copy(rocket = (r.rocket - 1).coerceAtLeast(0))
            GitHubApi.ReactionContent.EYES -> r.copy(eyes = (r.eyes - 1).coerceAtLeast(0))
        }
        return c.copy(reactions = rr)
    }

    fun toggleIssueState() {
        val state = _issue.value?.state ?: return
        save(IssueUpdate(state = if (state == "open") "closed" else "open"), if (state == "open") "Issue 已关闭" else "Issue 已重新打开")
    }

    fun saveIssue(title: String, body: String, labels: List<String>, assignees: List<String>, milestone: Int?) {
        save(IssueUpdate(title, body, labels, assignees, milestone = milestone), "Issue 已更新")
    }

    private data class IssueUpdate(val title: String? = null, val body: String? = null, val labels: List<String>? = null, val assignees: List<String>? = null, val milestone: Int? = null, val state: String? = null)
    private fun save(change: IssueUpdate, success: String) {
        val owner = loadedOwner ?: return; val repo = loadedRepo ?: return; val number = loadedNumber ?: return
        if (_isSaving.value || _isTogglingState.value) return
        viewModelScope.launch {
            if (change.state != null) _isTogglingState.value = true else _isSaving.value = true
            _actionMessage.value = null
            try {
                _issue.value = api.updateIssue(owner, repo, number, GitHubApi.IssueUpdateRequest(change.title, change.body, change.state, change.labels, change.assignees, change.milestone))
                _actionMessage.value = success
            } catch (e: Exception) { _actionMessage.value = e.localizedMessage ?: "Issue 更新失败" }
            finally { _isSaving.value = false; _isTogglingState.value = false }
        }
    }

    /** Build UI states for the CommentItem composable. */
    fun commentStates(): List<CommentUiState> {
        val login = _currentLogin.value ?: return emptyList()
        val busy = _busyComments.value
        val vrs = _viewerReactions.value
        return _comments.value.map { c ->
            CommentUiState(
                comment = c,
                repoContext = "${loadedOwner.orEmpty()}/${loadedRepo.orEmpty()}",
                isMine = c.user?.login == login,
                canModerate = false,
                viewerReactions = vrs[c.id].orEmpty(),
                isReacting = c.id in busy,
            )
        }
    }

    fun retry(owner: String, repo: String, number: Int) { loadedNumber = null; loadIssue(owner, repo, number) }
    fun clearActionMessage() { _actionMessage.value = null }
}
