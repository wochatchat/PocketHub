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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSendingComment = MutableStateFlow(false)
    val isSendingComment: StateFlow<Boolean> = _isSendingComment.asStateFlow()

    private val _isTogglingState = MutableStateFlow(false)
    val isTogglingState: StateFlow<Boolean> = _isTogglingState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError

    private var loadedOwner: String? = null
    private var loadedRepo: String? = null
    private var loadedNumber: Int? = null

    fun loadIssue(owner: String, repo: String, number: Int) {
        if (loadedNumber == number && _issue.value != null) return
        loadedOwner = owner; loadedRepo = repo; loadedNumber = number
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                _issue.update { api.getIssue(owner, repo, number) }
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "加载 Issue 失败" }
            } finally {
                _isLoading.update { false }
            }
            // Load comments independently so a comment fetch failure doesn't mask the issue body.
            viewModelScope.launch {
                try {
                    _comments.update { api.getIssueComments(owner, repo, number) }
                } catch (e: Exception) {
                    // Comments are optional — only log via error flow if the issue itself also failed.
                }
            }
        }
    }

    /** Post a new comment on the issue / PR. */
    fun postComment(body: String, onSuccess: () -> Unit = {}) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        val number = loadedNumber ?: return
        if (body.isBlank()) return

        viewModelScope.launch {
            _isSendingComment.update { true }
            _actionError.update { null }
            try {
                val newComment = api.createIssueComment(owner, repo, number, body)
                _comments.update { it + newComment }
                // Update comment count locally
                _issue.update { issue -> issue?.copy(comments = issue.comments + 1) }
                onSuccess()
            } catch (e: Exception) {
                _actionError.update { e.localizedMessage ?: "评论发送失败" }
            } finally {
                _isSendingComment.update { false }
            }
        }
    }

    /** Toggle issue state: open → closed, closed → open. */
    fun toggleIssueState() {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        val number = loadedNumber ?: return
        val currentState = _issue.value?.state ?: return
        val newState = if (currentState == "open") "closed" else "open"

        viewModelScope.launch {
            _isTogglingState.update { true }
            _actionError.update { null }
            try {
                val updated = api.updateIssueState(owner, repo, number, newState)
                _issue.update { updated }
            } catch (e: Exception) {
                _actionError.update { e.localizedMessage ?: "状态更新失败" }
            } finally {
                _isTogglingState.update { false }
            }
        }
    }

    fun retry(owner: String, repo: String, number: Int) {
        loadedNumber = null
        loadIssue(owner, repo, number)
    }

    /** Clear the action error so it doesn't persist across recompositions. */
    fun clearActionError() {
        _actionError.update { null }
    }
}
