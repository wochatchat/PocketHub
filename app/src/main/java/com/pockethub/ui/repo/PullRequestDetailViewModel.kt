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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/** Aggregated CI status for the PR head SHA. Rendered as a one-line banner. */
sealed interface CheckSummary {
    /** No check runs returned by GitHub — usually means CI is not set up on this repo. */
    data object NONE : CheckSummary
    data class Passed(val passed: Int, val total: Int) : CheckSummary
    data class Failed(val failed: Int, val total: Int) : CheckSummary
    data class Pending(val pending: Int, val total: Int) : CheckSummary
}

@HiltViewModel
class PullRequestDetailViewModel @Inject constructor(
    private val api: GitHubApi,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _pr = MutableStateFlow<GitHubApi.PullRequest?>(null)
    val pr: StateFlow<GitHubApi.PullRequest?> = _pr

    private val _files = MutableStateFlow<List<GitHubApi.PullRequestFile>>(emptyList())
    val files: StateFlow<List<GitHubApi.PullRequestFile>> = _files
    private val _filesError = MutableStateFlow<String?>(null)
    val filesError: StateFlow<String?> = _filesError.asStateFlow()

    private val _reviewComments = MutableStateFlow<List<GitHubApi.ReviewComment>>(emptyList())
    val reviewComments: StateFlow<List<GitHubApi.ReviewComment>> = _reviewComments
    private val _reviewCommentsError = MutableStateFlow<String?>(null)
    val reviewCommentsError: StateFlow<String?> = _reviewCommentsError.asStateFlow()

    private val _isSendingLineComment = MutableStateFlow(false)
    val isSendingLineComment: StateFlow<Boolean> = _isSendingLineComment.asStateFlow()

    private val _reviews = MutableStateFlow<List<GitHubApi.PullRequestReview>>(emptyList())
    val reviews: StateFlow<List<GitHubApi.PullRequestReview>> = _reviews
    private val _reviewsError = MutableStateFlow<String?>(null)
    val reviewsError: StateFlow<String?> = _reviewsError.asStateFlow()

    /**
     * PR review thread state, keyed by the comment id of the first / root comment of each thread.
     *
     * Filled lazily by [fetchThreadState]; holds the GraphQL thread node id and the
     * resolved flag (only available via GraphQL — REST `ReviewComment` has neither).
     * This is an in-memory cache — never persisted. Refreshed on PR refresh or on
     * resolve mutation failure.
     */
    private val _threadState = MutableStateFlow<Map<Long, ThreadInfo>>(emptyMap())
    val threadState: StateFlow<Map<Long, ThreadInfo>> = _threadState

    /** Hit-map of comment ids currently mutating (edit / delete / resolve). */
    private val _busyReviewComments = MutableStateFlow<Set<Long>>(emptySet())
    val busyReviewComments: StateFlow<Set<Long>> = _busyReviewComments

    /** Last error encountered while mutating inline review comments (edit / reply / resolve / delete). */
    private val _inlineCommentError = MutableStateFlow<String?>(null)
    val inlineCommentError: StateFlow<String?> = _inlineCommentError

    private val _comments = MutableStateFlow<List<GitHubApi.IssueComment>>(emptyList())
    val comments: StateFlow<List<GitHubApi.IssueComment>> = _comments
    private val _commentsError = MutableStateFlow<String?>(null)
    val commentsError: StateFlow<String?> = _commentsError.asStateFlow()

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
                _error.update { e.localizedMessage ?: "Failed to load PR" }
            } finally {
                _isLoading.update { false }
            }
            // Load files, reviews, and comments in parallel (independent)
            viewModelScope.launch {
                _filesError.update { null }
                try {
                    _files.update { api.getPullRequestFiles(owner, repo, number) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _filesError.update { e.localizedMessage ?: "Failed to load files" }
                }
            }
            viewModelScope.launch {
                _reviewsError.update { null }
                try {
                    _reviews.update { api.getPullRequestReviews(owner, repo, number) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _reviewsError.update { e.localizedMessage ?: "Failed to load reviews" }
                }
            }
            viewModelScope.launch {
                _reviewCommentsError.update { null }
                try {
                    _reviewComments.update { api.listPullRequestReviewComments(owner, repo, number) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _reviewCommentsError.update { e.localizedMessage ?: "Failed to load review comments" }
                }
            }
            viewModelScope.launch {
                runCatching { fetchThreadState(owner, repo, number) }
            }
            viewModelScope.launch {
                _commentsError.update { null }
                try {
                    val resp = api.getIssueComments(owner, repo, number)
                    _comments.update { resp.body().orEmpty() }
                    hydrateReactions(owner, repo)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _commentsError.update { e.localizedMessage ?: "Failed to load comments" }
                }
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

    /**
     * Retry a single section (files / reviews / reviewComments / comments)
     * after a per-section load failure. Each retry clears the matching error
     * before the attempt, so the UI banner flips back to its loading state.
     */
    fun retryFiles() = reloadSection("files")
    fun retryReviews() = reloadSection("reviews")
    fun retryReviewComments() = reloadSection("reviewComments")
    fun retryComments() = reloadSection("comments")

    private fun reloadSection(section: String) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        val number = loadedNumber ?: return
        viewModelScope.launch {
            when (section) {
                "files" -> {
                    _filesError.update { null }
                    try { _files.update { api.getPullRequestFiles(owner, repo, number) } }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _filesError.update { e.localizedMessage ?: "Failed to load files" }
                    }
                }
                "reviews" -> {
                    _reviewsError.update { null }
                    try { _reviews.update { api.getPullRequestReviews(owner, repo, number) } }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _reviewsError.update { e.localizedMessage ?: "Failed to load reviews" }
                    }
                }
                "reviewComments" -> {
                    _reviewCommentsError.update { null }
                    try { _reviewComments.update { api.listPullRequestReviewComments(owner, repo, number) } }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _reviewCommentsError.update { e.localizedMessage ?: "Failed to load review comments" }
                    }
                }
                "comments" -> {
                    _commentsError.update { null }
                    try {
                        val resp = api.getIssueComments(owner, repo, number)
                        _comments.update { resp.body().orEmpty() }
                        hydrateReactions(owner, repo)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _commentsError.update { e.localizedMessage ?: "Failed to load comments" }
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
                _commentError.update { e.localizedMessage ?: "Failed to post inline comment" }
            } finally {
                _isSendingLineComment.update { false }
            }
        }
    }

    /**
     * Reply within an existing review-comment thread — anchored to the root comment
     * by id; the server ignores `path` / `line` / `commit_id` when `in_reply_to_id`
     * is provided. Optimistically appends to [reviewComments]; rolls back on failure.
     */
    fun replyInlineComment(rootCommentId: Long, body: String) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        val number = loadedNumber ?: return
        if (body.isBlank() || rootCommentId in _busyReviewComments.value) return
        viewModelScope.launch {
            _busyReviewComments.update { it + rootCommentId }
            _inlineCommentError.update { null }
            try {
                val created = api.createPullRequestReviewComment(
                    owner, repo, number,
                    GitHubApi.ReviewCommentRequest(
                        body = body,
                        inReplyToId = rootCommentId,
                    ),
                )
                _reviewComments.update { it + created }
            } catch (e: Exception) {
                _inlineCommentError.update { e.localizedMessage ?: "Failed to reply" }
            } finally {
                _busyReviewComments.update { it - rootCommentId }
            }
        }
    }

    /**
     * Edit a pull request review comment's body. Owner / repo are read from the
     * loaded PR; caller is responsible for ensuring the current user authored
     * the comment (UI gate). Optimistically updates in-memory list then rolls
     * back on failure.
     */
    fun editInlineComment(commentId: Long, newBody: String) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        if (newBody.isBlank() || commentId in _busyReviewComments.value) return
        viewModelScope.launch {
            _busyReviewComments.update { it + commentId }
            _inlineCommentError.update { null }
            val snapshot = _reviewComments.value
            try {
                val updated = api.editPullRequestReviewComment(owner, repo, commentId, GitHubApi.EditReviewCommentRequest(newBody))
                _reviewComments.update { list -> list.map { if (it.id == commentId) updated else it } }
            } catch (e: Exception) {
                _reviewComments.update { snapshot }
                _inlineCommentError.update { e.localizedMessage ?: "Failed to update comment" }
            } finally {
                _busyReviewComments.update { it - commentId }
            }
        }
    }

    /**
     * Delete a review comment. If the comment is a thread root (`inReplyToId == null`),
     * the entire thread is removed from the in-memory list; otherwise just the
     * single reply. Shows a soft 404 toast if the comment is gone server-side.
     */
    fun deleteInlineComment(commentId: Long) {
        val owner = loadedOwner ?: return
        val repo = loadedRepo ?: return
        if (commentId in _busyReviewComments.value) return
        val isRoot = _reviewComments.value.firstOrNull { it.id == commentId }?.let { it.inReplyToId == null } ?: false
        viewModelScope.launch {
            _busyReviewComments.update { it + commentId }
            _inlineCommentError.update { null }
            val snapshot = _reviewComments.value
            try {
                val resp = api.deletePullRequestReviewComment(owner, repo, commentId)
                if (resp.isSuccessful || resp.code() == 404) {
                    _reviewComments.update { list ->
                        if (isRoot) list.filterNot { it.id == commentId || it.inReplyToId == commentId }
                        else list.filterNot { it.id == commentId }
                    }
                    if (resp.code() == 404) {
                        _inlineCommentError.update { "This comment no longer exists" }
                    }
                } else {
                    _inlineCommentError.update { "Delete failed (${resp.code()})" }
                }
            } catch (e: Exception) {
                _reviewComments.update { snapshot }
                _inlineCommentError.update { e.localizedMessage ?: "Failed to delete comment" }
            } finally {
                _busyReviewComments.update { it - commentId }
            }
        }
    }

    /**
     * Resolve a review thread via GraphQL. Uses the thread node id lookup table
     * in [threadState]; if missing, refreshes once and retries.
     */
    fun resolveThread(rootCommentId: Long) {
        val info = _threadState.value[rootCommentId]
        if (info == null) {
            viewModelScope.launch {
                runCatching { fetchThreadState(loadedOwner ?: return@launch, loadedRepo ?: return@launch, loadedNumber ?: return@launch) }
                    .onSuccess { _threadState.value[rootCommentId]?.let { resolveRoot(rootCommentId, it.threadId) } }
            }
            return
        }
        resolveRoot(rootCommentId, info.threadId)
    }

    private fun resolveRoot(rootCommentId: Long, threadId: String) {
        if (rootCommentId in _busyReviewComments.value) return
        viewModelScope.launch {
            _busyReviewComments.update { it + rootCommentId }
            _inlineCommentError.update { null }
            try {
                runThreadMutation(RESOLVE_MUTATION, threadId)
                _threadState.update { map -> map[rootCommentId]?.let { info -> map + (rootCommentId to info.copy(isResolved = true)) } ?: map }
            } catch (e: Exception) {
                _inlineCommentError.update { e.localizedMessage ?: "Failed to mark as resolved" }
            } finally {
                _busyReviewComments.update { it - rootCommentId }
            }
        }
    }

    /** Unresolve a review thread via GraphQL; mirror of [resolveThread]. */
    fun unresolveThread(rootCommentId: Long) {
        val info = _threadState.value[rootCommentId]
        if (info == null) {
            viewModelScope.launch {
                runCatching { fetchThreadState(loadedOwner ?: return@launch, loadedRepo ?: return@launch, loadedNumber ?: return@launch) }
                    .onSuccess { _threadState.value[rootCommentId]?.let { unresolveRoot(rootCommentId, it.threadId) } }
            }
            return
        }
        unresolveRoot(rootCommentId, info.threadId)
    }

    private fun unresolveRoot(rootCommentId: Long, threadId: String) {
        if (rootCommentId in _busyReviewComments.value) return
        viewModelScope.launch {
            _busyReviewComments.update { it + rootCommentId }
            _inlineCommentError.update { null }
            try {
                runThreadMutation(UNRESOLVE_MUTATION, threadId)
                _threadState.update { map -> map[rootCommentId]?.let { info -> map + (rootCommentId to info.copy(isResolved = false)) } ?: map }
            } catch (e: Exception) {
                _inlineCommentError.update { e.localizedMessage ?: "Failed to unmark resolved" }
            } finally {
                _busyReviewComments.update { it - rootCommentId }
            }
        }
    }

    /**
     * Fire a GraphQL mutation (resolve / unresolve) for [threadId]. Throws on any
     * server-side or GraphQL-level error so callers fall into the rollback branch.
     */
    private suspend fun runThreadMutation(mutation: String, threadId: String) {
        val resp = api.graphQL(
            GitHubApi.GraphQLRequest(
                query = mutation,
                variables = mapOf("id" to JsonPrimitive(threadId)),
            ),
        )
        val errs = resp.errors
        if (!errs.isNullOrEmpty()) {
            throw IllegalStateException(errs.firstOrNull()?.message?.ifBlank { "GraphQL mutation failed" } ?: "GraphQL mutation failed")
        }
        if (resp.data == null) throw IllegalStateException("Empty GraphQL response")
    }

    /**
     * Pull list of PR review threads via GraphQL, filling [_threadState] with
     * `rootCommentId (databaseId) -> ThreadInfo(threadId, isResolved)`. Page size
     * 100 is GitHub GraphQL max for this connection. Best-effort: any error is
     * swallowed (thread resolve buttons will refresh on demand instead).
     */
    private suspend fun fetchThreadState(owner: String, repo: String, number: Int) {
        val resp = api.graphQL(
            GitHubApi.GraphQLRequest(
                query = REVIEW_THREADS_QUERY,
                variables = mapOf(
                    "owner" to JsonPrimitive(owner),
                    "repo" to JsonPrimitive(repo),
                    "number" to JsonPrimitive(number),
                ),
            ),
        )
        val errs = resp.errors
        if (!errs.isNullOrEmpty()) {
            _inlineCommentError.update { errs.firstOrNull()?.message ?: "Failed to fetch thread status" }
            return
        }
        val data = resp.data ?: return
        val threads = data["repository"]?.jsonObject
            ?.get("pullRequest")?.jsonObject
            ?.get("reviewThreads")?.jsonObject
            ?.get("nodes")?.jsonArray
            ?: return
        val map = mutableMapOf<Long, ThreadInfo>()
        for (thread in threads) {
            val threadObj = thread.jsonObject
            val threadId = threadObj["id"]?.jsonPrimitive?.content ?: continue
            val isResolved = threadObj["isResolved"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: continue
            val firstComment = threadObj["comments"]?.jsonObject?.get("nodes")?.jsonArray?.firstOrNull()?.jsonObject
            val dbId = firstComment?.get("databaseId")?.jsonPrimitive?.content?.toLongOrNull() ?: continue
            map[dbId] = ThreadInfo(threadId = threadId, isResolved = isResolved)
        }
        _threadState.update { map }
    }

    fun clearInlineCommentError() { _inlineCommentError.update { null } }

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
                    _mergeResult.update { "Merged" }
                    // Refresh PR to show merged state
                    _pr.update { null }
                    loadedNumber = null
                    loadPullRequest(owner, repo, number)
                } else {
                    _mergeResult.update { result?.message ?: "Merge failed (${response.code()})" }
                }
            } catch (e: Exception) {
                _mergeResult.update { e.localizedMessage ?: "Merge failed" }
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
                        "APPROVE" -> "Approved"
                        "REQUEST_CHANGES" -> "Changes requested"
                        else -> "Reviewed"
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
                _commentError.update { e.localizedMessage ?: "Failed to post comment" }
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
                _commentError.update { e.localizedMessage ?: "Failed to update comment" }
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
                _commentError.update { e.localizedMessage ?: "Failed to delete comment" }
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
                _commentError.update { e.localizedMessage ?: "Failed to toggle reaction" }
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

    companion object {
        /** Meta state for one review thread, used by R3 resolve/unresolve. */
        data class ThreadInfo(val threadId: String, val isResolved: Boolean)

        /**
         * GraphQL query listing PR review threads so we can map the REST root comment
         * id (databaseId) → GraphQL thread node id, plus the resolved flag which REST
         * doesn't surface.
         */
        private const val REVIEW_THREADS_QUERY = """
            query ReviewThreads(${'$'}owner: String!, ${'$'}repo: String!, ${'$'}number: Int!) {
              repository(owner: ${'$'}owner, name: ${'$'}repo) {
                pullRequest(number: ${'$'}number) {
                  reviewThreads(first: 100) {
                    nodes {
                      id
                      isResolved
                      comments(first: 1) {
                        nodes { databaseId }
                      }
                    }
                  }
                }
              }
            }
        """

        /** Mark a review comment thread resolved. */
        private const val RESOLVE_MUTATION = """
            mutation ResolveThread(${'$'}id: ID!) {
              resolveReviewThread(input: {threadId: ${'$'}id}) {
                thread { isResolved }
              }
            }
        """

        /** Mark a previously-resolved review thread unresolved. */
        private const val UNRESOLVE_MUTATION = """
            mutation UnresolveThread(${'$'}id: ID!) {
              unresolveReviewThread(input: {threadId: ${'$'}id}) {
                thread { isResolved }
              }
            }
        """
    }
}
