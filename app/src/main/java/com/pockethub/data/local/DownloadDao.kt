package com.pockethub.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun allFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN (:states) ORDER BY createdAt ASC")
    fun flowByStates(states: List<String>): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :state ORDER BY updatedAt DESC")
    fun flowByState(state: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE url = :url")
    fun byUrl(url: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("UPDATE downloads SET status = :status, errorMsg = :errorMsg, updatedAt = :now WHERE url = :url")
    suspend fun setStatusWithError(url: String, status: String, errorMsg: String, now: Long)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, progressPct = :progressPct, updatedAt = :now WHERE url = :url")
    suspend fun setStatusWithProgress(url: String, status: String, downloadedBytes: Long, progressPct: Int, now: Long)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, progressPct = :progressPct, updatedAt = :now WHERE url = :url")
    suspend fun setStatusWithSize(url: String, status: String, downloadedBytes: Long, progressPct: Int, now: Long)
}
