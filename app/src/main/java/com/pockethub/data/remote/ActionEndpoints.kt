package com.pockethub.data.remote

// GitHub Actions: workflow runs, check runs, jobs, artifacts endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ActionEndpoints {

    /** GitHub Actions workflow runs for a repo. */
    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
        @Query("branch") branch: String? = null,
    ): GitHubApi.WorkflowRunsResponse

    /** Fetch exactly one workflow run by id — never guess from a list page. */
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
    ): GitHubApi.WorkflowRun

    /** Workflow runs for a single workflow definition (filter chip source). */
    @GET("repos/{owner}/{repo}/actions/workflows/{workflow_id}/runs")
    suspend fun getWorkflowRunsForWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: Long,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
        @Query("branch") branch: String? = null,
    ): GitHubApi.WorkflowRunsResponse

    /**
     * List check runs for a given commit ref — the canonical source for "PR checks"
     * (the PR header on GitHub web shows exactly this aggregate). Includes GitHub
     * Actions plus all third-party CI apps.
     */
    @GET("repos/{owner}/{repo}/commits/{ref}/check-runs")
    suspend fun listCheckRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("filter") filter: String = "latest",
    ): GitHubApi.CheckRunsResponse

    /**
     * List workflows (definitions) for a repo.
     * [ref] is optional — omit it to use the repo's default branch, or pass a
     * specific branch name to list workflows as defined on that branch.
     */
    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun getWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("ref") ref: String? = null,
    ): GitHubApi.WorkflowsResponse

    /** Trigger a `workflow_dispatch` event for a single workflow. */
    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: Long,
        @Body body: GitHubApi.WorkflowDispatchRequest,
    ): retrofit2.Response<Unit>

    /**
     * List build artifacts produced by a workflow run. Covers everything a
     * workflow uploaded via `actions/upload-artifact` regardless of format —
     * GitHub stores each artifact as a single zip (download endpoint returns
     * the zip). Expired artifacts (default 90-day retention) still appear in
     * the list but their download URL 404s.
     */
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun getWorkflowRunArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
    ): GitHubApi.ArtifactsResponse

    /** List jobs for a specific workflow run. */
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun getWorkflowRunJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("filter") filter: String = "latest",
    ): GitHubApi.WorkflowJobsResponse

    /**
     * Per-job logs endpoint. GitHub responds with HTTP 302 to a signed
     * objects.githubusercontent.com URL (zip). The retrofit call therefore must
     * use [retrofit2.Response] to surface the Location header for callers that
     * want to follow it themselves, or for callers that just want a 302 sentinel.
     */
    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    suspend fun getWorkflowJobLogsUrl(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long,
    ): retrofit2.Response<Unit>

    @PUT("repos/{owner}/{repo}/actions/runs/{run_id}/cancel")
    suspend fun cancelWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
    ): retrofit2.Response<Unit>

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/rerun")
    suspend fun rerunWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
    ): retrofit2.Response<Unit>
}
