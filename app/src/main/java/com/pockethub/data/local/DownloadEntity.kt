package com.pockethub.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persistent download record. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    /** Stable id = url hashcode; same asset enqueued twice → same row. */
    @PrimaryKey val url: String,
    val fileName: String,
    val contentType: String,
    val repoKey: String,            // "owner/repo", for grouping UI
    val releaseTag: String = "",    // release/version label
    val sizeBytes: Long,            // total size (bytes) from GitHub asset
    /** localPath = absolute path under app-private external Downloads dir. */
    val localPath: String,
    /** Status: QUEUED | IN_PROGRESS | DONE | FAILED. */
    val status: String,
    /** Downloaded bytes; within (0, sizeBytes). */
    val downloadedBytes: Long = 0,
    val progressPct: Int = 0,
    /** Error message if status==FAILED; empty otherwise. */
    val errorMsg: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
