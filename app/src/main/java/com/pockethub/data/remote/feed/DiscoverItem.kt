package com.pockethub.data.remote.feed

import kotlinx.serialization.Serializable

/**
 * Unified item surfaced by the Explore feed, regardless of which
 * [FeedSourceOption] produced it.
 *
 * The motivation: GitHub Search gives us a fully-populated
 * [com.pockethub.data.model.Repository] (license, topics, timestamps),
 * OSS Insight gives us a `total_score` momentum number, and Hacker News /
 * Reddit give us almost no repo stats — only a community post pointing at a
 * GitHub repository. Forcing all of those back into the GitHub [Repository]
 * model loses the community signal and forces us to fake fields we genuinely
 * don't have. This DTO holds the union of "what we can show" so the
 * ExploreScreen card can decide what to render.
 */
@Serializable
data class DiscoverItem(
    /** Stable synthetic id for LazyColumn keys — derived from owner/repo. */
    val id: String,
    val source: FeedSourceOption,
    val owner: String,
    val repo: String,
    /** Canonical repo URL (always github.com/owner/repo regardless of source). */
    val htmlUrl: String,
    val description: String? = null,
    val language: String? = null,
    val stars: Int = 0,
    val forks: Int = 0,
    val topics: List<String> = emptyList(),
    /** Owner's avatar so the card can show the avatar without an extra HTTP call. */
    val ownerAvatarUrl: String? = null,
    /**
     * Momentum metric provided by GitHub Trending API and OSS Insight, absent
     * for GitHub Search (which only stores absolute totals). When set, the
     * Explore card labels the item with "↑ X stars this week".
     */
    val starDelta: StarDelta? = null,
    /**
     * The community post that surfaced this repo. Only present for HN / Reddit
     * sources. For pure GitHub-backed sources this is null.
     */
    val communitySignal: CommunitySignal? = null,
) {
    val fullName: String get() = "$owner/$repo"

    companion object {
        /** Stable id derived from owner/repo — used for LazyColumn `key =`. */
        fun stableId(owner: String, repo: String): String = "$owner/$repo"
    }
}

/**
 * Time-windowed star growth. The Explore card prints this as
 * "↑ N stars in the past day/week/month" when [StarDelta.allDelta] is non-zero.
 */
@Serializable
data class StarDelta(
    /** Net new stars over the window. */
    val delta: Int,
    /** Period label, e.g. "day", "week", "month" — only used for UI copy. */
    val periodLabel: String,
)

/**
 * A community post that surfaced this repository (Hacker News / Reddit).
 * Lets the Explore card print a credits line like
 * "On Hacker News · 412 points by tptacek" or "On /r/androiddev · ↑ 240".
 */
@Serializable
data class CommunitySignal(
    val platform: Platform,
    val postTitle: String? = null,
    /** Permalink to the originating post (HN thread URL or Reddit permalink). */
    val postUrl: String,
    val score: Int = 0,
    val author: String? = null,
    val subreddit: String? = null,
) {
    @Serializable
    enum class Platform { HACKER_NEWS, REDDIT }
}
