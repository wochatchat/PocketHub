package com.pockethub.ui.repo

// In-app repo file viewer (net branch): opens a file by path, rendered as
// markdown (README translations, docs/, CONTRIBUTING…) or syntax-highlighted
// code. Reached from the GitHub link router via blob links and README-relative
// doc links. Links inside the rendered markdown route through the same
// GitHubLinkRouter, so doc-to-doc navigation stays in-app.

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.R
import com.pockethub.data.remote.GitHubApi
import com.pockethub.util.userMessage
import com.pockethub.ui.markdown.MarkdownText
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Loads a single repo file (Contents API) for [FileViewerScreen].
 * Same decode rules as [CodeBrowserViewModel.openFile].
 */
@HiltViewModel
class FileViewerViewModel @Inject constructor(
    private val api: GitHubApi,
    private val json: kotlinx.serialization.json.Json,
    private val issueReporter: com.pockethub.data.reporting.IssueReporter,
) : ViewModel() {

    data class State(
        val path: String = "",
        val ref: String? = null,
        val content: String? = null,
        val isBinary: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun load(owner: String, repo: String, path: String, ref: String?) {
        val requested = "$owner/$repo@$ref::$path"
        if (_state.value.path == path && _state.value.ref == ref && _state.value.content != null) return
        viewModelScope.launch {
            _state.update { it.copy(path = path, ref = ref, isLoading = true, error = null, content = null) }
            try {
                val element = api.getContents(owner, repo, path, ref)
                val fetched = (element as? kotlinx.serialization.json.JsonObject)
                    ?.let { json.decodeFromJsonElement<GitHubApi.ContentEntry>(it) }
                if (fetched == null) {
                    _state.update { it.copy(isLoading = false, error = "Unexpected response") }
                    return@launch
                }
                val decoded = if (fetched.encoding == "base64" && fetched.content.isNotBlank()) {
                    try {
                        String(Base64.decode(fetched.content.replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
                    } catch (_: Exception) { null }
                } else null
                _state.update {
                    it.copy(isLoading = false, content = decoded, isBinary = decoded == null)
                }
            } catch (e: Exception) {
                issueReporter.reportError("FileViewer", "load", e)
                _state.update { it.copy(isLoading = false, error = e.userMessage("Failed to load file")) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    owner: String,
    repo: String,
    path: String,
    ref: String?,
    onBack: () -> Unit,
    vm: FileViewerViewModel = hiltViewModel(),
    // Nested markdown keeps routing through the same GitHubLinkRouter.
    onNavigateToRepo: (String, String, String?) -> Unit = { _, _, _ -> },
    onNavigateToFile: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onNavigateToIssue: (String, String, Int) -> Unit = { _, _, _ -> },
    onNavigateToPR: (String, String, Int) -> Unit = { _, _, _ -> },
    onNavigateToCommit: (String, String, String) -> Unit = { _, _, _ -> },
    onNavigateToUser: (String) -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(owner, repo, path, ref) { vm.load(owner, repo, path, ref) }
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    Column {
                        Text(
                            path.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (state.content != null) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(state.content!!))
                            Toast.makeText(context, context.getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.action_copy),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        state.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.isBinary || state.content == null ->
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.binary_preview_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else -> {
                    val content = state.content!!
                    val isMarkdown = Regex("\\.(md|markdown|txt|rst|adoc)$", RegexOption.IGNORE_CASE)
                        .containsMatchIn(path)
                    if (isMarkdown) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            com.pockethub.ui.markdown.MarkdownText(
                                markdown = content,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                repoContext = "$owner/$repo",
                                defaultBranch = ref,
                                onLinkClick = com.pockethub.ui.markdown.rememberGitHubLinkHandler(
                                    com.pockethub.ui.markdown.GitHubLinkNav(
                                        owner = owner,
                                        repo = repo,
                                        onRepo = onNavigateToRepo,
                                        onFile = onNavigateToFile,
                                        onIssue = onNavigateToIssue,
                                        onPull = onNavigateToPR,
                                        onCommit = onNavigateToCommit,
                                        onUser = onNavigateToUser,
                                    ),
                                ),
                            )
                        }
                    } else {
                        SyntaxHighlightedCode(
                            code = content,
                            fileName = path.substringAfterLast('/'),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
