package com.pockethub.ui.markdown
import com.pockethub.ui.theme.semanticColors

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
import androidx.compose.ui.text.style.TextAlign
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
    // Off-github links: green (semantic success hue) — clearly distinct from
    // in-app primary so users can tell before tapping.
    val externalColor = semanticColors().success

    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val blockShape = RoundedCornerShape(8.dp)
    val linkResolver = rememberLinkResolver(repoContext)
    val imageResolver = rememberImageResolver(repoContext, defaultBranch)
    val uriHandler = LocalUriHandler.current
    val imagePreviewer = com.pockethub.ui.components.LocalImagePreviewer.current

    // Parse OFF the main thread: large documents (600KB+ awesome-lists) take
    // seconds-to-minutes synchronously and would ANR the Overview tab. Also
    // cap the input so pathological docs stay renderable.
    // The inline pass runs in the SAME off-main pass: renderRichInline is a
    // pure (text → List<InlineToken>) function, so each block's paragraph
    // links/emphasis/code-chips are pre-tokenized here too. Composition then
    // only assembles prebuilt AnnotatedStrings — long READMEs no longer
    // parse per-block on the UI thread during first layout.
    // Bundle the render inputs into one equality-safe key: the inline pass
    // must re-run when the theme (any color) or the resolvers change.
    val inlineCtx = InlineToken.Ctx(
        resolver = linkResolver,
        imageResolver = imageResolver,
        codeBackgroundColor = codeBackgroundColor,
        linkColor = linkColor,
        downloadColor = downloadColor,
        imageLinkColor = imageLinkColor,
        externalColor = externalColor,
    )
    val parsed by produceState<ParsedDoc>(
        initialValue = ParsedDoc(emptyList(), null),
        key1 = markdown,
        key2 = inlineCtx,
    ) {
        value = withContext(Dispatchers.Default) {
            try {
                val c = inlineCtx
                val blocks = parseMarkdown(cleanMarkdown(truncateOversized(markdown))).map { block ->
                    val text = when (block) {
                        is MdBlock.Heading -> block.text
                        is MdBlock.Paragraph -> block.text
                        is MdBlock.Alert -> block.text
                        is MdBlock.Blockquote -> block.text
                        is MdBlock.ListItem -> block.text
                        else -> null // Table/CodeBlock/HorizontalRule render themselves
                    }
                    block to when (text) {
                        null -> emptyList()
                        else -> renderRichInline(
                            text, c.resolver, c.imageResolver,
                            c.codeBackgroundColor, c.linkColor, c.downloadColor,
                            c.imageLinkColor, c.externalColor,
                        )
                    }
                }
                ParsedDoc(blocks, null)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ParsedDoc(emptyList(), e) // parser is total in practice; surface if it ever isn't
            }
        }
    }
    // Gallery of EVERY image in the parsed document, in render order. The srcs
    // come from the same inline pass that renders them (already resolved to
    // absolute URLs), so the tapped URL always matches an entry — and every
    // host screen (README, issues, PRs, comments, releases, file viewer) gets
    // ViewPager-style swiping between a document's images without having to
    // pass a gallery explicitly. A caller-supplied [imageGallery] still wins.
    val effectiveGallery: List<String> = if (imageGallery.isNotEmpty()) imageGallery else {
        val seen = LinkedHashSet<String>()
        for ((_, parts) in parsed.blocks) {
            for (part in parts) if (part is InlineToken.Image) seen.add(part.src)
        }
        seen.toList()
    }

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
                val idx = effectiveGallery.indexOf(url)
                if (idx >= 0) {
                    imagePreviewer(effectiveGallery, idx)
                } else {
                    imagePreviewer(listOf(url), 0)
                }
            }
            onLinkClick != null -> onLinkClick(url, kind)
            else -> uriHandler.openUri(url)
        }
    }

    // Long-press to select & copy any rendered markdown text (README,
    // issue/PR bodies, comments). NOTE: SelectionContainer is itself a Box
    // layout — it must wrap the Column as its SINGLE child. Wrapping the
    // multi-block forEach directly stacked every paragraph at the same
    // origin (the "ghosted text" regression).
    //
    // When the host screen lives in a pager, LocalPagerPageActive flips
    // false as the user swipes away: the selection (and floating copy
    // toolbar) is cleared — the old page stays composed under
    // beyondViewportPageCount>0.
    PagerAwareSelectionContainer(modifier = modifier) {
    Column {
        parsed.error?.let { MarkdownErrorBox(it) }
        parsed.blocks.forEach { (block, parts) ->
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
                    androidx.compose.foundation.layout.Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = if (block.centered) androidx.compose.ui.Alignment.CenterHorizontally else androidx.compose.ui.Alignment.Start,
                    ) {
                        RenderInlineParts(parts, style.copy(
                            fontWeight = FontWeight.SemiBold,
                            textAlign = if (block.centered) TextAlign.Center else null,
                            lineHeight = when (block.level) {
                                1 -> 32.sp
                                2 -> 28.sp
                                else -> 24.sp
                            },
                        ), onTap)
                    }
                    if (block.level <= 2) Spacer(Modifier.height(2.dp))
                }

                is MdBlock.Paragraph -> {
                    RichParagraph(parts, onTap, paragraphSpacing = 4.dp, centered = block.centered)
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
                    SimpleAlertCard(block.kind, parts, onTap)
                }

                is MdBlock.Blockquote -> {
                    SimpleBlockquote(parts, mutedColor, onTap)
                }

                is MdBlock.QuoteBlocks -> {
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
                        Column {
                            block.blocks.forEach { sub ->
                                when (sub) {
                                    is MdBlock.Paragraph -> RichParagraph(
                                        renderRichInline(sub.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor),
                                        onTap,
                                    )
                                    is MdBlock.Heading -> RenderInlineParts(
                                        renderRichInline(sub.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor),
                                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        onTap,
                                    )
                                    is MdBlock.ListItem -> SimpleListItem(
                                        if (sub.ordered) "${sub.index}. " else "• ",
                                        renderRichInline(sub.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor),
                                        (sub.level - 1) * 14,
                                        onTap,
                                    )
                                    is MdBlock.CodeBlock -> Text(
                                        sub.code,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(8.dp),
                                    )
                                    is MdBlock.Blockquote -> RichParagraph(
                                        renderRichInline(sub.text, linkResolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor),
                                        onTap,
                                    )
                                    else -> {}
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                is MdBlock.ListItem -> {
                    val bullet = when {
                        block.ordered -> "${block.index}. "
                        block.task == 'x' -> "☑ "
                        block.task == ' ' -> "☐ "
                        else -> "• "
                    }
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
    } // SelectionContainer
}

/**
 * True when the composable's pager page is the CURRENT page. Provided by
 * pager-based screens (repo detail tabs); defaults to true for standalone
 * usage outside a pager. SelectionContainer reads it to clear the active
 * selection (and dismiss the floating copy toolbar) when the user swipes
 * away — with beyondViewportPageCount>0 the old page stays composed and
 * would otherwise keep the toolbar on screen.
 */
val LocalPagerPageActive = androidx.compose.runtime.compositionLocalOf { true }

/**
 * SelectionContainer that drops its selection when [LocalPagerPageActive]
 * turns false (the host pager page was swiped away).
 *
 * This Compose version exposes only `SelectionContainer(modifier)` — the
 * selection-hoisting overload is internal, so there is no API to clear a
 * selection. Instead the container LEAVES COMPOSITION while the page is not
 * current (a plain Box keeps rendering the content), which discards the
 * selection state and the floating copy toolbar. Content state survives the
 * switch via [movableContentOf] (e.g. the markdown document's produceState
 * would otherwise re-parse on every activation).
 */
@Composable
fun PagerAwareSelectionContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val active = LocalPagerPageActive.current
    val currentContent by androidx.compose.runtime.rememberUpdatedState(content)
    val movable = androidx.compose.runtime.remember {
        androidx.compose.runtime.movableContentOf { currentContent() }
    }
    // The platform floating text toolbar (FloatingActionMode) only takes a
    // position when CREATED — Compose's SelectionManager re-shows it with
    // actionMode.invalidate(), which refreshes the menu but NOT the position,
    // so the toolbar stays wherever the PREVIOUS selection put it
    // (probabilistic drift). Wrap the default toolbar: force a hide() before
    // every showMenu so a fresh ActionMode — and position — is created each
    // time.
    val defaultToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
    val repositioningToolbar = androidx.compose.runtime.remember(defaultToolbar) {
        RepositioningTextToolbar(defaultToolbar)
    }
    if (active) {
        androidx.compose.foundation.text.selection.SelectionContainer(modifier = modifier) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalTextToolbar provides repositioningToolbar,
            ) { movable() }
        }
    } else {
        // Page swiped away: no container → selection + toolbar discarded.
        androidx.compose.foundation.layout.Box(modifier) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalTextToolbar provides repositioningToolbar,
            ) { movable() }
        }
    }
}

/** [TextToolbar] wrapper that re-creates the platform menu on every show —
 *  see the comment in [PagerAwareSelectionContainer]. */
private class RepositioningTextToolbar(
    private val inner: androidx.compose.ui.platform.TextToolbar,
) : androidx.compose.ui.platform.TextToolbar {
    override val status: androidx.compose.ui.platform.TextToolbarStatus
        get() = inner.status

    override fun showMenu(
        rect: androidx.compose.ui.geometry.Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        if (inner.status == androidx.compose.ui.platform.TextToolbarStatus.Shown) inner.hide()
        inner.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    override fun hide() = inner.hide()
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
        "TIP" -> "Tip" to semanticColors().success
        "IMPORTANT" -> "Important" to semanticColors().merged
        "WARNING" -> "Warning" to semanticColors().warning
        "CAUTION" -> "Caution" to semanticColors().danger
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
    /** `centered`: carried from <div align="center"> wrappers (hero areas). */
    data class Heading(val level: Int, val text: String, val centered: Boolean = false) : MdBlock()
    data class Paragraph(val text: String, val centered: Boolean = false) : MdBlock()
    data class CodeBlock(val code: String, val lang: String?) : MdBlock()
    data class Blockquote(val text: String) : MdBlock()
    /**
     * GFM blockquote whose body re-parses into real blocks (lists/headings/
     * fences inside a quote). Simple quotes stay [Blockquote].
     */
    data class QuoteBlocks(val blocks: List<MdBlock>) : MdBlock()
    /** GFM alert (`> [!NOTE]` etc). kind: NOTE/TIP/IMPORTANT/WARNING/CAUTION. */
    data class Alert(val kind: String, val text: String) : MdBlock()
    /** `task`: null = not a task item; ' ' = unchecked; 'x' = checked. */
    data class ListItem(val text: String, val ordered: Boolean, val index: Int, val level: Int, val task: Char? = null) : MdBlock()
    /** alignments per column: 0 left, 1 center, 2 right (from the `:---:` separator row). */
    data class Table(val headers: List<String>, val rows: List<List<String>>, val alignments: List<Int> = emptyList(), val hasHeader: Boolean = true) : MdBlock()
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

    /** All inputs of the off-main inline pass — one equality-checkable key. */
    internal data class Ctx(
        val resolver: com.pockethub.ui.markdown.LinkResolver,
        val imageResolver: com.pockethub.ui.markdown.ImageResolver,
        val codeBackgroundColor: androidx.compose.ui.graphics.Color,
        val linkColor: androidx.compose.ui.graphics.Color,
        val downloadColor: androidx.compose.ui.graphics.Color,
        val imageLinkColor: androidx.compose.ui.graphics.Color,
        val externalColor: androidx.compose.ui.graphics.Color,
    )
}

/** Result of the combined block+inline parse: pre-tokenized blocks or the parse error. */
private data class ParsedDoc(
    val blocks: List<Pair<MdBlock, List<InlineToken>>>,
    val error: Throwable?,
)
