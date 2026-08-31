package com.pockethub.ui.offline

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.data.offline.OfflineRepoManager
import com.pockethub.data.remote.GitHubApi
import com.pockethub.ui.repo.CodeBrowserViewModel
import com.pockethub.ui.repo.FullScreenFileViewer
import com.pockethub.ui.repo.FullScreenViewerHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Serves the shared full-screen code viewer from an extracted zip archive —
 * same [CodeBrowserViewModel.State] shape and [FullScreenViewerHost] contract
 * as the GitHub-backed [CodeBrowserViewModel], so the IDE-style UI (file tree
 * + syntax-highlighted body) is reused verbatim. Everything is local: no
 * network is touched in this screen.
 */
@HiltViewModel
class OfflineCodeViewModel @Inject constructor(
    private val offlineRepo: OfflineRepoManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), FullScreenViewerHost {

    private val url: String = checkNotNull(savedStateHandle["url"])
    private val displayName: String = savedStateHandle.get<String>("name").orEmpty()

    /** Resolved extraction root; null until [loadTree] finished (or failed). */
    private var root: File? = null

    private val _state = MutableStateFlow(CodeBrowserViewModel.State(repo = displayName))
    override val state: StateFlow<CodeBrowserViewModel.State> = _state.asStateFlow()

    override fun loadTree() {
        val s = _state.value
        if (s.fullTree.isNotEmpty() || s.isLoadingTree) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingTree = true, isLoading = true) }
            val extracted = offlineRepo.ensureExtracted(url)
            if (extracted == null) {
                // Marker string — rendered with real copy by the shared viewer.
                _state.update { it.copy(isLoadingTree = false, isLoading = false, error = "extract_failed") }
                return@launch
            }
            root = extracted
            _state.update {
                it.copy(
                    repo = displayName,
                    fullTree = offlineRepo.treeOf(extracted),
                    isLoadingTree = false,
                    isLoading = false,
                )
            }
        }
    }

    override fun openFile(entry: GitHubApi.ContentEntry) {
        val r = root ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, viewingFile = entry, error = null, fileContent = null) }
            val text = offlineRepo.readText(r, entry.path)
            _state.update {
                it.copy(
                    isLoading = false,
                    fileContent = text,
                    // Binary / oversized files surface a clear message instead
                    // of the empty "pick a file" hint.
                    error = if (text == null) "unpreviewable" else null,
                )
            }
        }
    }
}

/**
 * Navigation destination wrapping the shared [FullScreenFileViewer] with the
 * offline [OfflineCodeViewModel] — full-screen, file-tree equipped, offline.
 */
@Composable
fun OfflineCodeScreen(
    onBack: () -> Unit,
    vm: OfflineCodeViewModel = hiltViewModel(),
) {
    FullScreenFileViewer(vm = vm, onDismiss = onBack)
}

/**
 * Navigation destination wrapping the shared [FullScreenFileViewer] with the
 * offline [OfflineCodeViewModel] — full-screen, file-tree equipped, offline.
 */
@Composable
fun OfflineCodeScreen(
    onBack: () -> Unit,
    vm: OfflineCodeViewModel = hiltViewModel(),
) {
    FullScreenFileViewer(vm = vm, onDismiss = onBack)
}
