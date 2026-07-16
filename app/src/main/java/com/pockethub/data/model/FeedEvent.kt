package com.pockethub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub activity event (received_events endpoint). Used by the Explore feed's
 * "Following activity" section to show what people you follow are doing.
 */
@Serializable
data class FeedEvent(
    val id: String,
    val type: String = "", // PushEvent, WatchEvent, ForkEvent, CreateEvent, ...
    val actor: Actor? = null,
    val repo: EventRepo? = null,
    val payload: Payload? = null,
    val public: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
) {
    @Serializable
    data class Actor(
        val id: Long? = null,
        val login: String = "",
        @SerialName("display_login") val displayLogin: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
    )

    @Serializable
    data class EventRepo(
        val id: Long? = null,
        val name: String = "", // owner/repo full name
        val url: String? = null,
    )

    /**
     * Loose payload — different event types have wildly different shapes.
     * Unknown fields are ignored (ignoreUnknownKeys = true on the JSON parser).
     */
    @Serializable
    data class Payload(
        val action: String? = null,
        val ref: String? = null,
        @SerialName("ref_type") val refType: String? = null,
        @SerialName("master_branch") val masterBranch: String? = null,
        val size: Int = 0,
        val commits: List<Commit> = emptyList(),
        @SerialName("pull_request") val pullRequest: PullRequestRef? = null,
        val forkee: RepoRef? = null,
    ) {
        @Serializable
        data class Commit(
            val sha: String = "",
            val message: String = "",
            val author: User? = null,
            val url: String? = null,
        )

        @Serializable
        data class PullRequestRef(
            val title: String = "",
            val html_url: String? = null,
            val number: Int = 0,
        )

        @Serializable
        data class RepoRef(
            @SerialName("full_name") val fullName: String? = null,
            @SerialName("html_url") val htmlUrl: String? = null,
        )
    }
}
