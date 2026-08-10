package com.pockethub.data.remote.feed

import kotlinx.serialization.Serializable

/**
 * Which feed a [FeedSourceOption] is configured for on the Explore page.
 *
 * Mirrors [com.pockethub.ui.explore.ExploreSection] but kept in data-layer to
 * avoid Compose/UI coupling in persistent settings.
 */
enum class FeedTab { TRENDING, FEATURED, FOLLOWING }

/**
 * All built-in information sources for the Explore tabs.
 *
 * Each option defines a stable [id] persisted in DataStore and a [defaultBaseUrl]
 * used to seed the service. For sources that allow a user-overridable base URL
 * (HTML/JSON endpoints), [urlModifiable] is true; for sources that always call a
 * fixed GitHub endpoint (e.g. GitHub Search/Events), it is false.
 *
 * [supportsTrendingFilters] declares whether the Explore home screen should
 * render the trending language + time-range filter chips when this source is
 * selected for the Trending tab. Only sources whose response is actually
 * shaped by language/time-window parameters set this true; the chips are
 * hidden for everything else so the home screen never misleads a user with
 * controls that don't affect the feed.
 *
 * The string ids intentionally match enum names so old installs reading "GITHUB_SEARCH"
 * still resolve correctly even if we later rename the display label.
 */
@Serializable
enum class FeedSourceOption(
    val id: String,
    val defaultBaseUrl: String,
    val urlModifiable: Boolean = false,
    val supportsTrendingFilters: Boolean = false,
) {
    // Official GitHub source. It remains the default and can be tuned from
    // Settings → Explore information sources (language, time window and stars).
    GITHUB_SEARCH(
        id = "GITHUB_SEARCH",
        defaultBaseUrl = "https://api.github.com/",
        urlModifiable = false,
        supportsTrendingFilters = true,
    ),
    GITHUB_TRENDING_API(
        id = "GITHUB_TRENDING_API",
        // No reliable public community fork is alive long-term. Defaulting to
        // empty forces the user to fill a self-hosted URL in Settings; the
        // service then refuses to fetch on this source until that's set.
        defaultBaseUrl = "",
        urlModifiable = true,
        supportsTrendingFilters = true,
    ),

    // Public discovery sources. OSS Insight and Hacker News are intentionally
    // retained as built-in defaults alongside the official GitHub source.
    OSS_INSIGHT(
        id = "OSS_INSIGHT",
        defaultBaseUrl = "https://api.ossinsight.io/v1/",
        urlModifiable = true,
        supportsTrendingFilters = true,
    ),
    HACKER_NEWS_SHOWHN(
        id = "HACKER_NEWS_SHOWHN",
        defaultBaseUrl = "https://hacker-news.firebaseio.com/v0/",
        urlModifiable = false,
    ),
    NPM_REGISTRY(
        id = "NPM_REGISTRY",
        defaultBaseUrl = "https://registry.npmjs.org/",
        urlModifiable = true,
    ),
    LOBSTERS(
        id = "LOBSTERS",
        defaultBaseUrl = "https://lobste.rs/",
        urlModifiable = true,
    ),
    REDDIT_TOP(
        id = "REDDIT_TOP",
        // Reddit's .json endpoints can require a real browser UA or an
        // allow-listed IP. Keep it opt-in, but allow a public mirror URL.
        defaultBaseUrl = "",
        urlModifiable = true,
    ),

    // Following tab — GitHub events are the only practical public source.
    GITHUB_EVENTS(
        id = "GITHUB_EVENTS",
        defaultBaseUrl = "https://api.github.com/",
        urlModifiable = false,
    );

    companion object {
        fun fromId(value: String?): FeedSourceOption =
            values().firstOrNull { it.id == value }
                ?: defaultsFor(FeedTab.TRENDING)

        /** Default source chosen for a given tab. */
        fun defaultsFor(tab: FeedTab): FeedSourceOption = when (tab) {
            FeedTab.TRENDING  -> GITHUB_SEARCH
            FeedTab.FEATURED  -> OSS_INSIGHT
            FeedTab.FOLLOWING -> GITHUB_EVENTS
        }

        /** All options selectable for the given tab (used by the settings screen). */
        fun optionsFor(tab: FeedTab): List<FeedSourceOption> = when (tab) {
            // Keep the official GitHub endpoint first so it is easy to find and
            // remains the obvious zero-configuration choice.
            FeedTab.TRENDING  -> listOf(GITHUB_SEARCH, OSS_INSIGHT, GITHUB_TRENDING_API)
            FeedTab.FEATURED  -> listOf(OSS_INSIGHT, HACKER_NEWS_SHOWHN, NPM_REGISTRY, LOBSTERS, REDDIT_TOP, GITHUB_SEARCH)
            FeedTab.FOLLOWING -> listOf(GITHUB_EVENTS)
        }
    }
}

/**
 * Persisted user configuration for a single tab.
 *
 * [sourceId] is one of [FeedSourceOption.id]; [customBaseUrl] overrides the
 * source's [FeedSourceOption.defaultBaseUrl] when non-blank and [FeedSourceOption.urlModifiable]
 * is true. Trending also carries the active language + range filter chips.
 */
@Serializable
data class FeedSourceConfig(
    val sourceId: String,
    val customBaseUrl: String = "",
    val trendingLanguage: String = "All",
    val trendingRange: String = "Daily",
    /** GitHub Search sort: stars, forks or updated. */
    val githubSort: String = "stars",
    /** Inclusive star range used by the official GitHub source. */
    val githubMinStars: Int = 50,
    val githubMaxStars: Int = 20_000,
    /** Archived repositories are hidden by default to keep discovery useful. */
    val githubIncludeArchived: Boolean = false,
)
