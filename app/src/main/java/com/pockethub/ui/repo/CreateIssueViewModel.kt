package com.pockethub.ui.repo

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val api: GitHubApi,
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

    fun loadTemplates(owner: String, repo: String) {
        if (_templates.value.isNotEmpty() || _isLoadingTemplates.value) return
        viewModelScope.launch {
            _isLoadingTemplates.update { true }
            try {
                val list = parseTemplates(owner, repo)
                _templates.update { list }
            } catch (_: Exception) {
                // Non-fatal — fall back to blank form
            } finally {
                _isLoadingTemplates.update { false }
            }
        }
    }

    private suspend fun parseTemplates(owner: String, repo: String): List<IssueTemplate> {
        val arr = runCatching {
            api.getContents(owner, repo, ".github/ISSUE_TEMPLATE")
        }.getOrNull() ?: return emptyList()
        // contents response for a directory is a JSON array
        val els = runCatching {
            kotlinx.serialization.json.Json.decodeFromJsonElement(
                kotlinx.serialization.builtins.ListSerializer(GitHubApi.ContentEntry.serializer()),
                arr,
            )
        }.getOrNull() ?: return emptyList()
        // Only file entries with .md/.yaml/.yml extensions are templates
        return els
            .filter { it.type == "file" && (it.name.endsWith(".md") || it.name.endsWith(".yml") || it.name.endsWith(".yaml")) }
            .filter { !it.name.equals("config.yml", ignoreCase = true) && !it.name.equals("config.yaml", ignoreCase = true) }
            .map { parseOne(owner, repo, it) }
    }

    private suspend fun parseOne(owner: String, repo: String, entry: GitHubApi.ContentEntry): IssueTemplate {
        // Fetch the full file (with encoded content)
        val one = runCatching {
            api.getContents(owner, repo, entry.path)
        }.getOrNull() ?: return IssueTemplate(entry.name, entry.name, "", "", emptyList(), emptyList(), "")
        val fileEntry = runCatching {
            kotlinx.serialization.json.Json.decodeFromJsonElement(GitHubApi.ContentEntry.serializer(), one)
        }.getOrNull() ?: return IssueTemplate(entry.name, entry.name, "", "", emptyList(), emptyList(), "")
        val raw = if (fileEntry.encoding == "base64" && fileEntry.content.isNotBlank()) {
            runCatching { Base64.decode(fileEntry.content, Base64.DEFAULT).toString(Charsets.UTF_8) }.getOrDefault("")
        } else fileEntry.content
        return parseFrontMatter(entry.name, raw)
    }

    private fun parseFrontMatter(fileName: String, raw: String): IssueTemplate {
        // Front matter is `---\n...\n---\n<rest>`. If absent, use the whole raw as body.
        if (!raw.startsWith("---")) {
            return IssueTemplate(fileName, fileName, "", "", emptyList(), emptyList(), raw)
        }
        val endIdx = raw.indexOf("\n---", 3)
        if (endIdx < 0) return IssueTemplate(fileName, fileName, "", "", emptyList(), emptyList(), raw)
        val fm = raw.substring(3, endIdx).trim()
        val body = raw.substring(endIdx + 4).trimStart('-', '\n')
        var name = ""
        var about = ""
        var title = ""
        val labels = mutableListOf<String>()
        val assigns = mutableListOf<String>()
        // Very small YAML-front-matter parser: line-by-line `key: value` for scalars;
        // `labels:` / `assignees:` are simple `["a","b"]` arrays.
        fm.split("\n").forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val colon = trimmed.indexOf(':')
            if (colon < 0) return@forEach
            val key = trimmed.substring(0, colon).trim()
            val value = trimmed.substring(colon + 1).trim()
            when (key) {
                "name" -> name = value
                "about", "description" -> about = value
                "title" -> title = value
                "labels" -> labels.addAll(parseYamlStringList(value))
                "assignees", "assigns" -> assigns.addAll(parseYamlStringList(value))
            }
        }
        return IssueTemplate(
            fileName = fileName,
            name = name.ifEmpty { fileName },
            about = about,
            title = title,
            labels = labels,
            assigns = assigns,
            body = body,
        )
    }

    private fun parseYamlStringList(value: String): List<String> {
        // Accepts `["a", "b"]`, `a,b`, `a`, or empty
        if (value.isEmpty()) return emptyList()
        if (value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length - 1)
                .split(",")
                .map { it.trim().trim('"', '\'') }
                .filter { it.isNotEmpty() }
        }
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun selectTemplate(t: IssueTemplate?) { _selectedTemplate.update { t } }

    fun createIssue(owner: String, repo: String, title: String, body: String?) {
        if (_isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _actionError.value = null
            try {
                val issue = api.createIssue(
                    owner, repo,
                    GitHubApi.IssueCreateRequest(title = title, body = body?.takeIf { it.isNotBlank() }),
                )
                _result.value = Result.success(issue)
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Failed to create"
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
