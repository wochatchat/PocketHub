package com.pockethub.data.remote

// Issue CRUD, comments, labels, milestones endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import com.pockethub.data.model.Issue
import com.pockethub.data.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface IssueEndpoints {

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
        @Body body: GitHubApi.IssueCreateRequest,
    ): Issue

    /** Single issue detail. */
    @GET("repos/{owner}/{repo}/issues/{number}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): Issue

    /** Lock conversation on an issue or PR. Server returns 200 with empty body. */
    @Headers("Accept: application/vnd.github+json")
    @PUT("repos/{owner}/{repo}/issues/{number}/lock")
    suspend fun lockIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): Response<Unit>

    /** Unlock conversation on an issue or PR. Server returns 204 with empty body. */
    @Headers("Accept: application/vnd.github+json")
    @DELETE("repos/{owner}/{repo}/issues/{number}/lock")
    suspend fun unlockIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): Response<Unit>

    /** Comments on an issue or PR. Returns a Response so callers can read the
     *  `link` header to detect whether more pages exist (the GitHub API doesn't
     *  return total_count for this endpoint). */
    @GET("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun getIssueComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1,
    ): Response<List<GitHubApi.IssueComment>>

    /**
     * Timeline events for an issue / PR — labeled, assigned, closed, reopened,
     * referenced, cross-referenced, milestoned, locked, unlocked, etc. Used to
     * render a chronological event stream interleaved with comments.
     */
    @GET("repos/{owner}/{repo}/issues/{number}/events")
    suspend fun getIssueEvents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
    ): Response<List<GitHubApi.IssueEvent>>

    @POST("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun createIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body body: GitHubApi.CommentRequest,
    ): GitHubApi.IssueComment

    /** Update an issue's editable fields. Null fields are left unchanged by GitHub. */
    @PATCH("repos/{owner}/{repo}/issues/{number}")
    suspend fun updateIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body body: GitHubApi.IssueUpdateRequest,
    ): Issue

    /** Labels configured for a repository. */
    @GET("repos/{owner}/{repo}/labels")
    suspend fun getRepositoryLabels(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
    ): List<Issue.Label>

    /**
     * Users that can be assigned to issues in this repo: the owner, members
     * with push access, and everyone who has opened an issue/PR here. Publicly
     * readable — this is what GitHub's own assignee picker shows.
     */
    @GET("repos/{owner}/{repo}/assignees")
    suspend fun getRepositoryAssignees(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
    ): List<User>

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
        @Body body: GitHubApi.CommentRequest,
    ): GitHubApi.IssueComment

    /** Delete a comment. */
    @DELETE("repos/{owner}/{repo}/issues/comments/{comment_id}")
    suspend fun deleteIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("comment_id") commentId: Long,
    ): Response<Unit>

    // ── Commits ──────────────────────────────────────────
}
