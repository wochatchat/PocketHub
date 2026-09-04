package com.pockethub.ui.repo

import android.util.Base64
import com.pockethub.util.userMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Simple file-tree browser backed by GitHub's Contents API.
 *
 * Maintains a path stack so users can navigate into directories and back out.
 */
/** Max per-directory last-commit fetches in one pass (rate-limit guard).
 *  Results are cached, so re-visits don't refetch — the cap only throttles
 *  how quickly an unseen directory fills in its timestamps. */
private const val MAX_LAST_COMMIT_FETCH = 60

@HiltViewModel
class CodeBrowserViewModel @Inject constructor(
    private val api: GitHubApi,
    private val json: Json,
    private val issueReporter: com.pockethub.data.reporting.IssueReporter,) : ViewModel(), FullScreenViewerHost {

    /** Per-path last commit info (message + date), cached across navigations. */
    data class LastCommit(
        val message: String,
        val dateIso: String,
    )

    data class State(
        val owner: String = "",
        val repo: String = "",
        val ref: String? = null,
        val currentPath: String = "",
        val pathStack: List<String> = listOf(""),   // includes "" as root
        val entries: List<GitHubApi.ContentEntry> = emptyList(),
        val isLoading: Boolean = false,
        val viewingFile: GitHubApi.ContentEntry? = null,
        val fileContent: String? = null,            // decoded text (binary files stay null)
        val error: String? = null,
        /** Available branches (lazy-loaded once for the branch switcher). */
        val branches: List<GitHubApi.Branch> = emptyList(),
        val isLoadingBranches: Boolean = false,
        /** Map of entry.path → last commit for the currently visible directory. */
        val lastCommits: Map<String, LastCommit> = emptyMap(),
        /** Full recursive file tree (for the full-screen viewer's tree panel). */
        val fullTree: List<GitHubApi.GitTreeEntry> = emptyList(),
        val isLoadingTree: Boolean = false,
        /** GitHub caps the recursive tree at 100k entries / 7MB — flag it when hit. */
        val treeTruncated: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    override val state: StateFlow<State> = _state.asStateFlow()

    /** Persistent commit cache keyed by `${owner}/${repo}@${ref}::<path>` to avoid refetching. */
    private val commitCache = mutableMapOf<String, LastCommit>()

    fun init(owner: String, repo: String, ref: String? = null) {
        if (_state.value.owner == owner && _state.value.repo == repo) {
            // already initialized — just refresh current dir
            refreshCurrent()
            return
        }
        _state.update { State(owner = owner, repo = repo, ref = ref) }
        listDir("")
    }

    /** Called when the parent repo changes — resets Code tab branch to avoid
     *  carrying over the previous repo's selection. */
    fun resetRef() {
        _state.update { it.copy(ref = null) }
    }

    private fun refreshCurrent() {
        val s = _state.value
        listDir(s.currentPath)
    }

    fun listDir(path: String) {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, viewingFile = null, fileContent = null) }
            try {
                val element = if (path.isBlank()) {
                    api.getRootContents(s.owner, s.repo, s.ref)
                } else {
                    api.getContents(s.owner, s.repo, path, s.ref)
                }
                val list: List<GitHubApi.ContentEntry> = when (element) {
                    is JsonArray -> json.decodeFromJsonElement<List<GitHubApi.ContentEntry>>(element)
                    is JsonObject -> listOf(json.decodeFromJsonElement<GitHubApi.ContentEntry>(element))
                    else -> emptyList()
                }
                // Sort: directories first, then files, alphabetical.
                val sorted = list.sortedWith(compareBy<GitHubApi.ContentEntry>
                    { if (it.type == "dir") 0 else 1 }
                    .thenBy { it.name.lowercase() })
                val newStack = if (path == s.currentPath) s.pathStack else buildPathStack(path)
                _state.update {
                    it.copy(
                        entries = sorted,
                        currentPath = path,
                        pathStack = newStack,
                        isLoading = false,
                    )
                }
                // Fire-and-forget: concurrently fetch the last commit for each visible entry.
                fetchLastCommits(sorted, s.owner, s.repo, s.ref)
            } catch (e: HttpException) {
                if (e.code() != 409) issueReporter.reportError("Code", "loadDir", e)
                // GitHub returns 409 ("Git Repository is empty") for the Contents
                // API root of a newly-created repository. It is a valid empty
                // directory, not an error placeholder.
                if (e.code() == 409) {
                    _state.update {
                        it.copy(
                            entries = emptyList(),
                            currentPath = path,
                            pathStack = buildPathStack(path),
                            isLoading = false,
                            error = null,
                            lastCommits = emptyMap(),
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, error = e.userMessage("Failed to list contents")) }
                }
            } catch (e: Exception) {
                issueReporter.reportError("Code", "loadDir", e)
                _state.update { it.copy(isLoading = false, error = e.userMessage("Failed to list contents")) }
            }
        }
    }

    /** Push one segment onto the path stack and list the resulting dir. */
    fun openDir(name: String) {
        val s = _state.value
        val newPath = if (s.currentPath.isBlank()) name else "${s.currentPath}/$name"
        listDir(newPath)
    }

    /** Pop one segment off the stack — returns to parent dir. */
    fun popDir(): Boolean {
        val s = _state.value
        if (s.currentPath.isBlank()) return false
        val parent = s.currentPath.substringBeforeLast('/', "")
        listDir(parent)
        return true
    }

    /** Open a single file: fetch its content (ContentEntry with base64) and show inline. */
    override fun openFile(entry: GitHubApi.ContentEntry) {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, viewingFile = entry, error = null) }
            try {
                val element = api.getContents(s.owner, s.repo, entry.path, s.ref)
                if (element is JsonObject) {
                    val fetched = json.decodeFromJsonElement<GitHubApi.ContentEntry>(element)
                    // GitHub omits base64 content for files >1MB (encoding stays
                    // "none") — fall back to the raw download URL instead of
                    // mislabeling the file as binary.
                    val decoded: String? = when {
                        com.pockethub.util.FileTypes.isKnownBinary(fetched.path) -> null
                        fetched.encoding == "base64" && fetched.content.isNotBlank() -> {
                            try {
                                Base64.decode(fetched.content.replace("\n", ""), Base64.DEFAULT)
                                    .toString(Charsets.UTF_8)
                            } catch (_: Exception) { null }
                        }
                        fetched.encoding != "base64" && fetched.downloadUrl != null -> {
                            // Large text file: fetch raw bytes directly.
                            runCatching {
                                val req = okhttp3.Request.Builder().url(fetched.downloadUrl!!).build()
                                okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                                    if (!resp.isSuccessful) null else resp.body?.string()
                                }
                            }.getOrNull()
                        }
                        else -> null
                    }
                    val binary = decoded == null ||
                        com.pockethub.util.FileTypes.looksBinary(decoded)
                    _state.update {
                        it.copy(isLoading = false, viewingFile = fetched, fileContent = if (binary) null else decoded)
                    }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                issueReporter.reportError("Code", "loadFile", e)
                _state.update { it.copy(isLoading = false, error = e.userMessage("Failed to load file")) }
            }
        }
    }

    fun closeFile() {
        _state.update { it.copy(viewingFile = null, fileContent = null) }
    }

    /**
     * Handle a system-back press inside the code browser.
     * Returns true when the press was consumed (closed file or popped a directory),
     * false when already at the repository root (caller should navigate back).
     */
    fun handleBack(): Boolean {
        val s = _state.value
        return when {
            s.viewingFile != null -> { closeFile(); true }
            s.currentPath.isNotBlank() -> { popDir(); true }
            else -> false
        }
    }

    /** Lazy-load the branch list (only fetched once per repo). */
    fun loadBranches() {
        val s = _state.value
        if (s.owner.isBlank() || s.branches.isNotEmpty() || s.isLoadingBranches) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingBranches = true) }
            try {
                // Fetch up to 100 branches — enough for the vast majority of repos.
                val branches = api.getBranches(s.owner, s.repo, perPage = 100)
                _state.update { it.copy(branches = branches, isLoadingBranches = false) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoadingBranches = false) }
            }
        }
    }

    /** Switch the browsed ref (branch) and reload the tree from its root. */
    fun switchRef(ref: String) {
        val s = _state.value
        if (s.ref == ref) return
        commitCache.clear() // ref changed → cached commit info is stale
        _state.update { it.copy(ref = ref, viewingFile = null, fileContent = null, lastCommits = emptyMap(), fullTree = emptyList()) }
        listDir("")
    }

    /**
     * Fetch the full recursive file tree once per ref (used by the full-screen
     * viewer's file-tree panel). Cached in [State.fullTree] until the ref changes.
     */
    override fun loadTree() {
        val s = _state.value
        if (s.fullTree.isNotEmpty() || s.isLoadingTree) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingTree = true) }
            try {
                val resp = api.getGitTree(s.owner, s.repo, s.ref ?: "HEAD")
                _state.update {
                    it.copy(
                        fullTree = resp.tree.filter { e -> e.type == "blob" || e.type == "tree" },
                        isLoadingTree = false,
                        treeTruncated = resp.truncated,
                    )
                }
            } catch (e: Exception) {
                issueReporter.reportError("Code", "loadTree", e)
                _state.update { it.copy(isLoadingTree = false) }
            }
        }
    }

    private fun buildPathStack(path: String): List<String> {
        if (path.isBlank()) return listOf("")
        val parts = path.split("/")
        val stack = mutableListOf("")
        var acc = ""
        parts.forEach { part ->
            acc = if (acc.isBlank()) part else "$acc/$part"
            stack.add(acc)
        }
        return stack
    }

    /**
     * Concurrently fetch the last commit for each entry (limited to 5 parallel requests).
     * Results land in [_state].lastCommits and [commitCache]. Failures silently produce
     * no entry — the UI just falls back to the size-only subtitle.
     */
    private fun fetchLastCommits(
        entries: List<GitHubApi.ContentEntry>,
        owner: String,
        repo: String,
        ref: String?,
    ) {
        if (entries.isEmpty()) return
        val cacheKey = { path: String -> "${owner}/${repo}@${ref ?: "HEAD"}::$path" }
        // Anything already cached is applied immediately; the rest gets fetched.
        val cached = entries.mapNotNull { e ->
            commitCache[cacheKey(e.path)]?.let { e.path to it }
        }.toMap()
        if (cached.isNotEmpty()) {
            _state.update { it.copy(lastCommits = it.lastCommits + cached) }
        }
        val toFetch = entries.filter { cacheKey(it.path) !in commitCache }
            // Cap protects the API rate limit on huge directories. Cached entries
            // are excluded first, so re-visiting a directory costs nothing and the
            // timestamps eventually cover the whole list across visits.
            .take(MAX_LAST_COMMIT_FETCH)
        if (toFetch.isEmpty()) return

        viewModelScope.launch {
            val sem = Semaphore(5)
            // Each completed request eagerly updates the UI so the user sees the
            // time appear progressively instead of waiting for every entry to finish.
            withContext(Dispatchers.IO) {
                coroutineScope {
                    toFetch.map { entry ->
                        async {
                            sem.withPermit {
                                val lc = runCatching {
                                    val commits = api.getCommits(owner, repo, perPage = 1, sha = ref, path = entry.path)
                                    val c = commits.firstOrNull()?.commit
                                    val date = c?.committer?.date ?: c?.author?.date
                                    date?.let { LastCommit(message = "", dateIso = it) }
                                }.getOrNull() ?: return@withPermit
                                commitCache[cacheKey(entry.path)] = lc
                                // Apply only if still on the same dir; otherwise cache wins next time.
                                val cur = _state.value
                                if (cur.owner == owner && cur.repo == repo && cur.ref == ref) {
                                    _state.update { it.copy(lastCommits = it.lastCommits + (entry.path to lc)) }
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }
}
