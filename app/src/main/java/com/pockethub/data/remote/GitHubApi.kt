package com.pockethub.data.remote

import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * GitHub REST API v3 interface.
 *
 * All endpoints require an authenticated token (set via [AuthInterceptor]).
 * See https://docs.github.com/en/rest for the full reference.
 */
interface GitHubApi :
    UserEndpoints,
    FollowEndpoints,
    RepoEndpoints,
    ContentEndpoints,
    IssueEndpoints,
    ReactionEndpoints,
    PullRequestEndpoints,
    CommitEndpoints,
    BranchEndpoints,
    ReleaseEndpoints,
    ActionEndpoints,
    NotificationEndpoints,
    EventEndpoints,
    SearchEndpoints,
    OAuthEndpoints,
    GraphQLEndpoints {

    @kotlinx.serialization.Serializable
    data class GitHubErrorBody(
        val message: String? = null,
        val documentation_url: String? = null,
    )

    // ──────────────────────────────────────────────
    //  File browsing (content API)
    // ──────────────────────────────────────────────

    @kotlinx.serialization.Serializable
    data class ReadmeResponse(
        val name: String = "",
        val path: String = "",
        val content: String = "",          // base64 encoded markdown body
        val encoding: String = "base64",
        @kotlinx.serialization.SerialName("download_url") val downloadUrl: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val size: Long = 0,
    )

    @kotlinx.serialization.Serializable
    data class WatchSubscription(
        val subscribed: Boolean = false,
        val ignored: Boolean = false,
        val reason: String? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        val url: String? = null,
        @kotlinx.serialization.SerialName("repository_url") val repositoryUrl: String? = null,
        @kotlinx.serialization.SerialName("thread_url") val threadUrl: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class WatchSubscriptionRequest(
        val subscribed: Boolean = true,
        val ignored: Boolean = false,
    )

    /** GitHub accepts `name` (and optional `default_branch_only`) on fork creation. */
    @kotlinx.serialization.Serializable
    data class ForkRequest(
        val name: String? = null,
        @kotlinx.serialization.SerialName("default_branch_only") val defaultBranchOnly: Boolean = false,
    )

    @kotlinx.serialization.Serializable
    data class RepoUpdateRequest(
        /**
         * `visibility: "public" | "private"` — GitHub's authoritative visibility
         * field. The legacy boolean `private` field still works but is deprecated
         * by GitHub; reaching for `visibility` avoids ambiguity (see
         * https://docs.github.com/en/rest/repos/repos#update-a-repository).
         */
        val visibility: String? = null,
        /** `private: true` makes the repo private; GitHub treats this field as authoritative for pub/priv toggle. */
        val `private`: Boolean? = null,
        /** Optional name update — pass-through only, left null for visibility changes. */
        val name: String? = null,
        /** Optional description update — left null for visibility changes. */
        val description: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class ContentEntry(
        val name: String = "",
        val path: String = "",
        val sha: String = "",
        @kotlinx.serialization.SerialName("download_url") val downloadUrl: String? = null,
        val type: String = "file", // "file" | "dir" | "symlink" | "submodule"
        val size: Long = 0,
        val content: String = "",   // base64 (only present for single-file fetches)
        val encoding: String = "none",
    )

    /** Response of the recursive git/trees endpoint. */
    @kotlinx.serialization.Serializable
    data class GitTreeResponse(
        val sha: String = "",
        val truncated: Boolean = false,
        val tree: List<GitTreeEntry> = emptyList(),
    )

    /** One entry of a git tree: blob (file) or tree (directory). */
    @kotlinx.serialization.Serializable
    data class GitTreeEntry(
        val path: String = "",
        val mode: String = "",
        val type: String = "blob", // "blob" | "tree" | "commit"
        val sha: String = "",
        val size: Long = 0,
    )

    // ──────────────────────────────────────────────
    //  Issues & Pull Requests
    // ──────────────────────────────────────────────
    @kotlinx.serialization.Serializable
    data class IssueCreateRequest(
        val title: String,
        val body: String? = null,
        val labels: List<String> = emptyList(),
        val assignees: List<String> = emptyList(),
        val milestone: Int? = null,
    )

    @kotlinx.serialization.Serializable
    data class IssueEvent(
        val id: Long = 0,
        val event: String = "",
        @kotlinx.serialization.SerialName("commit_id") val commitId: String? = null,
        @kotlinx.serialization.SerialName("commit_url") val commitUrl: String? = null,
        val actor: User? = null,
        val label: Issue.Label? = null,
        val assignee: User? = null,
        val assigner: User? = null,
        val milestone: Issue.Milestone? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class IssueComment(
        val id: Long = 0,
        val body: String = "",
        val user: User? = null,
        @kotlinx.serialization.SerialName("author_association") val authorAssociation: String? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val reactions: com.pockethub.data.model.Reactions? = null,
    )

    /** GitHub reaction content values accepted by the reactions API. */
    enum class ReactionContent(val apiValue: String) {
        PLUS_ONE("+1"),
        MINUS_ONE("-1"),
        LAUGH("laugh"),
        CONFUSED("confused"),
        HEART("heart"),
        HOORAY("hooray"),
        ROCKET("rocket"),
        EYES("eyes");
    }

    @kotlinx.serialization.Serializable
    data class ReactionResponse(
        val id: Long = 0,
        val user: User? = null,
        val content: String = "",
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class ReactionRequest(val content: String)

    // ── Pull Requests (dedicated PR endpoints) ──────────

    @kotlinx.serialization.Serializable
    data class PullUpdateRequest(
        val state: String, // "open" | "closed"
    )

    @kotlinx.serialization.Serializable
    data class ReviewComment(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        val path: String = "",
        val line: Int? = null,
        @kotlinx.serialization.SerialName("start_line") val startLine: Int? = null,
        @kotlinx.serialization.SerialName("original_line") val originalLine: Int? = null,
        @kotlinx.serialization.SerialName("original_start_line") val originalStartLine: Int? = null,
        @kotlinx.serialization.SerialName("in_reply_to_id") val inReplyToId: Long? = null,
        val body: String = "",
        val user: User? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("commit_id") val commitId: String? = null,
    )

    /**
     * Body for [createPullRequestReviewComment].
     *
     * Fields follow the GitHub v3 doc:
     *   https://docs.github.com/en/rest/pulls/comments#create-a-review-comment
     *
     * Two modes:
     *   1. New anchored line comment — `path`, `line`, `commit_id`, `side` required.
     *   2. Reply within an existing thread — `in_reply_to_id` of the root comment;
     *      `path`/`line`/`commit_id` are ignored by the server in this mode.
     *
     * `subject_type` is "line" by default; `side` defaults to "RIGHT" (new file).
     */
    @kotlinx.serialization.Serializable
    data class ReviewCommentRequest(
        val body: String,
        @kotlinx.serialization.SerialName("in_reply_to_id") val inReplyToId: Long? = null,
        @kotlinx.serialization.SerialName("commit_id") val commitId: String? = null,
        val path: String? = null,
        val line: Int? = null,
        @kotlinx.serialization.SerialName("start_line") val startLine: Int? = null,
        val side: String = "RIGHT",
        @kotlinx.serialization.SerialName("start_side") val startSide: String? = null,
        @kotlinx.serialization.SerialName("subject_type") val subjectType: String = "line",
    )

    @kotlinx.serialization.Serializable
    data class PullRequest(
        val id: Long = 0,
        val number: Int = 0,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val state: String = "open", // "open" | "closed"
        @kotlinx.serialization.SerialName("state_reason") val stateReason: String? = null,
        val title: String = "",
        val body: String? = null,
        val user: User? = null,
        val labels: List<Issue.Label> = emptyList(),
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("closed_at") val closedAt: String? = null,
        @kotlinx.serialization.SerialName("merged_at") val mergedAt: String? = null,
        @kotlinx.serialization.SerialName("merged") val merged: Boolean = false,
        @kotlinx.serialization.SerialName("mergeable") val mergeable: Boolean? = null,
        @kotlinx.serialization.SerialName("merge_state") val mergeState: String? = null,
        @kotlinx.serialization.SerialName("merge_commit_sha") val mergeCommitSha: String? = null,
        @kotlinx.serialization.SerialName("draft") val draft: Boolean = false,
        val head: RefInfo? = null,
        val base: RefInfo? = null,
        @kotlinx.serialization.SerialName("changed_files") val changedFiles: Int = 0,
        @kotlinx.serialization.SerialName("additions") val additions: Int = 0,
        @kotlinx.serialization.SerialName("deletions") val deletions: Int = 0,
        @kotlinx.serialization.SerialName("commits") val commits: Int = 0,
        @kotlinx.serialization.SerialName("review_comments") val reviewComments: Int = 0,
        val comments: Int = 0,
        @kotlinx.serialization.SerialName("requested_reviewers") val requestedReviewers: List<User> = emptyList(),
        @kotlinx.serialization.SerialName("requested_teams") val requestedTeams: List<Team> = emptyList(),
        @kotlinx.serialization.SerialName("merged_by") val mergedBy: User? = null,
    ) {
        @kotlinx.serialization.Serializable
        data class RefInfo(
            val label: String = "",
            val ref: String = "",
            val sha: String = "",
            val repo: Repository? = null,
        )

        @kotlinx.serialization.Serializable
        data class Team(
            val id: Long = 0,
            val name: String = "",
            val slug: String = "",
        )
    }

    @kotlinx.serialization.Serializable
    data class PullRequestFile(
        val sha: String = "",
        val filename: String = "",
        val status: String = "", // "added" | "modified" | "removed" | "renamed"
        val additions: Int = 0,
        val deletions: Int = 0,
        val changes: Int = 0,
        val patch: String? = null,
        @kotlinx.serialization.SerialName("previous_filename") val previousFilename: String? = null,
        @kotlinx.serialization.SerialName("raw_url") val rawUrl: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class PullRequestReview(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        val user: User? = null,
        val state: String = "", // "APPROVED" | "CHANGES_REQUESTED" | "COMMENTED" | "DISMISSED" | "PENDING"
        val body: String? = null,
        @kotlinx.serialization.SerialName("submitted_at") val submittedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("pull_request_url") val pullRequestUrl: String? = null,
        @kotlinx.serialization.SerialName("author_association") val authorAssociation: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class MergeRequest(
        val commit_title: String? = null,
        val commit_message: String? = null,
        val merge_method: String = "merge", // "merge" | "squash" | "rebase"
    )

    @kotlinx.serialization.Serializable
    data class MergeResult(
        val sha: String? = null,
        val merged: Boolean = false,
        val message: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class ReviewRequest(
        val body: String? = null,
        val event: String, // "APPROVE" | "REQUEST_CHANGES" | "COMMENT"
        @kotlinx.serialization.SerialName("comments") val comments: List<GitHubApi.ReviewInlineComment> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class RequestedReviewersBody(
        val reviewers: List<String> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class ReviewInlineComment(
        val path: String? = null,
        val position: Int? = null,
        val body: String = "",
    )

    // ── Issue / PR actions ──────────────────────────────

    @kotlinx.serialization.Serializable
    data class CommentRequest(val body: String)

    @kotlinx.serialization.Serializable
    data class IssueUpdateRequest(
        val title: String? = null,
        val body: String? = null,
        val state: String? = null,
        val labels: List<String>? = null,
        val assignees: List<String>? = null,
        val milestone: Int? = null,
    )

    @kotlinx.serialization.Serializable
    data class CommitCommentCreate(
        val body: String,
        // Optional positional fields — omitted for top-level comments.
        val path: String? = null,
        val position: Int? = null,
        val line: Int? = null,
    )

    @kotlinx.serialization.Serializable
    data class UpdateRefRequest(
        val sha: String,
        val force: Boolean = false,
    )

    @kotlinx.serialization.Serializable
    data class CommitComment(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val body: String = "",
        val path: String? = null,
        val position: Int? = null,
        val line: Int? = null,
        val user: User? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class Commit(
        val sha: String = "",
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val commit: CommitInfo? = null,
        val author: User? = null,
        val committer: User? = null,
        @kotlinx.serialization.SerialName("parents") val parents: List<Parent> = emptyList(),
    ) {
        @kotlinx.serialization.Serializable
        data class CommitInfo(
            val message: String = "",
            val author: CommitAuthor? = null,
            val committer: CommitAuthor? = null,
        ) {
            @kotlinx.serialization.Serializable
            data class CommitAuthor(
                val name: String = "",
                val email: String = "",
                val date: String? = null,
            )
        }
        @kotlinx.serialization.Serializable
        data class Parent(val sha: String = "")
    }

    @kotlinx.serialization.Serializable
    data class CommitDetail(
        val sha: String = "",
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val commit: GitHubApi.Commit.CommitInfo? = null,
        val author: User? = null,
        val committer: User? = null,
        val stats: CommitStats? = null,
        val files: List<CommitFile> = emptyList(),
        @kotlinx.serialization.SerialName("parents") val parents: List<GitHubApi.Commit.Parent> = emptyList(),
    ) {
        @kotlinx.serialization.Serializable
        data class CommitStats(
            val total: Int = 0,
            val additions: Int = 0,
            val deletions: Int = 0,
        )

        @kotlinx.serialization.Serializable
        data class CommitFile(
            val sha: String = "",
            val filename: String = "",
            val status: String = "", // "added" | "modified" | "removed" | "renamed"
            val additions: Int = 0,
            val deletions: Int = 0,
            val changes: Int = 0,
            val patch: String? = null,
            @kotlinx.serialization.SerialName("previous_filename") val previousFilename: String? = null,
            @kotlinx.serialization.SerialName("raw_url") val rawUrl: String? = null,
            @kotlinx.serialization.SerialName("blob_url") val blobUrl: String? = null,
        )
    }

    // ── Branches ──────────────────────────────────────────

    @kotlinx.serialization.Serializable
    data class Branch(
        val name: String = "",
        val commit: BranchCommit? = null,
        val `protected`: Boolean = false,
    ) {
        @kotlinx.serialization.Serializable
        data class BranchCommit(
            val sha: String = "",
            @kotlinx.serialization.SerialName("url") val url: String? = null,
        )
    }

    @kotlinx.serialization.Serializable
    data class CheckRunsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        val runs: List<GitHubApi.CheckRun> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class CheckRun(
        val id: Long = 0,
        val name: String = "",
        val status: String? = null,              // queued | in_progress | completed
        val conclusion: String? = null,          // success | failure | neutral | cancelled | skipped | timed_out | action_required | stale
        @kotlinx.serialization.SerialName("started_at") val startedAt: String? = null,
        @kotlinx.serialization.SerialName("completed_at") val completedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("details_url") val detailsUrl: String? = null,
        val app: GitHubApi.CheckApp? = null,
    )

    @kotlinx.serialization.Serializable
    data class CheckApp(
        val name: String = "",
        @kotlinx.serialization.SerialName("slug") val slug: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowDispatchRequest(
        /** GitHubApi.Branch or tag name the workflow should run on. */
        val ref: String,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        @kotlinx.serialization.SerialName("workflows") val workflows: List<GitHubApi.Workflow> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class Workflow(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        val name: String = "",
        val path: String = "",
        val state: String = "",
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("badge_url") val badgeUrl: String? = null,
        @kotlinx.serialization.SerialName("deleted_at") val deletedAt: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowRunsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        @kotlinx.serialization.SerialName("workflow_runs") val runs: List<GitHubApi.WorkflowRun> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class WorkflowRun(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        val name: String = "",
        /** Run display title (defaults to the commit message subject on GitHub). */
        @kotlinx.serialization.SerialName("display_title") val displayTitle: String? = null,
        @kotlinx.serialization.SerialName("head_branch") val headBranch: String? = null,
        @kotlinx.serialization.SerialName("head_sha") val headSha: String? = null,
        val path: String? = null,
        @kotlinx.serialization.SerialName("run_number") val runNumber: Int = 0,
        val event: String? = null,
        val status: String? = null,
        val conclusion: String? = null,
        @kotlinx.serialization.SerialName("workflow_id") val workflowId: Long? = null,
        val url: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("run_started_at") val runStartedAt: String? = null,
        val actor: User? = null,
        @kotlinx.serialization.SerialName("head_commit")
        val headCommit: GitHubApi.HeadCommit? = null,
    )

    @kotlinx.serialization.Serializable
    data class HeadCommit(
        val message: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class Release(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("tag_name") val tagName: String = "",
        @kotlinx.serialization.SerialName("name") val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("published_at") val publishedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val author: User? = null,
        @kotlinx.serialization.SerialName("assets") val assets: List<ReleaseAsset> = emptyList(),
    ) {
        @kotlinx.serialization.Serializable
        data class ReleaseAsset(
            val id: Long = 0,
            val name: String = "",
            @kotlinx.serialization.SerialName("download_count") val downloadCount: Int = 0,
            val size: Long = 0,
            @kotlinx.serialization.SerialName("browser_download_url") val browserDownloadUrl: String = "",
        )
    }

    // ──────────────────────────────────────────────
    //  Notifications
    // ──────────────────────────────────────────────

    @kotlinx.serialization.Serializable
    data class SearchRepoResult(
        val total_count: Int = 0,
        val incomplete_results: Boolean = false,
        val items: List<Repository> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class SearchUserResult(
        val total_count: Int = 0,
        val incomplete_results: Boolean = false,
        val items: List<User> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class SearchCodeResult(
        val total_count: Int = 0,
        val incomplete_results: Boolean = false,
        val items: List<GitHubApi.CodeSearchItem> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class CodeSearchItem(
        val name: String = "",
        val path: String = "",
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String = "",
        val repository: Repository? = null,
    )

    /** Wrapper returned by /search/issues (issues + PRs share this shape). */
    @kotlinx.serialization.Serializable
    data class SearchIssueResult(
        val total_count: Int = 0,
        val incomplete_results: Boolean = false,
        val items: List<com.pockethub.data.model.Issue> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class OAuthTokenResponse(
        val access_token: String = "",
        val token_type: String = "",
        val scope: String = "",
        // OAuth session renewal — present only when the OAuth App has token
        // expiration enabled. Absent/0 for non-expiring tokens; with defaults
        // so every existing parse path keeps working unchanged.
        @kotlinx.serialization.SerialName("refresh_token") val refreshToken: String = "",
        @kotlinx.serialization.SerialName("expires_in") val expiresIn: Long = 0,
        @kotlinx.serialization.SerialName("refresh_token_expires_in") val refreshTokenExpiresIn: Long = 0,
        @kotlinx.serialization.SerialName("error") val error: String? = null,
        @kotlinx.serialization.SerialName("error_description") val errorDescription: String? = null,
    )

    // ──────────────────────────────────────────────
    //  PR inline review comment edit / delete
    //  (https://docs.github.com/en/rest/pulls/comments)
    // ──────────────────────────────────────────────

    /** Body for editing a pull request review comment. */
    @kotlinx.serialization.Serializable
    data class EditReviewCommentRequest(val body: String)

    /**
     * Body for a GraphQL query / mutation request.
     *
     * GitHub GraphQL v4 accepts POST with `{query, variables, operationName}`; the
     * `operationName` and `variables` fields can be omitted for single-operation
     * queries like the resolve / unresolve mutations used by this feature.
     */
    @kotlinx.serialization.Serializable
    data class GraphQLRequest(
        val query: String,
        @kotlinx.serialization.SerialName("variables") val variables: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    )

    /**
     * GitHub GraphQL response. `data` holds the per-field results object and
     * `errors` is non-empty on failure; both are optional per the GraphQL spec.
     */
    @kotlinx.serialization.Serializable
    data class GraphQLResponse(
        val data: kotlinx.serialization.json.JsonObject? = null,
        val errors: List<GitHubApi.GraphQLError>? = null,
    )

    @kotlinx.serialization.Serializable
    data class GraphQLError(
        val message: String = "",
        @kotlinx.serialization.SerialName("type") val type: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class ArtifactsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        val artifacts: List<GitHubApi.Artifact> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class Artifact(
        val id: Long = 0,
        val name: String = "",
        @kotlinx.serialization.SerialName("size_in_bytes") val sizeInBytes: Long = 0,
        @kotlinx.serialization.SerialName("archive_download_url") val archiveDownloadUrl: String = "",
        val expired: Boolean = false,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("expires_at") val expiresAt: String? = null,
        @kotlinx.serialization.SerialName("workflow_run") val workflowRun: GitHubApi.ArtifactWorkflowRun? = null,
    )

    @kotlinx.serialization.Serializable
    data class ArtifactWorkflowRun(
        val id: Long? = null,
        @kotlinx.serialization.SerialName("head_branch") val headBranch: String? = null,
        @kotlinx.serialization.SerialName("head_sha") val headSha: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowJobsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        val jobs: List<GitHubApi.WorkflowJob> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class WorkflowJob(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("run_id") val runId: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        @kotlinx.serialization.SerialName("head_sha") val headSha: String? = null,
        val status: String? = null,              // queued | in_progress | completed
        val conclusion: String? = null,          // success | failure | cancelled | skipped | neutral
        val name: String = "",
        val steps: List<GitHubApi.WorkflowStep> = emptyList(),
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        @kotlinx.serialization.SerialName("started_at") val startedAt: String? = null,
        @kotlinx.serialization.SerialName("completed_at") val completedAt: String? = null,
        val runnerName: String? = null,
        @kotlinx.serialization.SerialName("runner_group_id") val runnerGroupId: Long? = null,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowStep(
        val name: String = "",
        val status: String = "",                // queued | in_progress | completed
        val conclusion: String? = null,         // success | failure | cancelled | skipped
        val number: Int = 0,
        @kotlinx.serialization.SerialName("started_at") val startedAt: String? = null,
        @kotlinx.serialization.SerialName("completed_at") val completedAt: String? = null,
    )
}
