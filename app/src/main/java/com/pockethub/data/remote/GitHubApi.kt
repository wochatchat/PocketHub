package com.pockethub.data.remote

import com.pockethub.data.model.GitHubNotification
import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * GitHub REST API v3 interface.
 *
 * All endpoints require an authenticated token (set via [AuthInterceptor]).
 * See https://docs.github.com/en/rest for the full reference.
 */
interface GitHubApi {

    // ──────────────────────────────────────────────
    //  Auth / User
    // ──────────────────────────────────────────────

    /** Validate the current token and return the authenticated user. */
    @GET("user")
    suspend fun getAuthenticatedUser(): User

    /** User profile by login. */
    @GET("users/{login}")
    suspend fun getUser(@Path("login") login: String): User

    // ──────────────────────────────────────────────
    //  User following
    // ──────────────────────────────────────────────

    /** Check whether the authenticated user follows [login]. 204 = yes, 404 = no. */
    @GET("user/following/{login}")
    suspend fun checkFollowing(@Path("login") login: String): Response<Unit>

    /** Follow a user. */
    @PUT("user/following/{login}")
    suspend fun followUser(@Path("login") login: String): Response<Unit>

    /** Unfollow a user. */
    @DELETE("user/following/{login}")
    suspend fun unfollowUser(@Path("login") login: String): Response<Unit>

    /** Followers of a user. */
    @GET("users/{login}/followers")
    suspend fun getFollowers(
        @Path("login") login: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): List<User>

    /** Users the given user follows. */
    @GET("users/{login}/following")
    suspend fun getFollowing(
        @Path("login") login: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): List<User>

    // ──────────────────────────────────────────────
    //  Repositories
    // ──────────────────────────────────────────────

    /** Your repositories (paginated). */
    @GET("user/repos")
    suspend fun getMyRepositories(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "pushed",       // pushed | updated | created
        @Query("direction") direction: String = "desc",
        @Query("type") type: String? = null,           // owner | collaborator | member
        @Query("visibility") visibility: String? = null, // public | private
    ): List<Repository>

    /** Starred repositories. */
    @GET("user/starred")
    suspend fun getStarredRepositories(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "created",
        @Query("direction") direction: String = "desc",
    ): List<Repository>

    /** Repository by full name. */
    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Repository

    /** README — returns base64 content + download_url. Parsed into [ReadmeResponse]. */
    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadme(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): ReadmeResponse

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

    /** Toggle star — PUT with no body stars the repo. */
    @PUT("user/starred/{owner}/{repo}")
    suspend fun star(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /** Check if the current user has starred the repo — 204 starred, 404 not. */
    @GET("user/starred/{owner}/{repo}")
    suspend fun checkStarred(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    @DELETE("user/starred/{owner}/{repo}")
    suspend fun unstar(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /** Fork a repository — 202 Accepted, repo object returned when complete. */
    @POST("repos/{owner}/{repo}/forks")
    suspend fun forkRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Repository>

    /**
     * Delete a repository. Requires the authenticated user to be the owner (or an
     * org admin) AND the token to carry the `delete_repo` scope.
     * Returns 204 on success; 403 when missing rights/scope; 404 if not found.
     */
    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    // ──────────────────────────────────────────────
    //  File browsing (content API)
    // ──────────────────────────────────────────────

    /**
     * List contents of a directory or fetch a single file.
     *
     * The API returns either a [ContentEntry] (when `path` points to a file) or
     * a JSON array of [ContentEntry] (when it points to a directory). We declare the
     * return as [kotlinx.serialization.json.JsonElement] and decode in the caller via
     * [kotlinx.serialization.json.Json], so one method covers both cases.
     */
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String = "",
        @Query("ref") ref: String? = null,
    ): kotlinx.serialization.json.JsonElement

    /** Contents of the root of the repo's default branch (no path). */
    @GET("repos/{owner}/{repo}/contents")
    suspend fun getRootContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("ref") ref: String? = null,
    ): kotlinx.serialization.json.JsonElement

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

    // ──────────────────────────────────────────────
    //  Issues & Pull Requests
    // ──────────────────────────────────────────────

    /** Issues for a repo. (PRs are also returned by this endpoint.) */
    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "created",
        @Query("direction") direction: String = "desc",
    ): List<Issue>

    /** Create a new issue. */
    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: IssueCreateRequest,
    ): Issue

    @kotlinx.serialization.Serializable
    data class IssueCreateRequest(
        val title: String,
        val body: String? = null,
        val labels: List<String> = emptyList(),
        val assignees: List<String> = emptyList(),
        val milestone: Int? = null,
    )

    /** Single issue detail. */
    @GET("repos/{owner}/{repo}/issues/{number}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): Issue

    /** Comments on an issue or PR. */
    @GET("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun getIssueComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1,
    ): List<IssueComment>

    @kotlinx.serialization.Serializable
    data class IssueComment(
        val id: Long = 0,
        val body: String = "",
        val user: User? = null,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
        @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
        @kotlinx.serialization.SerialName("html_url") val htmlUrl: String? = null,
        val reactions: com.pockethub.data.model.Reactions? = null,
    )

    // ── Pull Requests (dedicated PR endpoints) ──────────

    /** Get a single pull request (includes merge info, diff stats, reviewers). */
    @GET("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun getPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
    ): PullRequest

    /** List files changed in a pull request. */
    @GET("repos/{owner}/{repo}/pulls/{pull_number}/files")
    suspend fun getPullRequestFiles(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<PullRequestFile>

    /** List reviews on a pull request. */
    @GET("repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    suspend fun getPullRequestReviews(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<PullRequestReview>

    /** Merge a pull request. */
    @PUT("repos/{owner}/{repo}/pulls/{pull_number}/merge")
    suspend fun mergePullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: MergeRequest = MergeRequest(),
    ): Response<MergeResult>

    /** Submit a pull request review. */
    @POST("repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    suspend fun createPullRequestReview(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: ReviewRequest,
    ): PullRequestReview

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
        @kotlinx.serialization.SerialName("comments") val comments: List<ReviewComment> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class ReviewComment(
        val path: String? = null,
        val position: Int? = null,
        val body: String = "",
    )

    // ── Issue / PR actions ──────────────────────────────

    @POST("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun createIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body body: CommentRequest,
    ): IssueComment

    @kotlinx.serialization.Serializable
    data class CommentRequest(val body: String)

    /** Update an issue's editable fields. Null fields are left unchanged by GitHub. */
    @PATCH("repos/{owner}/{repo}/issues/{number}")
    suspend fun updateIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body body: IssueUpdateRequest,
    ): Issue

    @kotlinx.serialization.Serializable
    data class IssueUpdateRequest(
        val title: String? = null,
        val body: String? = null,
        val state: String? = null,
        val labels: List<String>? = null,
        val assignees: List<String>? = null,
        val milestone: Int? = null,
    )

    /** Labels configured for a repository. */
    @GET("repos/{owner}/{repo}/labels")
    suspend fun getRepositoryLabels(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
    ): List<Issue.Label>

    /** Open milestones configured for a repository. */
    @GET("repos/{owner}/{repo}/milestones")
    suspend fun getRepositoryMilestones(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 100,
    ): List<Issue.Milestone>

    @PATCH("repos/{owner}/{repo}/issues/comments/{comment_id}")
    suspend fun editIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Body body: CommentRequest,
    ): IssueComment

    /** Delete a comment. */
    @DELETE("repos/{owner}/{repo}/issues/comments/{comment_id}")
    suspend fun deleteIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
    ): Response<Unit>

    // ── Commits ──────────────────────────────────────────

    /** List commits for a repo (paginated). */
    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sha") sha: String? = null, // branch or commit SHA
    ): List<Commit>

    /** Single commit detail (includes files diff). */
    @GET("repos/{owner}/{repo}/commits/{ref}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
    ): CommitDetail

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
        val commit: Commit.CommitInfo? = null,
        val author: User? = null,
        val committer: User? = null,
        val stats: CommitStats? = null,
        val files: List<CommitFile> = emptyList(),
        @kotlinx.serialization.SerialName("parents") val parents: List<Commit.Parent> = emptyList(),
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

    /** List branches for a repo. */
    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): List<Branch>

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

    /** Releases for a repo. */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
    ): List<Release>

    /** GitHub Actions workflow runs for a repo. */
    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
        @Query("branch") branch: String? = null,
    ): WorkflowRunsResponse

    /** List workflows (definitions) for a repo. */
    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun getWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): WorkflowsResponse

    /** Trigger a `workflow_dispatch` event for a single workflow. */
    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: Long,
        @Body body: WorkflowDispatchRequest,
    ): retrofit2.Response<Unit>

    @kotlinx.serialization.Serializable
    data class WorkflowDispatchRequest(
        /** Branch or tag name the workflow should run on. */
        val ref: String,
    )

    @kotlinx.serialization.Serializable
    data class WorkflowsResponse(
        @kotlinx.serialization.SerialName("total_count") val totalCount: Int = 0,
        @kotlinx.serialization.SerialName("workflows") val workflows: List<Workflow> = emptyList(),
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
        @kotlinx.serialization.SerialName("workflow_runs") val runs: List<WorkflowRun> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    data class WorkflowRun(
        val id: Long = 0,
        @kotlinx.serialization.SerialName("node_id") val nodeId: String? = null,
        val name: String = "",
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

    /** Unread notifications (all). */
    @GET("notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Query("all") all: Boolean = false,
        @Query("participating") participating: Boolean = false,
    ): List<GitHubNotification>

    /** Mark a thread as read. */
    @PATCH("notifications/threads/{thread_id}")
    suspend fun markNotificationRead(
        @Path("thread_id") threadId: String,
    ): Response<Unit>

    /** Mark all notifications as read. */
    @PUT("notifications")
    suspend fun markAllNotificationsRead(): Response<Unit>

    // ──────────────────────────────────────────────
    //  Activity feed (received_events — for the "Following" feed section)
    // ──────────────────────────────────────────────

    /** Public activity of a user (works for any public user; private events need the authed user). */
    @GET("users/{login}/received_events")
    suspend fun getReceivedEvents(
        @Path("login") login: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<com.pockethub.data.model.FeedEvent>

    /** Public activity of a single user. */
    @GET("users/{login}/events")
    suspend fun getUserEvents(
        @Path("login") login: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<com.pockethub.data.model.FeedEvent>

    /** Repositories owned/owned by a specific user. */
    @GET("users/{login}/repos")
    suspend fun getUserRepositories(
        @Path("login") login: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "updated",
        @Query("type") type: String? = null, // owner | member | all
    ): List<Repository>

    // ──────────────────────────────────────────────
    //  Trending (unofficial — scraped or search-based)
    // ──────────────────────────────────────────────

    /**
     * Generic repo search — single endpoint backing both the Explore feed
     * (Trending / Featured / For You sections) and the global Search screen.
     *
     * GitHub has no official Trending API; the search API is the closest
     * equivalent. Callers compose the appropriate `created:>/stars:>…` filter
     * strings and pick `sort`/`order`.
     */
    @GET("search/repositories")
    suspend fun searchTrending(
        @Query("q") query: String = "stars:>1",
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
    ): SearchRepoResult

    /** Global search — repositories. */
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
    ): SearchRepoResult

    /** Global search — users. */
    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
    ): SearchUserResult

    /** Global search — code. */
    @GET("search/code")
    suspend fun searchCode(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
    ): SearchCodeResult

    /**
     * Global search — issues & pull requests (GitHub's /search/issues endpoint
     * returns both; use `is:issue` / `is:pr` to scope). Backs the Profile work-list
     * ("Assigned to me", "Mentions me", "Created by me") via qualifier strings like
     * `assignee:<login> state:open`, `involves:<login>`, `author:<login>`.
     */
    @GET("search/issues")
    suspend fun searchIssues(
        @Query("q") query: String,
        @Query("sort") sort: String = "updated",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): SearchIssueResult

    // ──────────────────────────────────────────────
    //  Generic / raw endpoint for OAuth token exchange
    // ──────────────────────────────────────────────

    /** Exchange OAuth code for access token (POST to GitHub, not api.github.com). */
    @FormUrlEncoded
    @POST("https://github.com/login/oauth/access_token")
    suspend fun exchangeOAuthCode(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
    ): OAuthTokenResponse

    // ──────────────────────────────────────────────
    //  Search result wrappers
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
        val items: List<CodeSearchItem> = emptyList(),
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
        @kotlinx.serialization.SerialName("error") val error: String? = null,
        @kotlinx.serialization.SerialName("error_description") val errorDescription: String? = null,
    )
}
