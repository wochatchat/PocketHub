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
            FeedSourceOption.OSS_INSIGHT -> fetchOssInsight(cfg, forceFresh)
            // The official GitHub REST search path is the stable zero-config
            // fallback for old or malformed saved source ids.
            else -> searchGitHub(cfg, forceFresh)
        }
    }

    // ── Featured ────────────────────────────────────────────────────────────

    suspend fun loadFeatured(forceFresh: Boolean): List<DiscoverItem> {
        val cfg = repo.configFlow(FeedTab.FEATURED).first()
        return when (FeedSourceOption.fromId(cfg.sourceId)) {
            FeedSourceOption.GITHUB_SEARCH -> searchGitHub(cfg, forceFresh)
            FeedSourceOption.OSS_INSIGHT -> fetchOssInsight(cfg, forceFresh)
            FeedSourceOption.HACKER_NEWS_SHOWHN -> fetchHackerNewsShowHN(forceFresh)
            FeedSourceOption.NPM_REGISTRY -> fetchNpmRegistry(cfg, forceFresh)
            FeedSourceOption.LOBSTERS -> fetchLobsters(cfg, forceFresh)
            FeedSourceOption.REDDIT_TOP ->
                if (!isConfigured(FeedSourceOption.REDDIT_TOP, cfg.customBaseUrl)) emptyList()
                else fetchRedditTop(cfg, forceFresh)
            // Following-only sources are safely mapped to the official feed if
            // an old install somehow persisted them under Featured.
            FeedSourceOption.GITHUB_EVENTS,
            FeedSourceOption.GITHUB_TRENDING_API -> searchGitHub(cfg, forceFresh)
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
        val minStars = cfg.githubMinStars.coerceAtLeast(0)
        val maxStars = cfg.githubMaxStars.takeIf { it > minStars }
        val starPart = if (maxStars != null) "stars:$minStars..$maxStars" else "stars:>=$minStars"
        val archivedPart = if (cfg.githubIncludeArchived) "" else " archived:false"
        // The official GitHub Search API is deliberately configurable. These
        // qualifiers are all supported by GitHub's public REST endpoint, so the
        // settings page never needs a third-party proxy to tune the feed.
        val q = "$starPart$langPart created:>$created$archivedPart"
        val sort = when (cfg.githubSort) {
            "forks", "updated" -> cfg.githubSort
            else -> "stars"
        }
        val perPage = 30
        val result = if (forceFresh) {
            cache.searchTrendingFresh(query = q, sort = sort, perPage = perPage)
        } else {
            cache.searchTrending(query = q, sort = sort, perPage = perPage)
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
        // Map the Trending-range chip to a day count too — newer search-proxy
        // envelopes (eg self-hosted search wrappers) speak `days=7|30|1` rather
        // than either `since` or `language`. We let it stay empty for the
        // "All languages" chip so the proxy reverts to its own default mix.
        val lang = if (cfg.trendingLanguage == "All") "" else cfg.trendingLanguage.lowercase()
        val langParam = java.net.URLEncoder.encode(lang, "UTF-8")
        val days = when (since) {
            "weekly" -> 7
            "monthly" -> 30
            else -> 1
        }
        val periodLabel = when (since) {
            "weekly"  -> "week"
            "monthly" -> "month"
            else      -> "day"
        }
        // Emit a union of every filter syntax proxies of this family use, so the
        // same URL works against:
        //   legacy forks        (?since=&language=)  — they ignore lang/days
        //   search-proxy envelopes (?lang=&days=)   — they ignore since/language
        // Net effect: the Trending-tab language + time-range chips actually
        // shape the result across both proxy families.
        fun appendFilters(url: String): String = buildString {
            append(url)
            // Legacy forks speak ?since=&language=; the search-proxy format
            // speaks ?lang= and ?days=. Emit both pairs so both families honour
            // the chips without us sniffing the URL ahead of time. When the
            // language chip is "All" we drop both language params so neither
            // treats an empty filter as "filter by empty string" and zeroes the
            // list.
            append("?since=$since&days=$days")
            if (langParam.isNotEmpty()) {
                append("&language=$langParam&lang=$langParam")
            }
        }

        // Each candidate URL is fetched in priority order and parsed against
        // every supported shape. URLs come first because the *field* shape
        // cannot be known ahead of time; we just see which parse wins and
        // return the first non-empty result so the user's self-host URL only
        // needs to match one of them.
        val urls = listOf(
            appendFilters("${base}repositories"),
            appendFilters(base.removeSuffix("/")),
        )
        for (url in urls) {
            val body = runCatching { requestText(url, forceFresh) }.getOrNull() ?: continue
            if (body.isBlank()) continue

            // Shape A — legacy community forks: top-level JSON array of
            // { author, name, url, currentPeriodStars, ... }.
            val legacy: List<DiscoverItem> = runCatching {
                json.decodeFromString<List<TrendingApiResponseItem>>(body)
                    .map { it.toDiscoverItem(periodLabel) }
            }.getOrDefault(emptyList())
            if (legacy.isNotEmpty()) return legacy

            // Shape B — search-proxy envelope used by several self-hosted
            // services: { items: [{ name:"owner/repo", url, description,
            // language, stars, forks, topics:[...], owner:{ login, avatar } }] }.
            val envelope = parseSearchProxyEnvelope(body, periodLabel)
            if (envelope.isNotEmpty()) return envelope
        }
        return emptyList()
    }

    /**
     * Parses a "search-proxy envelope": a top-level JSON object that wraps a
     * `items` array where every item is a simplified repo (full_name as `name`,
     * `owner.login`, `owner.avatar`, `topics`, etc.). This shape is produced
     * by various self-hosted GitHub-trending proxies that wrap GitHub's Search
     * Repositories response — we only consume the fields we surface, ignore
     * the rest. Designed to be tolerant of missing fields; any parsing failure
     * returns an empty list so the caller can fall through.
     */
    private fun parseSearchProxyEnvelope(body: String, periodLabel: String): List<DiscoverItem> {
        val env = runCatching {
            json.parseToJsonElement(body) as? JsonObject
        }.getOrNull() ?: return emptyList()
        // Accept either `items` (a search-proxy envelope) or a bare search
        // response `items` key. A few proxies use `data`; we look for the
        // first array-typed field we recognise.
        val itemsEl = env["items"] as? JsonArray
            ?: env["data"] as? JsonArray
            ?: env["repositories"] as? JsonArray
            ?: env["repos"] as? JsonArray
            ?: return emptyList()
        return itemsEl.mapNotNull { rowElement ->
            val row = rowElement as? JsonObject ?: return@mapNotNull null
            // `name` here is the full "owner/repo" slug; split it. Fall back to
            // `full_name` for proxies that preserve GitHub's field name, and
            // to separate `owner`/`repo` keys when those exist.
            val fullName = row.rowStr("name").ifBlank { row.rowStr("full_name") }
            if (fullName.isBlank()) return@mapNotNull null
            val parts = fullName.split("/", limit = 2).map { it.trim() }
            val owner = parts.getOrNull(0).orEmpty()
            val repo = parts.getOrNull(1).orEmpty().ifBlank { row.rowStr("repo") }
            if (owner.isBlank() || repo.isBlank()) return@mapNotNull null

            // Owner may be a string or a nested object; prefer the nested
            // `owner.login` / `owner.avatar` when present, else fall back to
            // the slug's owner segment and an avatar URL synthesised from it.
            val ownerObj = row["owner"] as? JsonObject
            val ownerLogin = ownerObj?.let { it.rowStr("login") }?.ifBlank { null } ?: owner
            val avatar = ownerObj?.let { it.rowStr("avatar").ifBlank { null } }
                ?: ownerObj?.let { it.rowStr("avatar_url").ifBlank { null } }
                ?: "https://avatars.githubusercontent.com/$owner"

            val stars = row.rowStr("stars").extractInt()
            val forks = row.rowStr("forks").extractInt()
            val htmlUrl = row.rowStr("url").ifBlank { row.rowStr("html_url") }
                .ifBlank { "https://github.com/$owner/$repo" }
            val language = row.rowStr("language").ifBlank { row.rowStr("primary_language") }
            val description = row.rowStr("description")
            val topicsArr = row["topics"] as? JsonArray
            val topics = topicsArr?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.filter { it.isNotBlank() } ?: emptyList()

            // `currentPeriodStars` is the legacy field name for a star delta;
            // search-proxy envelopes usually omit it. If present we surface it;
            // else we leave starDelta null and the card simply prints the
            // absolute star count, which is still useful.
            val delta = maybeStarsDelta(row, periodLabel)
            DiscoverItem(
                id = DiscoverItem.stableId(ownerLogin, repo),
                source = FeedSourceOption.GITHUB_TRENDING_API,
                owner = ownerLogin,
                repo = repo,
                htmlUrl = htmlUrl,
                description = description.takeIf { it.isNotBlank() },
                language = language.takeIf { it.isNotBlank() },
                stars = stars,
                forks = forks,
                topics = topics,
                ownerAvatarUrl = avatar,
                starDelta = delta,
            )
        }
    }

    private fun maybeStarsDelta(row: JsonObject, periodLabel: String): StarDelta? {
        // Several variants we may see: `currentPeriodStars` (legacy), `stars_delta`,
        // or `period_stars`. Take the first one that parses to a positive int.
        for (key in listOf("currentPeriodStars", "stars_delta", "period_stars", "delta")) {
            val v = row.rowStr(key).extractInt()
            if (v > 0) return StarDelta(v, periodLabel)
        }
        return null
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

    // ── npm Registry — public package search, resolved to GitHub repos ───────

    @Serializable
    private data class NpmSearchResponse(
        val objects: List<NpmSearchObject> = emptyList(),
    )

    @Serializable
    private data class NpmSearchObject(
        val downloads: NpmDownloads? = null,
        val `package`: NpmPackage = NpmPackage(),
    )

    @Serializable
    private data class NpmDownloads(val monthly: Int = 0)

    @Serializable
    private data class NpmPackage(
        val name: String = "",
        val description: String? = null,
        val links: NpmLinks? = null,
        val keywords: List<String> = emptyList(),
    )

    @Serializable
    private data class NpmLinks(val repository: String? = null, val homepage: String? = null)

    private suspend fun fetchNpmRegistry(cfg: FeedSourceConfig, forceFresh: Boolean): List<DiscoverItem> {
        val source = FeedSourceOption.NPM_REGISTRY
        val base = baseUrlFor(source, cfg.customBaseUrl)
        if (base.isEmpty()) return emptyList()
        val url = "${base}-/v1/search?text=keywords%3Aopensource&size=30"
        val body = requestText(url, forceFresh) ?: return emptyList()
        val response = runCatching { json.decodeFromString<NpmSearchResponse>(body) }.getOrNull()
            ?: return emptyList()
        return response.objects.mapNotNull { row ->
            val github = HnLinkParser.parseGitHubRepo(row.`package`.links?.repository)
                ?: HnLinkParser.parseGitHubRepo(row.`package`.links?.homepage)
                ?: return@mapNotNull null
            val (owner, repoName) = github
            DiscoverItem(
                id = DiscoverItem.stableId(owner, repoName),
                source = source,
                owner = owner,
                repo = repoName,
                htmlUrl = "https://github.com/$owner/$repoName",
                description = row.`package`.description,
                topics = row.`package`.keywords.take(4),
                ownerAvatarUrl = "https://avatars.githubusercontent.com/$owner",
                communitySignal = CommunitySignal(
                    platform = CommunitySignal.Platform.NPM,
                    postTitle = row.`package`.name,
                    postUrl = "https://www.npmjs.com/package/${row.`package`.name}",
                    score = row.downloads?.monthly ?: 0,
                ),
            )
        }.distinctBy { it.id }
    }

    // ── Lobsters — public JSON feed, filtered to GitHub projects ────────────

    @Serializable
    private data class LobstersStory(
        val title: String = "",
        val url: String? = null,
        val score: Int = 0,
        @kotlinx.serialization.SerialName("short_id_url") val shortIdUrl: String = "",
        val submitter_user: String? = null,
        val tags: List<String> = emptyList(),
    )

    private suspend fun fetchLobsters(cfg: FeedSourceConfig, forceFresh: Boolean): List<DiscoverItem> {
        val source = FeedSourceOption.LOBSTERS
        val base = baseUrlFor(source, cfg.customBaseUrl)
        if (base.isEmpty()) return emptyList()
        val body = requestText("${base}hottest.json", forceFresh) ?: return emptyList()
        val stories = runCatching { json.decodeFromString<List<LobstersStory>>(body) }.getOrNull()
            ?: return emptyList()
        return stories.mapNotNull { story ->
            val (owner, repoName) = HnLinkParser.parseGitHubRepo(story.url) ?: return@mapNotNull null
            DiscoverItem(
                id = DiscoverItem.stableId(owner, repoName),
                source = source,
                owner = owner,
                repo = repoName,
                htmlUrl = "https://github.com/$owner/$repoName",
                description = story.title,
                topics = story.tags.take(4),
                ownerAvatarUrl = "https://avatars.githubusercontent.com/$owner",
                communitySignal = CommunitySignal(
                    platform = CommunitySignal.Platform.LOBSTERS,
                    postTitle = story.title,
                    postUrl = story.shortIdUrl,
                    score = story.score,
                    author = story.submitter_user,
                ),
            )
        }.distinctBy { it.id }
    }

    // ── Reddit JSON: r/programming + r/androiddev + r/MachineLearning weekly top

    private suspend fun fetchRedditTop(cfg: FeedSourceConfig, forceFresh: Boolean): List<DiscoverItem> {
        val source = FeedSourceOption.REDDIT_TOP
        val base = baseUrlFor(source, cfg.customBaseUrl)
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
