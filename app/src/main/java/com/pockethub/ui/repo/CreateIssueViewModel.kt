package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import com.pockethub.util.userMessage
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.pockethub.data.model.Issue
import com.pockethub.data.model.User
import com.pockethub.data.remote.AttachmentUploader
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

/** A parsed GitHub issue template (an `.md` or `.yml` file under `.github/ISSUE_TEMPLATE`). */
data class IssueTemplate(
    val fileName: String,
    val name: String,
    val about: String,
    val title: String,
    val labels: List<String>,
    val assigns: List<String>,
    /** Markdown body to prefill in the editor (front-matter stripped). */
    val body: String,
)
@HiltViewModel
class CreateIssueViewModel @Inject constructor(
    private val issueReporter: com.pockethub.data.reporting.IssueReporter,
    private val api: GitHubApi,
    attachmentUploader: AttachmentUploader,
    @ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _result = MutableStateFlow<Result<Issue>?>(null)
    val result: StateFlow<Result<Issue>?> = _result

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _templates = MutableStateFlow<List<IssueTemplate>>(emptyList())
    val templates: StateFlow<List<IssueTemplate>> = _templates.asStateFlow()

    private val _isLoadingTemplates = MutableStateFlow(false)
    val isLoadingTemplates: StateFlow<Boolean> = _isLoadingTemplates.asStateFlow()

    /** Selected template — null means "blank issue". */
    private val _selectedTemplate = MutableStateFlow<IssueTemplate?>(null)
    val selectedTemplate: StateFlow<IssueTemplate?> = _selectedTemplate.asStateFlow()

    // ── YAML issue-form support (new .yml templates) ────────────────────

    /** Parsed `.yml` form templates, shown in the chooser alongside legacy ones. */
    private val _forms = MutableStateFlow<List<IssueForm>>(emptyList())
    val forms: StateFlow<List<IssueForm>> = _forms.asStateFlow()

    /** The selected YAML form — null when a legacy template or blank issue is active. */
    private val _selectedForm = MutableStateFlow<IssueForm?>(null)
    val selectedForm: StateFlow<IssueForm?> = _selectedForm.asStateFlow()

    /** Answers for the selected form, keyed by field index. */
    private val _formAnswers = MutableStateFlow<Map<Int, IssueFormAnswer>>(emptyMap())
    val formAnswers: StateFlow<Map<Int, IssueFormAnswer>> = _formAnswers.asStateFlow()

    /** True once the user explicitly picked "Blank issue" (fixes chooser reopening). */
    private val _blankSelected = MutableStateFlow(false)
    val blankSelected: StateFlow<Boolean> = _blankSelected.asStateFlow()

    /** contact_links from config.yml (external links shown in the chooser). */
    private val _contactLinks = MutableStateFlow<List<IssueContactLink>>(emptyList())
    val contactLinks: StateFlow<List<IssueContactLink>> = _contactLinks.asStateFlow()

    /** Set once loading succeeded but no templates exist anywhere — skip chooser, go straight to editor. */
    private val _noTemplatesFound = MutableStateFlow(false)
    val noTemplatesFound: StateFlow<Boolean> = _noTemplatesFound.asStateFlow()

    /** Editor state — labels selected for the new issue. Prefilled from template front-matter. */
    private val _labels = MutableStateFlow<List<String>>(emptyList())
    val labels: StateFlow<List<String>> = _labels.asStateFlow()

    /** Editor state — assignees selected for the new issue. Prefilled from template front-matter. */
    private val _assignees = MutableStateFlow<List<String>>(emptyList())
    val assignees: StateFlow<List<String>> = _assignees.asStateFlow()

    // ── Picker metadata: preset labels, assignable users, push access ───
    // GitHub silently drops labels/assignees from users without push
    // access, and auto-creates unknown label names in the repo — so the
    // editors only offer the repo's real presets, and only for maintainers.

    private val _repoLabels = MutableStateFlow<List<Issue.Label>>(emptyList())
    val repoLabels: StateFlow<List<Issue.Label>> = _repoLabels.asStateFlow()

    private val _assignableUsers = MutableStateFlow<List<User>>(emptyList())
    val assignableUsers: StateFlow<List<User>> = _assignableUsers.asStateFlow()

    /** True when the viewer has push access — gates the label/assignee pickers. */
    private val _canSetMetadata = MutableStateFlow(false)
    val canSetMetadata: StateFlow<Boolean> = _canSetMetadata.asStateFlow()

    private var metadataLoaded = false

    /**
     * Best-effort fetch of the picker data: repo permission, preset labels
     * and assignable users. Everything is non-fatal — on failure the
     * pickers simply stay hidden (or empty) and issue creation still works.
     * [force] re-runs after a failed first attempt (template retry).
     */
    fun loadMetadata(owner: String, repo: String, force: Boolean = false) {
        if (metadataLoaded && !force) return
        metadataLoaded = true
        viewModelScope.launch {
            val info = runCatching { api.getRepository(owner, repo) }.getOrNull()
            _canSetMetadata.value = info?.permissions?.push == true
            if (!_canSetMetadata.value) return@launch
            launch {
                runCatching { api.getRepositoryLabels(owner, repo) }
                    .onSuccess { _repoLabels.value = it }
            }
            launch {
                runCatching { api.getRepositoryAssignees(owner, repo) }
                    .onSuccess { _assignableUsers.value = it }
            }
        }
    }

    private val _templatesFailed = MutableStateFlow(false)

    /** True when template loading failed (network/API error) — chooser shows a retry. */
    val templatesFailed: StateFlow<Boolean> = _templatesFailed.asStateFlow()

    fun loadTemplates(owner: String, repo: String) {
        if (_templates.value.isNotEmpty() || _forms.value.isNotEmpty() || _isLoadingTemplates.value) return
        viewModelScope.launch {
            _isLoadingTemplates.update { true }
            _templatesFailed.update { false }
            try {
                val result = parseTemplateDir(owner, repo)
                _forms.update { result.forms }
                _templates.update { result.legacyTemplates }
                _contactLinks.update { result.contactLinks }
                // Nothing found at all — remember so the editor opens directly (not an error)
                if (result.forms.isEmpty() && result.legacyTemplates.isEmpty()) {
                    _noTemplatesFound.update { true }
                }
            } catch (e: Exception) {
                issueReporter.reportError("CreateIssue", "loadTemplates", e)
                _templatesFailed.update { true }
            } finally {
                _isLoadingTemplates.update { false }
            }
        }
    }

    /**
     * Fetch and classify every entry under `.github/ISSUE_TEMPLATE` (falling back to
     * the repo root for legacy top-level `ISSUE_TEMPLATE*.md`):
     *  - `config.yml` → contact links
     *  - `.yml`/`.yaml` → structured issue forms
     *  - `.md` → legacy free-text templates
     */
    /** Lenient JSON decoder — GitHub contents responses carry many extra fields. */
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun parseTemplateDir(owner: String, repo: String): IssueFormParser.Result {
        val entries = listTemplateEntries(owner, repo)

        var legacy = emptyList<IssueTemplate>()
        var forms = emptyList<IssueForm>()
        var contactLinks = emptyList<IssueContactLink>()
        entries.forEach { entry ->
            val raw = fetchRaw(owner, repo, entry.path)
            when {
                entry.name.equals("config.yml", true) || entry.name.equals("config.yaml", true) -> {
                    if (raw.isNotEmpty()) contactLinks = IssueFormParser.parseConfigYaml(raw).second
                }
                entry.name.endsWith(".yml", true) || entry.name.endsWith(".yaml", true) ->
                    IssueFormParser.parseFormYaml(raw)?.let { forms += it }
                entry.name.endsWith(".md", true) ->
                    legacy += IssueFormParser.parseLegacy(entry.name, raw)
            }
        }
        return IssueFormParser.Result(forms, legacy, contactLinks)
    }

    /**
     * Enumerate candidate template files across all three layouts GitHub supports:
     *  1. `.github/ISSUE_TEMPLATE/` directory (forms + legacy md + config.yml)
     *  2. `.github/ISSUE_TEMPLATE.md` single file
     *  3. top-level `ISSUE_TEMPLATE*.md` (oldest repos)
     */
    private suspend fun listTemplateEntries(owner: String, repo: String): List<GitHubApi.ContentEntry> {
        val dir = decodeDirectory(runCatching {
            api.getContents(owner, repo, ".github/ISSUE_TEMPLATE")
        }.getOrNull())
        if (dir.isNotEmpty()) {
            return dir.filter { it.type == "file" }
        }
        // Layout 2: single .github/ISSUE_TEMPLATE.md — probe it directly
        runCatching { api.getContents(owner, repo, ".github/ISSUE_TEMPLATE.md") }.getOrNull()?.let { el ->
            decodeFile(el)?.let { return listOf(it) }
        }
        // Layout 3: top-level ISSUE_TEMPLATE*.md
        val root = decodeDirectory(runCatching { api.getRootContents(owner, repo) }.getOrNull())
        return root.filter {
            it.type == "file" &&
                it.name.startsWith("ISSUE_TEMPLATE", ignoreCase = true) &&
                it.name.endsWith(".md", ignoreCase = true)
        }
    }

    /** Decode a single-file contents response; null when the element is a directory listing. */
    private fun decodeFile(el: JsonElement): GitHubApi.ContentEntry? =
        runCatching {
            json.decodeFromJsonElement(GitHubApi.ContentEntry.serializer(), el)
        }.getOrNull()

    private fun decodeDirectory(el: JsonElement?): List<GitHubApi.ContentEntry> {
        if (el == null) return emptyList()
        return runCatching {
            json.decodeFromJsonElement(
                ListSerializer(GitHubApi.ContentEntry.serializer()),
                el,
            )
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchRaw(owner: String, repo: String, path: String): String {
        val one = runCatching { api.getContents(owner, repo, path) }.getOrNull() ?: return ""
        val fileEntry = decodeFile(one) ?: return ""
        return IssueFormParser.decodeContent(fileEntry.content, fileEntry.encoding)
    }

    fun selectTemplate(t: IssueTemplate?) {
        // t == null only clears the selection (back to chooser); use [chooseBlank] for blank issues
        _selectedTemplate.update { t }
        _selectedForm.update { null }
        if (t == null) _blankSelected.update { false }
        _labels.update { t?.labels.orEmpty() }
        _assignees.update { t?.assigns.orEmpty() }
        _formAnswers.update { emptyMap() }
    }

    /** User explicitly chose "Blank issue" — show the plain editor. */
    fun chooseBlank() {
        _selectedForm.update { null }
        _selectedTemplate.update { null }
        _blankSelected.update { true }
        _labels.update { emptyList() }
        _assignees.update { emptyList() }
        _formAnswers.update { emptyMap() }
    }

    /** Return from any editor state to the template chooser. */
    fun backToChooser() {
        _selectedForm.update { null }
        _selectedTemplate.update { null }
        _blankSelected.update { false }
    }

    /** Retry after a failed load (clears the failure flag first so the guard passes). */
    fun retryTemplates(owner: String, repo: String) {
        _templatesFailed.update { false }
        loadTemplates(owner, repo)
    }

    /** Select a parsed YAML issue form — the UI switches to the native form renderer. */
    fun selectForm(form: IssueForm?) {
        _selectedForm.update { form }
        _selectedTemplate.update { null }
        _blankSelected.update { false }
        _labels.update { form?.labels.orEmpty() }
        _assignees.update { form?.assignees.orEmpty() }
        // Pre-seed answers with template default values so they're editable and included on submit
        val seed = mutableMapOf<Int, IssueFormAnswer>()
        form?.fields?.forEach { f ->
            if (f is IssueFormField.TextInput && !f.defaultValue.isNullOrBlank()) {
                seed[f.index] = IssueFormAnswer(text = f.defaultValue)
            }
        }
        _formAnswers.update { seed }
    }

    /** Update one field's answer (text input / checkbox / dropdown all funnel through here). */
    fun updateAnswer(index: Int, answer: IssueFormAnswer) {
        _formAnswers.update { it + (index to answer) }
    }

    private val _validationError = MutableStateFlow<String?>(null)

    /** First unanswered required prompt (label text) when submitting an incomplete form. */
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    fun clearValidationError() {
        _validationError.value = null
    }

    /**
     * Validate + submit a YAML form: builds GitHub-style markdown from the answers.
     * [titleOverride] is the title edited on screen (may fall back to the template preset).
     */
    fun submitForm(owner: String, repo: String, titleOverride: String? = null) {
        val form = _selectedForm.value ?: return
        val missing = form.firstMissingRequired(_formAnswers.value)
        if (missing != null) {
            _validationError.update { missing }
            return
        }
        val title = titleOverride?.takeIf { it.isNotBlank() } ?: effectiveTitle()
        createIssue(owner, repo, title, IssueFormParser.buildBody(form, _formAnswers.value))
    }

    private fun effectiveTitle(): String {
        val form = _selectedForm.value ?: return ""
        return form.title.ifBlank {
            // No title preset in the template: use the first short text answer as a hint
            _formAnswers.value.entries.sortedBy { it.key }
                .firstOrNull { (i, a) ->
                    (form.fields.firstOrNull { it.index == i } as? IssueFormField.TextInput)?.multiline == false && a.text.isNotBlank()
                }?.value?.text.orEmpty()
                .take(80)
        }
    }

    /** Toggle a preset label in the selection (case-insensitive). */
    fun toggleLabel(name: String) {
        _labels.update { list ->
            if (list.any { it.equals(name, ignoreCase = true) }) {
                list.filterNot { it.equals(name, ignoreCase = true) }
            } else {
                list + name
            }
        }
    }

    /** Toggle an assignable user in the selection (case-insensitive). */
    fun toggleAssignee(login: String) {
        _assignees.update { list ->
            if (list.any { it.equals(login, ignoreCase = true) }) {
                list.filterNot { it.equals(login, ignoreCase = true) }
            } else {
                list + login
            }
        }
    }

    // ── Attachments ──────────────────────────────────────────────────────
    // Images upload to the self-hosted CF worker (pockethub-issue, see
    // AttachmentUploader) at submit time; markdown is appended to the body.
    // Files are not supported yet — the UI intercepts file picks.

    private val attachmentState = AttachmentState(appContext, attachmentUploader) { sizeBytes ->
        _actionError.value = formatTooLarge(appContext, sizeBytes)
    }

    val attachments: StateFlow<List<IssueAttachment>> = attachmentState.attachments

    fun addAttachment(uri: Uri) = attachmentState.add(uri)

    fun removeAttachment(id: Long) = attachmentState.remove(id)

    fun createIssue(owner: String, repo: String, title: String, body: String?) {
        if (_isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _actionError.value = null
            try {
                val attachmentBlock = attachmentState.uploadAll()
                val fullBody = listOfNotNull(
                    body?.takeIf { it.isNotBlank() },
                    attachmentBlock?.takeIf { it.isNotBlank() },
                ).joinToString("\n\n")
                // Defense in depth: only send labels/assignees GitHub will
                // honor. Unknown label names get auto-created in the repo
                // (polluting it), and non-assignable logins are silently
                // dropped — drop both here regardless of what the editors
                // collected (also guards template front-matter).
                val knownLabels = _repoLabels.value.map { it.name }
                val knownUsers = _assignableUsers.value.map { it.login }
                val labels = _labels.value.filter { l -> knownLabels.any { it.equals(l, ignoreCase = true) } }
                val assignees = _assignees.value.filter { a -> knownUsers.any { it.equals(a, ignoreCase = true) } }
                val issue = api.createIssue(
                    owner, repo,
                    GitHubApi.IssueCreateRequest(
                        title = title,
                        body = fullBody.takeIf { it.isNotBlank() },
                        labels = labels,
                        assignees = assignees,
                    ),
                )
                _result.value = Result.success(issue)
            } catch (e: Exception) {
                issueReporter.reportError("CreateIssue", "createIssue", e)
                if (e is kotlinx.coroutines.CancellationException) throw e
                _actionError.value = when (e) {
                    is AttachmentUploader.StorageFullException ->
                        appContext.getString(com.pockethub.R.string.attachment_storage_full)
                    is AttachmentUploader.ImageTooLargeException ->
                        formatTooLarge(appContext, e.sizeBytes)
                    is AttachmentUploader.UploadException ->
                        appContext.getString(com.pockethub.R.string.attachment_upload_failed, e.fileName)
                    else -> e.userMessage("Failed to create")
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    fun clearResult() {
        _result.value = null
    }

    fun clearActionError() {
        _actionError.value = null
    }
}
