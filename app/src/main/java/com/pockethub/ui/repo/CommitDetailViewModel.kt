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

@HiltViewModel
class CommitDetailViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _commit = MutableStateFlow<GitHubApi.CommitDetail?>(null)
    val commit: StateFlow<GitHubApi.CommitDetail?> = _commit.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _comments = MutableStateFlow<List<GitHubApi.CommitComment>>(emptyList())
    val comments: StateFlow<List<GitHubApi.CommitComment>> = _comments.asStateFlow()

    private val _commentsError = MutableStateFlow<String?>(null)
    val commentsError: StateFlow<String?> = _commentsError.asStateFlow()

    private val _isSendingComment = MutableStateFlow(false)
    val isSendingComment: StateFlow<Boolean> = _isSendingComment.asStateFlow()

    private val _commentError = MutableStateFlow<String?>(null)
    val commentError: StateFlow<String?> = _commentError.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    private var loadedSha: String? = null

    fun load(owner: String, repo: String, sha: String) {
        if (loadedSha == sha && _commit.value != null) return
        loadedSha = sha
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                _commit.update { api.getCommit(owner, repo, sha) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.update { e.localizedMessage ?: "Failed to load commit" }
            } finally {
                _isLoading.update { false }
            }
        }
        // Load commit comments in parallel — independent of the commit itself.
        loadComments(owner, repo, sha)
    }

    fun retry(owner: String, repo: String, sha: String) {
        loadedSha = null
        load(owner, repo, sha)
    }

    fun loadComments(owner: String, repo: String, sha: String) {
        viewModelScope.launch {
            _commentsError.update { null }
            try {
                _comments.update { api.getCommitComments(owner, repo, sha) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _commentsError.update { e.localizedMessage ?: "Failed to load commit comments" }
            }
        }
    }

    /**
     * Post a top-level commit comment (no positional info). Mirrors GitHub web's
     * "Comment on this commit" footer — used to leave a general remark about the
     * whole commit, not tied to a specific file line.
     */
    fun postComment(owner: String, repo: String, sha: String, body: String) {
        if (body.isBlank() || _isSendingComment.value) return
        viewModelScope.launch {
            _isSendingComment.update { true }
            _commentError.update { null }
            try {
                val created = api.createCommitComment(owner, repo, sha, GitHubApi.CommitCommentCreate(body = body))
                _comments.update { it + created }
                _actionMessage.update { "Comment added" }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _commentError.update { e.localizedMessage ?: "Failed to post comment" }
            } finally {
                _isSendingComment.update { false }
            }
        }
    }

    fun clearActionMessage() { _actionMessage.update { null } }
    fun clearCommentError() { _commentError.update { null } }
    fun retryComments(owner: String, repo: String, sha: String) = loadComments(owner, repo, sha)
}
