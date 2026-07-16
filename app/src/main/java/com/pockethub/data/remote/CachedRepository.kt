package com.pockethub.data.remote

import com.pockethub.data.local.CacheDao
import com.pockethub.data.local.CachedItemEntity
import com.pockethub.data.model.FeedEvent
import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache-first wrapper around [GitHubApi]. Reads go through [CacheDao] with configurable
 * TTLs; writes bypass the cache and invalidate relevant entries.
 *
 * Cache TTLs:
 *  - Repositories / Issues / Releases / Feed: 5 minutes
 *  - Trending / Featured: 10 minutes (less time-sensitive)
 *  - Single repo / README: 3 minutes
 */
@Singleton
class CachedRepository @Inject constructor(
    private val api: GitHubApi,
    private val cacheDao: CacheDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val FIVE_MIN = 5 * 60 * 1000L
        private const val TEN_MIN = 10 * 60 * 1000L
        private const val THREE_MIN = 3 * 60 * 1000L
    }

    // ── Repositories ────────────────────────────────────

    suspend fun getMyRepositories(page: Int = 1, sort: String = "pushed", type: String? = null, visibility: String? = null): List<Repository> {
        val key = "repos:mine:$page:$sort:$type:$visibility"
        return cacheFirst(key, FIVE_MIN) {
            api.getMyRepositories(page = page, sort = sort, type = type, visibility = visibility)
        }
    }

    suspend fun getStarredRepositories(page: Int = 1): List<Repository> {
        val key = "repos:starred:$page"
        return cacheFirst(key, FIVE_MIN) {
            api.getStarredRepositories(page = page)
        }
    }

    suspend fun getRepository(owner: String, repo: String): Repository {
        val key = "repo:$owner/$repo"
        return cacheFirst(key, THREE_MIN) {
            api.getRepository(owner, repo)
        }
    }

    suspend fun getUserRepositories(login: String, page: Int = 1, sort: String = "updated"): List<Repository> {
        val key = "repos:user:$login:$page:$sort"
        return cacheFirst(key, FIVE_MIN) {
            api.getUserRepositories(login, page = page, sort = sort)
        }
    }

    // ── Issues ──────────────────────────────────────────

    suspend fun getIssues(owner: String, repo: String, state: String = "open", page: Int = 1): List<Issue> {
        val key = "issues:$owner/$repo:$state:$page"
        return cacheFirst(key, FIVE_MIN) {
            api.getIssues(owner, repo, state = state, page = page)
        }
    }

    // ── Releases ────────────────────────────────────────

    suspend fun getReleases(owner: String, repo: String, page: Int = 1): List<GitHubApi.Release> {
        val key = "releases:$owner/$repo:$page"
        return cacheFirst(key, FIVE_MIN) {
            api.getReleases(owner, repo, page = page)
        }
    }

    // ── Feed / Trending ─────────────────────────────────

    suspend fun searchTrending(query: String, sort: String = "stars", perPage: Int = 20): GitHubApi.SearchRepoResult {
        val key = "trending:$query:$sort:$perPage"
        return cacheFirst(key, TEN_MIN) {
            api.searchTrending(query = query, sort = sort, perPage = perPage)
        }
    }

    suspend fun getReceivedEvents(login: String, perPage: Int = 30): List<FeedEvent> {
        val key = "feed:$login:$perPage"
        return cacheFirst(key, FIVE_MIN) {
            api.getReceivedEvents(login, perPage = perPage)
        }
    }

    // ── Notifications (always fresh — don't cache these) ─

    suspend fun getNotifications(page: Int = 1, all: Boolean = false): List<com.pockethub.data.model.GitHubNotification> =
        api.getNotifications(page = page, all = all)

    // ── README ──────────────────────────────────────────

    suspend fun getReadme(owner: String, repo: String): GitHubApi.ReadmeResponse {
        val key = "readme:$owner/$repo"
        return cacheFirst(key, THREE_MIN) {
            api.getReadme(owner, repo)
        }
    }

    // ── Cache invalidation ──────────────────────────────

    /** Clear all cached items for a specific repo (e.g. after starring/unstarring). */
    suspend fun invalidateRepo(owner: String, repo: String) {
        cacheDao.evictOlderThan(Long.MAX_VALUE) // nuke everything — simple for now
    }

    /** Evict items older than [maxAge]. Called periodically or on manual refresh. */
    suspend fun evictOlderThan(maxAge: Long) {
        cacheDao.evictOlderThan(maxAge)
    }

    suspend fun clearCache(): Int = cacheDao.clearAll()

    // ── Generic cache-first helper ──────────────────────

    @OptIn(ExperimentalSerializationApi::class)
    private suspend inline fun <reified T> cacheFirst(
        key: String,
        ttlMs: Long,
        fetch: suspend () -> T,
    ): T {
        // 1. Check cache
        val cached = cacheDao.getIfFresh(key, System.currentTimeMillis() - ttlMs)
        if (cached != null) {
            return json.decodeFromString(serializer(), cached)
        }
        // 2. Fetch from network
        val result = fetch()
        // 3. Cache the result
        try {
            val serialized = json.encodeToString(serializer(), result)
            cacheDao.put(CachedItemEntity(key = key, json = serialized))
        } catch (_: Exception) {
            // Serialization failure is non-fatal — we just won't cache
        }
        return result
    }
}
