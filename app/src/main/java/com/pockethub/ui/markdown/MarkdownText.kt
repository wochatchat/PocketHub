package com.pockethub.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * A lightweight, dependency-free Markdown renderer (enhanced).
 *
 * Supports: H1-H6, bold (**), italic (*), inline code, fenced code blocks,
 * ordered / unordered lists (with nesting), blockquotes, horizontal rules,
 * paragraphs, images `![alt](src)`, wrapped badge links `[![alt](src)](href)`,
 * autolinks (`<url>` and bare URLs), GitHub-relative references
 * (#123 issue, @user, owner/repo, bare commit SHA).
 *
 * Images are loaded with Coil's [AsyncImage] so README badges / banners / screenshots
 * render properly inside the Overview tab.
 *
 * Links are classified into kinds (see [LinkKind]) and rendered with distinct
 * color/icon/decoration so users can tell apart in-app GitHub destinations,
 * downloadable assets, image links, and external links at a glance.
 *
 * Does NOT support: tables, footnotes, math, task lists (rendered as plain text).
 */

private const val LINK_TAG = "url"
private const val LINK_KIND_TAG = "kind"

/** Visual/logical kind of a clickable link. Lets the host screen route it appropriately. */
enum class LinkKind {
    /** Same-host GitHub repository — `https://github.com/<owner>/<repo>` or `.../<owner>/<repo>/…` */
    GITHUB_REPO,

    /** Same-host GitHub user/organization profile page. */
    GITHUB_USER,

    /** Issue or PR on GitHub. */
    GITHUB_ISSUE,

    /** Commit on GitHub. */
    GITHUB_COMMIT,

    /** Direct asset a user can download — `.apk`/`.zip`/`.tar.gz`/`.dmg`/… or
     *  `raw.githubusercontent.com` / `releases/download/…` URLs. */
    DOWNLOADABLE,

    /** An image that was wrapped in a link and is being clicked via its container. */
    IMAGE,

    /** Bare image URL — clickable to open the image in browser. */
    IMAGE_URL,

    /** Everything else (websites, gists, markdown files, etc.). */
    EXTERNAL,
}

/**
 * Classify an absolute URL into a [LinkKind]. URL is assumed already absolute (http/https).
 */
fun classifyLink(url: String): LinkKind {
    val u = url.lowercase()
    if (u.startsWith("https://github.com/") || u.startsWith("http://github.com/")) {
        val path = u.substringAfter("github.com/", "").removePrefix("/").trimEnd('/')
        val parts = path.split('/').filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> LinkKind.EXTERNAL
            parts.size == 1 -> LinkKind.GITHUB_USER
            parts.size == 2 -> LinkKind.GITHUB_REPO
            parts.size >= 3 -> when (parts[2]) {
                "issues", "pull" -> LinkKind.GITHUB_ISSUE
                "commit", "commits", "tree", "blob" -> LinkKind.GITHUB_REPO
                "pulls" -> LinkKind.GITHUB_ISSUE
                else -> LinkKind.GITHUB_REPO
            }
            else -> LinkKind.EXTERNAL
        }
    }
    if (u.startsWith("https://raw.githubusercontent.com/")) return LinkKind.DOWNLOADABLE
    if (u.contains("/releases/download/")) return LinkKind.DOWNLOADABLE
    val ext = u.substringBefore('?', u).substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
    if (ext in DOWNLOADABLE_EXTS) return LinkKind.DOWNLOADABLE
    if (ext in IMAGE_EXTS) return LinkKind.IMAGE_URL
    return LinkKind.EXTERNAL
}

private val DOWNLOADABLE_EXTS = setOf(
    "apk", "zip", "gz", "tgz", "tar", "7z", "rar", "bz2", "xz",
    "dmg", "pkg", "deb", "rpm", "msi", "exe", "ipa",
    "jar", "aar", "war",
    "pdf", "epub", "mobi", "azw3",
)
private val IMAGE_EXTS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico",
)

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    /** Current repo context — "owner/repo" — for resolving relative links. Null OK in non-repo contexts. */
    repoContext: String? = null,
    /** Override link navigation. Default uses LocalUriHandler (system browser). Receives both
     *  the (already-resolved) URL and its [LinkKind], so the caller can route downloads, in-app
     *  navigation, and external opens differently. */
    onLinkClick: ((url: String, kind: LinkKind) -> Unit)? = null,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val downloadColor = MaterialTheme.colorScheme.tertiary
    val imageLinkColor = MaterialTheme.colorScheme.secondary
    val externalColor = MaterialTheme.colorScheme.primary

    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val blockShape = RoundedCornerShape(8.dp)
    val linkResolver = rememberLinkResolver(repoContext)
    val uriHandler = LocalUriHandler.current

    val onTap: (String, LinkKind) -> Unit = { url, kind ->
        if (onLinkClick != null) onLinkClick(url, kind) else uriHandler.openUri(url)
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
                        style = style.copy(
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = when (block.level) {
                                1 -> 32.sp
                                2 -> 28.sp
                                else -> 24.sp
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (block.level <= 2) Spacer(Modifier.height(2.dp))
                }

                is MdBlock.Paragraph -> {
                    val parts = renderRichInline(block.text, linkResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RichParagraph(parts, onTap, paragraphSpacing = 4.dp)
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
                    val parts = renderRichInline(block.text, linkResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RichBlockquote(parts, accentColor, mutedColor, onTap)
                }

                is MdBlock.ListItem -> {
                    val bullet = if (block.ordered) "${block.index}. " else "• "
                    val indent = (block.level - 1) * 14
                    val parts = renderRichInline(block.text, linkResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RichListItem(bullet, parts, indent, mutedColor, onTap)
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

// ── Block types ──────────────────────────────────────────────────────

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val code: String, val lang: String?) : MdBlock()
    data class Blockquote(val text: String) : MdBlock()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int, val level: Int) : MdBlock()
    object HorizontalRule : MdBlock()
}

// ── Inline tokens (rich — can mix text + images in one paragraph) ─────

private sealed class InlineToken {
    /** Flowable annotated text — clickable links live here. */
    data class Text(val span: AnnotatedString) : InlineToken()
    /** Standalone image. `wrapUrl` non-null → image is wrapped in a link (render with hover style). */
    data class Image(val src: String, val alt: String, val wrapUrl: String?) : InlineToken()
}

@Composable
private fun RichParagraph(parts: List<InlineToken>, onTap: (String, LinkKind) -> Unit, paragraphSpacing: androidx.compose.ui.unit.Dp = 3.dp) {
    // Inline-aligned images: collect adjacent images into a horizontal row
    // so README "badge walls" stack side-by-side instead of vertically. Text tokens
    // get rendered as standalone ClickableText below.
    var i = 0
    Column(Modifier.padding(top = paragraphSpacing, bottom = paragraphSpacing)) {
        while (i < parts.size) {
            val run = mutableListOf<InlineToken.Image>()
            while (i < parts.size && parts[i] is InlineToken.Image) {
                run.add(parts[i] as InlineToken.Image)
                i++
            }
            if (run.isNotEmpty()) {
                BadgesRow(run, onTap)
                continue
            }
            val txt = parts[i] as InlineToken.Text
            ClickableText(
                text = txt.span,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.padding(vertical = 2.dp),
                onClick = { offset ->
                    txt.span.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { annotation ->
                        val kind = txt.span.getStringAnnotations(LINK_KIND_TAG, offset, offset)
                            .firstOrNull()?.item?.let { runCatching { LinkKind.valueOf(it) }.getOrNull() }
                            ?: LinkKind.EXTERNAL
                        onTap(annotation.item, kind)
                    }
                },
            )
            i++
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgesRow(images: List<InlineToken.Image>, onTap: (String, LinkKind) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        images.forEach { img ->
            val modifier = Modifier.heightIn(min = 0.dp, max = 40.dp)
            val clickableModifier = if (img.wrapUrl != null) {
                Modifier.clip(RoundedCornerShape(4.dp)).clickable { onTap(img.wrapUrl, classifyLink(img.wrapUrl)) }
            } else {
                Modifier.clip(RoundedCornerShape(4.dp)).clickable { onTap(img.src, LinkKind.IMAGE_URL) }
            }
            Box(modifier.then(clickableModifier)) {
                AsyncImage(
                    model = img.src,
                    contentDescription = img.alt.takeIf { it.isNotBlank() },
                    modifier = Modifier
                        .heightIn(min = 16.dp, max = 40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

@Composable
private fun RichBlockquote(
    parts: List<InlineToken>,
    accentColor: Color,
    mutedColor: Color,
    onTap: (String, LinkKind) -> Unit,
) {
    val hasOnlyText = parts.all { it is InlineToken.Text }
    if (hasOnlyText) {
        // fast path — render whole as one ClickableText
        val span = buildAnnotatedString {
            parts.forEach { append((it as InlineToken.Text).span) }
        }
        ClickableText(
            text = span,
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
            onClick = { offset ->
                span.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { annotation ->
                    val kind = span.getStringAnnotations(LINK_KIND_TAG, offset, offset)
                        .firstOrNull()?.item?.let { runCatching { LinkKind.valueOf(it) }.getOrNull() }
                        ?: LinkKind.EXTERNAL
                    onTap(annotation.item, kind)
                }
            },
        )
        Spacer(Modifier.height(4.dp))
        return
    }
    // has images too — render paragraph-like
    Column(
        Modifier
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .drawBehind {
                drawLine(
                    color = accentColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 3.dp.toPx(),
                )
            }
    ) { RichParagraph(parts, onTap) }
}

@Composable
private fun RichListItem(
    bullet: String,
    parts: List<InlineToken>,
    indent: Int,
    mutedColor: Color,
    onTap: (String, LinkKind) -> Unit,
) {
    Column(Modifier.padding(start = (4 + indent).dp, end = 8.dp, top = 2.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(bullet, color = mutedColor, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(2.dp))
            Column(Modifier.weight(1f)) { RichParagraph(parts, onTap) }
        }
    }
}

// ── Markdown cleaning ────────────────────────────────────────────────

@Composable
private fun rememberCleanedMarkdown(markdown: String): String {
    return androidx.compose.runtime.remember(markdown) {
        markdown
            // Convert common standalone raw-HTML <img src> into markdown ![](...) so our
            // image rendering kicks in. (<img> tags inside <a> won't convert cleanly here, but
            // those are far less common than markdown form below.)
            .replace(
                Regex(
                    "<\\s*img\\s+[^>]*?src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?(?:alt\\s*=\\s*[\"']([^\"']*)[\"'])?[^>]*?/?>",
                    RegexOption.IGNORE_CASE,
                )
            ) { m ->
                val src = m.groupValues[1]
                val alt = m.groupValues[2]
                "![${alt}](${src})"
            }
            // Strip common HTML block/inline tags (leave text between pairs) — but keep <a href>
            // as markdown so we don't lose navigation context for legacy README HTML.
            .replace(
                Regex(
                    "<\\s*a\\s+[^>]*?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>(.*?)<\\s*/\\s*a\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )
            ) { m ->
                "[${m.groupValues[2]}](${m.groupValues[1]})"
            }
            .replace(
                Regex("<\\s*(/?)\\s*(div|span|p|details|summary|center|section|article|figure|figcaption|picture|source|video|audio|sub|sup|small|big|font|table|thead|tbody|tr|td|th|pre)(\\s[^>]*)?>", RegexOption.IGNORE_CASE),
                "",
            )
            // Self-closing / void tags (img already converted above; keep others stripped)
            .replace(
                Regex("<\\s*(br|hr|input|meta|link|area|base|col|embed|param|track|wbr)(\\s[^>]*)?/?>", RegexOption.IGNORE_CASE),
                "",
            )
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

// ── Parsing ─────────────────────────────────────────────────────────

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
 *  - `#123`            → https://github.com/<owner/repo>/issues/123  (needs repoContext)
 *  - `@user`           → https://github.com/<user>
 *  - `owner/repo` or `owner/repo#123` → https://github.com/...
 *  - 40-hex-char SHA   → https://github.com/<repo>/commit/<sha>  (needs repoContext)
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
    if (raw.startsWith("@")) {
        val user = raw.removePrefix("@")
        if (user.matches(Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$"))) return@LinkResolver "$gh/$user"
        return@LinkResolver null
    }
    // Treat the URL as relative to repo if it starts with `/` or `./` or `../`
    if ((raw.startsWith("/") || raw.startsWith("./") || raw.startsWith("../")) && repoContext != null) {
        return@LinkResolver "$gh/$repoContext/${raw.removePrefix("./")}"
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

// ── Rich inline rendering ───────────────────────────────────────────

// Patterns pre-compiled once per rendering call. Each uses the *anchor at start*
// semantic by requiring the match to begin at position 0 of the substring passed.
// In the loop we slice off the part from i onward and try matching.
private val WRAPPED_IMG_PATTERN = Regex("^\\[!?\\[([^\\]]*)\\]\\(([^)]+)\\)\\]\\(([^)]+)\\)")
private val STANDALONE_IMG_PATTERN = Regex("^!\\[([^\\]]*)\\]\\(([^)]+)\\)")

/**
 * Render a paragraph/inline text into a mix of [InlineToken]s. Images (`![alt](src)`)
 * and badge-wrapped images (`[![alt](src)](href)`) are extracted as [InlineToken.Image]
 * so they can be composed with Coil's [AsyncImage] instead of turning into junk text.
 *
 * Pattern starts at the current scan position to avoid jumping past plain text.
 */
private fun renderRichInline(
    text: String,
    resolver: LinkResolver,
    codeBackgroundColor: Color,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    externalColor: Color,
): List<InlineToken> {
    val out = mutableListOf<InlineToken>()
    val textBuffer = StringBuilder()

    fun flushText() {
        if (textBuffer.isNotEmpty()) {
            val str = stringFromSource(
                textBuffer.toString(),
                resolver,
                codeBackgroundColor,
                linkColor,
                downloadColor,
                imageLinkColor,
                externalColor,
            )
            out.add(InlineToken.Text(str))
            textBuffer.clear()
        }
    }

    var i = 0
    val len = text.length
    while (i < len) {
        val rest = text.substring(i)
        // Try wrapped image link [![alt](src)](href) — only if it begins at i.
        val wrappedMatch = WRAPPED_IMG_PATTERN.find(rest)
        if (wrappedMatch != null) {
            flushText()
            val alt = wrappedMatch.groupValues[1]
            val src = wrappedMatch.groupValues[2].trim()
            val href = wrappedMatch.groupValues[3].trim()
            val resolvedHref = resolver(href) ?: href
            out.add(InlineToken.Image(src = src, alt = alt, wrapUrl = resolvedHref))
            i += wrappedMatch.value.length
            continue
        }
        // Try standalone image ![alt](src)
        val imgMatch = STANDALONE_IMG_PATTERN.find(rest)
        if (imgMatch != null) {
            flushText()
            val alt = imgMatch.groupValues[1]
            val src = imgMatch.groupValues[2].trim()
            out.add(InlineToken.Image(src = src, alt = alt, wrapUrl = null))
            i += imgMatch.value.length
            continue
        }
        // Otherwise accumulate to text buffer (raw chars preserved so a later
        // markdown link [text](url) is fully visible to stringFromSource).
        textBuffer.append(text[i])
        i++
    }
    flushText()
    return out
}

private fun stringFromSource(
    src: String,
    resolver: LinkResolver,
    codeBackgroundColor: Color,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    externalColor: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        // Autolink <url>
        if (src[i] == '<') {
            val close = src.indexOf('>', i + 1)
            if (close != -1) {
                val inner = src.substring(i + 1, close)
                if (inner.startsWith("http://") || inner.startsWith("https://")) {
                    appendLink(inner, inner, classifyLink(inner), linkColor, downloadColor, imageLinkColor, externalColor)
                    i = close + 1; continue
                }
            }
        }
        // Markdown link [text](url)
        if (src[i] == '[') {
            val closeBracket = src.indexOf(']', i + 1)
            if (closeBracket != -1 && closeBracket + 1 < src.length && src[closeBracket + 1] == '(') {
                val closeParen = src.indexOf(')', closeBracket + 2)
                if (closeParen != -1) {
                    val linkText = src.substring(i + 1, closeBracket)
                    val linkUrl = src.substring(closeBracket + 2, closeParen).trim()
                    val url = resolver(linkUrl)
                    if (url != null) {
                        appendLink(linkText, url, classifyLink(url), linkColor, downloadColor, imageLinkColor, externalColor)
                    } else {
                        append(linkText)
                    }
                    i = closeParen + 1; continue
                }
            }
        }
        // Bare URL
        if (src.regionMatches(i, "https://", 0, 8, ignoreCase = false) ||
            src.regionMatches(i, "http://", 0, 7, ignoreCase = false)) {
            val end = findUrlEnd(src, i)
            if (end > i) {
                val url = src.substring(i, end)
                appendLink(url, url, classifyLink(url), linkColor, downloadColor, imageLinkColor, externalColor)
                i = end; continue
            }
        }
        // GitHub shortcut #123 / @user
        if (src[i] == '#' || src[i] == '@') {
            val m = if (src[i] == '#') Regex("^#(\\d+)").find(src.substring(i))
            else Regex("^@[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})").find(src.substring(i))
            if (m != null) {
                val ref = m.value
                val url = resolver(ref)
                if (url != null) {
                    val displayText = ref
                    appendLink(displayText, url, classifyLink(url), linkColor, downloadColor, imageLinkColor, externalColor)
                    i += m.range.last + 1; continue
                }
            }
        }
        // Bold **text**
        if (i + 1 < src.length && src[i] == '*' && src[i + 1] == '*') {
            val end = src.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(src.substring(i + 2, end)) }
                i = end + 2; continue
            }
        }
        // Italic *text*
        if (src[i] == '*') {
            val end = src.indexOf('*', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(src.substring(i + 1, end)) }
                i = end + 1; continue
            }
        }
        // Inline code `text`
        if (src[i] == '`') {
            val end = src.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackgroundColor)) {
                    append(src.substring(i + 1, end))
                }
                i = end + 1; continue
            }
        }
        append(src[i]); i++
    }
}

private fun AnnotatedString.Builder.appendLink(
    displayText: String,
    url: String,
    kind: LinkKind,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    @Suppress("UNUSED_PARAMETER") externalColor: Color,
) {
    // Tiny textual cue (emoji-free) for downloadable links — rendered before the styled span.
    val prefix = when (kind) {
        LinkKind.DOWNLOADABLE -> "⬇ "
        else -> ""
    }
    if (prefix.isNotEmpty()) append(prefix)
    // Now mark the actual link span with annotations + styles.
    val start = length
    addStringAnnotation(LINK_TAG, url, start, start + displayText.length)
    addStringAnnotation(LINK_KIND_TAG, kind.name, start, start + displayText.length)
    val style = when (kind) {
        LinkKind.DOWNLOADABLE -> SpanStyle(color = downloadColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)
        LinkKind.IMAGE_URL -> SpanStyle(color = imageLinkColor, textDecoration = TextDecoration.Underline)
        LinkKind.GITHUB_REPO, LinkKind.GITHUB_USER, LinkKind.GITHUB_ISSUE, LinkKind.GITHUB_COMMIT,
        LinkKind.IMAGE, LinkKind.EXTERNAL -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    }
    addStyle(style, start, start + displayText.length)
    append(displayText)
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
