package com.pockethub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub reactions summary object returned on issues, PRs and comments.
 *
 * Example JSON:
 * ```json
 * {
 *   "url": "https://api.github.com/repos/.../reactions",
 *   "total_count": 5,
 *   "+1": 2,
 *   "-1": 0,
 *   "laugh": 0,
 *   "confused": 0,
 *   "heart": 1,
 *   "hooray": 1,
 *   "eyes": 0,
 *   "rocket": 0
 * }
 * ```
 */
@Serializable
data class Reactions(
    val url: String? = null,
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("+1") val plusOne: Int = 0,
    @SerialName("-1") val minusOne: Int = 0,
    val laugh: Int = 0,
    val confused: Int = 0,
    val heart: Int = 0,
    val hooray: Int = 0,
    val eyes: Int = 0,
    val rocket: Int = 0,
)
