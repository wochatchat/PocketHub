package com.pockethub.data.remote

import com.pockethub.data.model.GitHubNotification
import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User
import retrofit2.Response
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

    // ── Issue / PR actions ──────────────────────────────

    /** Create a comment on an issue or PR. */
    @FormUrlEncoded
    @POST("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun createIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Field("body") body: String,
    ): IssueComment

    /** Close or reopen an issue (state = "open" or "closed"). */
    @FormUrlEncoded
    @PATCH("repos/{owner}/{repo}/issues/{number}")
    suspend fun updateIssueState(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Field("state") state: String, // "open" or "closed"
    ): Issue

    /** Edit an existing comment. */
    @FormUrlEncoded
    @PATCH("repos/{owner}/{repo}/issues/comments/{comment_id}")
    suspend fun editIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
        @Field("body") body: String,
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

    @kotlinx.serialization.Serializable
    data class OAuthTokenResponse(
        val access_token: String = "",
        val token_type: String = "",
        val scope: String = "",
        @kotlinx.serialization.SerialName("error") val error: String? = null,
        @kotlinx.serialization.SerialName("error_description") val errorDescription: String? = null,
    )
}
