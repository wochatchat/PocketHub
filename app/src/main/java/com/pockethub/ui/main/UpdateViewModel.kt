package com.pockethub.ui.main

import android.content.Context
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
    @ApplicationContext private val appContext: Context,
    private val updater: UpdateChecker,
    private val settings: SettingsRepository,
    private val client: OkHttpClient,
) : ViewModel() {

    private val owner = "wochatchat"
    private val repo = "PocketHub"

    /** Minimum gap between two automatic background checks (24h). */
    private val autoCheckIntervalMs = 24L * 60 * 60 * 1000

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
            runCheck(includePre = false)
        }
    }

    fun manualCheck() {
        _state.value = State.Checking
        viewModelScope.launch {
            settings.markUpdateCheckedNow()
            runCheck(includePre = false)
        }
    }

    private suspend fun runCheck(includePre: Boolean) {
        val info = updater.fetchLatest(owner, repo, includePre)
        if (info == null) {
            _state.value = State.Error
            return
        }
        val ignored = settings.ignoredUpdateVersion.first()
        if (updater.isNewer(info) && info.latestVersionName != ignored) {
            _state.value = State.UpdateAvailable(info)
        } else {
            _state.value = State.UpToDate
        }
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
                    val request = Request.Builder().url(url).build()
                    // GitHub CDN issues redirects to release-assets; follow them.
                    val dlClient = client.newBuilder().followRedirects(true).build()
                    dlClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            _download.value = DownloadState.Failed("HTTP ${resp.code}")
                            return@withContext
                        }
                        val total = (resp.body?.contentLength()?.takeIf { it > 0 }
                            ?: info.assetSizeBytes).coerceAtLeast(0)
                        val body = resp.body ?: throw java.io.IOException("Empty body")
                        body.byteStream().use { input ->
                            tmp.outputStream().use { output ->
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
                                        val pct = if (total > 0) ((read * 100) / total).toInt().coerceIn(0, 100) else 0
                                        _download.value = DownloadState.Running(pct, read, total)
                                        lastEmit = read
                                    }
                                }
                            }
                        }
                    }
                    if (dest.exists()) dest.delete()
                    if (!tmp.renameTo(dest)) {
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                    _download.value = DownloadState.Done(dest.absolutePath)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                tmp.delete()
                // On cancel we surface Idle rather than Failed — user already saw it die.
                _download.value = DownloadState.Idle
                throw e
            } catch (e: Throwable) {
                tmp.delete()
                _download.value = DownloadState.Failed(e.localizedMessage ?: e.javaClass.simpleName)
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
}
