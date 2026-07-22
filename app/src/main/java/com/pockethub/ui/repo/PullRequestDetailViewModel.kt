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
class PullRequestDetailViewModel @Inject constructor(
    private val api: GitHubApi,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _pr = MutableStateFlow<GitHubApi.PullRequest?>(null)
    val pr: StateFlow<GitHubApi.PullRequest?> = _pr

    private val _files = MutableStateFlow<List<GitHubApi.PullRequestFile>>(emptyList())
    val files: StateFlow<List<GitHubApi.PullRequestFile>> = _files

    private val _reviewComments = MutableStateFlow<List<GitHubApi.ReviewComment>>(emptyList())
    val reviewComments: StateFlow<List<GitHubApi.ReviewComment>> = _reviewComments

    private val _isSendingLineComment = MutableStateFlow(false)
    val isSendingLineComment: StateFlow<Boolean> = _isSendingLineComment.asStateFlow()

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

    private val _viewerReactions = MutableStateFlow<Map<Long, Map<String, Long>>>(emptyMap())
    val viewerReactions: StateFlow<Map<Long, Map<String, Long>>> = _viewerReactions

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
                    _reviewComments.update { api.listPullRequestReviewComments(owner, repo, number) }
                } catch (_: Exception) {}
            }
            viewModelScope.launch {
                try {
                    _comments.update { api.getIssueComments(owner, repo, number) }
                    hydrateReactions(owner, repo)
                } catch (_: Exception) {}
            }
            // Load CI checks for the PR head SHA so users see whether the PR is
            // mergeable from a checks perspective (parallel with files/reviews).
            viewModelScope.launch {
                val sha = _pr.value?.head?.sha
                if (!sha.isNullOrBlank()) {
                    runCatching { api.listCheckRuns(owner, repo, sha) }.onSuccess { resp ->
                        _checkRuns.update { resp.runs }
                        _checkSummary.update { summarize(resp.runs) }
                    }
                }
            }
        }
    }

    private val _checkRuns = MutableStateFlow<List<GitHubApi.CheckRun>>(emptyList())
    val checkRuns: StateFlow<List<GitHubApi.CheckRun>> = _checkRuns

    /** Aggregated PR checks status — a single line shown above the files section. */
    private val _checkSummary = MutableStateFlow<CheckSummary>(CheckSummary.NONE)
    val checkSummary: StateFlow<CheckSummary> = _checkSummary

    /** Manually re-fetch check runs. Used when the user pulls to refresh CI status. */
    fun refreshCheckRuns(owner: String, repo: String) {
        val sha = _pr.value?.head?.sha ?: return
        viewModelScope.launch {
            runCatching { api.listCheckRuns(owner, repo, sha) }.onSuccess { resp ->
                _checkRuns.update { resp.runs }
                _checkSummary.update { summarize(resp.runs) }
            }
        }
    }

    /** Computes the headline status from a list of check runs. */
    private fun summarize(runs: List<GitHubApi.CheckRun>): CheckSummary {
        if (runs.isEmpty()) return CheckSummary.NONE
        val total = runs.size
        val passed = runs.count { it.status == "completed" && it.conclusion == "success" }
        val failed = runs.count { it.status == "completed" && it.conclusion in FAILED_CONCLUSIONS }
        val neutral = runs.count { it.status == "completed" && it.conclusion in NEUTRAL_CONCLUSIONS }
        val pending = total - passed - failed - neutral

        return when {
            failed > 0 -> CheckSummary.Failed(failed = failed, total = total)
            pending > 0 -> CheckSummary.Pending(pending = pending, total = total)
            passed + neutral == total -> CheckSummary.Passed(passed = passed, total = total)
            else -> CheckSummary.Pending(pending = total - passed - neutral, total = total)
        }
    }

    /** Conclusions that should appear red on the PR check summary. */
    private val FAILED_CONCLUSIONS = setOf("failure", "cancelled", "timed_out", "action_required")
    /** Conclusions that are non-passing but also non-failing (skipped/neutral/stale). */
    private val NEUTRAL_CONCLUSIONS = setOf("neutral", "skipped", "stale")

    /** Aggregated CI status for the PR head SHA. Rendered as a one-line banner. */
    sealed interface CheckSummary {
        /** No check runs returned by GitHub — usually means CI is not set up on this repo. */
        data object NONE : CheckSummary
        data class Passed(val passed: Int, val total: Int) : CheckSummary
        data class Failed(val failed: Int, val total: Int) : CheckSummary
        data class Pending(val pending: Int, val total: Int) : CheckSummary
    }

    /**
     * Post a line-level review comment anchored to a file + line on the PR diff.
     * Optimistically appends to [reviewComments]; on failure rolls back + surfaces via [commentError].
     */
    fun postLineComment(path: String, line: Int, body: String, startLine: Int? = null) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        val number = loadedNumber ?: return
        if (body.isBlank() || _isSendingLineComment.value) return
        val commitId = _pr.value?.head?.sha
        viewModelScope.launch {
            _isSendingLineComment.update { true }
            _commentError.update { null }
            try {
                val created = api.createPullRequestReviewComment(
                    owner, repo, number,
                    GitHubApi.ReviewCommentRequest(
                        body = body,
                        commitId = commitId,
                        path = path,
                        line = line,
                        startLine = startLine,
                    ),
                )
                _reviewComments.update { it + created }
                _pr.update { pr -> pr?.copy(reviewComments = pr.reviewComments + 1) }
            } catch (e: Exception) {
                _commentError.update { e.localizedMessage ?: "行评论发送失败" }
            } finally {
                _isSendingLineComment.update { false }
            }
        }
    }

    private suspend fun hydrateReactions(owner: String, repo: String) {
        val current = accounts.getActiveLogin()
        if (current.isBlank()) return
        for (c in _comments.value) {
            if (c.reactions == null || c.reactions.totalCount == 0) continue
            runCatching { api.listIssueCommentReactions(owner, repo, c.id) }.onSuccess { list ->
                val mine = list.filter { it.user?.login == current }.associate { it.content to it.id }
                if (mine.isNotEmpty()) _viewerReactions.update { it + (c.id to mine) }
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
                // Refresh the PR so mergeable / merge_state / requested reviewers
                // reflect the newly submitted review — same pattern as merge().
                _pr.update { null }
                loadedNumber = null
                loadPullRequest(owner, repo, number)
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

    fun editComment(commentId: Long, newBody: String) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        if (newBody.isBlank() || commentId in _busyComments.value) return
        viewModelScope.launch {
            _busyComments.update { it + commentId }
            _commentError.update { null }
            try {
                val updated = api.editIssueComment(owner, repo, commentId, GitHubApi.CommentRequest(newBody))
                _comments.update { list -> list.map { if (it.id == commentId) updated else it } }
            } catch (e: Exception) {
                _commentError.update { e.localizedMessage ?: "评论更新失败" }
            } finally {
                _busyComments.update { it - commentId }
            }
        }
    }

    fun deleteComment(commentId: Long) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        if (commentId in _busyComments.value) return
        viewModelScope.launch {
            _busyComments.update { it + commentId }
            _commentError.update { null }
            try {
                api.deleteIssueComment(owner, repo, commentId)
                _comments.update { list -> list.filter { it.id != commentId } }
                _pr.update { pr -> pr?.copy(comments = (pr.comments - 1).coerceAtLeast(0)) }
                _viewerReactions.update { it - commentId }
            } catch (e: Exception) {
                _commentError.update { e.localizedMessage ?: "评论删除失败" }
            } finally {
                _busyComments.update { it - commentId }
            }
        }
    }

    fun toggleReaction(commentId: Long, content: GitHubApi.ReactionContent) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        if (commentId in _busyComments.value) return
        val mine = _viewerReactions.value[commentId]?.get(content.apiValue)
        viewModelScope.launch {
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
                _commentError.update { e.localizedMessage ?: "反应操作失败" }
            } finally {
                _busyComments.update { it - commentId }
            }
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

    fun commentStates(): List<CommentUiState> {
        val login = _currentLogin.value ?: return emptyList()
        val busy = _busyComments.value
        val vrs = _viewerReactions.value
        return _comments.value.map { c ->
            CommentUiState(
                comment = c,
                repoContext = "${loadedOwner.orEmpty()}/${loadedRepo.orEmpty()}",
                isMine = c.user?.login == login,
                viewerReactions = vrs[c.id].orEmpty(),
                isReacting = c.id in busy,
            )
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
