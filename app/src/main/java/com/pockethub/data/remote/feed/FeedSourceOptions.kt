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
 * The string ids intentionally match enum names so old installs reading "GITHUB_SEARCH"
 * still resolve correctly even if we later rename the display label.
 */
@Serializable
enum class FeedSourceOption(
    val id: String,
    val defaultBaseUrl: String,
    val urlModifiable: Boolean = false,
) {
    // Trending tab
    GITHUB_SEARCH(
        id = "GITHUB_SEARCH",
        defaultBaseUrl = "https://api.github.com/",
    ),
    GITHUB_TRENDING_API(
        id = "GITHUB_TRENDING_API",
        // No reliable public community fork is alive long-term. Defaulting to
        // empty forces the user to fill a self-hosted URL in Settings; the
        // service then refuses to fetch on this source until that's set.
        defaultBaseUrl = "",
        urlModifiable = true,
    ),

    // Featured tab
    OSS_INSIGHT(
        id = "OSS_INSIGHT",
        defaultBaseUrl = "https://api.ossinsight.io/v1/",
        urlModifiable = true,
    ),
    HACKER_NEWS_SHOWHN(
        id = "HACKER_NEWS_SHOWHN",
        defaultBaseUrl = "https://hacker-news.firebaseio.com/v0/",
        urlModifiable = false,
    ),
    REDDIT_TOP(
        id = "REDDIT_TOP",
        // Reddit's .json endpoints are reachable in many networks but tend to
        // require a real browser UA + IP allow-listing in some data centres.
        // Defaulting empty so the user must opt in via Settings; if Reddit is
        // blocked, the page stays empty with a "not configured" hint instead of
        // trying forever and looking broken.
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
            FeedTab.TRENDING  -> listOf(GITHUB_SEARCH, GITHUB_TRENDING_API)
            FeedTab.FEATURED  -> listOf(OSS_INSIGHT, HACKER_NEWS_SHOWHN, REDDIT_TOP)
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
)
