package com.pockethub.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun allFlow(): Flow<List<DownloadEntity>>

    /** Active downloads (QUEUED | IN_PROGRESS | FAILED) ordered oldest-first. */
    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','IN_PROGRESS','FAILED') ORDER BY createdAt ASC")
    fun activeFlow(): Flow<List<DownloadEntity>>

    /** Completed (DONE) downloads newest-first. */
    @Query("SELECT * FROM downloads WHERE status = 'DONE' ORDER BY updatedAt DESC")
    fun doneFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE url = :url")
    fun byUrl(url: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("UPDATE downloads SET status='FAILED', errorMsg=:msg, updatedAt=:now WHERE url=:url")
    suspend fun markFailed(url: String, msg: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status='IN_PROGRESS', downloadedBytes=:downloadedBytes, progressPct=:pct, updatedAt=:now WHERE url=:url")
    suspend fun reportProgress(url: String, downloadedBytes: Long, pct: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status='DONE', downloadedBytes=:bytes, progressPct=100, updatedAt=:now WHERE url=:url")
    suspend fun markDone(url: String, bytes: Long, now: Long = System.currentTimeMillis())
}
