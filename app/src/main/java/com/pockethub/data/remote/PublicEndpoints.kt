package com.pockethub.data.remote

import com.pockethub.data.model.User
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * UNAUTHENTICATED mirror of the public read endpoints that the follow
 * lists need.
 *
 * Why it exists: GitHub org policies (OAuth-app access restrictions / IP
 * allow lists) make GitHub answer requests made with a member's OAuth-app
 * token to org-related endpoints with a 404 —
 * "Although you appear to have the correct authorization credentials, the
 * `<org>` organization has ..." — even when the data is public. The same
 * resource fetched WITHOUT credentials succeeds (verified on-device).
 * VMs call the authed endpoint first and fall back here on failure via
 * [withPublicFallback].
 */
interface PublicEndpoints {

    /** Followers of a user/org — public data, no token needed. */
    @GET("users/{login}/followers")
    suspend fun getFollowers(
        @Path("login") login: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): List<User>

    /** Users/orgs the given account follows — public data. */
    @GET("users/{login}/following")
    suspend fun getFollowing(
        @Path("login") login: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): List<User>
}

/**
 * Try [authed] first; on failure retry [anonymous]. Returns
 * (data, null) when either succeeds, or (null, the ORIGINAL error) when
 * both fail — the authed error is the more actionable one to surface.
 */
suspend fun <T> withPublicFallback(
    authed: suspend () -> T,
    anonymous: suspend () -> T,
): Pair<T?, Exception?> = try {
    authed() to null
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    try {
        anonymous() to null
    } catch (e2: kotlinx.coroutines.CancellationException) {
        throw e2
    } catch (_: Exception) {
        null to e
    }
}
