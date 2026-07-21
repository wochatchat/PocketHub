package com.pockethub.ui.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Lightweight, dependency-free syntax highlighter for the inline file viewer.
 *
 * Not a full parser — a pragmatic tokenizer covering the common cases for the
 * languages GitHub users browse most (Kotlin/Java, Python, JS/TS, Rust, Go, C/C++,
 * JSON, XML/HTML, YAML, Shell, Markdown). Unknown extensions render plain.
 */
object CodeHighlighter {

    data class Palette(
        val keyword: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val annotation: Color,
    )

    /** Infer a language tag from a file name's extension. */
    fun languageFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js", "jsx", "mjs", "cjs" -> "javascript"
            "ts", "tsx" -> "typescript"
            "rs" -> "rust"
            "go" -> "go"
            "c", "h" -> "c"
            "cpp", "cc", "cxx", "hpp" -> "cpp"
            "cs" -> "csharp"
            "json" -> "json"
            "xml", "html", "htm", "svg" -> "xml"
            "yml", "yaml" -> "yaml"
            "sh", "bash", "zsh" -> "shell"
            "md" -> "markdown"
            "rb" -> "ruby"
            "php" -> "php"
            "swift" -> "swift"
            "sql" -> "sql"
            "gradle", "toml", "ini", "properties", "cfg" -> "properties"
            else -> ""
        }
    }

    @Composable
    fun highlight(code: String, fileName: String): AnnotatedString {
        val scheme = MaterialTheme.colorScheme
        val palette = rememberPalette(scheme)
        return highlight(code, fileName, palette)
    }

    /**
     * Non-composable overload — safe to call inside `remember { }`. Pass a [Palette]
     * captured from the current `MaterialTheme.colorScheme`.
     */
    fun highlight(code: String, fileName: String, palette: Palette): AnnotatedString {
        val lang = languageFor(fileName)
        if (lang.isEmpty() || code.isEmpty()) return AnnotatedString(code)
        return runCatching { tokenize(code, lang, palette) }.getOrElse { AnnotatedString(code) }
    }

    @Composable
    private fun rememberPalette(scheme: androidx.compose.material3.ColorScheme): Palette {
        return androidx.compose.runtime.remember(scheme) {
            Palette(
                keyword = scheme.primary,
                string = scheme.tertiary,
                comment = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                number = scheme.secondary,
                annotation = scheme.primary,
            )
        }
    }

    private val KEYWORDS: Map<String, Set<String>> = mapOf(
        "kotlin" to setOf(
            "package", "import", "class", "interface", "object", "fun", "val", "var", "return",
            "if", "else", "when", "for", "while", "do", "try", "catch", "finally", "throw",
            "is", "in", "as", "this", "super", "null", "true", "false", "suspend", "private",
            "public", "internal", "protected", "override", "abstract", "open", "data", "sealed",
            "enum", "companion", "init", "constructor", "by", "lazy", "lateinit", "vararg",
            "inline", "reified", "out", "typealias", "it", "break", "continue", "typeof",
        ),
        "java" to setOf(
            "package", "import", "class", "interface", "enum", "extends", "implements", "void",
            "int", "long", "double", "float", "boolean", "char", "byte", "short", "new", "return",
            "if", "else", "switch", "case", "default", "for", "while", "do", "try", "catch",
            "finally", "throw", "throws", "instanceof", "this", "super", "null", "true", "false",
            "public", "private", "protected", "static", "final", "abstract", "synchronized",
            "volatile", "transient", "native", "record", "sealed", "var", "break", "continue",
        ),
        "python" to setOf(
            "def", "class", "import", "from", "as", "return", "if", "elif", "else", "for",
            "while", "try", "except", "finally", "raise", "with", "lambda", "pass", "break",
            "continue", "and", "or", "not", "is", "in", "None", "True", "False", "global",
            "nonlocal", "yield", "async", "await", "assert", "del", "print", "self",
        ),
        "javascript" to setOf(
            "function", "const", "let", "var", "return", "if", "else", "for", "while", "do",
            "switch", "case", "default", "break", "continue", "try", "catch", "finally", "throw",
            "new", "delete", "typeof", "instanceof", "in", "of", "class", "extends", "super",
            "this", "null", "undefined", "true", "false", "async", "await", "yield", "import",
            "export", "from", "static", "get", "set", "void",
        ),
        "typescript" to setOf(
            "function", "const", "let", "var", "return", "if", "else", "for", "while", "do",
            "switch", "case", "default", "break", "continue", "try", "catch", "finally", "throw",
            "new", "delete", "typeof", "instanceof", "in", "of", "class", "extends", "super",
            "this", "null", "undefined", "true", "false", "async", "await", "yield", "import",
            "export", "from", "static", "get", "set", "void", "interface", "type", "enum",
            "implements", "public", "private", "protected", "readonly", "abstract", "namespace",
            "declare", "keyof", "never", "unknown", "any", "string", "number", "boolean", "as",
        ),
        "rust" to setOf(
            "fn", "let", "mut", "const", "static", "struct", "enum", "impl", "trait", "for",
            "in", "while", "loop", "if", "else", "match", "return", "break", "continue", "pub",
            "use", "mod", "crate", "self", "Self", "super", "where", "async", "await", "move",
            "ref", "type", "dyn", "unsafe", "extern", "true", "false", "Some", "None", "Ok", "Err",
        ),
        "go" to setOf(
            "package", "import", "func", "var", "const", "type", "struct", "interface", "map",
            "chan", "go", "defer", "return", "if", "else", "for", "range", "switch", "case",
            "default", "break", "continue", "fallthrough", "select", "nil", "true", "false",
        ),
        "c" to setOf(
            "int", "long", "double", "float", "char", "void", "unsigned", "signed", "short",
            "struct", "union", "enum", "typedef", "static", "const", "volatile", "extern",
            "register", "if", "else", "switch", "case", "default", "for", "while", "do", "return",
            "break", "continue", "goto", "sizeof", "NULL",
        ),
        "cpp" to setOf(
            "int", "long", "double", "float", "char", "void", "unsigned", "signed", "short",
            "struct", "union", "enum", "typedef", "static", "const", "volatile", "extern",
            "if", "else", "switch", "case", "default", "for", "while", "do", "return", "break",
            "continue", "sizeof", "nullptr", "class", "namespace", "template", "typename",
            "public", "private", "protected", "virtual", "override", "new", "delete", "this",
            "true", "false", "auto", "using", "try", "catch", "throw", "noexcept", "constexpr",
        ),
        "csharp" to setOf(
            "using", "namespace", "class", "interface", "struct", "enum", "public", "private",
            "protected", "internal", "static", "readonly", "const", "var", "void", "int", "long",
            "double", "float", "bool", "string", "char", "new", "return", "if", "else", "switch",
            "case", "default", "for", "foreach", "while", "do", "try", "catch", "finally",
            "throw", "null", "true", "false", "this", "base", "is", "as", "in", "out", "ref",
            "async", "await", "override", "virtual", "abstract", "sealed", "break", "continue",
        ),
        "ruby" to setOf(
            "def", "end", "class", "module", "require", "include", "extend", "attr_accessor",
            "attr_reader", "attr_writer", "if", "elsif", "else", "unless", "while", "until",
            "for", "do", "begin", "rescue", "ensure", "raise", "return", "yield", "block_given?",
            "nil", "true", "false", "self", "super", "and", "or", "not", "in", "then", "case",
            "when", "lambda", "proc", "puts", "new", "break", "next",
        ),
        "php" to setOf(
            "function", "class", "interface", "trait", "extends", "implements", "public",
            "private", "protected", "static", "const", "var", "new", "return", "if", "elseif",
            "else", "switch", "case", "default", "for", "foreach", "while", "do", "try", "catch",
            "finally", "throw", "null", "true", "false", "echo", "print", "require", "include",
            "namespace", "use", "as", "break", "continue", "fn",
        ),
        "swift" to setOf(
            "func", "var", "let", "class", "struct", "enum", "protocol", "extension", "import",
            "return", "if", "else", "guard", "switch", "case", "default", "for", "while",
            "repeat", "do", "try", "catch", "throw", "throws", "nil", "true", "false", "self",
            "super", "init", "deinit", "public", "private", "internal", "fileprivate", "open",
            "static", "final", "override", "mutating", "lazy", "weak", "unowned", "async",
            "await", "some", "any", "in", "is", "as", "break", "continue",
        ),
        "sql" to setOf(
            "SELECT", "select", "FROM", "from", "WHERE", "where", "INSERT", "insert", "INTO",
            "into", "VALUES", "values", "UPDATE", "update", "SET", "set", "DELETE", "delete",
            "CREATE", "create", "TABLE", "table", "DROP", "drop", "ALTER", "alter", "JOIN",
            "join", "LEFT", "left", "RIGHT", "right", "INNER", "inner", "OUTER", "outer", "ON",
            "on", "AND", "and", "OR", "or", "NOT", "not", "NULL", "null", "ORDER", "order",
            "BY", "by", "GROUP", "group", "HAVING", "having", "LIMIT", "limit", "OFFSET",
            "offset", "AS", "as", "DISTINCT", "distinct", "UNION", "union", "PRIMARY", "primary",
            "KEY", "key", "FOREIGN", "foreign", "REFERENCES", "references", "INDEX", "index",
        ),
    )

    /** Extensions to the keyword map for languages that alias another's set. */
    private fun keywordsFor(lang: String): Set<String> = when (lang) {
        "markdown" -> emptySet()
        else -> KEYWORDS[lang].orEmpty()
    }

    private fun tokenize(code: String, lang: String, palette: Palette): AnnotatedString {
        // Line-oriented languages first (simpler & cheaper).
        when (lang) {
            "json" -> return tokenizeJson(code, palette)
            "xml" -> return tokenizeXml(code, palette)
            "yaml" -> return tokenizeYaml(code, palette)
            "shell" -> return tokenizeShell(code, palette)
            "markdown" -> return tokenizeMarkdown(code, palette)
            "properties" -> return tokenizeProperties(code, palette)
        }

        val keywords = keywordsFor(lang)
        val hasSlashComments = lang !in setOf("python", "ruby")
        val hasHashComments = lang in setOf("python", "ruby", "shell")
        val hasTripleString = lang == "python"

        return buildAnnotatedString {
            var i = 0
            val n = code.length
            while (i < n) {
                val c = code[i]
                when {
                    // Line comment //
                    hasSlashComments && c == '/' && i + 1 < n && code[i + 1] == '/' -> {
                        val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                        withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // Block comment /* ... */
                    hasSlashComments && c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                        val close = code.indexOf("*/", i + 2).let { if (it < 0) n - 2 else it }
                        val end = (close + 2).coerceAtMost(n)
                        withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // Hash comment
                    hasHashComments && c == '#' -> {
                        val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                        withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                            append(code.substring(i, end))
                        }
                        i = end
                    }
                    // Python docstring / triple-quoted string
                    hasTripleString && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i)) -> {
                        val q = code.substring(i, i + 3)
                        val close = code.indexOf(q, i + 3).let { if (it < 0) n - 3 else it }
                        val end = (close + 3).coerceAtMost(n)
                        withStyle(SpanStyle(color = palette.string)) { append(code.substring(i, end)) }
                        i = end
                    }
                    // String literals
                    c == '"' || c == '\'' || (c == '`' && lang in setOf("javascript", "typescript", "go", "kotlin")) -> {
                        var j = i + 1
                        while (j < n) {
                            if (code[j] == '\\') { j += 2; continue }
                            if (code[j] == c) { j++; break }
                            if (code[j] == '\n' && c != '`') break
                            j++
                        }
                        withStyle(SpanStyle(color = palette.string)) {
                            append(code.substring(i, j.coerceAtMost(n)))
                        }
                        i = j.coerceAtMost(n)
                    }
                    // Char literal handled above with '\''. Numbers:
                    c.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit() && code[i - 1] != '_') -> {
                        var j = i
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                        val token = code.substring(i, j)
                        if (token.any { it.isDigit() }) {
                            withStyle(SpanStyle(color = palette.number)) { append(token) }
                        } else {
                            append(token)
                        }
                        i = j
                    }
                    // Annotations / decorators
                    (c == '@' && lang in setOf("kotlin", "java", "csharp", "python")) -> {
                        var j = i + 1
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                        withStyle(SpanStyle(color = palette.annotation)) { append(code.substring(i, j)) }
                        i = j
                    }
                    // Word — keyword or plain identifier
                    c.isLetter() || c == '_' -> {
                        var j = i
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                        val word = code.substring(i, j)
                        if (word in keywords) {
                            withStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)) {
                                append(word)
                            }
                        } else {
                            append(word)
                        }
                        i = j
                    }
                    else -> {
                        append(c)
                        i++
                    }
                }
            }
        }
    }

    private fun tokenizeJson(code: String, palette: Palette) = buildAnnotatedString {
        var i = 0
        val n = code.length
        while (i < n) {
            when (val c = code[i]) {
                '"' -> {
                    var j = i + 1
                    while (j < n) {
                        if (code[j] == '\\') { j += 2; continue }
                        if (code[j] == '"') { j++; break }
                        j++
                    }
                    val end = j.coerceAtMost(n)
                    // Key if the next non-space char is ':'
                    var k = end
                    while (k < n && code[k].isWhitespace()) k++
                    val isKey = k < n && code[k] == ':'
                    withStyle(SpanStyle(color = if (isKey) palette.keyword else palette.string)) {
                        append(code.substring(i, end))
                    }
                    i = end
                }
                c.isDigit() || c == '-' -> {
                    var j = i + 1
                    while (j < n && (code[j].isDigit() || code[j] in ".eE+-")) j++
                    withStyle(SpanStyle(color = palette.number)) { append(code.substring(i, j)) }
                    i = j
                }
                c.isLetter() -> {
                    var j = i
                    while (j < n && code[j].isLetter()) j++
                    val w = code.substring(i, j)
                    if (w in setOf("true", "false", "null")) {
                        withStyle(SpanStyle(color = palette.number, fontWeight = FontWeight.SemiBold)) { append(w) }
                    } else append(w)
                    i = j
                }
                else -> { append(c); i++ }
            }
        }
    }

    private fun tokenizeXml(code: String, palette: Palette) = buildAnnotatedString {
        var i = 0
        val n = code.length
        while (i < n) {
            when {
                code.startsWith("<!--", i) -> {
                    val close = code.indexOf("-->", i + 4).let { if (it < 0) n - 3 else it }
                    val end = (close + 3).coerceAtMost(n)
                    withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                        append(code.substring(i, end))
                    }
                    i = end
                }
                code[i] == '<' -> {
                    // Tag: color name & attributes until '>'
                    val end = code.indexOf('>', i).let { if (it < 0) n else it + 1 }
                    val tag = code.substring(i, end)
                    append("<")
                    // tag name
                    val nameMatch = Regex("^</?([A-Za-z0-9:_-]+)").find(tag)
                    if (nameMatch != null) {
                        if (tag.length > 1 && tag[1] == '/') append("/")
                        withStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)) {
                            append(nameMatch.groupValues[1])
                        }
                        // rest: attribute="value"
                        var rest = tag.substring(nameMatch.value.length)
                        if (rest.endsWith(">")) rest = rest.dropLast(1)
                        var k = 0
                        while (k < rest.length) {
                            val m = Regex("([A-Za-z0-9:_-]+)(=)(\"[^\"]*\"|'[^']*')").find(rest, k)
                            if (m == null || m.range.first > k) {
                                append(rest.substring(k, m?.range?.first ?: rest.length))
                            }
                            if (m == null) break
                            append(rest.substring(m.range.first, m.range.first + m.groupValues[1].length))
                            withStyle(SpanStyle(color = palette.string)) {
                                append(m.groupValues[2] + m.groupValues[3])
                            }
                            k = m.range.last + 1
                        }
                    } else {
                        append(tag.drop(1).dropLast(1))
                    }
                    append(">")
                    i = end
                }
                else -> { append(code[i]); i++ }
            }
        }
    }

    private fun tokenizeYaml(code: String, palette: Palette) = buildAnnotatedString {
        code.split("\n").forEachIndexed { idx, line ->
            if (idx > 0) append("\n")
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") -> withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) { append(line) }
                else -> {
                    val colon = line.indexOf(':')
                    if (colon > 0 && !line.substring(0, colon).contains(' ')) {
                        withStyle(SpanStyle(color = palette.keyword)) { append(line.substring(0, colon)) }
                        append(line.substring(colon))
                    } else append(line)
                }
            }
        }
    }

    private fun tokenizeShell(code: String, palette: Palette) = buildAnnotatedString {
        var i = 0
        val n = code.length
        val kw = setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case",
            "esac", "in", "function", "return", "exit", "echo", "export", "local", "readonly",
            "shift", "source", "set", "unset", "cd", "eval", "exec", "trap", "true", "false",
        )
        while (i < n) {
            val c = code[i]
            when {
                c == '#' -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                    withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) { append(code.substring(i, end)) }
                    i = end
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < n) {
                        if (code[j] == '\\' && c == '"') { j += 2; continue }
                        if (code[j] == c) { j++; break }
                        j++
                    }
                    withStyle(SpanStyle(color = palette.string)) { append(code.substring(i, j.coerceAtMost(n))) }
                    i = j.coerceAtMost(n)
                }
                c == '$' -> {
                    var j = i + 1
                    if (j < n && code[j] == '{') {
                        j = code.indexOf('}', j).let { if (it < 0) n else it + 1 }
                    } else {
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    }
                    withStyle(SpanStyle(color = palette.number)) { append(code.substring(i, j.coerceAtMost(n))) }
                    i = j.coerceAtMost(n)
                }
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < n && (code[j].isLetterOrDigit() || code[j] in "_-.")) j++
                    val w = code.substring(i, j)
                    if (w in kw) {
                        withStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)) { append(w) }
                    } else append(w)
                    i = j
                }
                else -> { append(c); i++ }
            }
        }
    }

    private fun tokenizeMarkdown(code: String, palette: Palette) = buildAnnotatedString {
        var inCodeFence = false
        code.split("\n").forEachIndexed { idx, line ->
            if (idx > 0) append("\n")
            when {
                line.trimStart().startsWith("```") -> {
                    inCodeFence = !inCodeFence
                    withStyle(SpanStyle(color = palette.string)) { append(line) }
                }
                inCodeFence -> withStyle(SpanStyle(color = palette.string)) { append(line) }
                line.startsWith("#") -> withStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)) { append(line) }
                else -> append(line)
            }
        }
    }

    private fun tokenizeProperties(code: String, palette: Palette) = buildAnnotatedString {
        code.split("\n").forEachIndexed { idx, line ->
            if (idx > 0) append("\n")
            val t = line.trimStart()
            if (t.startsWith("#") || t.startsWith(";")) {
                withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) { append(line) }
            } else {
                val eq = line.indexOf('=')
                if (eq > 0) {
                    withStyle(SpanStyle(color = palette.keyword)) { append(line.substring(0, eq)) }
                    append(line.substring(eq))
                } else append(line)
            }
        }
    }
}
