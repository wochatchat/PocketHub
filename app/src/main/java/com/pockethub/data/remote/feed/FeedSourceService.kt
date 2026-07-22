package com.pockethub.data.remote.feed

import com.pockethub.data.remote.CachedRepository
import com.pockethub.data.remote.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

/**
 * Loads Explore feed items for each tab from its configured [FeedSourceOption]
 * and normalises every source's response to a single [DiscoverItem] list.
 *
 * Conventions:
 *  - Third-party hosts are reached on a brand-new OkHttp client so GitHub's
 *    auth interceptor never sees those hosts (no token leakage).
 *  - All public APIs are tolerant to missing fields — we parse defensively
 *    and treat a single failed source as "empty list for this load", not as
 *    an app-wide error.
 *  - Sources whose default base URL is empty (Reddit, GitHub Trending API)
 *    require the user to fill a custom URL in Settings. When that's missing
 *    we surface a small "not configured" hint rather than firing requests.
 */
@Singleton
class FeedSourceService @Inject constructor(
    private val repo: FeedSourceRepository,
    private val cache: CachedRepository,
    private val api: GitHubApi, // reserved for direct-search variants; unused on the current path
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    private val bareClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Effective base URL for [source], with custom-URL override honoured,
     * trailing slash guaranteed. Empty string is returned verbatim — callers
     * use that to gate "not configured" branches.
     */
    fun baseUrlFor(source: FeedSourceOption, customBaseUrl: String): String {
        val raw = customBaseUrl.trim().ifEmpty { source.defaultBaseUrl }
        if (raw.isEmpty()) return ""
        return if (raw.endsWith("/")) raw else "$raw/"
    }

    /** Returns the "source ready to call" state so the screen can render an
     *  inline hint when a required custom URL hasn't been set yet. */
    fun isConfigured(source: FeedSourceOption, customBaseUrl: String): Boolean =
        source.defaultBaseUrl.isNotEmpty() || customBaseUrl.trim().isNotEmpty()

    // ── Trending ────────────────────────────────────────────────────────────

    suspend fun loadTrending(forceFresh: Boolean): List<DiscoverItem> {
        val cfg = repo.configFlow(FeedTab.TRENDING).first()
        return when (FeedSourceOption.fromId(cfg.sourceId)) {
            FeedSourceOption.GITHUB_TRENDING_API -> {
                if (!isConfigured(FeedSourceOption.GITHUB_TRENDING_API, cfg.customBaseUrl)) emptyList()
                    else fetchGitHubTrendingApi(cfg, forceFresh)
            }
            // The GitHub REST search-based path is the most stable; it's also the
            // implicit fallback for anything unexpected so an odd saved config still
            // shows results rather than blowing up the feed.
            else -> searchGitHub(cfg, forceFresh)
        }
    }

    // ── Featured ────────────────────────────────────────────────────────────

    suspend fun loadFeatured(forceFresh: Boolean): List<DiscoverItem> {
        val cfg = repo.configFlow(FeedTab.FEATURED).first()
        return when (FeedSourceOption.fromId(cfg.sourceId)) {
            FeedSourceOption.HACKER_NEWS_SHOWHN -> fetchHackerNewsShowHN(forceFresh)
            FeedSourceOption.REDDIT_TOP         ->
                if (!isConfigured(FeedSourceOption.REDDIT_TOP, cfg.customBaseUrl)) emptyList()
                else fetchRedditTop(forceFresh)
            // OSS Insight is the default Featured source; resolve any unrecognised
            // saved source id back to it to keep the tab from going dark.
            else -> fetchOssInsight(cfg, forceFresh)
        }
    }

    // ── Following (handled separately — returns GitHub events, not repos) ──
    suspend fun loadFollowing(activeLogin: String, perPage: Int = 30): List<com.pockethub.data.model.FeedEvent> {
        if (activeLogin.isBlank()) return emptyList()
        return cache.getReceivedEvents(activeLogin, perPage = perPage)
    }

    // ── GitHub Search (canonical Repository → DiscoverItem) ────────────────

    private suspend fun searchGitHub(cfg: FeedSourceConfig, forceFresh: Boolean): List<DiscoverItem> {
        val created = when (cfg.trendingRange) {
            "Weekly"  -> LocalDate.now().minusWeeks(1)
            "Monthly" -> LocalDate.now().minusMonths(1)
            else      -> LocalDate.now().minusDays(1)
        }.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val langPart = if (cfg.trendingLanguage == "All") "" else " language:${cfg.trendingLanguage}"
        // Cap stars in a rising-star band so we surface newcomers rather than the
        // evergreen Top-50. Filter out archived repos so we never show "dead" picks.
        val q = "stars:50..20000$langPart created:>$created archived:false"
        val perPage = 30
        val result = if (forceFresh) {
            cache.searchTrendingFresh(query = q, sort = "stars", perPage = perPage)
        } else {
            cache.searchTrending(query = q, sort = "stars", perPage = perPage)
        }
        return result.items.map { it.toDiscoverItem() }
    }

    private fun com.pockethub.data.model.Repository.toDiscoverItem(): DiscoverItem =
        DiscoverItem(
            id = DiscoverItem.stableId(owner.login, name),
            source = FeedSourceOption.GITHUB_SEARCH,
            owner = owner.login,
            repo = name,
            htmlUrl = htmlUrl ?: "https://github.com/${owner.login}/$name",
            description = description,
            language = language,
            stars = stars,
            forks = forks,
            topics = topics,
            ownerAvatarUrl = owner.avatarUrl,
        )

    // ── GitHub Trending API (community HTML-scraping forks) ─────────────────

    @Serializable
    private data class TrendingApiResponseItem(
        val author: String = "",
        val name: String = "",
        val url: String = "",
        val description: String? = null,
        val language: String? = null,
        val stars: Int = 0,
        val forks: Int = 0,
        @kotlinx.serialization.SerialName("currentPeriodStars") val currentPeriodStars: Int = 0,
    )

    private suspend fun fetchGitHubTrendingApi(cfg: FeedSourceConfig, forceFresh: Boolean): List<DiscoverItem> {
        val source = FeedSourceOption.GITHUB_TRENDING_API
        val base = baseUrlFor(source, cfg.customBaseUrl)
        if (base.isEmpty()) return emptyList()
        val since = when (cfg.trendingRange) {
            "Weekly"  -> "weekly"
            "Monthly" -> "monthly"
            else      -> "daily"
        }
        val lang = if (cfg.trendingLanguage == "All") "" else cfg.trendingLanguage.lowercase()
        val langParam = java.net.URLEncoder.encode(lang, "UTF-8")
        // Older forks speak `?since=&language=` directly on the base path; newer
        // ones put the list under `/repositories` (the original huchenme fork).
        // Try both shapes before giving up so the user's self-host URL only needs
        // to match one of them.
        val firstUrl = "${base}repositories?since=$since&language=$langParam"
        val secondUrl = "${base}?since=$since&language=$langParam"
        val items: List<TrendingApiResponseItem> = runCatching {
            requestJsonArray(firstUrl, forceFresh) ?: requestJsonArray(secondUrl, forceFresh).orEmpty()
        }.getOrDefault(emptyList())
        return items.map { it.toDiscoverItem(since) }
    }

    private fun TrendingApiResponseItem.toDiscoverItem(since: String): DiscoverItem {
        val owner = author
        val name = this.name
        val periodLabel = when (since) {
            "weekly"  -> "week"
            "monthly" -> "month"
            else      -> "day"
        }
        return DiscoverItem(
            id = DiscoverItem.stableId(owner, name),
            source = FeedSourceOption.GITHUB_TRENDING_API,
            owner = owner,
            repo = name,
            htmlUrl = url.takeIf { it.isNotBlank() } ?: "https://github.com/$owner/$name",
            description = description,
            language = language,
            stars = stars,
            forks = forks,
            ownerAvatarUrl = "https://avatars.githubusercontent.com/$owner",
            starDelta = if (currentPeriodStars > 0) StarDelta(currentPeriodStars, periodLabel) else null,
        )
    }

    // ── OSS Insight — /v1/trends/repos/?period=&language= ──────────────────

    /**
     * OSS Insight SQL endpoint format (verified against api.ossinsight.io on
     * 2026-07-22):
     *
     * {
     *   "type": "sql_endpoint",
     *   "data": {
     *     "columns": [
     *       {"col":"repo_id","data_type":"INT","nullable":true},
     *       {"col":"repo_name","data_type":"VARCHAR"}, // "owner/repo"
     *       {"col":"primary_language","data_type":"VARCHAR"},
     *       {"col":"description","data_type":"VARCHAR"},
     *       {"col":"stars","data_type":"INT"}, // STRING in JSON
     *       {"col":"forks","data_type":"INT"},
     *       {"col":"pull_requests","data_type":"INT"},
     *       {"col":"pushes","data_type":"INT"},
     *       {"col":"total_score","data_type":"DOUBLE"},
     *       {"col":"contributor_logins","data_type":"VARCHAR"},
     *       {"col":"collection_names","data_type":"VARCHAR"}
     *     ],
     *     "rows": [ { "repo_id": "...", "repo_name": "owner/repo", ... } ]
     *   }
     * }
     *
     * We're parsing the rows as raw `JsonObject` and pulling fields by name,
     * which survives column reordering and avoids hand-authoring a data class
     * for every column OSS Insight may add later.
     */
    private suspend fun fetchOssInsight(cfg: FeedSourceConfig, forceFresh: Boolean): List<DiscoverItem> {
        val source = FeedSourceOption.OSS_INSIGHT
        val base = baseUrlFor(source, cfg.customBaseUrl)
        val langParam = if (cfg.trendingLanguage == "All") {
            ""
        } else {
            "&language=" + java.net.URLEncoder.encode(cfg.trendingLanguage, "UTF-8")
        }
        val period = when (cfg.trendingRange) {
            "Weekly"  -> "past_7_days"
            "Monthly" -> "past_1_month"
            else      -> "past_24_hours"
        }
        val periodLabel = when (cfg.trendingRange) {
            "Weekly"  -> "week"
            "Monthly" -> "month"
            else      -> "day"
        }
        val url = "${base}trends/repos/?period=$period$langParam"
        return runCatching {
            val body = requestText(url, forceFresh) ?: return@runCatching emptyList<DiscoverItem>()
            // Top-level: { type, data: { columns, rows: [ {col:val,...} ] } }
            val env = json.parseToJsonElement(body) as? JsonObject
                ?: return@runCatching emptyList<DiscoverItem>()
            val data = env["data"] as? JsonObject
                ?: return@runCatching emptyList<DiscoverItem>()
            val rows = data["rows"] as? JsonArray ?: return@runCatching emptyList<DiscoverItem>()
            rows.mapNotNull { rowElement ->
                val row = rowElement as? JsonObject ?: return@mapNotNull null
                row.toDiscover(periodLabel)
            }
        }.getOrDefault(emptyList())
    }

    private fun JsonObject.toDiscover(periodLabel: String): DiscoverItem {
        val repoName = rowStr("repo_name").ifBlank { "unknown/repo" }
        val owner = repoName.substringBefore('/', "unknown")
        val repo = repoName.substringAfter('/', "repo")
        val stars = rowStr("stars").extractInt()
        val forks = rowStr("forks").extractInt()
        val totalScore = rowStr("total_score").extractInt()
        return DiscoverItem(
            id = DiscoverItem.stableId(owner, repo),
            source = FeedSourceOption.OSS_INSIGHT,
            owner = owner,
            repo = repo,
            htmlUrl = "https://github.com/$repoName",
            description = rowStr("description").takeIf { it.isNotBlank() },
            language = rowStr("primary_language").takeIf { it.isNotBlank() },
            stars = stars,
            forks = forks,
            ownerAvatarUrl = "https://avatars.githubusercontent.com/$owner",
            // total_score is OSS Insight's momentum proxy for the period; surface
            // it as the starDelta so the Explore card can print "↑ N in past day".
            starDelta = if (totalScore > 0) StarDelta(totalScore, periodLabel) else null,
        )
    }

    private fun JsonObject.rowStr(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    // ── Hacker News: showstories → filter GitHub links → top N ──────────────

    @Serializable
    private data class HnItem(
        val id: Long = 0,
        val title: String = "",
        val url: String? = null,
        val score: Int = 0,
        val by: String = "",
        @kotlinx.serialization.SerialName("descendants") val descendants: Int = 0,
    )

    private suspend fun fetchHackerNewsShowHN(forceFresh: Boolean): List<DiscoverItem> = coroutineScope {
        val source = FeedSourceOption.HACKER_NEWS_SHOWHN
        val base = baseUrlFor(source, "")
        val limit = if (forceFresh) 60 else 40
        val ids = runCatching {
            requestText("${base}showstories.json", forceFresh)
                ?.let { json.decodeFromString<List<Long>>(it) }
        }.getOrNull().orEmpty()

        // Parallel-limit at 20 so we stay well under HN's throttling threshold.
        val allItems = ids.take(limit).chunked(20).flatMap { chunk ->
            chunk.map { id ->
                async(Dispatchers.IO) {
                    runCatching {
                        requestText("${base}item/$id.json", forceFresh)
                            ?.let { json.decodeFromString<HnItem>(it) }
                    }.getOrNull()
                }
            }.awaitAll().mapNotNull { it }
        }

        allItems.mapNotNull { item ->
            val parsed = HnLinkParser.parseGitHubRepo(item.url) ?: return@mapNotNull null
            val (owner, repoName) = parsed
            DiscoverItem(
                id = DiscoverItem.stableId(owner, repoName),
                source = source,
                owner = owner,
                repo = repoName,
                htmlUrl = "https://github.com/$owner/$repoName",
                ownerAvatarUrl = "https://avatars.githubusercontent.com/$owner",
                description = item.title.takeIf { it.isNotBlank() },
                communitySignal = CommunitySignal(
                    platform = CommunitySignal.Platform.HACKER_NEWS,
                    postTitle = item.title,
                    postUrl = "https://news.ycombinator.com/item?id=${item.id}",
                    score = item.score,
                    author = item.by.ifBlank { null },
                ),
            )
        }.sortedByDescending { it.communitySignal?.score ?: 0 }
    }

    // ── Reddit JSON: r/programming + r/androiddev + r/MachineLearning weekly top

    private suspend fun fetchRedditTop(forceFresh: Boolean): List<DiscoverItem> {
        val source = FeedSourceOption.REDDIT_TOP
        val base = baseUrlFor(source, "")
        if (base.isEmpty()) return emptyList()
        val subs = listOf("programming", "androiddev", "MachineLearning")
        val limit = if (forceFresh) 30 else 25
        val all = mutableListOf<Triple<RedditJsonParser.ParsedPost, String, String>>()
        for (sub in subs) {
            val url = "${base}r/$sub/top.json?t=week&limit=$limit"
            val body = runCatching { requestText(url, forceFresh) }.getOrNull() ?: continue
            val posts = RedditJsonParser.parseTopPosts(body)
            for (p in posts) {
                val parsed = HnLinkParser.parseGitHubRepo(p.url) ?: continue
                all += Triple(p, parsed.first, parsed.second)
            }
        }
        return all
            .sortedByDescending { it.first.score }
            .map { (post, owner, repoName) ->
                DiscoverItem(
                    id = DiscoverItem.stableId(owner, repoName),
                    source = source,
                    owner = owner,
                    repo = repoName,
                    htmlUrl = "https://github.com/$owner/$repoName",
                    ownerAvatarUrl = "https://avatars.githubusercontent.com/$owner",
                    description = post.subreddit,
                    communitySignal = CommunitySignal(
                        platform = CommunitySignal.Platform.REDDIT,
                        postTitle = post.title,
                        postUrl = post.url,
                        score = post.score,
                        subreddit = post.subreddit,
                        author = null,
                    ),
                )
            }
    }

    // ── low-level HTTP ──────────────────────────────────────────────────────

    private suspend fun requestText(url: String, forceFresh: Boolean): String? = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url)
                .header("User-Agent", "PocketHub/1.0 (Android; +https://github.com/wochatchat/PocketHub)")
                .header("Accept", "application/json, text/plain;q=0.9")
            if (forceFresh) builder.header("Cache-Control", "no-cache")
            val resp = bareClient.newCall(builder.build()).execute()
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string()
        }.getOrNull()
    }

    private suspend fun requestJsonArray(url: String, forceFresh: Boolean): List<TrendingApiResponseItem>? =
        requestText(url, forceFresh)?.let {
            runCatching { json.decodeFromString<List<TrendingApiResponseItem>>(it) }.getOrNull()
        }
}

/** Greedy-parse the first integer-looking sequence out of an OSS-Insight value. */
private fun String.extractInt(): Int {
    val digits = filter { it.isDigit() }
    return digits.toIntOrNull() ?: 0
}

/** Simple GitHub repository-URL parser shared by HN and Reddit extracts. */
private object HnLinkParser {
    private val regex = Regex(
        "https?://(?:www\\.)?github\\.com/([A-Za-z0-9_\\-+.]+)/([A-Za-z0-9_.\\-]+)",
        RegexOption.IGNORE_CASE,
    )
    private val blockedOwners = setOf(
        "about", "features", "pricing", "login", "signup", "settings",
        "explore", "trending", "collections", "sponsors", "marketplace",
        "orgs", "topics", "notifications", "pulls", "issues", "new", "gist",
    )
    private val blockedNames = setOf("about", "settings", "notifications", "stars", "repositories")

    fun parseGitHubRepo(rawUrl: String?): Pair<String, String>? {
        val url = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val m = regex.find(url) ?: return null
        val owner = m.groupValues[1]
        val name = m.groupValues[2].removeSuffix(".git")
        if (owner in blockedOwners || name in blockedNames) return null
        return if (name.isNotBlank()) owner to name else null
    }
}

/** Reddit JSON top-listing parser. Maps to a flat list of posts that mention a GitHub link. */
private object RedditJsonParser {
    @Serializable private data class Listing(val data: ListingData = ListingData())
    @Serializable private data class ListingData(val children: List<Child> = emptyList())
    @Serializable private data class Child(val data: Post = Post())
    @Serializable private data class Post(
        val title: String = "",
        val url: String = "",
        val score: Int = 0,
        val subreddit: String = "",
        val selftext: String = "",
        @kotlinx.serialization.SerialName("selftext_html") val selftextHtml: String? = null,
        @kotlinx.serialization.SerialName("permalink") val permalink: String = "",
    )

    data class ParsedPost(
        val title: String,
        val url: String,
        val score: Int,
        val subreddit: String,
        val permalink: String,
    )

    private val githubLink = Regex("https?://(?:www\\.)?github\\.com/[A-Za-z0-9_\\-+.]+/[A-Za-z0-9_.\\-]+")

    fun parseTopPosts(body: String): List<ParsedPost> {
        val listing = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<Listing>(body) }
            .getOrNull() ?: return emptyList()
        return listing.data.children.mapNotNull { child ->
            val p = child.data
            val resolved = HnLinkParser.parseGitHubRepo(p.url)?.let { p.url }
                ?: p.selftext.takeIf { it.isNotBlank() }?.let { githubLink.find(it)?.value }
                ?: p.selftextHtml?.let { githubLink.find(it)?.value }
            if (resolved == null) return@mapNotNull null
            val fullPermalink = if (p.permalink.isBlank()) resolved else "https://www.reddit.com${p.permalink}"
            ParsedPost(p.title.ifBlank { resolved }, resolved, p.score, p.subreddit, fullPermalink)
        }
    }
}
