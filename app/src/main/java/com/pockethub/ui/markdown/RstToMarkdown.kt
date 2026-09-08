package com.pockethub.ui.markdown

// Minimal reStructuredText → Markdown converter for README.rst documents.
//
// GitHub renders .rst server-side (docutils), but the README API returns the
// RAW source, so feeding it to the markdown pipeline rendered obs-studio's
// `.. image::` directives as literal text with auto-linked URLs. A full docutils
// port is out of scope; this covers the subset that appears in real READMEs:
//   - `.. image:: URL` / `.. figure:: URL` with indented :alt:/:target:/:width:
//     options → `[![alt|hint](url)](target)` (the wrapped-image form the
//     renderer's badge/badge-wall logic already understands)
//   - `.. |name| image:: URL` substitution definitions → markdown image +
//     `|name|` reference rewrite (the standard centered badge-table pattern)
//   - setext headings ("Title\n=====" / "-----") → ATX `#` headings
//   - RST inline links "`text <url>`_" → markdown [text](url)
//   - `.. note::`-style admonitions → blockquote with a bold marker
//   - `.. code:: lang` / `.. code-block:: lang` → fenced code block
//   - `::` literal blocks → fenced code (dedented)
// Everything else (footnotes, citations, tables) passes through as text.

private val RST_DIRECTIVE = Regex("^\\.\\.\\s+(image|figure|note|warning|tip|important|caution|code|code-block)::\\s*(.*)$")
private val RST_SUB_DEF = Regex("^\\.\\.\\s+\\|([^|]+)\\|\\s+(image|figure)::\\s*(\\S+)\\s*$")
private val RST_OPTION = Regex("^:(alt|target|align|width|height):\\s*(.*)$")
private val RST_ADMONITION = Regex("^(note|warning|tip|important|caution|attention|danger|error|hint|admonition)$", RegexOption.IGNORE_CASE)
private val RST_INLINE_LINK = Regex("(`+)([^`<>]+)\\s*<([^<>`]+)>\\1_?(_?)")
private val RST_SETTEXT = Regex("^(=+|-+|~+|\\*+|`+)$")
private val RST_ENUM_LIST = Regex("^(?:\\d+|#)\\.\\s+")
private val RST_FIELD = Regex("^:[\\w-]+:")

internal fun isRstReadme(name: String?): Boolean {
    val n = (name ?: "").lowercase()
    return n.endsWith(".rst") || n.endsWith(".rest")
}

internal fun rstToMarkdown(rst: String): String {
    if (!rst.contains(".. ")) return rst // cheap pre-filter: most docs are markdown
    val normalized = rst.replace("\r\n", "\n").replace("\r", "\n")
    val lines = normalized.split("\n")
    val out = StringBuilder()
    var i = 0
    val n = lines.size
    // Substitution definitions: |name| → markdown image (with target/hints).
    val substitutions = HashMap<String, String>()

    fun indentOf(s: String) = s.length - s.trimStart().length

    fun dedent(block: List<String>): List<String> {
        val ind = block.filter { it.isNotBlank() }.minOfOrNull { indentOf(it) } ?: 0
        return block.map { if (it.isBlank()) "" else it.drop(ind) }
    }

    while (i < n) {
        val line = lines[i]
        val trimmed = line.trim()

        // ── Substitution definition: .. |name| image:: URL
        val subDef = RST_SUB_DEF.matchEntire(trimmed)
        if (subDef != null) {
            val name = subDef.groupValues[1].trim()
            val url = subDef.groupValues[3]
            var alt = ""
            var target: String? = null
            var w: String? = null
            i++
            while (i < n) {
                val opt = lines[i]
                if (opt.isBlank()) break
                val om = RST_OPTION.matchEntire(opt.trim()) ?: break
                when (om.groupValues[1]) {
                    "alt" -> alt = om.groupValues[2].trim()
                    "target" -> target = om.groupValues[2].trim()
                    "width" -> w = om.groupValues[2].trim()
                }
                i++
            }
            val hint = w?.removeSuffix("px")?.toIntOrNull()?.let { "\u0001${it.coerceIn(1, 4000)}x0" } ?: ""
            val img = "![${alt}${hint}]($url)"
            substitutions[name] = if (target != null) "[${img}](${target})" else img
            out.append('\n')
            continue
        }

        // ── Directives: image / figure / admonitions / code
        val dir = RST_DIRECTIVE.matchEntire(trimmed)
        if (dir != null) {
            val kind = dir.groupValues[1].lowercase()
            val arg = dir.groupValues[2].trim()
            // collect indented option/content block
            val block = mutableListOf<String>()
            i++
            while (i < n && lines[i].isNotBlank() && indentOf(lines[i]) > 0) {
                block.add(lines[i])
                i++
            }
            when {
                kind == "image" || kind == "figure" -> {
                    if (arg.isNotEmpty()) {
                        var alt = ""
                        var target: String? = null
                        var w: String? = null
                        var h: String? = null
                        for (b in block) {
                            val om = RST_OPTION.matchEntire(b.trim())
                            when (om?.groupValues?.getOrNull(1)) {
                                "alt" -> alt = om!!.groupValues[2].trim()
                                "target" -> target = om!!.groupValues[2].trim()
                                "width" -> w = om!!.groupValues[2].trim()
                                "height" -> h = om!!.groupValues[2].trim()
                            }
                        }
                        fun dim(v: String?): Int? =
                            v?.removeSuffix("px")?.toIntOrNull()?.coerceIn(1, 4000)
                        val wv = dim(w)
                        val hv = dim(h)
                        val hint = when {
                            wv != null && hv != null -> "\u0001${wv}x${hv}"
                            wv != null -> "\u0001${wv}x0"
                            hv != null -> "\u00010x${hv}"
                            else -> ""
                        }
                        val img = "![${alt}${hint}]($arg)"
                        out.append("\n")
                        out.append(if (target != null) "[${img}](${target})" else img)
                        out.append("\n\n")
                    }
                }
                kind == "code" || kind == "code-block" -> {
                    val body = dedent(block).joinToString("\n")
                    out.append("\n```").append(arg.ifBlank { "" }).append('\n')
                        .append(body).append("\n```\n\n")
                }
                RST_ADMONITION.matches(kind) -> {
                    val body = dedent(block).joinToString("\n").trim()
                    out.append("\n> **")
                        .append(kind.uppercase())
                        .append("**\n")
                    // re-parse body as blocks: prefix "> " per line
                    out.append(body.lines().joinToString("\n") { if (it.isBlank()) ">" else "> ${it}" })
                    out.append("\n\n")
                }
                else -> {
                    // Unknown directive (contents, toctree, math, …): drop the
                    // option block, keep nothing — matches GitHub's visual
                    // result for README purposes.
                }
            }
            continue
        }

        // ── Setext heading: "Title" + =====/----- underline
        if (i + 1 < n && trimmed.isNotEmpty() && !trimmed.startsWith("..") && trimmed.contains('|').not()) {
            val next = lines[i + 1].trim()
            val m = RST_SETTEXT.matchEntire(next)
            val isHeading = m != null && next.length >= trimmed.length * 0.5 &&
                !trimmed.startsWith("|") && !RST_ENUM_LIST.containsMatchIn(trimmed) &&
                !RST_FIELD.containsMatchIn(trimmed)
            // Overline form ("====\nTitle\n====") is handled implicitly:
            // the first ===== line hits this rule only if the NEXT line is
            // also an adornment — guarded below.
            if (isHeading && !(trimmed.startsWith("|") || next.startsWith("|"))) {
                val level = when (m!!.value[0]) {
                    '=' -> 1
                    '-' -> 2
                    else -> 3
                }
                out.append('\n').append("#".repeat(level)).append(' ').append(trimmed).append("\n\n")
                i += 2
                continue
            }
        }

        // ── Inline links: `text <url>`_ → [text](url)
        var line2 = line
        if (line2.contains("`")) {
            line2 = line2.replace(RST_INLINE_LINK) { m ->
                val text = m.groupValues[2].trim()
                val url = m.groupValues[3].trim()
                val trailing = m.groupValues[4] // "__" anonymous → same; keep none
                "[${text}]($url)${if (trailing.isNotEmpty()) " " else ""}"
            }
        }
        // Substitution references |name| → resolved image/link markdown
        if (line2.contains('|') && substitutions.isNotEmpty()) {
            line2 = Regex("\\|([^|]+)\\|").replace(line2) { m ->
                substitutions[m.groupValues[1].trim()] ?: m.value
            }
        }
        out.append(line2).append('\n')
        i++
    }
    return out.toString().trim() + "\n"
}
