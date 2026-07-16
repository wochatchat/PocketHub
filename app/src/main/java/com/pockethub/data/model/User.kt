package com.pockethub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal owner / author payload shared across repo / issue / comment endpoints.
 */
@Serializable
data class User(
    val login: String,
    val id: Long? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val type: String? = null, // "User" | "Organization"
    val name: String? = null,
    val bio: String? = null,
    val company: String? = null,
    val blog: String? = null,
    val location: String? = null,
    val email: String? = null,
    @SerialName("public_repos") val publicRepos: Int? = null,
    val followers: Int? = null,
    val following: Int? = null,
)
