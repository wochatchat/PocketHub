package com.pockethub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub notification item. Notifications are grouped by repository on the client side
 * (the API already returns repo info, but grouping happens locally for display).
 */
@Serializable
data class GitHubNotification(
    val id: String,
    @SerialName("repository") val repository: NotificationRepo? = null,
    @SerialName("subject") val subject: Subject,
    val reason: String = "", // "assign", "review_requested", "ci_activity", ...
    val unread: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_read_at") val lastReadAt: String? = null,
    val url: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
) {
    @Serializable
    data class Subject(
        val title: String = "",
        val url: String? = null,
        @SerialName("latest_comment_url") val latestCommentUrl: String? = null,
        @SerialName("type") val type: String = "Issue", // Issue, PullRequest, Release, Discussion, ...
    )

    @Serializable
    data class NotificationRepo(
        val id: Long? = null,
        @SerialName("full_name") val fullName: String = "",
        @SerialName("html_url") val htmlUrl: String? = null,
        val owner: OwnerRef? = null,
        val name: String? = null,
    )

    @Serializable
    data class OwnerRef(
        val login: String = "",
        @SerialName("avatar_url") val avatarUrl: String? = null,
    )
}

/**
 * Reason keys GitHub returns on a notification.
 *
 * See https://docs.github.com/en/rest/activity/notifications#notification-reasons
 * for the canonical list. The raw `reason` string on [GitHubNotification] is one of these.
 */
enum class NotificationReason(val apiValue: String) {
    ASSIGN("assign"),
    AUTHOR("author"),
    COMMENT("comment"),
    CI_ACTIVITY("ci_activity"),
    MENTION("mention"),
    REVIEW_REQUESTED("review_requested"),
    STATE_CHANGE("state_change"),
    TEAM_MENTION("team_mention"),
    MANUAL("manual"),
    SUBSCRIBED("subscribed"),
    UNKNOWN("");

    companion object {
        fun fromApi(value: String?): NotificationReason =
            entries.firstOrNull { it.apiValue == value } ?: UNKNOWN
    }
}
