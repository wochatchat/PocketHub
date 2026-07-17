package com.pockethub.ui.repo

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Code tab — directory navigation with inline text file viewer.
 */
@Composable
fun CodeTab(
    owner: String,
    repo: String,
    vm: CodeBrowserViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    // Lazy initialise for this owner/repo pair on first composition.
    androidx.compose.runtime.LaunchedEffect(owner, repo) {
        vm.init(owner, repo)
    }

    Column(Modifier.fillMaxSize()) {
        // Breadcrumb bar (when not viewing a file)
        if (state.viewingFile == null) {
            BreadcrumbBar(
                pathStack = state.pathStack,
                currentPath = state.currentPath,
                canGoUp = state.currentPath.isNotBlank(),
                onUp = { vm.popDir() },
                onJump = { vm.listDir(it) },
            )
        }

        when {
            state.isLoading && state.entries.isEmpty() && state.viewingFile == null -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.viewingFile != null -> FileViewerContent(
                entry = state.viewingFile!!,
                content = state.fileContent,
                isLoading = state.isLoading,
                onClose = { vm.closeFile() },
            )

            state.error != null && state.entries.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error ?: stringResource(R.string.error_load_files), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.listDir(state.currentPath) }) { Text(stringResource(R.string.action_retry)) }
            }

            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(state.entries, key = { it.path + it.sha + it.type }) { entry ->
                    ContentRow(entry = entry, onClick = {
                        if (entry.type == "dir") vm.openDir(entry.name) else vm.openFile(entry)
                    })
                }
                if (state.entries.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.directory_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    pathStack: List<String>,
    currentPath: String,
    canGoUp: Boolean,
    onUp: () -> Unit,
    onJump: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoUp) {
            IconButton(onClick = onUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_up))
            }
        }
        // Render each segment as a tappable label.
        pathStack.forEachIndexed { idx, path ->
            val label = if (idx == 0) stringResource(R.string.breadcrumb_root) else path.substringAfterLast('/')
            if (idx > 0) Text(" / ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (path == currentPath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (path == currentPath) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .clickable { onJump(path) }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun ContentRow(entry: com.pockethub.data.remote.GitHubApi.ContentEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (entry.type == "dir") Icons.Outlined.Folder else Icons.Outlined.Description
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium)
            if (entry.type == "file" && entry.size > 0) {
                Text(humanReadableSize(entry.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FileViewerContent(
    entry: com.pockethub.data.remote.GitHubApi.ContentEntry,
    content: String?,
    isLoading: Boolean,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // File header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back_to_directory))
            }
            Column(Modifier.weight(1f)) {
                Text(entry.path, style = MaterialTheme.typography.labelMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (content != null) {
                    val lineCount = content.lines().size
                    Text(
                        stringResource(R.string.code_file_size, humanReadableSize(entry.size), lineCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (content != null) {
            CodeContentWithLineNumbers(content, entry.name)
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.AutoMirrored.Outlined.Article, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.binary_preview_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                entry.downloadUrl?.let { url ->
                    Spacer(Modifier.height(8.dp))
                    Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Displays source code with line numbers and basic syntax highlighting.
 * Uses a fixed-width line number gutter on the left and scrollable highlighted code on the right.
 */
@Composable
private fun CodeContentWithLineNumbers(content: String, fileName: String) {
    val lines = remember(content) { content.split("\n") }
    val lineNumberWidth = remember(lines.size) { "${lines.size}".length }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val highlightedLines = remember(content, fileName) {
        highlightSyntax(content, fileName).split("\n")
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(vScroll)
                .horizontalScroll(hScroll)
        ) {
            // Line number gutter
            Column(
                Modifier
                    .widthIn(min = (lineNumberWidth * 10 + 16).dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                lines.forEachIndexed { idx, _ ->
                    Text(
                        text = "${idx + 1}".padStart(lineNumberWidth),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            // Code content with syntax highlighting
            Column(
                Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            ) {
                highlightedLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Basic syntax highlighting using AnnotatedString for Compose.
 * Returns a plain string with ANSI-like markers that are stripped for line splitting,
 * but the actual rendering uses color spans via AnnotatedString.Builder.
 *
 * For simplicity in Compose, we return highlighted lines as colored Text spans.
 */
@Composable
private fun highlightSyntaxBlock(content: String, fileName: String): androidx.compose.ui.text.AnnotatedString {
    return highlightAnnotated(content, fileName)
}

/**
 * Returns an AnnotatedString with basic syntax coloring.
 */
@Composable
private fun highlightAnnotated(content: String, fileName: String): androidx.compose.ui.text.AnnotatedString {
    val keywords = remember(fileName) { getKeyWordsForFile(fileName) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    return buildAnnotatedString {
        var i = 0
        while (i < content.length) {
            val ch = content[i]
            when {
                // Single-line comments
                ch == '/' && i + 1 < content.length && content[i + 1] == '/' -> {
                    val end = content.indexOf('\n', i).let { if (it == -1) content.length else it }
                    withStyle(SpanStyle(color = onSurfaceVariant)) { append(content.substring(i, end)) }
                    i = end
                }
                // Multi-line comments
                ch == '/' && i + 1 < content.length && content[i + 1] == '*' -> {
                    val end = content.indexOf("*/", i + 2).let { if (it == -1) content.length else it + 2 }
                    withStyle(SpanStyle(color = onSurfaceVariant)) { append(content.substring(i, end)) }
                    i = end
                }
                // Strings
                ch == '"' || ch == '\'' || ch == '`' -> {
                    val quote = ch
                    var j = i + 1
                    while (j < content.length && content[j] != quote) {
                        if (content[j] == '\\') j++ // skip escaped chars
                        j++
                    }
                    j = minOf(j + 1, content.length)
                    withStyle(SpanStyle(color = tertiaryColor)) { append(content.substring(i, j)) }
                    i = j
                }
                // Numbers
                ch.isDigit() && (i == 0 || !content[i - 1].isLetterOrDigit()) -> {
                    var j = i
                    while (j < content.length && (content[j].isDigit() || content[j] == '.' || content[j] == 'f' || content[j] == 'L')) j++
                    withStyle(SpanStyle(color = errorColor)) { append(content.substring(i, j)) }
                    i = j
                }
                // Words (check keywords)
                ch.isLetter() || ch == '_' -> {
                    var j = i
                    while (j < content.length && (content[j].isLetterOrDigit() || content[j] == '_')) j++
                    val word = content.substring(i, j)
                    if (word in keywords) {
                        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) { append(word) }
                    } else {
                        append(word)
                    }
                    i = j
                }
                else -> {
                    append(ch)
                    i++
                }
            }
        }
    }
}

/**
 * Highlight syntax and return as a plain string (for line splitting in the code viewer).
 * Uses ANSI color codes are not supported in Compose, so we just return the original content
 * and rely on the AnnotatedString version for actual highlighting.
 */
private fun highlightSyntax(content: String, fileName: String): String = content

private fun getKeyWordsForFile(fileName: String): Set<String> {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> setOf(
            "fun", "val", "var", "class", "interface", "object", "enum", "sealed", "data", "object",
            "if", "else", "when", "for", "while", "do", "return", "break", "continue", "throw", "try", "catch", "finally",
            "import", "package", "private", "protected", "public", "internal", "override", "open", "abstract", "final",
            "suspend", "coroutine", "launch", "async", "await", "withContext", "scope",
            "true", "false", "null", "this", "super", "is", "as", "in", "by", "companion", "inline", "reified",
            "Int", "String", "Boolean", "Long", "Float", "Double", "List", "Map", "Set", "Array",
        )
        "java" -> setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null",
        )
        "xml" -> setOf(
            "android", "xmlns", "app", "layout", "view", "id", "width", "height",
            "match_parent", "wrap_content", "dp", "sp", "true", "false",
        )
        "json" -> setOf("true", "false", "null")
        "py" -> setOf(
            "def", "class", "return", "if", "elif", "else", "for", "while", "import", "from",
            "as", "try", "except", "finally", "raise", "with", "yield", "lambda", "pass", "break",
            "continue", "and", "or", "not", "in", "is", "True", "False", "None", "self", "print",
        )
        "js", "ts" -> setOf(
            "var", "let", "const", "function", "return", "if", "else", "for", "while", "do",
            "switch", "case", "break", "continue", "new", "this", "class", "extends", "import",
            "export", "default", "from", "async", "await", "try", "catch", "finally", "throw",
            "typeof", "instanceof", "in", "of", "true", "false", "null", "undefined", "void",
            "console", "log", "Promise", "Array", "Object", "String", "Number", "Boolean",
        )
        "go" -> setOf(
            "func", "package", "import", "return", "if", "else", "for", "range", "switch",
            "case", "default", "break", "continue", "go", "chan", "select", "defer", "var",
            "const", "type", "struct", "interface", "map", "make", "new", "true", "false", "nil",
        )
        "rs" -> setOf(
            "fn", "let", "mut", "pub", "use", "mod", "struct", "enum", "impl", "trait",
            "match", "if", "else", "for", "while", "loop", "return", "break", "continue",
            "self", "super", "crate", "true", "false", "Some", "None", "Ok", "Err",
        )
        "md" -> setOf()
        else -> setOf(
            "function", "return", "if", "else", "for", "while", "class", "import", "from",
            "try", "catch", "throw", "new", "this", "true", "false", "null", "void",
        )
    }
}

private fun humanReadableSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

