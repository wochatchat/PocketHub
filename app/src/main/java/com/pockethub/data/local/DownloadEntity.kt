package com.pockethub.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persistent download record. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val url: String = "",
    val fileName: String = "",
    val contentType: String = "application/octet-stream",
    val repoKey: String = "",
    val releaseTag: String = "",
    val sizeBytes: Long = 0L,
    val localPath: String = "",
    val status: String = "QUEUED",
    val downloadedBytes: Long = 0L,
    val progressPct: Int = 0,
    val errorMsg: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
