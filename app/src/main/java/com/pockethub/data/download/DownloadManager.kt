package com.pockethub.data.download

import android.content.Context
import android.os.Environment
import com.pockethub.data.local.DownloadDao
import com.pockethub.data.local.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
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

/**
 * Background-friendly service that downloads GitHub release assets to app-private
 * external storage. Status & progress are persisted in [DownloadDao]; active jobs
 * run on a dedicated supervisor scope so that UI cancellation only affects the
 * specific coroutine, and failed assets are recoverable for re-download.
 *
 * There is no rate-limitting of concurrent downloads — downloads are serialized by
 * dispatching through a single coordinator (one at a time) to keep network usage
 * predictable on a phone.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val client: OkHttpClient,
    private val dao: DownloadDao,
) {

    /** Asset to enqueue. */
    data class EnqueueRequest(
        val url: String,
        val fileName: String,
        val contentType: String,
        val sizeBytes: Long,
        val repoKey: String,
        val releaseTag: String = "",
    )

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /**
     * One-shot event stream the UI listens to. Emits when an asset starts, completes,
     * or fails; lets the UI Auto-switch tabs/scroll to the right item without
     * watching the database directly.
     */
    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    /** Tracks the currently-running download job, if any. */
    private var currentJob: Job? = null

    /** All downloads (active+done), full DB stream. */
    fun allFlow(): Flow<List<DownloadEntity>> = dao.allFlow()
    fun activeFlow(): Flow<List<DownloadEntity>> = dao.flowByStates(listOf("QUEUED", "IN_PROGRESS", "FAILED"))
    fun doneFlow(): Flow<List<DownloadEntity>> = dao.flowByState("DONE")

    suspend fun get(url: String): DownloadEntity? = dao.byUrl(url)

    /**
     * Enqueue a download. If the row already exists and is not FAILED, this is a
     * no-op (idempotent). If FAILED, retry by re-inserting the row as QUEUED.
     */
    suspend fun enqueue(req: EnqueueRequest) {
        val dir = File(workRoot(), req.repoKey.ifBlank { "common" })
        val destFile = File(dir, req.fileName)

        val existing = dao.byUrl(req.url)
        if (existing?.status == "IN_PROGRESS" || existing?.status == "QUEUED") {
            return // already queued/in-flight
        }
        if (existing?.status == "DONE" && destFile.exists()) {
            return // already downloaded
        }
        if (existing != null && !destFile.exists()) {
            // row exists but file is gone (user cleaned disk) — fall through to create fresh
        }

        val now = System.currentTimeMillis()
        val entity = DownloadEntity(
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
        dao.upsert(entity)

        _events.tryEmit(DownloadEvent.Started(req.url))

        // Serialize downloads — only run one at a time.
        runNextIfIdle()
    }

    /** Reset a FAILED download back to QUEUED. */
    suspend fun retry(url: String) {
        val existing = dao.byUrl(url) ?: return
        destFileOrNull(existing)?.delete()
        dao.upsert(existing.copy(status = "QUEUED", downloadedBytes = 0, progressPct = 0, errorMsg = "", updatedAt = System.currentTimeMillis(), createdAt = System.currentTimeMillis()))
        _events.tryEmit(DownloadEvent.Started(url))
        runNextIfIdle()
    }

    /** Cancel a download (if running) and remove its row + partial file. */
    suspend fun cancel(url: String) {
        val existing = dao.byUrl(url) ?: return
        if (existing.status == "IN_PROGRESS" && currentJob != null) {
            currentJob?.cancel()
        }
        destFileOrNull(existing)?.delete()
        dao.deleteByUrl(url)
        _events.tryEmit(DownloadEvent.Removed(url))
    }

    /** Remove a DONE record (and its file) from disk + DB. */
    suspend fun removeCompleted(url: String) {
        val existing = dao.byUrl(url) ?: return
        destFileOrNull(existing)?.delete()
        dao.deleteByUrl(url)
        _events.tryEmit(DownloadEvent.Removed(url))
    }

    private fun destFileOrNull(entity: DownloadEntity): File? =
        entity.localPath.takeIf { it.isNotBlank() }?.let { File(it) }

    /** Root dir for downloads; use the app-private external space so no permission needed. */
    private fun workRoot(): File {
        // We intentionally use the per-app external Files dir (getExternalFilesDir)
        // — no READ/WRITE_EXTERNAL_STORAGE permission needed on Android 13+.
        val root = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir,
            "PocketHub",
        )
        if (!root.exists()) root.mkdirs()
        return root
    }

    /** Kick off the head of the queue if no current job is in flight. */
    private fun runNextIfIdle() {
        if (currentJob?.isActive == true) return
        scope.launch {
            val nextRow = dao.activeFlow().first().firstOrNull { it.status == "QUEUED" } ?: return@launch
            executeDownload(nextRow)
            // try next queued download, then continue until queue empty
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
                // GitHub release assets redirect to objects.githubusercontent.com — we need
                // followRedirects = true for download streaming. The injected client's default
                // OkHttp builder disables redirects for login; so we make a per-request client
                // with redirects enabled.
                val dlClient = client.newBuilder().followRedirects(true).build()
                val response = withContext(ioDispatcher) { dlClient.newCall(request).execute() }
                if (!response.isSuccessful) {
                    dao.setStatusWithError(currentUrl, "FAILED", "HTTP ${response.code}", System.currentTimeMillis())
                    _events.tryEmit(DownloadEvent.Failed(currentUrl, "HTTP ${response.code}"))
                    return@launch
                }

                val totalBytes: Long = response.body?.contentLength()?.let {
                    if (it <= 0) entity.sizeBytes else it
                } ?: entity.sizeBytes
                val body = response.body ?: throw IOException("No body in response")

                dao.upsert(entity.copy(status = "IN_PROGRESS", sizeBytes = totalBytes))

                withContext(ioDispatcher) {
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
                                // Throttle progress reports to 1 update per 100KB to save DB writes.
                                if (totalRead - lastReport >= 100 * 1024 || totalRead == totalBytes) {
                                    val pct = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else 0
                                    dao.setStatusWithProgress(currentUrl, "IN_PROGRESS", totalRead, pct.coerceIn(0, 100), System.currentTimeMillis())
                                    lastReport = totalRead
                                }
                            }
                        }
                    }
                }

                // Move .part → final name
                if (targetFile.exists()) targetFile.delete()
                if (!destFile.renameTo(targetFile)) {
                    // Fallback if rename fails across mounts
                    destFile.copyTo(targetFile, overwrite = true)
                    destFile.delete()
                }

                dao.setStatusWithSize(currentUrl, "DONE", totalBytes, 100, System.currentTimeMillis())
                _events.tryEmit(DownloadEvent.Done(currentUrl, targetFile.absolutePath))
            } catch (e: kotlinx.coroutines.CancellationException) {
                destFile.delete()
                dao.setStatusWithError(currentUrl, "FAILED", "Cancelled", System.currentTimeMillis())
                throw e
            } catch (e: Throwable) {
                destFile.delete()
                val msg = e.localizedMessage ?: e.javaClass.simpleName
                dao.setStatusWithError(currentUrl, "FAILED", msg, System.currentTimeMillis())
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
