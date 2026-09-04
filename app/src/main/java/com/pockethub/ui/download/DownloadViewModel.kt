package com.pockethub.ui.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.download.DownloadManager
import com.pockethub.data.download.openLocalFile
import com.pockethub.data.local.DownloadEntity
import com.pockethub.data.remote.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val manager: DownloadManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    val activeList: StateFlow<List<DownloadEntity>> = manager.activeFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val doneList: StateFlow<List<DownloadEntity>> = manager.doneFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** User-chosen SAF download folder (null = default app directory). */
    val downloadFolderUri: StateFlow<String?> = settings.downloadFolderUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** URLs the user tapped with install intent — when the row lands DONE, open the installer directly. */
    private val installPending = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            manager.allFlow().collect { rows ->
                val pending = installPending.value
                if (pending.isEmpty()) return@collect
                val hits = rows.filter { it.url in pending && it.status == "DONE" }
                if (hits.isEmpty()) return@collect
                installPending.value = pending - hits.map { it.url }.toSet()
                hits.forEach { openAsApkIfPossible(it) }
            }
        }
    }

    /**
     * Release-tab APK assets: open the system installer as soon as the file is
     * on disk — either right now (already cached from a previous download) or
     * when the fetch completes. No detour through the Downloads tab to install.
     */
    fun installWhenDone(url: String) {
        viewModelScope.launch {
            val row = manager.get(url)
            if (row?.status == "DONE") {
                if (openAsApkIfPossible(row)) return@launch
            }
            installPending.value = installPending.value + url
            // Watchdog: if DONE landed between the room-flow registration and
            // the line above, no further emission would arrive — recheck once.
            kotlinx.coroutines.delay(1_500)
            if (url !in installPending.value) return@launch
            val r2 = manager.get(url)
            if (r2?.status == "DONE") {
                installPending.value = installPending.value - url
                openAsApkIfPossible(r2)
            }
        }
    }

    private fun openAsApkIfPossible(row: DownloadEntity): Boolean {
        val f = row.localPath.takeIf { it.isNotBlank() }?.let { File(it) } ?: return false
        if (!f.exists() || !f.name.lowercase().endsWith(".apk")) return false
        return openLocalFile(appContext, f)
    }

    fun setDownloadFolder(uri: String?) {
        viewModelScope.launch { settings.setDownloadFolderUri(uri) }
    }

    fun enqueue(req: DownloadManager.EnqueueRequest) {
        viewModelScope.launch { manager.enqueue(req) }
    }

    fun retry(url: String) = viewModelScope.launch { manager.retry(url) }
    fun cancel(url: String) = viewModelScope.launch { manager.cancel(url) }
    fun removeCompleted(url: String) = viewModelScope.launch { manager.removeCompleted(url) }
}

enum class DownloadTab { ACTIVE, DONE }
