package com.pockethub.ui.repo

import androidx.lifecycle.viewModelScope
import com.pockethub.util.userMessage
import com.pockethub.data.remote.GitHubApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal const val REVIEW_THREADS_QUERY = """
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

internal const val RESOLVE_MUTATION = """
    mutation ResolveThread(${'$'}id: ID!) {
      resolveReviewThread(input: {threadId: ${'$'}id}) {
        thread { isResolved }
      }
    }
"""

internal const val UNRESOLVE_MUTATION = """
    mutation UnresolveThread(${'$'}id: ID!) {
      unresolveReviewThread(input: {threadId: ${'$'}id}) {
        thread { isResolved }
      }
    }
"""



/**
 * Post a line-level review comment anchored to a file + line on the PR diff.
 * Optimistically appends to [reviewComments]; on failure rolls back + surfaces via [commentError].
 */
internal fun PullRequestDetailViewModel.postLineComment(path: String, line: Int, body: String, startLine: Int? = null) {
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
            issueReporter.reportError("PullRequestDetail", "postLineComment", e)
            _commentError.update { e.userMessage("Failed to post inline comment") }
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
internal fun PullRequestDetailViewModel.replyInlineComment(rootCommentId: Long, body: String) {
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
            issueReporter.reportError("PullRequestDetail", "replyInlineComment", e)
            _inlineCommentError.update { e.userMessage("Failed to reply") }
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
internal fun PullRequestDetailViewModel.editInlineComment(commentId: Long, newBody: String) {
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
            issueReporter.reportError("PullRequestDetail", "editInlineComment", e)
            _reviewComments.update { snapshot }
            _inlineCommentError.update { e.userMessage("Failed to update comment") }
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
internal fun PullRequestDetailViewModel.deleteInlineComment(commentId: Long) {
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
            issueReporter.reportError("PullRequestDetail", "deleteInlineComment", e)
            _reviewComments.update { snapshot }
            _inlineCommentError.update { e.userMessage("Failed to delete comment") }
        } finally {
            _busyReviewComments.update { it - commentId }
        }
    }
}

/**
 * Resolve a review thread via GraphQL. Uses the thread node id lookup table
 * in [threadState]; if missing, refreshes once and retries.
 */
internal fun PullRequestDetailViewModel.resolveThread(rootCommentId: Long) {
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

internal fun PullRequestDetailViewModel.resolveRoot(rootCommentId: Long, threadId: String) {
    if (rootCommentId in _busyReviewComments.value) return
    viewModelScope.launch {
        _busyReviewComments.update { it + rootCommentId }
        _inlineCommentError.update { null }
        try {
            runThreadMutation(RESOLVE_MUTATION, threadId)
            _threadState.update { map -> map[rootCommentId]?.let { info -> map + (rootCommentId to info.copy(isResolved = true)) } ?: map }
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "resolveRoot", e)
            _inlineCommentError.update { e.userMessage("Failed to mark as resolved") }
        } finally {
            _busyReviewComments.update { it - rootCommentId }
        }
    }
}

/** Unresolve a review thread via GraphQL; mirror of [resolveThread]. */
internal fun PullRequestDetailViewModel.unresolveThread(rootCommentId: Long) {
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

internal fun PullRequestDetailViewModel.unresolveRoot(rootCommentId: Long, threadId: String) {
    if (rootCommentId in _busyReviewComments.value) return
    viewModelScope.launch {
        _busyReviewComments.update { it + rootCommentId }
        _inlineCommentError.update { null }
        try {
            runThreadMutation(UNRESOLVE_MUTATION, threadId)
            _threadState.update { map -> map[rootCommentId]?.let { info -> map + (rootCommentId to info.copy(isResolved = false)) } ?: map }
        } catch (e: Exception) {
            issueReporter.reportError("PullRequestDetail", "unresolveRoot", e)
            _inlineCommentError.update { e.userMessage("Failed to unmark resolved") }
        } finally {
            _busyReviewComments.update { it - rootCommentId }
        }
    }
}

/**
 * Fire a GraphQL mutation (resolve / unresolve) for [threadId]. Throws on any
 * server-side or GraphQL-level error so callers fall into the rollback branch.
 */
internal suspend fun PullRequestDetailViewModel.runThreadMutation(mutation: String, threadId: String) {
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
internal suspend fun PullRequestDetailViewModel.fetchThreadState(owner: String, repo: String, number: Int) {
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

internal fun PullRequestDetailViewModel.clearInlineCommentError() { _inlineCommentError.update { null } }

internal suspend fun PullRequestDetailViewModel.hydrateReactions(owner: String, repo: String) {
    val current = accounts.getActiveLogin()
    if (current.isBlank()) return
    val targets = _comments.value.filter { it.reactions?.totalCount ?: 0 > 0 }
    if (targets.isEmpty()) return
    // Keep requests concurrent but bounded: GitHub API and mobile networks both
    // perform poorly when one request is made per comment in a serial loop.
    val permits = Semaphore(4)
    coroutineScope {
        targets.map { comment ->
            async {
                permits.withPermit {
                    runCatching { api.listIssueCommentReactions(owner, repo, comment.id) }
                        .onSuccess { list ->
                            val mine = list.filter { it.user?.login == current }
                                .associate { it.content to it.id }
                            if (mine.isNotEmpty()) {
                                _viewerReactions.update { it + (comment.id to mine) }
                            }
                        }
                }
            }
        }.awaitAll()
    }
}