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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage

/**
 * A lightweight, dependency-free Markdown renderer (enhanced).
 *
 * Supports: H1-H6, bold (**), italic (*), strikethrough (~~), inline code, fenced code blocks,
 * ordered / unordered lists (with nesting), GitHub task lists (- [ ] / - [x]), blockquotes,
 * horizontal rules, paragraphs, GitHub-style pipe tables, images `![alt](src)`, wrapped badge
 * links `[![alt](src)](href)`, autolinks (`<url>` and bare URLs), GitHub-relative references
 * (#123 issue, @user, owner/repo, bare commit SHA), and common raw-HTML inline tags
 * (<strong>/<b>, <em>/<i>, <code>/<kbd>, <del>, <br>, <hr>, <img>).
 *
 * Images are loaded with Coil so README badges / banners / screenshots render properly inside
 * the Overview tab. Content images fill the column width at their natural aspect ratio (capped
 * for readability), while badge walls stay compact and inline. Relative image paths are resolved
 * to raw.githubusercontent.com using [repoContext] + [defaultBranch].
 *
 * Links are classified into kinds (see [LinkKind]) and rendered with distinct
 * color/icon/decoration so users can tell apart in-app GitHub destinations, downloadable assets,
 * image links, and external links at a glance.
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
    /** Current repo context — "owner/repo" — for resolving relative links/images. Null OK in non-repo contexts. */
    repoContext: String? = null,
    /** Default branch of the repo, used to resolve relative image paths to raw.githubusercontent.com. */
    defaultBranch: String? = null,
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
    val imageResolver = rememberImageResolver(repoContext, defaultBranch)
    val uriHandler = LocalUriHandler.current

    val onTap: (String, LinkKind) -> Unit = { url, kind ->
        if (onLinkClick != null) onLinkClick(url, kind) else uriHandler.openUri(url)
    }

    val parseResult = androidx.compose.runtime.remember(markdown) {
        runCatching { parseMarkdown(cleanMarkdown(markdown)) }
    }
    Column(modifier = modifier) {
        parseResult.onFailure { MarkdownErrorBox(it) }
        parseResult.getOrNull()?.forEach { block ->
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
                    val parts = renderRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
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
                    val parts = renderRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RichBlockquote(parts, accentColor, mutedColor, onTap)
                }

                is MdBlock.ListItem -> {
                    val bullet = when {
                        block.ordered -> "${block.index}. "
                        block.task == 'x' -> "☑ "
                        block.task == ' ' -> "☐ "
                        else -> "• "
                    }
                    val indent = (block.level - 1) * 14
                    val parts = renderRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RichListItem(bullet, parts, indent, mutedColor, onTap)
                }

                is MdBlock.Table -> {
                    TableBlock(
                        block,
                        linkResolver,
                        imageResolver,
                        codeBackgroundColor,
                        linkColor,
                        downloadColor,
                        imageLinkColor,
                        externalColor,
                        onTap,
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

@Composable
private fun MarkdownErrorBox(error: Throwable) {
    val trace = androidx.compose.runtime.remember(error) {
        error.stackTraceToString().take(1500)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Text(
            "README 解析出错: ${error.javaClass.simpleName}: ${error.message ?: ""}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            trace,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

// ── Block types ──────────────────────────────────────────────────────

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val code: String, val lang: String?) : MdBlock()
    data class Blockquote(val text: String) : MdBlock()
    /** `task`: null = not a task item; ' ' = unchecked; 'x' = checked. */
    data class ListItem(val text: String, val ordered: Boolean, val index: Int, val level: Int, val task: Char? = null) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
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
    // Inline-aligned images: collect adjacent images into a run, then split the run into
    // badge walls (compact, inline) and content images (full-width). Text tokens get rendered
    // as standalone ClickableText below.
    var i = 0
    Column(Modifier.padding(top = paragraphSpacing, bottom = paragraphSpacing)) {
        while (i < parts.size) {
            val run = mutableListOf<InlineToken.Image>()
            while (i < parts.size && parts[i] is InlineToken.Image) {
                run.add(parts[i] as InlineToken.Image)
                i++
            }
            if (run.isNotEmpty()) {
                RenderImageRun(run, onTap)
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

/**
 * Render a run of adjacent images. Badge-style images (shields.io, CI status, etc.) are grouped
 * into a compact [BadgesRow]; everything else is shown as a full-width [ContentImage] so README
 * screenshots and banners are legible on a phone instead of squished to a strip.
 */
@Composable
private fun RenderImageRun(images: List<InlineToken.Image>, onTap: (String, LinkKind) -> Unit) {
    var j = 0
    while (j < images.size) {
        if (isBadgeUrl(images[j].src)) {
            val badges = mutableListOf<InlineToken.Image>()
            while (j < images.size && isBadgeUrl(images[j].src)) {
                badges.add(images[j])
                j++
            }
            BadgesRow(badges, onTap)
        } else {
            ContentImage(images[j], onTap)
            j++
        }
    }
}

/** A content image shown at a readable size with loading / error states and tap-to-open.
 *  Uses SubcomposeAsyncImage so Coil resolves the request against the component's bounded
 *  layout size (never Size.ORIGINAL) — large README screenshots decode downsampled, no OOM. */
@Composable
private fun ContentImage(img: InlineToken.Image, onTap: (String, LinkKind) -> Unit) {
    val clickTarget = img.wrapUrl ?: img.src
    val kind = if (img.wrapUrl != null) classifyLink(img.wrapUrl) else LinkKind.IMAGE_URL
    SubcomposeAsyncImage(
        model = img.src,
        contentDescription = img.alt.takeIf { it.isNotBlank() },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 360.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onTap(clickTarget, kind) },
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        },
        error = {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (img.alt.isNotBlank()) {
                    Text(
                        img.alt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    img.src,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
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
            val clickableModifier = if (img.wrapUrl != null) {
                Modifier.clip(RoundedCornerShape(4.dp)).clickable { onTap(img.wrapUrl, classifyLink(img.wrapUrl)) }
            } else {
                Modifier.clip(RoundedCornerShape(4.dp)).clickable { onTap(img.src, LinkKind.IMAGE_URL) }
            }
            Box(modifier = clickableModifier) {
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

// ── Tables ───────────────────────────────────────────────────────────

@Composable
private fun TableBlock(
    table: MdBlock.Table,
    resolver: LinkResolver,
    imageResolver: ImageResolver,
    codeBackgroundColor: Color,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    externalColor: Color,
    onTap: (String, LinkKind) -> Unit,
) {
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val colCount = table.headers.size.coerceAtLeast(1)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
    ) {
        Row(Modifier.fillMaxWidth().background(headerBg)) {
            table.headers.forEach { cell ->
                val parts = renderRichInline(cell, resolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                TableCell(parts, Modifier.width(0.dp).weight(1f), bold = true, onTap = onTap)
            }
        }
        HorizontalDivider(color = borderColor)
        table.rows.forEach { row ->
            val padded = (row + List((colCount - row.size).coerceAtLeast(0)) { "" }).take(colCount)
            Row(Modifier.fillMaxWidth()) {
                padded.forEach { cell ->
                    val parts = renderRichInline(cell, resolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    TableCell(parts, Modifier.width(0.dp).weight(1f), bold = false, onTap = onTap)
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    parts: List<InlineToken>,
    modifier: Modifier,
    bold: Boolean,
    onTap: (String, LinkKind) -> Unit,
) {
    val span = buildAnnotatedString {
        parts.forEach { if (it is InlineToken.Text) append(it.span) }
    }
    ClickableText(
        text = span,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        ),
        modifier = modifier.padding(horizontal = 6.dp, vertical = 5.dp),
        onClick = { offset ->
            span.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { a ->
                val kind = span.getStringAnnotations(LINK_KIND_TAG, offset, offset)
                    .firstOrNull()?.item?.let { runCatching { LinkKind.valueOf(it) }.getOrNull() }
                    ?: LinkKind.EXTERNAL
                onTap(a.item, kind)
            }
        },
    )
}

// ── Markdown cleaning ────────────────────────────────────────────────

private fun cleanMarkdown(markdown: String): String {
    return markdown
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
            // Convert raw-HTML inline emphasis/code/keystroke/strikethrough into markdown so it
            // renders styled instead of leaking raw tags. Must run before the generic tag strip.
            .replace(
                Regex("<\\s*(?:strong|b)\\b[^>]*>(.*?)<\\s*/\\s*(?:strong|b)\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "**${it.groupValues[1]}**" }
            .replace(
                Regex("<\\s*(?:em|i)\\b[^>]*>(.*?)<\\s*/\\s*(?:em|i)\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "*${it.groupValues[1]}*" }
            .replace(
                Regex("<\\s*(?:code|kbd)\\b[^>]*>(.*?)<\\s*/\\s*(?:code|kbd)\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "`${it.groupValues[1]}`" }
            .replace(
                Regex("<\\s*(?:del|s|strike)\\b[^>]*>(.*?)<\\s*/\\s*(?:del|s|strike)\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "~~${it.groupValues[1]}~~" }
            // Collapsible-section titles → bold heading so <details> blocks stay scannable.
            .replace(
                Regex("<\\s*summary\\b[^>]*>(.*?)<\\s*/\\s*summary\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            ) { "\n**${it.groupValues[1].trim()}**\n" }
            // Inline tags with no markdown equivalent — drop the tag, keep inner text.
            .replace(Regex("<\\s*/?(?:u|mark|small|big|font|sub|sup)\\b[^>]*>", RegexOption.IGNORE_CASE), "")
            // Block-level line breaks / rules → markdown forms (before the void-tag strip below).
            .replace(Regex("<\\s*br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<\\s*hr\\s*/?>", RegexOption.IGNORE_CASE), "\n\n---\n\n")
            .replace(
                Regex("<\\s*(/?)\\s*(div|span|p|details|summary|center|section|article|figure|figcaption|picture|source|video|audio|table|thead|tbody|tr|td|th|pre)(\\s[^>]*)?>", RegexOption.IGNORE_CASE),
                "",
            )
            // Self-closing / void tags (img/br/hr already converted above; keep others stripped)
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

// ── Parsing ─────────────────────────────────────────────────────────

private val TABLE_SEP_REGEX = Regex("^\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?$")

private fun isTableSeparator(line: String): Boolean {
    val l = line.trim()
    return l.contains("-") && l.contains("|") && TABLE_SEP_REGEX.matches(l)
}

private fun looksLikeTableRow(line: String): Boolean {
    val l = line.trim()
    return l.isNotBlank() && (l.startsWith("|") || l.count { it == '|' } >= 2)
}

private fun splitTableRow(line: String): List<String> {
    val raw = line.trim()
    val hasLeading = raw.startsWith("|")
    val hasTrailing = raw.endsWith("|")
    var cells = raw.split("|").map { it.trim() }
    if (hasLeading && cells.isNotEmpty()) cells = cells.drop(1)
    if (hasTrailing && cells.isNotEmpty()) cells = cells.dropLast(1)
    return cells
}

private fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = src.lines()
    var i = 0

    fun listLevel(line: String): Int {
        val leading = line.takeWhile { it == ' ' }.length
        return (leading / 2) + 1
    }

    /** A table header begins at [idx] when [idx] is a pipe row and [idx]+1 is a separator. */
    fun isTableHeaderAt(idx: Int): Boolean =
        idx + 1 < lines.size && looksLikeTableRow(lines[idx]) && isTableSeparator(lines[idx + 1])

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

        // Unordered list (with optional GitHub task-list checkbox)
        if (line.matches(Regex("^\\s*[-*+]\\s+.+"))) {
            while (i < lines.size && lines[i].matches(Regex("^\\s*[-*+]\\s+.+"))) {
                val raw = lines[i].trim().substringAfter(" ").trim()
                val taskMatch = Regex("^\\[([ xX])]\\s+(.*)").matchEntire(raw)
                val (text, task) = if (taskMatch != null) {
                    val checked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                    taskMatch.groupValues[2] to (if (checked) 'x' else ' ')
                } else {
                    raw to null
                }
                blocks.add(MdBlock.ListItem(text, ordered = false, index = 0, level = listLevel(lines[i]), task = task))
                i++
            }
            continue
        }

        // GitHub-style pipe table
        if (isTableHeaderAt(i)) {
            val headers = splitTableRow(lines[i])
            i += 2 // skip header + separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && looksLikeTableRow(lines[i]) && !isTableSeparator(lines[i]) && !lines[i].isBlank()) {
                rows.add(splitTableRow(lines[i]))
                i++
            }
            blocks.add(MdBlock.Table(headers, rows))
            continue
        }

        // Paragraph
        val paraLines = mutableListOf<String>()
        while (i < lines.size && !isBlockStart(lines[i]) && !isTableHeaderAt(i)) {
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

// ── Image resolver ───────────────────────────────────────────────────

/**
 * Resolve an image `src` to an absolute, Coil-loadable URL.
 *  - absolute http(s) / `//` / `data:` → returned (near-)as-is
 *  - relative path (e.g. `docs/shot.png`, `./a/b.gif`, `/assets/x.png`)
 *    → `https://raw.githubusercontent.com/<owner>/<repo>/<defaultBranch>/<path>`
 *    so README screenshots that use repo-relative URLs actually load.
 */
fun interface ImageResolver {
    operator fun invoke(src: String): String
}

@Composable
private fun rememberImageResolver(repoContext: String?, defaultBranch: String?): ImageResolver = ImageResolver { src ->
    val raw = src.trim()
    if (raw.isEmpty()) return@ImageResolver raw
    if (raw.startsWith("http://") || raw.startsWith("https://")) return@ImageResolver raw
    if (raw.startsWith("//")) return@ImageResolver "https:$raw"
    if (raw.startsWith("data:")) return@ImageResolver raw
    if (repoContext.isNullOrBlank()) return@ImageResolver raw
    val parts = repoContext.split("/")
    val owner = parts.getOrNull(0)
    val repo = parts.getOrNull(1)
    if (owner.isNullOrBlank() || repo.isNullOrBlank()) return@ImageResolver raw
    val branch = defaultBranch?.ifBlank { null } ?: "main"
    val path = raw.removePrefix("./").removePrefix("/").replace(Regex("(?:\\.\\./)+"), "")
    "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
}

/** Heuristic: does this URL look like a tiny status badge (shields.io, CI, etc.)? */
private fun isBadgeUrl(url: String): Boolean {
    val u = url.lowercase()
    if (u.contains("img.shields.io") || u.contains("shields.io")) return true
    if (u.contains("badge.fury.io")) return true
    if (u.contains("travis-ci.org") || u.contains("travis-ci.com")) return true
    if (u.contains("codecov.io") || u.contains("coveralls.io")) return true
    if (u.contains("circleci.com") || u.contains("badgen.net")) return true
    if (u.contains("gitter.im")) return true
    if (u.contains("/badge/")) return true
    if (u.contains("/buildstatus") || u.contains("/status-badge")) return true
    if (u.contains("actions/workflows") && u.contains("badge")) return true
    if ((u.contains("opencollective.com") || u.contains("snyk.io") || u.contains("app.codacy.com") || u.contains("deepscan.io")) && u.contains("badge")) return true
    if (u.contains("lgtm.com") || u.contains("lgtm.app")) return true
    return false
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
 * (with [imageResolver] applied to their src) so they can be composed with Coil instead
 * of turning into junk text.
 *
 * Pattern starts at the current scan position to avoid jumping past plain text.
 */
private fun renderRichInline(
    text: String,
    resolver: LinkResolver,
    imageResolver: ImageResolver,
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
            val src = imageResolver(wrappedMatch.groupValues[2].trim())
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
            val src = imageResolver(imgMatch.groupValues[2].trim())
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
        // Strikethrough ~~text~~
        if (i + 1 < src.length && src[i] == '~' && src[i + 1] == '~') {
            val end = src.indexOf("~~", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(src.substring(i + 2, end)) }
                i = end + 2; continue
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
