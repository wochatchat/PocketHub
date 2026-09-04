package com.pockethub.data.remote

// User profile endpoints.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.

import com.pockethub.data.model.Repository
import com.pockethub.data.model.User
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface UserEndpoints {

    /** Validate the current token and return the authenticated user. */
    @GET("user")
    suspend fun getAuthenticatedUser(): User

    /** User profile by login. */
    @GET("users/{login}")
    suspend fun getUser(@Path("login") login: String): User

    // ──────────────────────────────────────────────
    //  User following
    // ──────────────────────────────────────────────

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
}
