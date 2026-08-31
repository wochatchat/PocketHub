package com.pockethub.ui.main

import android.content.Context
import com.pockethub.util.userMessage
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.SettingsRepository
import com.pockethub.data.remote.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

/**
 * Owns in-app auto-update detection and the entire flow inside the dialog:
 * detect → prompt → download (with progress) → install.
 *
 * The APK is downloaded into the app's cache directory (auto-purged on uninstall
 * and on cache clear) and installed via the system PackageInstaller through a
 * FileProvider URI. No browser is opened, so the user never leaves the app.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val issueReporter: com.pockethub.data.reporting.IssueReporter,
    @ApplicationContext private val appContext: Context,
    private val updater: UpdateChecker,
    private val settings: SettingsRepository,
    private val client: OkHttpClient,
) : ViewModel() {

    private val owner = "wochatchat"
    private val repo = "PocketHub"

    /** Minimum gap between two automatic background checks (6h). */
    private val autoCheckIntervalMs = 6L * 60 * 60 * 1000

    /**
     * Once the user has dismissed an update prompt (tapped "Later" / backed out),
     * the same release won't be auto-shown again for this long — avoids nagging
     * the user every app launch. Manual "Check for updates" from Settings bypasses
     * this throttle and always shows whatever's found.
     *
     * Kept short (1 day) so users who tap "Later" still see the prompt next day —
     * the previous 3-day window let people miss updates altogether.
     */
    private val promptSuppressMs = 1L * 24 * 60 * 60 * 1000

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpdateAvailable(val info: UpdateChecker.UpdateInfo) : State
        data object UpToDate : State
        data object Error : State
    }

    /** Lifecycle of the in-dialog download + install step. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Running(val progressPct: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadState
        data class Done(val path: String) : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    private var downloadJob: kotlinx.coroutines.Job? = null

    fun maybeAutoCheck() {
        viewModelScope.launch {
            val lastMs = settings.getLastUpdateCheckMs()
            if (System.currentTimeMillis() - lastMs < autoCheckIntervalMs) return@launch
            settings.markUpdateCheckedNow()
            runCheck(includePre = false, forceShow = false)
        }
    }

    fun manualCheck() {
        _state.value = State.Checking
        viewModelScope.launch {
            settings.markUpdateCheckedNow()
            runCheck(includePre = false, forceShow = true)
        }
    }

    private suspend fun runCheck(includePre: Boolean, forceShow: Boolean = false) {
        val info = updater.fetchLatest(owner, repo, includePre)
        if (info == null) {
            _state.value = State.Error
            return
        }
        val ignored = settings.ignoredUpdateVersion.first()
        if (updater.isNewer(info) && info.latestVersionName != ignored) {
            // Auto-check path: respect the suppress window after "Later" dismiss.
            val lastPromptMs = settings.getLastUpdatePromptMs()
            val suppressed = lastPromptMs > 0 && System.currentTimeMillis() - lastPromptMs < promptSuppressMs
            // Escape hatch: a release published *after* the user last dismissed
            // a prompt is genuinely new, so always surface it. Without this, a
            // user who tapped "Later" yesterday would miss today's hotfix entirely
            // — the most common reason people report "I never get the update popup".
            val releaseNewSinceDismiss = parsePublishedEpochMs(info.publishedAt) > lastPromptMs
            if (forceShow || !suppressed || releaseNewSinceDismiss) {
                settings.markUpdatePromptedNow()
                // Prefer the AI-authored changelog committed to the repo over
                // the release body (see fetchChangelogNotes).
                val notes = fetchChangelogNotes(info.latestVersionName)
                _state.value = State.UpdateAvailable(
                    if (notes != null) info.copy(releaseNotes = notes) else info
                )
            } else {
                // Still newer than installed, but user recently said "Later" — don't auto-pop.
                _state.value = State.Idle
            }
        } else {
            _state.value = State.UpToDate
        }
    }

    /** Parse an ISO-8601 published_at (e.g. "2025-01-02T03:04:05Z") to epoch ms, 0 on parse failure. */
    private fun parsePublishedEpochMs(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return runCatching {
            java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    /**
     * Fetch the AI-authored bilingual changelog committed to the repo
     * (changelog/latest.json — written before the build is triggered), and
     * wrap it in the `<!--pockethub-changelog {json}-->` block the update
     * dialog already parses. The dialog localizes summary/items on its own,
     * so display language always follows the app setting.
     *
     * The file's `version` field must match the release tag — a stale file
     * describing a different build is ignored and the release body is used.
     * Raw GitHub is tried first, then the jsDelivr mirror (reachable when
     * raw.githubusercontent.com is blocked).
     */
    private suspend fun fetchChangelogNotes(releaseVersion: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val wanted = releaseVersion.removePrefix("v")
            val urls = listOf(
                "https://raw.githubusercontent.com/$owner/$repo/main/changelog/latest.json",
                "https://cdn.jsdelivr.net/gh/$owner/$repo@main/changelog/latest.json",
            )
            for (url in urls) {
                val body = runCatching {
                    client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) null else resp.body?.string()
                    }
                }.getOrNull() ?: continue
                val obj = runCatching { org.json.JSONObject(body) }.getOrNull() ?: continue
                val fileVersion = obj.optString("version").removePrefix("v")
                val items = obj.optJSONArray("items") ?: continue
                if (items.length() == 0) continue
                if (fileVersion.isBlank() || !fileVersion.equals(wanted, ignoreCase = true)) continue
                return@withContext "<!--pockethub-changelog $obj-->"
            }
            null
        }

    fun ignoreVersion(version: String) {
        viewModelScope.launch {
            settings.setIgnoredUpdateVersion(version)
            _state.value = State.Idle
            resetDownload()
        }
    }

    fun dismiss() {
        // Cancelling any in-flight download when the dialog is dismissed.
        cancelDownload()
        _state.value = State.Idle
    }

    /** Begin downloading the APK pointed at by [info], into the cache dir. */
    fun startDownload(info: UpdateChecker.UpdateInfo) {
        // If the same URL's download already finished, just re-arm install.
        val current = _download.value
        if (current is DownloadState.Done) {
            val existing = File(current.path)
            if (existing.exists()) return
        }
        cancelDownload()
        _download.value = DownloadState.Running(0, 0L, info.assetSizeBytes)
        downloadJob = viewModelScope.launch {
            val url = info.downloadUrl ?: info.htmlUrl
            if (url.isNullOrBlank()) {
                _download.value = DownloadState.Failed("No download URL")
                return@launch
            }
            val dest = updatesDir().let { dir ->
                dir.mkdirs()
                File(dir, "pockethub-${info.latestVersionName}.apk")
            }
            val tmp = File(dest.parentFile, "${dest.name}.part")
            try {
                withContext(Dispatchers.IO) {
                    // Route the APK download through the accelerator when configured.
                    val mirrored = com.pockethub.util.applyMirrorPrefix(url, settings.downloadMirrorPrefix.first())
                    // GitHub CDN issues redirects to release-assets; follow them.
                    // (OkHttp preserves the Range header across redirect hops.)
                    // An explicit 30s read timeout turns stalled VPN/proxy
                    // connections into a fast SocketTimeout → auto-resume with the
                    // .part tail, instead of hanging for the shared client's
                    // default timeout on every stall.
                    val dlClient = client.newBuilder()
                        .followRedirects(true)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    // Byte-range resume: a leftover .part file (previous failure /
                    // app kill) re-requests only the missing tail — 206 appends,
                    // 200 restarts, 416 (stale part) drops it once and retries.
                    // Transient network errors (EOF mid-stream etc.) auto-resume
                    // up to MAX_RESUME times with a linear backoff.
                    var droppedStale = false
                    var autoResumes = 0
                    var bytesAtAttemptStart = 0L
                    while (true) {
                        try {
                            val base = if (tmp.exists()) tmp.length() else 0L
                            bytesAtAttemptStart = base
                            val req = Request.Builder().url(mirrored)
                            if (base > 0) req.header("Range", "bytes=$base-")
                            var retryStale = false
                            dlClient.newCall(req.build()).execute().use { resp ->
                                if (resp.code == 416 && base > 0 && !droppedStale) {
                                    droppedStale = true
                                    retryStale = true
                                    return@use
                                }
                                if (!resp.isSuccessful) {
                                    _download.value = DownloadState.Failed("HTTP ${resp.code}")
                                    return@withContext
                                }
                                val resume = resp.code == 206 && base > 0
                                val already = if (resume) base else 0L
                                val total = when {
                                    resp.code == 206 -> resp.header("Content-Range")
                                        ?.let { Regex("bytes .*/(\\d+)").find(it)?.groupValues?.get(1)?.toLong() }
                                        ?: (base + (resp.body?.contentLength()?.takeIf { it > 0 } ?: 0L))
                                    else -> resp.body?.contentLength()?.takeIf { it > 0 } ?: info.assetSizeBytes
                                }.coerceAtLeast(0)
                                val body = resp.body ?: throw java.io.IOException("Empty body")
                                body.byteStream().use { input ->
                                    java.io.FileOutputStream(tmp, resume).use { output ->
                                        val buf = ByteArray(32 * 1024)
                                        var read = 0L
                                        var lastEmit = 0L
                                        while (true) {
                                            val n = input.read(buf)
                                            if (n == -1) break
                                            if (!isActive) throw kotlinx.coroutines.CancellationException("cancelled")
                                            output.write(buf, 0, n)
                                            read += n
                                            if (read - lastEmit >= 200 * 1024) {
                                                val downloaded = already + read
                                                val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
                                                _download.value = DownloadState.Running(pct, downloaded, total)
                                                lastEmit = read
                                            }
                                        }
                                        // Integrity: a "clean" stream end short of
                                        // Content-Length (proxies love doing this) must
                                        // NOT rename a corrupt APK into place — throw so
                                        // the resume loop fetches the tail.
                                        val written = already + read
                                        if (total > 0 && written < total) {
                                            throw java.io.IOException("Truncated download: $written/$total")
                                        }
                                    }
                                }
                            }
                            if (retryStale) { tmp.delete(); continue }

                            if (dest.exists()) dest.delete()
                            if (!tmp.renameTo(dest)) {
                                tmp.copyTo(dest, overwrite = true)
                                tmp.delete()
                            }
                            // Auto-launch the system PackageInstaller — the user already
                            // tapped Download, so handing off immediately spares them an extra
                            // confirmation tap inside the dialog. The OS still shows its own
                            // permission prompt, preserving user control.
                            _download.value = DownloadState.Done(dest.absolutePath)
                            install(appContext, dest.absolutePath)
                            return@withContext
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: java.io.IOException) {
                            // Transient network error mid-transfer (EOF, HTTP/2
                            // "stream was reset", socket timeout …): the .part file
                            // kept everything downloaded so far — the loop re-issues
                            // the Range request and only fetches the tail. Any
                            // attempt that grew the file resets the budget, so a
                            // long download rides out unbounded hiccups.
                            autoResumes = if (tmp.length() > bytesAtAttemptStart) 0 else autoResumes + 1
                            if (autoResumes > MAX_RESUME) throw e
                            kotlinx.coroutines.delay(minOf(RESUME_BACKOFF_MS * autoResumes, MAX_BACKOFF_MS))
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Keep the .part file — the next download attempt resumes from it.
                // On cancel we surface Idle rather than Failed — user already saw it die.
                _download.value = DownloadState.Idle
                throw e
            } catch (e: Throwable) {
                issueReporter.reportError("Update", "startDownload", e)
                // Keep the .part file so the retry byte-range resumes.
                _download.value = DownloadState.Failed(e.userMessage(e.javaClass.simpleName))
            }
        }
    }

    /** Cancel + reset the in-flight download; safe to call from idle state. */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        resetDownload()
    }

    private fun resetDownload() {
        _download.value = DownloadState.Idle
    }

    /** Hand the downloaded APK to the system PackageInstaller. Must be invoked from UI. */
    fun install(context: Context, path: String) {
        val file = File(path)
        if (!file.exists()) {
            _download.value = DownloadState.Idle
            return
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.packageManager.queryIntentActivities(intent, 0).forEach {
            context.grantUriPermission(it.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun updatesDir(): File {
        val dir = File(appContext.cacheDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private companion object {
        /** Consecutive zero-progress auto-resume attempts before giving up.
         *  Attempts that DID grow the .part file reset the budget, so a long
         *  transfer survives unbounded hiccups on a flaky network — only a
         *  truly dead path (server refuses Range, no bytes flowing) aborts. */
        const val MAX_RESUME = 8

        /** Linear backoff between auto-resume attempts, multiplied by the attempt number. */
        const val RESUME_BACKOFF_MS = 1_500L

        /** Upper bound for the backoff delay. */
        const val MAX_BACKOFF_MS = 15_000L
    }
}
