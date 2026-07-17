package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.remote.GitHubApi
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

    private var loadedOwner: String? = null
    private var loadedRepo: String? = null
    private var loadedNumber: Int? = null

    fun loadIssue(owner: String, repo: String, number: Int) {
        if (loadedNumber == number && _issue.value != null) return
        loadedOwner = owner; loadedRepo = repo; loadedNumber = number
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try { _issue.value = api.getIssue(owner, repo, number) }
            catch (e: Exception) { _error.value = e.localizedMessage ?: "加载 Issue 失败" }
            finally { _isLoading.value = false }
        }
        viewModelScope.launch { runCatching { api.getIssueComments(owner, repo, number) }.onSuccess { _comments.value = it } }
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
            } catch (e: Exception) { _actionMessage.value = e.localizedMessage ?: "评论发送失败" }
            finally { _isSendingComment.value = false }
        }
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

    fun retry(owner: String, repo: String, number: Int) { loadedNumber = null; loadIssue(owner, repo, number) }
    fun clearActionMessage() { _actionMessage.value = null }
}
