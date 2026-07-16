package com.pockethub.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Generic API response cache entry. Stores the raw JSON response as a string, keyed by
 * a deterministic key derived from the endpoint + query params.
 *
 * TTL is enforced in queries via [CacheDao.getIfFresh] which filters by [maxAge].
 */
@Entity(tableName = "cached_items")
data class CachedItemEntity(
    @PrimaryKey val key: String,
    val json: String,
    val cachedAt: Long = System.currentTimeMillis(),
)
