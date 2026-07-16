package com.pockethub.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.download.DownloadManager
import com.pockethub.data.local.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val manager: DownloadManager,
) : ViewModel() {

    /** Latest download events (for one-shot reactions from UI). */
    private val _lastEvent = MutableStateFlow<com.pockethub.data.download.DownloadEvent?>(null)
    val lastEvent: StateFlow<com.pockethub.data.download.DownloadEvent?> = _lastEvent

    val activeList: StateFlow<List<DownloadEntity>> = manager.activeFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val doneList: StateFlow<List<DownloadEntity>> = manager.doneFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            manager.events.collect { ev -> _lastEvent.value = ev }
        }
    }

    fun enqueue(req: DownloadManager.EnqueueRequest) {
        viewModelScope.launch { manager.enqueue(req) }
    }

    fun retry(url: String) = viewModelScope.launch { manager.retry(url) }
    fun cancel(url: String) = viewModelScope.launch { manager.cancel(url) }
    fun removeCompleted(url: String) = viewModelScope.launch { manager.removeCompleted(url) }
}

enum class DownloadTab { ACTIVE, DONE }
