package com.pockethub.ui.repo


import androidx.lifecycle.viewModelScope
import com.pockethub.util.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
internal fun RepoDetailViewModel.loadIssues(owner: String, repo: String, state: String? = null, force: Boolean = false): Job? {
    val effectiveState = state ?: _issueStateFilter.value.apiValue
    if (!force && loadedIssueState == effectiveState && (_issues.value.isNotEmpty() || _pulls.value.isNotEmpty())) return null
    loadedIssueState = effectiveState
    issuePage = 1
    issuesCanLoadMore = true
    return fetchIssuesPage(owner, repo, effectiveState, append = false, forceFresh = force)
}

internal fun RepoDetailViewModel.loadPulls(owner: String, repo: String, force: Boolean = false): Job? {
    // Dedicated /pulls endpoint with the PR tab's OWN filter. The old code
    // seeded _pulls from the /issues endpoint (page 1 mixes issues and PRs,
    // so 30 items could contain just 1 PR) and the guard then treated that
    // stale data as "already loaded" — the "only one PR shows / All is empty" bug.
    val effectiveState = _prStateFilter.value
    if (!force && loadedPullState == effectiveState.apiValue && _pulls.value.isNotEmpty()) return null
    loadedPullState = effectiveState.apiValue
    prPage = 1
    pullsCanLoadMore = true
    return fetchPullsPage(owner, repo, effectiveState, append = false, forceFresh = force)
}

/** Fetch the next page of PRs for the current filter. */
internal fun RepoDetailViewModel.loadMorePulls(owner: String, repo: String) {
    if (!pullsCanLoadMore || _isLoadingMorePulls.value) return
    prPage++
    fetchPullsPage(owner, repo, _prStateFilter.value, append = true)
}

/** Fetch the next page of issues for the current filter. */
internal fun RepoDetailViewModel.loadMoreIssues(owner: String, repo: String) {
    if (!issuesCanLoadMore || _isLoadingMoreIssues.value) return
    val state = _issueStateFilter.value.apiValue
    issuePage++
    fetchIssuesPage(owner, repo, state, append = true)
}

internal fun RepoDetailViewModel.fetchPullsPage(owner: String, repo: String, filter: PRStateFilter, append: Boolean, forceFresh: Boolean = false): Job {
    return viewModelScope.launch {
        if (append) _isLoadingMorePulls.update { true } else _isLoadingPulls.update { true }
        try {
            // MERGED: the /pulls list endpoint has no merged param — fetch
            // closed and filter on merged_at client-side.
            val apiState = if (filter == PRStateFilter.MERGED) "closed" else filter.apiValue
            val issuesState = if (filter == PRStateFilter.MERGED) "closed" else apiState
            // Both the pulls page and the comment counts are needed before the
            // list can publish, so run the two requests in parallel — total
            // latency is max(the two) instead of their sum.
            val issuesDeferred = async {
                runCatching { api.getIssues(owner, repo, state = issuesState, page = 1, perPage = 100) }
            }
            var pulls = api.getPullRequests(owner, repo, state = apiState, page = prPage)
            if (filter == PRStateFilter.MERGED) pulls = pulls.filter { it.isMerged }
            pullsCanLoadMore = pulls.size >= 30
            // The /pulls LIST endpoint omits the `comments` field entirely (it
            // only exists on /issues and PR detail), so rows would render "0 条评论"
            // and flicker to the real count after a post-publish patch. Harvest the
            // real counts from /issues — it also returns PRs — BEFORE publishing,
            // so the list appears once, already correct. Best-effort: on failure
            // the list still loads, just with 0s.
            if (pulls.isNotEmpty()) {
                runCatching {
                    val needed = pulls.map { it.number }.toSet()
                    val counts = mutableMapOf<Int, Int>()
                    // Consume the parallel /issues fetch; deeper pages only if
                    // the first 100 issues don't cover all PRs on this page
                    // (issue-heavy repos — appended PR pages sort older).
                    issuesDeferred.await().getOrDefault(emptyList())
                        .filter { it.pullRequest != null && it.number in needed }
                        .forEach { counts[it.number] = it.comments }
                    if (!counts.keys.containsAll(needed)) {
                        for (p in 2..5) {
                            val page = api.getIssues(owner, repo, state = issuesState, page = p, perPage = 100)
                            page.filter { it.pullRequest != null && it.number in needed }
                                .forEach { counts[it.number] = it.comments }
                            if (counts.keys.containsAll(needed) || page.size < 100) break
                        }
                    }
                    if (counts.isNotEmpty()) {
                        pulls = pulls.map { pr ->
                            counts[pr.number]?.let { if (it != pr.comments) pr.copy(comments = it) else pr } ?: pr
                        }
                    }
                }.onFailure { issueReporter.reportError("RepoDetail", "hydratePullCommentCounts", it) }
            }
            if (append) {
                val existingIds = _pulls.value.map { it.id }.toSet()
                _pulls.update { it + pulls.filter { n -> n.id !in existingIds } }
            } else {
                _pulls.update { pulls }
            }
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "fetchPullsPage", e)
            if (!append) _pulls.update { emptyList() }
            _error.update { e.userMessage("Failed to load pull requests") }
        } finally {
            if (append) _isLoadingMorePulls.update { false } else _isLoadingPulls.update { false }
        }
    }
}

internal fun RepoDetailViewModel.fetchIssuesPage(owner: String, repo: String, state: String, append: Boolean, forceFresh: Boolean = false): Job {
    return viewModelScope.launch {
        if (append) _isLoadingMoreIssues.update { true } else _isLoadingIssues.update { true }
        try {
            // forceFresh goes straight to the network (bypassing the 5-min TTL)
            // so pull-to-refresh always re-fetches instead of serving the same
            // cached blob — the "spinner spins but nothing changes" bug.
            val all = cache.getIssues(owner, repo, state = state, page = issuePage, forceFresh = forceFresh)
            // PRs used to be seeded here from the /issues response — that
            // clobbered the PR tab's list (page 1 of /issues holds at most a
            // couple PRs among 30 items). The PR tab is fed exclusively by
            // fetchPullsPage now.
            val issuesOnly = all.filter { it.pullRequest == null }
            if (append) {
                val existingIssueIds = _issues.value.map { it.id }.toSet()
                _issues.update { it + issuesOnly.filter { n -> n.id !in existingIssueIds } }
            } else {
                _issues.update { issuesOnly }
            }
            issuesCanLoadMore = all.size >= 30
        } catch (e: Exception) {
            issueReporter.reportError("RepoDetail", "fetchIssuesPage", e)
            if (!append) _issues.update { emptyList() }
            _error.update { e.userMessage("Failed to load issues") }
        } finally {
            if (append) _isLoadingMoreIssues.update { false } else _isLoadingIssues.update { false }
        }
    }
}
