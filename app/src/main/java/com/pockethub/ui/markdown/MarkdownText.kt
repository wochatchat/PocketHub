package com.pockethub.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A lightweight, dependency-free Markdown renderer.
 *
 * Supports: H1–H6, bold (**), italic (*), inline code, fenced code blocks,
 * ordered / unordered lists (with nesting), blockquotes, horizontal rules,
 * paragraphs, autolinks (<> and bare URLs), and GitHub-relative references
 * (#123 issue, @user, owner/repo, bare commit SHA).
 *
 * Links are clickable via [LinkAnnotation.Url] / [LinkAnnotation.Clickable] and
 * handled through [ClickableText]. By default links open in the system browser
 * through [LocalUriHandler]; pass [onLinkClick] to intercept (e.g. for in-app
 * navigation). Relative links are resolved against [repoContext] ("owner/repo")
 * before being handed back.
 *
 * Does NOT support: tables, footnotes, math, task lists, raw HTML, images.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    /** Current repo context — "owner/repo" — for resolving relative links. Null OK in non-repo contexts. */
    repoContext: String? = null,
    /** Override link navigation. Default uses LocalUriHandler (system browser). */
    onLinkClick: ((String) -> Unit)? = null,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
        pressedStyle = SpanStyle(color = linkColor.copy(alpha = 0.7f), textDecoration = TextDecoration.Underline),
        hoveredStyle = SpanStyle(color = linkColor.copy(alpha = 0.9f), textDecoration = TextDecoration.Underline),
    )
    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val blockShape = RoundedCornerShape(8.dp)
    val linkResolver = rememberLinkResolver(repoContext)
    val uriHandler = LocalUriHandler.current

    val onTap: (String) -> Unit = { url ->
        if (onLinkClick != null) onLinkClick(url) else uriHandler.openUri(url)
    }

    val cleaned = rememberCleanedMarkdown(markdown)
    Column(modifier = modifier) {
        val blocks = parseMarkdown(cleaned)
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.headlineSmall
                        3 -> MaterialTheme.typography.titleLarge
                        4 -> MaterialTheme.typography.titleMedium
                        5 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    }
                    if (block.level <= 2) Spacer(Modifier.height(if (block.level == 1) 10.dp else 6.dp))
                    Text(
                        text = block.text,
                        style = style.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (block.level <= 2) Spacer(Modifier.height(2.dp))
                }

                is MdBlock.Paragraph -> {
                    val annotated = renderInline(block.text, linkStyles, linkResolver, codeBackgroundColor, linkColor)
                    ClickableText(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.padding(vertical = 3.dp),
                        onClick = { offset -> annotated.getLinkAnnotations(offset, offset).firstOrNull()?.let { link ->
                            val url = when (val item = link.item) {
                                is LinkAnnotation.Url -> item.url
                                is LinkAnnotation.Clickable -> item.tag as? String
                                else -> null
                            }
                            url?.let { onTap(it) }
                        } },
                    )
                }

                is MdBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(blockShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, blockShape)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                is MdBlock.Blockquote -> {
                    val annotated = renderInline(block.text, linkStyles, linkResolver, codeBackgroundColor, linkColor)
                    ClickableText(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            color = mutedColor,
                        ),
                        modifier = Modifier
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            .drawBehind {
                                drawLine(
                                    color = accentColor,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 3.dp.toPx(),
                                )
                            },
                        onClick = { offset -> annotated.getLinkAnnotations(offset, offset).firstOrNull()?.let { link ->
                            val url = when (val item = link.item) {
                                is LinkAnnotation.Url -> item.url
                                is LinkAnnotation.Clickable -> item.tag as? String
                                else -> null
                            }
                            url?.let { onTap(it) }
                        } },
                    )
                    Spacer(Modifier.height(4.dp))
                }

                is MdBlock.ListItem -> {
                    val bullet = if (block.ordered) "${block.index}. " else "• "
                    val indent = (block.level - 1) * 14
                    val annotated = buildAnnotatedString {
                        withStyle(SpanStyle(color = mutedColor)) { append(bullet) }
                        append(renderInline(block.text, linkStyles, linkResolver, codeBackgroundColor, linkColor))
                    }
                    ClickableText(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.padding(start = (4 + indent).dp, end = 8.dp, vertical = 2.dp),
                        onClick = { offset -> annotated.getLinkAnnotations(offset, offset).firstOrNull()?.let { link ->
                            val url = when (val item = link.item) {
                                is LinkAnnotation.Url -> item.url
                                is LinkAnnotation.Clickable -> item.tag as? String
                                else -> null
                            }
                            url?.let { onTap(it) }
                        } },
                    )
                }

                is MdBlock.HorizontalRule -> {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

// ── Parsing ──────────────────────────────────────────────────────────

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val code: String, val lang: String?) : MdBlock()
    data class Blockquote(val text: String) : MdBlock()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int, val level: Int) : MdBlock()
    object HorizontalRule : MdBlock()
}

@Composable
private fun rememberCleanedMarkdown(markdown: String): String {
    return androidx.compose.runtime.remember(markdown) {
        markdown
            // Strip common HTML block/inline tags (leave text between pairs).
            .replace(Regex("<\\s*(/?)\\s*(a|div|span|p|details|summary|center|section|article|figure|figcaption|picture|source|video|audio|sub|sup|small|big|font|table|thead|tbody|tr|td|th|pre)(\\s[^>]*)?>", RegexOption.IGNORE_CASE), "")
            // Self-closing / void tags
            .replace(Regex("<\\s*(br|hr|img|input|meta|link|area|base|col|embed|param|track|wbr)(\\s[^>]*)?/?>", RegexOption.IGNORE_CASE), "")
            // Decode a few common HTML entities
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rarr;", "→")
            .replace("&larr;", "←")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&nbsp;", " ")
            // Collapse multiple blank lines left by tag removal
            .replace(Regex("\\n\\s*\\n\\s*\\n"), "\n\n")
    }
}

private fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = src.lines()
    var i = 0

    fun listLevel(line: String): Int {
        val leading = line.takeWhile { it == ' ' }.length
        return (leading / 2) + 1
    }

    val isBlockStart: (String) -> Boolean = { l ->
        l.isBlank() || l.startsWith("#") || l.trim().startsWith("```") ||
            l.trimStart().startsWith(">") ||
            l.matches(Regex("^\\s*[-*+]\\s+.+")) || l.matches(Regex("^\\s*\\d+\\.\\s+.+")) ||
            l.matches(Regex("^-{3,}\\s*$")) || l.matches(Regex("^\\*{3,}\\s*$"))
    }

    while (i < lines.size) {
        val line = lines[i]

        if (line.isBlank()) { i++; continue }

        if (line.matches(Regex("^-{3,}\\s*$")) || line.matches(Regex("^\\*{3,}\\s*$"))) {
            blocks.add(MdBlock.HorizontalRule); i++; continue
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.+)").matchEntire(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            blocks.add(MdBlock.Heading(level, headingMatch.groupValues[2].trim()))
            i++; continue
        }

        if (line.trim().startsWith("```")) {
            val lang = line.trim().removePrefix("```").trim().ifBlank { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            i++
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n"), lang))
            continue
        }

        if (line.trimStart().startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                quoteLines.add(lines[i].trimStart().removePrefix(">").trim())
                i++
            }
            blocks.add(MdBlock.Blockquote(quoteLines.joinToString("\n")))
            continue
        }

        // Ordered list
        if (line.matches(Regex("^\\s*\\d+\\.\\s+.+"))) {
            var orderedIndex = 0
            while (i < lines.size && lines[i].matches(Regex("^\\s*\\d+\\.\\s+.+"))) {
                orderedIndex++
                val text = lines[i].trim().substringAfter(". ").trim()
                blocks.add(MdBlock.ListItem(text, ordered = true, index = orderedIndex, level = listLevel(lines[i])))
                i++
            }
            continue
        }

        // Unordered list
        if (line.matches(Regex("^\\s*[-*+]\\s+.+"))) {
            while (i < lines.size && lines[i].matches(Regex("^\\s*[-*+]\\s+.+"))) {
                val text = lines[i].trim().substringAfter(" ").trim()
                blocks.add(MdBlock.ListItem(text, ordered = false, index = 0, level = listLevel(lines[i])))
                i++
            }
            continue
        }

        // Paragraph
        val paraLines = mutableListOf<String>()
        while (i < lines.size && !isBlockStart(lines[i])) {
            paraLines.add(lines[i])
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
        }
    }
    return blocks
}

// ── Link resolver ────────────────────────────────────────────────────

/**
 * Resolve a raw GitHub reference to an absolute URL.
 *  - absolute http(s) → returned as-is
 *  - `#123`            → https://github.com/&lt;owner/repo&gt;/issues/123  (needs repoContext)
 *  - `@user`           → https://github.com/&lt;user&gt;
 *  - `owner/repo` or `owner/repo#123` → https://github.com/...
 *  - 40-hex-char SHA   → https://github.com/&lt;repo&gt;/commit/&lt;sha&gt;  (needs repoContext)
 *  - otherwise         → null (will be rendered as plain text)
 */
fun interface LinkResolver {
    operator fun invoke(ref: String): String?
}

@Composable
private fun rememberLinkResolver(repoContext: String?): LinkResolver = LinkResolver { ref ->
    val raw = ref.trim()
    if (raw.isEmpty()) return@LinkResolver null
    if (raw.startsWith("http://") || raw.startsWith("https://")) return@LinkResolver raw
    val gh = "https://github.com"
    if (raw.startsWith("#")) {
        val num = raw.removePrefix("#").trim()
        if (repoContext != null && num.matches(Regex("\\d+"))) return@LinkResolver "$gh/$repoContext/issues/$num"
        return@LinkResolver null
    }
    if (raw.startsWith("@") && raw.drop(1).matches(Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$"))) {
        return@LinkResolver "$gh/${raw.removePrefix("@")}"
    }
    val repoIssue = Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:#(\\d+))?$").matchEntire(raw)
    if (repoIssue != null) {
        val (owner, name, num) = repoIssue.destructured
        return@LinkResolver if (num.isNotEmpty()) "$gh/$owner/$name/issues/$num" else "$gh/$owner/$name"
    }
    if (raw.matches(Regex("^[0-9a-f]{40}$")) && repoContext != null) {
        return@LinkResolver "$gh/$repoContext/commit/$raw"
    }
    null
}

// ── Inline rendering ─────────────────────────────────────────────────

private fun renderInline(
    text: String,
    linkStyles: TextLinkStyles,
    resolver: LinkResolver,
    codeBackgroundColor: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        // Autolink <url>
        if (text[i] == '<') {
            val close = text.indexOf('>', i + 1)
            if (close != -1) {
                val inner = text.substring(i + 1, close)
                if (inner.startsWith("http://") || inner.startsWith("https://")) {
                    addLink(LinkAnnotation.Url(inner, linkStyles), start = length, end = length + inner.length)
                    append(inner)
                    i = close + 1; continue
                }
            }
        }
        // Markdown link [text](url)
        if (text[i] == '[') {
            val closeBracket = text.indexOf(']', i + 1)
            if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', closeBracket + 2)
                if (closeParen != -1) {
                    val linkText = text.substring(i + 1, closeBracket)
                    val linkUrl = text.substring(closeBracket + 2, closeParen).trim()
                    val url = resolver(linkUrl)
                    if (url != null) {
                        addLink(LinkAnnotation.Url(url, linkStyles), start = length, end = length + linkText.length)
                    }
                    append(linkText)
                    i = closeParen + 1; continue
                }
            }
        }
        // Bare URL
        if (text.regionMatches(i, "https://", 0, ignoreCase = false) ||
            text.regionMatches(i, "http://", 0, ignoreCase = false)) {
            val end = findUrlEnd(text, i)
            if (end > i) {
                val url = text.substring(i, end)
                addLink(LinkAnnotation.Url(url, linkStyles), start = length, end = length + url.length)
                append(url)
                i = end; continue
            }
        }
        // GitHub shortcut #123 / @user
        if (text[i] == '#' || text[i] == '@') {
            val m = if (text[i] == '#') Regex("^#(\\d+)").find(text.substring(i))
            else Regex("^@[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})").find(text.substring(i))
            if (m != null) {
                val ref = m.value
                val url = resolver(ref)
                if (url != null) {
                    addLink(LinkAnnotation.Url(url, linkStyles), start = length, end = length + ref.length)
                    append(ref)
                    i += m.range.last + 1; continue
                }
            }
        }
        // Bold **text**
        if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                i = end + 2; continue
            }
        }
        // Italic *text*
        if (text[i] == '*') {
            val end = text.indexOf('*', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                i = end + 1; continue
            }
        }
        // Inline code `text`
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackgroundColor)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1; continue
            }
        }
        append(text[i]); i++
    }
}

private fun findUrlEnd(text: String, start: Int): Int {
    var end = start
    while (end < text.length) {
        val c = text[end]
        if (c.isWhitespace() || c in setOf(')', ']', '}', '<', '>', '"', '\'', '|')) break
        end++
    }
    while (end > start + 1 && text[end - 1] in setOf('.', ',', ';', ':', '!', '?')) end--
    return end
}
