package com.pockethub.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Lightweight key-value cache for API responses. Each entry stores a JSON blob keyed by
 * the endpoint path + query params, with a TTL for automatic expiration.
 *
 * This avoids creating per-model Room entities while still providing cache-first reads.
 */
@Dao
interface CacheDao {

    @Query("SELECT json FROM cached_items WHERE `key` = :key AND cachedAt > :maxAge")
    suspend fun getIfFresh(key: String, maxAge: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: CachedItemEntity)

    @Query("DELETE FROM cached_items WHERE cachedAt < :maxAge")
    suspend fun evictOlderThan(maxAge: Long): Int

    @Query("DELETE FROM cached_items")
    suspend fun clearAll(): Int

    @Query("SELECT COUNT(*) FROM cached_items")
    suspend fun count(): Int
}
