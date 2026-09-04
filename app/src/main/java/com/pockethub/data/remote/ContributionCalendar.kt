package com.pockethub.data.remote

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One day of the GitHub contributions heatmap. */
data class ContributionDay(
    val date: String,
    val count: Int,
)

/**
 * A user's contributions calendar (last 12 months) plus the per-type totals
 * from contributionsCollection — everything the profile data card shows.
 */
data class ContributionCalendar(
    val weeks: List<List<ContributionDay>>,
    val total: Int,
    val commits: Int,
    val pullRequests: Int,
    val issues: Int,
    val reviews: Int,
)

// ── Contributions calendar (GraphQL v4) ─────────────────────────────

/**
 * Fetch the contribution calendar for [login]. REST has no equivalent for
 * this data — it is the same source GitHub's own profile heatmap uses.
 * Week lists are ordered oldest → newest; each inner list has 7 entries.
 */
suspend fun GitHubApi.getContributionCalendar(login: String): ContributionCalendar {
    val resp = contributionCalendar(
        GitHubApi.GraphQLRequest(
            query = """
                query(${'$'}login: String!) {
                  user(login: ${'$'}login) {
                    contributionsCollection {
                      totalCommitContributions
                      totalPullRequestContributions
                      totalIssueContributions
                      totalPullRequestReviewContributions
                      contributionCalendar {
                        totalContributions
                        weeks { contributionDays { date contributionCount } }
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("login" to JsonPrimitive(login)),
        ),
    )
    val collection = resp.data?.get("user")
        ?.let { it as? kotlinx.serialization.json.JsonObject }
        ?.get("contributionsCollection")
        ?.let { it as? kotlinx.serialization.json.JsonObject }
        ?: throw IllegalStateException("No contributionsCollection for $login")

    val calendar = collection["contributionCalendar"]?.jsonObject
    val weeks = calendar?.get("weeks")?.jsonArray
        ?.map { w ->
            w.jsonObject["contributionDays"]?.jsonArray
                ?.map { d ->
                    val o = d.jsonObject
                    ContributionDay(
                        date = o["date"]?.jsonPrimitive?.content.orEmpty(),
                        count = o["contributionCount"]?.jsonPrimitive?.int ?: 0,
                    )
                }
                .orEmpty()
        }
        .orEmpty()
        .filter { it.isNotEmpty() }

    fun total(field: String): Int =
        collection[field]?.jsonPrimitive?.int ?: 0

    return ContributionCalendar(
        weeks = weeks,
        total = calendar?.get("totalContributions")?.jsonPrimitive?.int
            ?: weeks.sumOf { week -> week.sumOf { it.count } },
        commits = total("totalCommitContributions"),
        pullRequests = total("totalPullRequestContributions"),
        issues = total("totalIssueContributions"),
        reviews = total("totalPullRequestReviewContributions"),
    )
}
