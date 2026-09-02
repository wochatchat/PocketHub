package com.pockethub.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted GitHub account. Multiple accounts can coexist; one is marked active.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val login: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val token: String,             // PAT or OAuth access token
    val tokenType: String = "bearer", // "bearer" | "oauth"
    val isActive: Boolean = false, // the "current" account
    val scopes: String = "",       // space-separated OAuth scopes
    val createdAt: Long = System.currentTimeMillis(),
    // ── OAuth session renewal (v4 schema) ────────────────
    /** OAuth refresh token. Empty for PAT sessions — they never refresh. */
    val refreshToken: String = "",
    /** Access-token expiry in epoch millis. 0 = unknown/never expires (PAT). */
    val tokenExpiresAt: Long = 0,
)
