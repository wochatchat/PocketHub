package com.pockethub.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A lightweight, dependency-free Markdown renderer (enhanced).
 *
 * Supports: H1-H6, bold (** and __), italic (* and _), bold-italic (***), strikethrough (~~), inline code, fenced code blocks
 * (``` and ~~~, nested long fences), ordered / unordered lists (with nesting and continuation lines),
 * GitHub task lists (- [ ] / - [x]), blockquotes, GFM alerts (> [!NOTE] etc), horizontal rules, paragraphs
 * with hard line breaks, GitHub-style pipe tables (alignment + escaped pipes), reference-style links
 * ([text][ref] + [ref]: url), images `![alt](src)`, wrapped badge links `[![alt](src)](href)`,
 * autolinks (`<url>` and bare URLs), GitHub-relative references (#123 issue, @user, owner/repo, bare commit SHA),
 * and common raw-HTML inline tags (<strong>/<b>, <em>/<i>, <code>/<kbd>, <del>, <br>, <hr>, <img>).
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

internal const val LINK_TAG = "url"
internal const val LINK_KIND_TAG = "kind"

/** Visual/logical kind of a clickable link. Lets the host screen route it appropriately. */
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
    /** All image URLs in this document, in render order — lets the full-screen
     *  preview swipe between them (ViewPager-style). Empty = single image. */
    imageGallery: List<String> = emptyList(),
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
    val imagePreviewer = com.pockethub.ui.components.LocalImagePreviewer.current

    val onTap: (String, LinkKind) -> Unit = { url, kind ->
        when {
            // Image links: prefer the in-app zoomable preview if the host screen has
            // registered one. Markdown README / issue / PR bodies carry screenshots /
            // diagrams which the web UI opens inline; routing them to the browser is
            // a worse experience, so we hijack the tap here. We only hijack when the
            // image pointer is the link target (wrapUrl set to the image itself, or the
            // inline image src with no wrapping link) — keeping wrapped-link cases
            // (an image wrapped around a click to another URL) routed through onLinkClick.
            (kind == LinkKind.IMAGE_URL || kind == LinkKind.IMAGE) && imagePreviewer != null -> {
                // Open the preview positioned at the tapped image, with the rest
                // of the document's images swipeable (single-image fallback).
                val idx = imageGallery.indexOf(url)
                if (idx >= 0) {
                    imagePreviewer(imageGallery, idx)
                } else {
                    imagePreviewer(listOf(url), 0)
                }
            }
            onLinkClick != null -> onLinkClick(url, kind)
            else -> uriHandler.openUri(url)
        }
    }

    // Parse OFF the main thread: large documents (600KB+ awesome-lists) take
    // seconds-to-minutes synchronously and would ANR the Overview tab. Also
    // cap the input so pathological docs stay renderable.
    val parseResult by produceState<Result<List<MdBlock>>>(
        initialValue = Result.success(emptyList()),
        key1 = markdown,
    ) {
        value = runCatching {
            withContext(Dispatchers.Default) {
                parseMarkdown(cleanMarkdown(truncateOversized(markdown)))
            }
        }
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
                    // Render inline markdown (links, code, bold) inside headings so `## Getting `code``
                    // shows a code chip instead of literal backticks.
                    val parts = rememberRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RenderInlineParts(parts, style.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = when (block.level) {
                            1 -> 32.sp
                            2 -> 28.sp
                            else -> 24.sp
                        },
                    ), onTap)
                    if (block.level <= 2) Spacer(Modifier.height(2.dp))
                }

                is MdBlock.Paragraph -> {
                    val parts = rememberRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    RichParagraph(parts, onTap, paragraphSpacing = 4.dp)
                }

                is MdBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(blockShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, blockShape)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        if (!block.lang.isNullOrBlank()) {
                            Text(
                                block.lang.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 12.dp, top = 10.dp),
                            )
                        }
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 12.dp, end = 12.dp,
                                top = if (block.lang.isNullOrBlank()) 12.dp else 4.dp,
                                bottom = 12.dp,
                            ),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                is MdBlock.Alert -> {
                    val parts = rememberRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    SimpleAlertCard(block.kind, parts, onTap)
                }

                is MdBlock.Blockquote -> {
                    val parts = rememberRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    SimpleBlockquote(parts, mutedColor, onTap)
                }

                is MdBlock.ListItem -> {
                    val bullet = when {
                        block.ordered -> "${block.index}. "
                        block.task == 'x' -> "☑ "
                        block.task == ' ' -> "☐ "
                        else -> "• "
                    }
                    val parts = rememberRichInline(block.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    SimpleListItem(bullet, parts, (block.level - 1) * 14, onTap)
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

/** Minimal GFM alert card ([!NOTE] etc.) — accent-colored left rule + label. */
@Composable
private fun SimpleAlertCard(
    kind: String,
    parts: List<InlineToken>,
    onTap: (String, LinkKind) -> Unit,
) {
    val (label, color) = when (kind.uppercase()) {
        "NOTE" -> "Note" to MaterialTheme.colorScheme.primary
        "TIP" -> "Tip" to Color(0xFF2EA043)
        "IMPORTANT" -> "Important" to Color(0xFF8250DF)
        "WARNING" -> "Warning" to Color(0xFFBF8700)
        "CAUTION" -> "Caution" to Color(0xFFD1242F)
        else -> kind.ifBlank { "Note" } to MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(IntrinsicSize.Min)
                .fillMaxHeight()
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            Spacer(Modifier.height(2.dp))
            RichParagraph(parts, onTap, paragraphSpacing = 2.dp)
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** Minimal blockquote — left rule + muted text. */
@Composable
private fun SimpleBlockquote(
    parts: List<InlineToken>,
    mutedColor: Color,
    onTap: (String, LinkKind) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(IntrinsicSize.Min)
                .fillMaxHeight()
                .background(mutedColor.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        RichParagraph(parts, onTap)
    }
    Spacer(Modifier.height(4.dp))
}

/** Minimal list item — hanging bullet + inline content. */
@Composable
private fun SimpleListItem(
    bullet: String,
    parts: List<InlineToken>,
    indentDp: Int,
    onTap: (String, LinkKind) -> Unit,
) {
    Row(Modifier.padding(start = indentDp.dp, bottom = 3.dp)) {
        Text(
            bullet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RichParagraph(parts, onTap)
    }
}

@Composable
internal fun MarkdownErrorBox(error: Throwable) {
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

internal sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val code: String, val lang: String?) : MdBlock()
    data class Blockquote(val text: String) : MdBlock()
    /** GFM alert (`> [!NOTE]` etc). kind: NOTE/TIP/IMPORTANT/WARNING/CAUTION. */
    data class Alert(val kind: String, val text: String) : MdBlock()
    /** `task`: null = not a task item; ' ' = unchecked; 'x' = checked. */
    data class ListItem(val text: String, val ordered: Boolean, val index: Int, val level: Int, val task: Char? = null) : MdBlock()
    /** alignments per column: 0 left, 1 center, 2 right (from the `:---:` separator row). */
    data class Table(val headers: List<String>, val rows: List<List<String>>, val alignments: List<Int> = emptyList()) : MdBlock()
    object HorizontalRule : MdBlock()
}

// ── Inline tokens (rich — can mix text + images in one paragraph) ─────

internal sealed class InlineToken {
    /** Flowable annotated text — clickable links live here. */
    data class Text(val span: AnnotatedString) : InlineToken()
    /** Standalone image. `wrapUrl` non-null → image is wrapped in a link (render with hover style). */
    data class Image(
        val src: String,
        val alt: String,
        val wrapUrl: String?,
        /** Display hint from the source HTML <img width height> attrs, in dp. */
        val hintW: Int? = null,
        val hintH: Int? = null,
    ) : InlineToken()
}
