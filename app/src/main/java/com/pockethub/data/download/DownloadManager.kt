package com.pockethub.data.download

import android.content.Context
import android.os.Environment
import com.pockethub.data.local.DownloadDao
import com.pockethub.data.local.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val client: OkHttpClient,
    private val dao: DownloadDao,
) {

    data class EnqueueRequest(
        val url: String,
        val fileName: String,
        val contentType: String,
        val sizeBytes: Long,
        val repoKey: String,
        val releaseTag: String = "",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    private var currentJob: Job? = null

    fun allFlow(): Flow<List<DownloadEntity>> = dao.allFlow()
    fun activeFlow(): Flow<List<DownloadEntity>> =
        dao.flowByStates(listOf("QUEUED", "IN_PROGRESS", "FAILED"))
    fun doneFlow(): Flow<List<DownloadEntity>> = dao.flowByState("DONE")

    suspend fun get(url: String): DownloadEntity? = dao.byUrl(url)

    suspend fun enqueue(req: EnqueueRequest) {
        val dir = File(workRoot(), req.repoKey.ifBlank { "common" })
        val destFile = File(dir, req.fileName)
        val existing = dao.byUrl(req.url)
        if (existing?.status == "IN_PROGRESS" || existing?.status == "QUEUED") return
        if (existing?.status == "DONE" && destFile.exists()) return

        val now = System.currentTimeMillis()
        dao.upsert(
            DownloadEntity(
                url = req.url,
                fileName = req.fileName,
                contentType = req.contentType,
                repoKey = req.repoKey,
                releaseTag = req.releaseTag,
                sizeBytes = req.sizeBytes,
                localPath = destFile.absolutePath,
                status = "QUEUED",
                createdAt = now,
                updatedAt = now,
            )
        )
        _events.tryEmit(DownloadEvent.Started(req.url))
        runNextIfIdle()
    }

    suspend fun retry(url: String) {
        val existing = dao.byUrl(url) ?: return
        destFileOrNull(existing)?.delete()
        dao.upsert(
            existing.copy(
                status = "QUEUED",
                downloadedBytes = 0,
                progressPct = 0,
                errorMsg = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
        _events.tryEmit(DownloadEvent.Started(url))
        runNextIfIdle()
    }

    suspend fun cancel(url: String) {
        val existing = dao.byUrl(url) ?: return
        if (existing.status == "IN_PROGRESS" && currentJob != null) currentJob?.cancel()
        destFileOrNull(existing)?.delete()
        dao.deleteByUrl(url)
        _events.tryEmit(DownloadEvent.Removed(url))
    }

    suspend fun removeCompleted(url: String) {
        val existing = dao.byUrl(url) ?: return
        destFileOrNull(existing)?.delete()
        dao.deleteByUrl(url)
        _events.tryEmit(DownloadEvent.Removed(url))
    }

    private fun destFileOrNull(entity: DownloadEntity): File? =
        entity.localPath.takeIf { it.isNotBlank() }?.let { File(it) }

    private fun workRoot(): File {
        val root = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir,
            "PocketHub",
        )
        if (!root.exists()) root.mkdirs()
        return root
    }

    private fun runNextIfIdle() {
        if (currentJob?.isActive == true) return
        scope.launch {
            val queued = dao.flowByStates(listOf("QUEUED", "IN_PROGRESS", "FAILED")).first()
                .firstOrNull { it.status == "QUEUED" } ?: return@launch
            executeDownload(queued)
            runNextIfIdle()
        }
    }

    private suspend fun executeDownload(entity: DownloadEntity) {
        val targetFile = File(entity.localPath)
        targetFile.parentFile?.mkdirs()
        val currentUrl = entity.url
        val destFile = File(targetFile.parentFile, "${targetFile.name}.part")
        val job = scope.launch {
            try {
                val request = Request.Builder().url(currentUrl).build()
                val dlClient = client.newBuilder().followRedirects(true).build()
                val response = withContext(Dispatchers.IO) { dlClient.newCall(request).execute() }
                if (!response.isSuccessful) {
                    dao.upsert(
                        entity.copy(
                            status = "FAILED",
                            errorMsg = "HTTP ${response.code}",
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                    _events.tryEmit(DownloadEvent.Failed(currentUrl, "HTTP ${response.code}"))
                    return@launch
                }

                val reportedTotal: Long = response.body?.contentLength() ?: -1
                val totalBytes = if (reportedTotal > 0) reportedTotal else entity.sizeBytes
                val body = response.body ?: throw IOException("No body in response")

                dao.upsert(entity.copy(status = "IN_PROGRESS", sizeBytes = totalBytes, updatedAt = System.currentTimeMillis()))

                withContext(Dispatchers.IO) {
                    body.byteStream().use { input ->
                        destFile.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var totalRead = 0L
                            var lastReport = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                totalRead += read
                                if (totalRead - lastReport >= 100 * 1024) {
                                    val pct = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else 0
                                    dao.upsert(
                                        entity.copy(
                                            status = "IN_PROGRESS",
                                            downloadedBytes = totalRead,
                                            progressPct = pct.coerceIn(0, 100),
                                            updatedAt = System.currentTimeMillis(),
                                        )
                                    )
                                    lastReport = totalRead
                                }
                            }
                        }
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!destFile.renameTo(targetFile)) {
                    destFile.copyTo(targetFile, overwrite = true)
                    destFile.delete()
                }

                dao.upsert(
                    entity.copy(
                        status = "DONE",
                        downloadedBytes = totalBytes,
                        progressPct = 100,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                _events.tryEmit(DownloadEvent.Done(currentUrl, targetFile.absolutePath))
            } catch (e: kotlinx.coroutines.CancellationException) {
                destFile.delete()
                dao.upsert(entity.copy(status = "FAILED", errorMsg = "Cancelled", updatedAt = System.currentTimeMillis()))
                throw e
            } catch (e: Throwable) {
                destFile.delete()
                val msg = e.localizedMessage ?: e.javaClass.simpleName
                dao.upsert(entity.copy(status = "FAILED", errorMsg = msg, updatedAt = System.currentTimeMillis()))
                _events.tryEmit(DownloadEvent.Failed(currentUrl, msg))
            }
        }
        currentJob = job
        job.join()
        currentJob = null
    }
}

sealed class DownloadEvent {
    data class Started(val url: String) : DownloadEvent()
    data class Done(val url: String, val path: String) : DownloadEvent()
    data class Failed(val url: String, val reason: String) : DownloadEvent()
    data class Removed(val url: String) : DownloadEvent()
}
