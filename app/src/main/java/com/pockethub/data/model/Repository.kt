package com.pockethub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub repository model. Covers the fields displayed in the list and detail screen.
 */
@Serializable
data class Repository(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: User,
    val private: Boolean = false,
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val homepage: String? = null,
    @SerialName("default_branch") val defaultBranch: String = "main",

    // Stats
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("watchers_count") val watchers: Int = 0,
    @SerialName("forks_count") val forks: Int = 0,
    @SerialName("open_issues_count") val openIssues: Int = 0,

    // Language
    val language: String? = null,
    @SerialName("language_color") val languageColor: String? = null,

    // Licensing
    val license: License? = null,

    // Topics
    val topics: List<String> = emptyList(),

    // Timestamps
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("pushed_at") val pushedAt: String? = null,

    // Relation flags
    @SerialName("has_wiki") val hasWiki: Boolean = false,
    @SerialName("has_pages") val hasPages: Boolean = false,
    @SerialName("has_downloads") val hasDownloads: Boolean = true,
) {
    @Serializable
    data class License(
        val key: String,
        val name: String,
        val spdxId: String? = null,
    )
}
