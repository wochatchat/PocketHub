package com.pockethub.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Downloads persistence — designed to mirror CacheDao's minimal form so Room
 * KSP can process it reliably. All updates go through [upsert] (full entity
 * REPLACE), no separate UPDATE statements.
 */
@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun allFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN (:states) ORDER BY createdAt ASC")
    fun flowByStates(states: List<String>): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :state ORDER BY updatedAt DESC")
    fun flowByState(state: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}
