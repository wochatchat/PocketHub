package com.pockethub.data.download

import android.content.Context
import com.pockethub.util.userMessage
import android.os.Environment
import com.pockethub.data.local.DownloadDao
import com.pockethub.data.local.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val client: OkHttpClient,
    private val dao: DownloadDao,
    private val settings: com.pockethub.data.remote.SettingsRepository,
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
    private val queueSignals = Channel<Unit>(Channel.CONFLATED)
    private val cancelledUrls = ConcurrentHashMap.newKeySet<String>()

    /**
     * Bare client used for redirect hops. Built from scratch because OkHttp
     * cannot REMOVE inherited interceptors via newBuilder() — the shared
     * [client] carries [com.pockethub.data.remote.AuthInterceptor], which would
     * attach the GitHub Bearer token to the redirect target (Azure Blob / CDN),
     * and those hosts reject foreign tokens with 401.
     */
    private val redirectClient = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var currentJob: Job? = null
    @Volatile private var currentUrl: String? = null
    @Volatile private var currentCall: Call? = null

    init {
        scope.launch {
            // The app may have been killed mid-download: any rows left in
            // IN_PROGRESS are stale. Reset them to QUEUED so the next drain
            // resumes them (the .part file on disk is kept for byte-range
            // resume). Runs before the drain loop so the first pass sees them.
            val stale = dao.flowByStates(listOf("IN_PROGRESS")).first()
            stale.forEach {
                dao.upsert(it.copy(status = "QUEUED", errorMsg = "", updatedAt = System.currentTimeMillis()))
            }
            for (ignored in queueSignals) drainQueue()
        }
        queueSignals.trySend(Unit)
    }

    fun allFlow(): Flow<List<DownloadEntity>> = dao.allFlow()
    fun activeFlow(): Flow<List<DownloadEntity>> =
        dao.flowByStates(listOf("QUEUED", "IN_PROGRESS", "FAILED"))
    fun doneFlow(): Flow<List<DownloadEntity>> = dao.flowByState("DONE")

    suspend fun get(url: String): DownloadEntity? = dao.byUrl(url)

    suspend fun enqueue(req: EnqueueRequest) {
        cancelledUrls.remove(req.url)
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
        runNextIfIdle()
    }

    /**
     * Re-queue a failed/cancelled download. The `.part` file is kept so
     * [executeDownload] resumes from the already-downloaded bytes via an HTTP
     * Range request; pass [fromScratch] = true (long-press "restart" style
     * actions) to delete it and start over.
     */
    suspend fun retry(url: String, fromScratch: Boolean = false) {
        cancelledUrls.remove(url)
        val existing = dao.byUrl(url) ?: return
        if (fromScratch) destFileOrNull(existing)?.let { f ->
            File(f.parentFile, "${f.name}.part").delete()
        }
        dao.upsert(
            existing.copy(
                status = "QUEUED",
                errorMsg = "",
                updatedAt = System.currentTimeMillis(),
            )
        )
        runNextIfIdle()
    }

    suspend fun cancel(url: String) {
        val existing = dao.byUrl(url) ?: return
        if (currentUrl == url) {
            cancelledUrls += url
            currentCall?.cancel()
            currentJob?.cancel()
        }
        destFileOrNull(existing)?.let {
            it.delete()
            File(it.parentFile, "${it.name}.part").delete()
        }
        dao.deleteByUrl(url)
        runNextIfIdle()
    }

    suspend fun removeCompleted(url: String) {
        val existing = dao.byUrl(url) ?: return
        destFileOrNull(existing)?.delete()
        dao.deleteByUrl(url)
    }

    private fun destFileOrNull(entity: DownloadEntity): File? =
        entity.localPath.takeIf { it.isNotBlank() }?.let { File(it) }

    /** Directory where downloads for [repoKey] are stored. */
    fun dirFor(repoKey: String): File = File(workRoot(), repoKey.ifBlank { "common" })

    /**
     * Copy a finished download into the user's chosen SAF folder (when set).
     * Failures are swallowed — the user can still open the internal copy, and
     * a broken export must never flip the download back to FAILED.
     */
    private suspend fun exportToUserFolder(entity: DownloadEntity, source: File) {
        val treeUri = settings.downloadFolderUri.first() ?: return
        runCatching {
            val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                appContext, android.net.Uri.parse(treeUri),
            ) ?: return
            // Replace an older export of the same name (re-download case).
            root.findFile(source.name)?.delete()
            val mime = entity.contentType.takeIf { it.contains('/') }
                ?: "application/octet-stream"
            val doc = root.createFile(mime, source.name) ?: return
            appContext.contentResolver.openOutputStream(doc.uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
        }
    }

    private fun workRoot(): File {
        val root = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir,
            "PocketHub",
        )
        if (!root.exists()) root.mkdirs()
        return root
    }

    private fun runNextIfIdle() {
        queueSignals.trySend(Unit)
    }

    private suspend fun drainQueue() {
        while (true) {
            val queued = dao.flowByStates(listOf("QUEUED", "IN_PROGRESS", "FAILED")).first()
                .firstOrNull { it.status == "QUEUED" }
                ?: return
            executeDownload(queued)
        }
    }

    /**
     * Opens [url] following redirects manually:
     *
     * - The initial request goes through the shared [client] (in a
     *   no-redirect derived copy), so [com.pockethub.data.remote.AuthInterceptor]
     *   attaches the Bearer token — required because GitHub's artifact download
     *   URLs (`archive_download_url`) only authenticate on the API host
     *   (mandatory for private repos; public ones tolerate it).
     * - GitHub answers with 302 → signed Azure Blob / CDN URL whose query string
     *   already carries the authorization (SAS token). Those redirect targets
     *   REJECT a foreign `Authorization` header with 401, and OkHttp would
     *   otherwise copy the header across hosts — so each hop is issued via the
     *   bare [redirectClient] with no auth header attached.
     *
     * Returns the final call (usable for cancellation) and its response.
     *
     * When [rangeHeader] is non-null (byte-range resume), it is attached to
     * every hop — the signed redirect targets accept Range requests and the
     * bare client must carry the header explicitly because requests are
     * rebuilt per hop.
     */
    private fun openDownload(url: String, rangeHeader: String? = null): Pair<Call, Response> {
        val baseReq = Request.Builder().url(url)
        if (rangeHeader != null) baseReq.header("Range", rangeHeader)
        var call = client.newBuilder().followRedirects(false).build()
            .newCall(baseReq.build())
        currentCall = call
        var response = call.execute()
        var hops = 0
        while (response.isRedirect && hops < 5) {
            val nextUrl = response.header("Location")?.let { response.request.url.resolve(it) }
            response.close()
            if (nextUrl == null) throw IOException("Redirect missing Location header")
            val hopReq = Request.Builder().url(nextUrl)
            if (rangeHeader != null) hopReq.header("Range", rangeHeader)
            call = redirectClient.newCall(hopReq.build())
            currentCall = call
            response = call.execute()
            hops++
        }
        return call to response
    }

    private suspend fun executeDownload(entity: DownloadEntity) {
        val targetFile = File(entity.localPath)
        targetFile.parentFile?.mkdirs()
        // Route GitHub file downloads through the user's accelerator when set.
        val mirrorPrefix = settings.downloadMirrorPrefix.first()
        val url = com.pockethub.util.applyMirrorPrefix(entity.url, mirrorPrefix)
        val destFile = File(targetFile.parentFile, "${targetFile.name}.part")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var droppedStale = false  // 416: the stale .part was dropped once already
            var autoResumes = 0       // transient network failures auto-resumed so far
            while (true) {
                try {
                    // Byte-range resume: a leftover .part file (from a previous
                    // failure, cancel or app kill) requests only the missing
                    // tail. 206 → append; 200 → server ignored Range, restart.
                    val baseBytes = if (destFile.exists()) destFile.length() else 0L
                    val (call, response) = openDownload(
                        url, if (baseBytes > 0) "bytes=$baseBytes-" else null,
                    )
                    currentCall = call

                    // Stale part that can no longer be satisfied (content
                    // changed server-side or the part was already complete):
                    // drop it and restart from scratch — exactly once, so a
                    // persisting 416 fails cleanly below.
                    if (response.code == 416 && baseBytes > 0 && !droppedStale) {
                        response.close()
                        destFile.delete()
                        droppedStale = true
                        continue
                    }

                    if (!response.isSuccessful) {
                        val code = response.code
                        response.close()
                        // Server-side hiccups (5xx) get the same bounded
                        // auto-resume treatment as in-stream network errors.
                        if (code in 500..599 && autoResumes < MAX_AUTO_RESUME) {
                            autoResumes++
                            kotlinx.coroutines.delay(AUTO_RESUME_BACKOFF_MS * autoResumes)
                            continue
                        }
                        dao.upsert(entity.copy(status = "FAILED", errorMsg = "HTTP $code", updatedAt = System.currentTimeMillis()))
                        return@launch
                    }

                    response.use {
                        val resume = it.code == 206 && baseBytes > 0
                        val alreadyBytes = if (resume) baseBytes else 0L
                        val totalBytes = when {
                            it.code == 206 -> it.header("Content-Range")
                                ?.let { r -> Regex("bytes .*/(\\d+)").find(r)?.groupValues?.get(1)?.toLong() }
                                ?: (baseBytes + (it.body?.contentLength()?.takeIf { len -> len > 0 } ?: 0L))
                            else -> it.body?.contentLength()?.takeIf { size -> size > 0 } ?: entity.sizeBytes
                        }
                        val body = it.body ?: throw IOException("No body in response")
                        dao.upsert(entity.copy(status = "IN_PROGRESS", sizeBytes = totalBytes, updatedAt = System.currentTimeMillis()))

                        body.byteStream().use { input ->
                            java.io.FileOutputStream(destFile, resume).use { output ->
                                val buffer = ByteArray(16 * 1024)
                                var read = 0L
                                var lastReported = 0L
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n == -1) break
                                    output.write(buffer, 0, n)
                                    read += n
                                    if (read - lastReported >= 100 * 1024) {
                                        val downloaded = alreadyBytes + read
                                        val progress = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                                        dao.upsert(entity.copy(status = "IN_PROGRESS", downloadedBytes = downloaded, progressPct = progress.coerceIn(0, 100), updatedAt = System.currentTimeMillis()))
                                        lastReported = read
                                    }
                                }
                            }
                        }

                        if (targetFile.exists()) targetFile.delete()
                        if (!destFile.renameTo(targetFile)) {
                            destFile.copyTo(targetFile, overwrite = true)
                            destFile.delete()
                        }
                        dao.upsert(entity.copy(status = "DONE", downloadedBytes = totalBytes, progressPct = 100, updatedAt = System.currentTimeMillis()))
                        // Mirror into the user-chosen download folder (best-effort;
                        // the app-private copy above stays authoritative for APK
                        // install / artifact extraction flows).
                        exportToUserFolder(entity, targetFile)
                    }
                    break
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Keep the .part file — retry resumes from it; cancel() deletes
                    // it explicitly for user-requested removals.
                    if (!cancelledUrls.remove(url)) {
                        dao.upsert(entity.copy(status = "FAILED", errorMsg = "Cancelled", updatedAt = System.currentTimeMillis()))
                    }
                    throw e
                } catch (e: IOException) {
                    // A user-requested cancel surfaces as an OkHttp IOException —
                    // honour the cancelled set before any auto-resume.
                    if (cancelledUrls.remove(url)) return@launch
                    // Transient network error mid-transfer: the .part file kept
                    // everything downloaded so far, so re-entering the loop
                    // re-issues the Range request and only fetches the tail.
                    if (autoResumes < MAX_AUTO_RESUME) {
                        autoResumes++
                        kotlinx.coroutines.delay(AUTO_RESUME_BACKOFF_MS * autoResumes)
                        continue
                    }
                    // Keep the .part file so retry() can byte-range resume.
                    dao.upsert(entity.copy(status = "FAILED", errorMsg = e.userMessage(e.javaClass.simpleName), updatedAt = System.currentTimeMillis()))
                    return@launch
                } catch (e: Throwable) {
                    // Unexpected non-IO failure — fail cleanly instead of
                    // crashing the scope; the .part file stays for retry().
                    if (cancelledUrls.remove(url)) return@launch
                    dao.upsert(entity.copy(status = "FAILED", errorMsg = e.userMessage(e.javaClass.simpleName), updatedAt = System.currentTimeMillis()))
                    return@launch
                }
            }
        }
        currentUrl = url
        currentJob = job
        job.start()
        job.join()
        currentCall = null
        currentJob = null
        currentUrl = null
    }

    private companion object {
        /** Bounded auto-resume attempts for transient network failures (5xx / IOException). */
        const val MAX_AUTO_RESUME = 3

        /** Linear backoff between auto-resume attempts, multiplied by the attempt number. */
        const val AUTO_RESUME_BACKOFF_MS = 1_500L
    }
}
