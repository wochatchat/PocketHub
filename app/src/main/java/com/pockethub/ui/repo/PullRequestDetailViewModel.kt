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
class PullRequestDetailViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _pr = MutableStateFlow<GitHubApi.PullRequest?>(null)
    val pr: StateFlow<GitHubApi.PullRequest?> = _pr

    private val _files = MutableStateFlow<List<GitHubApi.PullRequestFile>>(emptyList())
    val files: StateFlow<List<GitHubApi.PullRequestFile>> = _files

    private val _reviews = MutableStateFlow<List<GitHubApi.PullRequestReview>>(emptyList())
    val reviews: StateFlow<List<GitHubApi.PullRequestReview>> = _reviews

    private val _comments = MutableStateFlow<List<GitHubApi.IssueComment>>(emptyList())
    val comments: StateFlow<List<GitHubApi.IssueComment>> = _comments

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isMerging = MutableStateFlow(false)
    val isMerging: StateFlow<Boolean> = _isMerging.asStateFlow()

    private val _mergeResult = MutableStateFlow<String?>(null)
    val mergeResult: StateFlow<String?> = _mergeResult

    private val _isSendingReview = MutableStateFlow(false)
    val isSendingReview: StateFlow<Boolean> = _isSendingReview.asStateFlow()

    private val _reviewResult = MutableStateFlow<String?>(null)
    val reviewResult: StateFlow<String?> = _reviewResult

    private val _isSendingComment = MutableStateFlow(false)
    val isSendingComment: StateFlow<Boolean> = _isSendingComment.asStateFlow()

    private val _commentError = MutableStateFlow<String?>(null)
    val commentError: StateFlow<String?> = _commentError

    private var loadedOwner: String? = null
    private var loadedRepo: String? = null
    private var loadedNumber: Int? = null

    fun loadPullRequest(owner: String, repo: String, number: Int) {
        if (loadedNumber == number && _pr.value != null) return
        loadedOwner = owner; loadedRepo = repo; loadedNumber = number
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                _pr.update { api.getPullRequest(owner, repo, number) }
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "加载 PR 失败" }
            } finally {
                _isLoading.update { false }
            }
            // Load files, reviews, and comments in parallel (independent)
            viewModelScope.launch {
                try {
                    _files.update { api.getPullRequestFiles(owner, repo, number) }
                } catch (_: Exception) {}
            }
            viewModelScope.launch {
                try {
                    _reviews.update { api.getPullRequestReviews(owner, repo, number) }
                } catch (_: Exception) {}
            }
            viewModelScope.launch {
                try {
                    _comments.update { api.getIssueComments(owner, repo, number) }
                } catch (_: Exception) {}
            }
        }
    }

    fun merge(owner: String, repo: String, number: Int, method: String = "merge") {
        if (_isMerging.value) return
        viewModelScope.launch {
            _isMerging.update { true }
            _mergeResult.update { null }
            try {
                val response = api.mergePullRequest(owner, repo, number, GitHubApi.MergeRequest(merge_method = method))
                val result = response.body()
                if (response.isSuccessful && result?.merged == true) {
                    _mergeResult.update { "已合并" }
                    // Refresh PR to show merged state
                    _pr.update { null }
                    loadedNumber = null
                    loadPullRequest(owner, repo, number)
                } else {
                    _mergeResult.update { result?.message ?: "合并失败 (${response.code()})" }
                }
            } catch (e: Exception) {
                _mergeResult.update { e.localizedMessage ?: "合并失败" }
            } finally {
                _isMerging.update { false }
            }
        }
    }

    fun submitReview(owner: String, repo: String, number: Int, event: String, body: String) {
        if (_isSendingReview.value) return
        viewModelScope.launch {
            _isSendingReview.update { true }
            _reviewResult.update { null }
            try {
                val review = api.createPullRequestReview(
                    owner, repo, number,
                    GitHubApi.ReviewRequest(body = body.ifBlank { null }, event = event),
                )
                _reviews.update { it + review }
                _reviewResult.update {
                    when (event) {
                        "APPROVE" -> "已批准"
                        "REQUEST_CHANGES" -> "已请求修改"
                        else -> "已评论"
                    }
                }
            } catch (e: Exception) {
                _reviewResult.update { e.localizedMessage ?: "Review 提交失败" }
            } finally {
                _isSendingReview.update { false }
            }
        }
    }

    fun postComment(body: String, onSuccess: () -> Unit = {}) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        val number = loadedNumber ?: return
        if (body.isBlank()) return
        viewModelScope.launch {
            _isSendingComment.update { true }
            _commentError.update { null }
            try {
                val newComment = api.createIssueComment(owner, repo, number, GitHubApi.CommentRequest(body))
                _comments.update { it + newComment }
                _pr.update { pr -> pr?.copy(comments = pr.comments + 1) }
                onSuccess()
            } catch (e: Exception) {
                _commentError.update { e.localizedMessage ?: "评论发送失败" }
            } finally {
                _isSendingComment.update { false }
            }
        }
    }

    fun clearMergeResult() { _mergeResult.update { null } }
    fun clearReviewResult() { _reviewResult.update { null } }
    fun clearCommentError() { _commentError.update { null } }

    fun retry(owner: String, repo: String, number: Int) {
        loadedNumber = null
        loadPullRequest(owner, repo, number)
    }
}
