package com.pockethub.ui.markdown

// Markdown rendering composables: inline parts, paragraphs, images, quotes,
// lists, tables + annotated-string inline builder. Split out of MarkdownText.kt.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.pockethub.R
import com.pockethub.ui.LocalAppImageLoader
import com.pockethub.ui.components.PhAsyncImage

// Pre-compiled hot-path patterns for the inline tokenizer. These used to be
// constructed inside the per-character loop of emitInline — one Pattern.compile
// per word start / @ / # occurrence is disastrous on long PR bodies (main
// thread, during composition). Compiled once here instead.
private val EMAIL_AUTOLINK_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")
private val ISSUE_REF_REGEX = Regex("#(\\d+)")
private val MENTION_REGEX = Regex("@[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})")
private val IMG_ALT_DIMENSION_REGEX = Regex("(\\d+)x(\\d+)")

@Composable
internal fun RenderInlineParts(parts: List<InlineToken>, style: androidx.compose.ui.text.TextStyle, onTap: (String, LinkKind) -> Unit) {
    parts.forEach { part ->
        when (part) {
            is InlineToken.Text -> ClickableText(
                text = part.span,
                style = style.copy(color = MaterialTheme.colorScheme.onSurface),
                onClick = { offset ->
                    part.span.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { annotation ->
                        val kind = part.span.getStringAnnotations(LINK_KIND_TAG, offset, offset)
                            .firstOrNull()?.item?.let { runCatching { LinkKind.valueOf(it) }.getOrNull() }
                            ?: LinkKind.EXTERNAL
                        onTap(annotation.item, kind)
                    }
                },
            )
            is InlineToken.Image -> RenderImageRun(listOf(part), onTap)
        }
    }
}

@Composable
internal fun RichParagraph(parts: List<InlineToken>, onTap: (String, LinkKind) -> Unit, paragraphSpacing: androidx.compose.ui.unit.Dp = 3.dp) {
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
 * Render a run of adjacent images the way a phone-sized web page would:
 * every image adapts to its intrinsic (or HTML-hinted) size — small badges
 * sit inline next to each other, banners/screenshots take the full width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RenderImageRun(images: List<InlineToken.Image>, onTap: (String, LinkKind) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        images.forEach { img -> AdaptiveImage(img, onTap) }
    }
}

/** Alt-suffix parser for the "alt|WxH" hint written by cleanSegment's <img> conversion. */
private fun splitAltHint(alt: String): Triple<String, Int?, Int?> {
    val marker = alt.indexOf('\u0001')
    if (marker == -1) return Triple(alt, null, null)
    val display = alt.substring(0, marker)
    val m = IMG_ALT_DIMENSION_REGEX.find(alt, marker + 1)
        ?: return Triple(display, null, null)
    return Triple(display, m.groupValues[1].toIntOrNull(), m.groupValues[2].toIntOrNull())
}

/** Segments of a <picture> alt produced by cleanSegment: "alt\u0001WxH\u0003darkSrc\u0002lightSrc". */
private data class PictureAlt(val base: String, val w: Int?, val h: Int?, val darkSrc: String?, val lightSrc: String?)

private fun splitPictureAlt(alt: String): PictureAlt {
    val p1 = alt.indexOf('\u0001')
    val p3 = alt.indexOf('\u0003')
    val baseEnd = if (p1 >= 0) p1 else if (p3 >= 0) p3 else alt.length
    val base = alt.substring(0, baseEnd)
    var w: Int? = null
    var h: Int? = null
    if (p1 in 0 until p3.coerceAtLeast(alt.length)) {
        IMG_ALT_DIMENSION_REGEX.find(alt, p1 + 1)?.let {
            w = it.groupValues[1].toIntOrNull(); h = it.groupValues[2].toIntOrNull()
        }
    }
    var dark: String? = null
    var light: String? = null
    if (p3 >= 0) {
        val pair = alt.substring(p3 + 1)
        val p2 = pair.indexOf('\u0002')
        if (p2 >= 0) {
            dark = pair.substring(0, p2).trim().takeIf { it.isNotBlank() }
            light = pair.substring(p2 + 1).trim().takeIf { it.isNotBlank() }
        }
    }
    return PictureAlt(base, w, h, dark, light)
}

/** Badge/small-image threshold: web badges are ~20 CSS px tall. */
private val SMALL_IMAGE_MAX_DP = 48.dp
private val MEDIUM_IMAGE_MAX_DP = 160.dp

/**
 * Resolve a URL's intrinsic pixel size through the app image loader. The
 * decode hits the memory cache, so the follow-up render is free.
 *
 * Three outcomes (was: null for BOTH error and success-without-size, which
 * spun the placeholder forever on SVGs lacking width/height attributes —
 * androidsvg can't derive an intrinsic size from viewBox-only files — and
 * on any load failure):
 *  - loaded=true,  size=w/h    → normal bucketing
 *  - loaded=true,  size=null   → SVG/unknown-size success → full-width render
 *  - loaded=false             → failed → hand to RenderContentImage, whose
 *                                error slot shows the broken-image row
 */
private data class ImageMeta(val loaded: Boolean, val size: androidx.compose.ui.unit.IntSize?)

@Composable
private fun rememberImageMeta(src: String): ImageMeta {
    val loader = LocalAppImageLoader.current
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.produceState<ImageMeta>(
        initialValue = ImageMeta(false, null),
        key1 = src,
    ) {
        val request = coil.request.ImageRequest.Builder(context)
            .data(src)
            // NOTE: keep the default hardware setting — the render pass below
            // issues the identical request and must hit the memory cache.
            .build()
        val result = loader.execute(request)
        val drawable = (result as? coil.request.SuccessResult)?.drawable
        val w = drawable?.intrinsicWidth ?: 0
        val h = drawable?.intrinsicHeight ?: 0
        value = if (drawable != null) {
            ImageMeta(true, if (w > 0 && h > 0) androidx.compose.ui.unit.IntSize(w, h) else null)
        } else {
            ImageMeta(false, null)
        }
    }.value
}

@Composable
private fun rememberIntrinsicImageSize(src: String): androidx.compose.ui.unit.IntSize? =
    rememberImageMeta(src).size

/**
 * Display policy for README images, tuned against a 3.4k-image corpus of real
 * READMEs. Web rule of thumb: 1 image pixel == 1 CSS px == 1 dp, so intrinsic
 * sizes are applied 1:1 in dp (NOT px.toDp() — dividing by density shrank every
 * 1x asset and mis-sized 2x ones, which is what made badges go haywire).
 *
 * Buckets:
 *  1. Badge hosts (shields.io, badgen, CI…) → 20dp-tall inline strip.
 *  2. HTML width/height hints (contributors grids, resized screenshots).
 *  3. Small images (intrinsic height ≤ 48dp) → natural size, inline, no card.
 *  4. Medium images (≤ 160dp, logos/icons) → natural size capped, no card.
 *  5. Everything else (banners/screenshots) → full-width, rounded.
 */
@Composable
private fun AdaptiveImage(img: InlineToken.Image, onTap: (String, LinkKind) -> Unit) {
    val clickTarget = img.wrapUrl ?: img.src
    val kind = if (img.wrapUrl != null) classifyLink(img.wrapUrl) else LinkKind.IMAGE_URL
    // <picture> dark/light variants: GitHub swaps by prefers-color-scheme.
    val pic = splitPictureAlt(img.alt)
    val themeSrc = if (isSystemInDarkTheme()) pic.darkSrc ?: img.src else pic.lightSrc ?: img.src
    val effectiveImg = if (themeSrc != img.src) img.copy(src = themeSrc, alt = pic.base) else img
    val meta = rememberImageMeta(effectiveImg.src)
    val intrinsic = meta.size

    when {
        isBadgeUrl(effectiveImg.src) -> {
            val aspect = intrinsic?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 0f
            val h = 20.dp
            val w = if (aspect > 0f) (h * aspect).coerceIn(24.dp, 300.dp) else 96.dp
            RenderStripImage(effectiveImg, w, h, clickTarget, kind, onTap)
        }
        (img.hintW != null && img.hintH != null && img.hintW > 0 && img.hintH > 0) ||
            (pic.w != null && pic.h != null && pic.w > 0 && pic.h > 0) -> {
            val wDp = (img.hintW ?: pic.w ?: 0).dp.coerceAtMost(320.dp)
            val hDp = (img.hintH ?: pic.h ?: 0).dp.coerceAtMost(360.dp)
            RenderSizedImage(effectiveImg, wDp, hDp, clickTarget, kind, onTap)
        }
        // Load FAILED (or no size yet): RenderContentImage re-issues the same
        // request — failure shows its broken-image row instead of spinning.
        meta.loaded == false -> RenderContentImage(effectiveImg, clickTarget, kind, onTap)
        // Decoded fine but has no intrinsic size (SVG with viewBox only):
        // SvgDrawable rasterizes at draw size, so full-width stays sharp.
        intrinsic == null -> RenderContentImage(effectiveImg, clickTarget, kind, onTap)
        else -> {
            // 1 image px == 1 dp, mirroring how a phone browser lays these out.
            val hDp = intrinsic.height.dp
            val wDp = intrinsic.width.dp
            when {
                hDp <= SMALL_IMAGE_MAX_DP -> RenderStripImage(
                    effectiveImg,
                    wDp.coerceIn(12.dp, 300.dp),
                    hDp.coerceAtLeast(12.dp),
                    clickTarget, kind, onTap,
                )
                hDp <= MEDIUM_IMAGE_MAX_DP -> RenderStripImage(
                    effectiveImg,
                    wDp.coerceIn(24.dp, 320.dp),
                    hDp.coerceAtMost(MEDIUM_IMAGE_MAX_DP),
                    clickTarget, kind, onTap,
                )
                else -> RenderContentImage(effectiveImg, clickTarget, kind, onTap)
            }
        }
    }
}

/** Badge / small logo: bare inline image, no background card, no rounded clip. */
@Composable
private fun RenderStripImage(
    img: InlineToken.Image,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    clickTarget: String,
    kind: LinkKind,
    onTap: (String, LinkKind) -> Unit,
) {
    SubcomposeAsyncImage(
        model = img.src,
        imageLoader = LocalAppImageLoader.current,
        contentDescription = img.alt.takeIf { it.isNotBlank() },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .width(width)
            .height(height)
            .clickable { onTap(clickTarget, kind) },
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.dp)
            }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.BrokenImage, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
        },
    )
}

/** Author-specified size (<img width height> / alt|WxH): respect it, lightly capped. */
@Composable
private fun RenderSizedImage(
    img: InlineToken.Image,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    clickTarget: String,
    kind: LinkKind,
    onTap: (String, LinkKind) -> Unit,
) {
    SubcomposeAsyncImage(
        model = img.src,
        imageLoader = LocalAppImageLoader.current,
        contentDescription = img.alt.takeIf { it.isNotBlank() },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onTap(clickTarget, kind) },
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.BrokenImage, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        },
    )
}

/** Big banner / screenshot: full-width, rounded, tappable. */
@Composable
private fun RenderContentImage(
    img: InlineToken.Image,
    clickTarget: String,
    kind: LinkKind,
    onTap: (String, LinkKind) -> Unit,
) {
    SubcomposeAsyncImage(
        model = img.src,
        imageLoader = LocalAppImageLoader.current,
        contentDescription = img.alt.takeIf { it.isNotBlank() },
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 360.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onTap(clickTarget, kind) },
        loading = {
            Box(
                Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        },
        error = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.BrokenImage, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    img.alt.ifBlank { "image" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

// ── Tables ───────────────────────────────────────────────────────────

/** Max rows rendered before the "show all rows" affordance appears. */
private const val TABLE_ROW_CAP = 50

@Composable
internal fun TableBlock(
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
    // Column count comes from headers AND rows — headerless HTML grids
    // (screenshot/logo walls) carry empty headers but multi-cell rows.
    val colCount = (table.rows.maxOfOrNull { it.size } ?: 0)
        .coerceAtLeast(table.headers.size).coerceAtLeast(1)
    // Content-weighted column widths for the scrolled layout. Equal
    // weight(1f) crushed 4+ column tables (10% of corpus): a 2-char column
    // got the same width as a 60-char one.
    val colWidths = remember(table) { columnWidths(table, colCount) }
    val needsScroll = colCount > 3
    // Row cap: a 1000-row table eagerly composed ~1000 Rows of ClickableText
    // on first layout = seconds of jank on low-end phones. 50 rows render
    // instantly; deeper rows unfold on tap.
    var expanded by remember(table) { androidx.compose.runtime.mutableStateOf(false) }
    val visibleRows = if (expanded || table.rows.size <= TABLE_ROW_CAP) table.rows else table.rows.take(TABLE_ROW_CAP)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .then(if (needsScroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
    ) {
        if (table.hasHeader) {
            Row(Modifier.fillMaxWidth().background(headerBg)) {
                table.headers.forEachIndexed { col, cell ->
                    val parts = rememberRichInline(cell, resolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    TableCell(parts, if (needsScroll) Modifier.width(colWidths[col.coerceAtMost(colWidths.size - 1)].dp) else Modifier.width(0.dp).weight(1f), bold = true, align = table.alignments.getOrNull(col) ?: 0, onTap = onTap)
                }
            }
            HorizontalDivider(color = borderColor)
        }
        visibleRows.forEach { row ->
            val padded = (row + List((colCount - row.size).coerceAtLeast(0)) { "" }).take(colCount)
            Row(Modifier.fillMaxWidth()) {
                padded.forEachIndexed { col, cell ->
                    val parts = rememberRichInline(cell, resolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
                    TableCell(parts, if (needsScroll) Modifier.width(colWidths[col.coerceAtMost(colWidths.size - 1)].dp) else Modifier.width(0.dp).weight(1f), bold = false, align = table.alignments.getOrNull(col) ?: 0, onTap = onTap)
                }
            }
            HorizontalDivider(color = borderColor.copy(alpha = 0.4f))
        }
        if (!expanded && table.rows.size > TABLE_ROW_CAP) {
            Text(
                "+${table.rows.size - TABLE_ROW_CAP} rows — tap to expand",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(10.dp),
            )
        }
    }
}

/**
 * Per-column display width in dp for the horizontally-scrolling layout:
 * the longest cell in the first ~30 rows drives the width, discounted for
 * markdown syntax chars, clamped so extreme columns can't dominate.
 */
private fun columnWidths(table: MdBlock.Table, colCount: Int): List<Int> {
    val w = IntArray(colCount)
    table.headers.forEachIndexed { i, c -> if (i < colCount) w[i] = c.length }
    for (row in table.rows.take(30)) {
        row.forEachIndexed { i, c -> if (i < colCount) w[i] = maxOf(w[i], c.length) }
    }
    return w.map { len ->
        val text = (len * 0.7f).toInt() // discount ![]() ** `` syntax noise
        (text * 7).coerceIn(56, 220)
    }
}

@Composable
internal fun TableCell(
    parts: List<InlineToken>,
    modifier: Modifier,
    bold: Boolean,
    align: Int = 0,
    onTap: (String, LinkKind) -> Unit,
) {
    // Image cells (screenshot grids / logo walls from HTML tables): the old
    // code appended only Text tokens — every image inside a cell silently
    // vanished. RichParagraph renders image runs + text together.
    if (parts.any { it is InlineToken.Image }) {
        Column(modifier.padding(4.dp)) {
            RichParagraph(parts, onTap, paragraphSpacing = 0.dp)
        }
        return
    }
    val span = buildAnnotatedString {
        parts.forEach { if (it is InlineToken.Text) append(it.span) }
    }
    ClickableText(
        text = span,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = when (align) {
                1 -> TextAlign.Center
                2 -> TextAlign.End
                else -> TextAlign.Start
            },
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

internal fun renderRichInline(
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

    /**
     * Flush before an image — except drop pure-whitespace buffers that sit
     * between two images, so a wall of badges stays one adjacent run and
     * renders as a single FlowRow of inline-sized images.
     */
    fun flushImageGap() {
        if (textBuffer.isNotBlank()) {
            flushText()
        } else if (out.lastOrNull() is InlineToken.Image) {
            textBuffer.clear()
        } else {
            flushText()
        }
    }

    /**
     * Resolve the <picture> dark/light variant URLs embedded in the alt by
     * cleanSegment ("alt\u0001WxH\u0003dark\u0002light") through the same
     * imageResolver as the main src, so relative "./dark.png" variants load.
     */
    fun resolvePictureAlt(alt: String): String {
        val p3 = alt.indexOf('\u0003')
        if (p3 == -1) return alt
        val head = alt.substring(0, p3)
        val pair = alt.substring(p3 + 1)
        val p2 = pair.indexOf('\u0002')
        val dark = pair.substring(0, p2.coerceAtLeast(0)).trim()
        val light = pair.substring(p2 + 1).trim().takeIf { p2 >= 0 }
        return head + "\u0003" +
            dark.takeIf { it.isNotBlank() }?.let { imageResolver(it) }.orEmpty() +
            "\u0002" +
            light?.let { imageResolver(it) }.orEmpty()
    }

    var i = 0
    val len = text.length
    while (i < len) {
        val rest = text.substring(i)
        // Try wrapped image link [![alt](src)](href) — only if it begins at i.
        val wrappedMatch = WRAPPED_IMG_PATTERN.find(rest)
        if (wrappedMatch != null) {
            flushImageGap()
            val (alt, hw, hh) = splitAltHint(wrappedMatch.groupValues[1])
            val src = imageResolver(wrappedMatch.groupValues[2].trim())
            val href = wrappedMatch.groupValues[3].trim()
            val resolvedHref = resolver(href) ?: href
            out.add(InlineToken.Image(src = src, alt = resolvePictureAlt(alt), wrapUrl = resolvedHref, hintW = hw, hintH = hh))
            i += wrappedMatch.value.length
            continue
        }
        // Try standalone image ![alt](src)
        val imgMatch = STANDALONE_IMG_PATTERN.find(rest)
        if (imgMatch != null) {
            flushImageGap()
            val (alt, hw, hh) = splitAltHint(imgMatch.groupValues[1])
            val src = imageResolver(imgMatch.groupValues[2].trim())
            out.add(InlineToken.Image(src = src, alt = resolvePictureAlt(alt), wrapUrl = null, hintW = hw, hintH = hh))
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

/**
 * Composition-scoped cache for [renderRichInline]. Inline tokenization is pure
 * text→tokens work, but it runs on the main thread during composition; without
 * this, every recomposition of a markdown block (image load finishing, any
 * upstream state change) re-parses the whole paragraph. The resolvers are
 * themselves remembered upstream, so they are stable keys.
 */
@Composable
internal fun rememberRichInline(
    text: String,
    resolver: LinkResolver,
    imageResolver: ImageResolver,
    codeBackgroundColor: Color,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    externalColor: Color,
): List<InlineToken> = remember(
    text, resolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor,
) {
    renderRichInline(text, resolver, imageResolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
}

/** Markdown punctuation that can be backslash-escaped outside code spans. */
private val ESCAPABLE_CHARS = "\\`*_{}[]()<>#+-.!|~".toSet()

internal fun stringFromSource(
    src: String,
    resolver: LinkResolver,
    codeBackgroundColor: Color,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    externalColor: Color,
): AnnotatedString = buildAnnotatedString {
    emitInline(src, resolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
}

/**
 * Inline tokenizer. Emphasis branches recurse on their inner content so nested
 * markup (a link inside **bold**, code inside _italic_, bold inside ~~strike~~)
 * renders properly instead of leaking raw characters.
 */
private fun AnnotatedString.Builder.emitInline(
    src: String,
    resolver: LinkResolver,
    codeBackgroundColor: Color,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    externalColor: Color,
) {
    /** Closing [marker] index at/after [from]; for `_` markers the char after must not be a word char (GFM intraword rule). */
    fun closeOf(from: Int, marker: String, wordBoundaryAfter: Boolean): Int? {
        var idx = from
        while (true) {
            idx = src.indexOf(marker, idx)
            if (idx == -1) return null
            val after = idx + marker.length
            if (wordBoundaryAfter && after < src.length && src[after].isLetterOrDigit()) { idx = after; continue }
            return idx
        }
    }

    fun emit(inner: String, style: SpanStyle) = withStyle(style) {
        emitInline(inner, resolver, codeBackgroundColor, linkColor, downloadColor, imageLinkColor, externalColor)
    }

    var i = 0
    while (i < src.length) {
        // Escaped markdown punctuation: \* \_ \[ … → literal character
        if (src[i] == '\\' && i + 1 < src.length && src[i + 1] in ESCAPABLE_CHARS) {
            append(src[i + 1]); i += 2; continue
        }
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
                // URL may contain balanced parentheses (CommonMark allows them;
                // GitHub PR titles end up in links all the time). A naive
                // indexOf(')') closed the destination early and the leftover
                // "...)(tail)" leaked into the visible text — swallowing chars.
                var closeParen = -1
                var depth = 0
                var k = closeBracket + 2
                while (k < src.length) {
                    when (src[k]) {
                        '\\' -> k++          // skip escaped char
                        '(' -> depth++
                        ')' -> { if (depth == 0) break; depth-- }
                    }
                    k++
                }
                closeParen = if (k < src.length) k else -1
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
        // Bare URL / www.* autolink
        if (src.regionMatches(i, "https://", 0, 8, ignoreCase = false) ||
            src.regionMatches(i, "http://", 0, 7, ignoreCase = false)) {
            val end = findUrlEnd(src, i)
            if (end > i) {
                val url = src.substring(i, end)
                appendLink(url, url, classifyLink(url), linkColor, downloadColor, imageLinkColor, externalColor)
                i = end; continue
            }
        }
        if (src.regionMatches(i, "www.", 0, 4)) {
            val end = findUrlEnd(src, i)
            if (end > i + 4) {
                val url = "https://${src.substring(i, end)}"
                appendLink(url, url, classifyLink(url), linkColor, downloadColor, imageLinkColor, externalColor)
                i = end; continue
            }
        }
        // Email autolink (GFM): bare user@host.tld, optionally in <…>.
        // Requires a dotted domain so @user mentions are unaffected; the
        // previous-char guard prevents matching mid-word. Checked before the
        // #123 / @user shortcuts.
        if (src[i] == '@' || src[i].isLetterOrDigit()) {
            val preceded = i > 0 && (src[i - 1].isLetterOrDigit() || src[i - 1] == '.' || src[i - 1] == '@')
            if (!preceded) {
                // find(src, i) anchors the ^-anchored pattern at i without
                // substring-copying the rest of the document (was O(n²) allocs).
                val m = EMAIL_AUTOLINK_REGEX.find(src, i)?.takeIf { it.range.first == i }
                if (m != null) {
                    val email = m.value.trimEnd('.')
                    appendLink("mailto:$email", "mailto:$email", LinkKind.EXTERNAL, linkColor, downloadColor, imageLinkColor, externalColor)
                    i += email.length; continue
                }
            }
        }
        // GitHub shortcut #123 / @user
        if (src[i] == '#' || src[i] == '@') {
            val m = if (src[i] == '#') ISSUE_REF_REGEX.find(src, i)?.takeIf { it.range.first == i }
            else MENTION_REGEX.find(src, i)?.takeIf { it.range.first == i }
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
        // GFM emphasis — ***x*** / **x** / *x* / __x__ / _x_ (recursive).
        // Asterisk branches require the char after the opening marker not to be
        // whitespace, so "a * b * c" stays plain text.
        if (src.startsWith("***", i)) {
            val close = closeOf(i + 3, "***", wordBoundaryAfter = false)
            if (close != null && close > i + 3) {
                emit(src.substring(i + 3, close), SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                i = close + 3; continue
            }
        }
        if (src.startsWith("**", i)) {
            val close = closeOf(i + 2, "**", wordBoundaryAfter = false)
            if (close != null && close > i + 2 && !src.startsWith(" ", i + 2)) {
                emit(src.substring(i + 2, close), SpanStyle(fontWeight = FontWeight.Bold))
                i = close + 2; continue
            }
        }
        if (src[i] == '*' && src.getOrElse(i + 1) { ' ' } != '*' && !src.startsWith(" ", i + 1)) {
            val close = closeOf(i + 1, "*", wordBoundaryAfter = false)
            if (close != null && close > i + 1) {
                emit(src.substring(i + 1, close), SpanStyle(fontStyle = FontStyle.Italic))
                i = close + 1; continue
            }
        }
        if (src.startsWith("__", i) && (i == 0 || !src[i - 1].isLetterOrDigit())) {
            val close = closeOf(i + 2, "__", wordBoundaryAfter = true)
            if (close != null && close > i + 2 && !src.startsWith(" ", i + 2)) {
                emit(src.substring(i + 2, close), SpanStyle(fontWeight = FontWeight.Bold))
                i = close + 2; continue
            }
        }
        if (src[i] == '_' && (i == 0 || !src[i - 1].isLetterOrDigit()) && !src.startsWith(" ", i + 1)) {
            val close = closeOf(i + 1, "_", wordBoundaryAfter = true)
            if (close != null && close > i + 1) {
                emit(src.substring(i + 1, close), SpanStyle(fontStyle = FontStyle.Italic))
                i = close + 1; continue
            }
        }
        // Strikethrough ~~text~~ (recursive so inner emphasis still renders)
        if (src.startsWith("~~", i)) {
            val close = closeOf(i + 2, "~~", wordBoundaryAfter = false)
            if (close != null && close > i + 2) {
                emit(src.substring(i + 2, close), SpanStyle(textDecoration = TextDecoration.LineThrough))
                i = close + 2; continue
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

internal fun AnnotatedString.Builder.appendLink(
    displayText: String,
    url: String,
    kind: LinkKind,
    linkColor: Color,
    downloadColor: Color,
    imageLinkColor: Color,
    externalColor: Color,
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
        LinkKind.IMAGE -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        // Off-github links get a different hue so users can tell in-app
        // navigation from "this opens in a browser" before tapping.
        LinkKind.EXTERNAL -> SpanStyle(color = externalColor, textDecoration = TextDecoration.Underline)
    }
    addStyle(style, start, start + displayText.length)
    append(displayText)
}

internal fun findUrlEnd(text: String, start: Int): Int {
    var end = start
    while (end < text.length) {
        val c = text[end]
        if (c.isWhitespace() || c in setOf(')', ']', '}', '<', '>', '"', '\'', '|')) break
        end++
    }
    while (end > start + 1 && text[end - 1] in setOf('.', ',', ';', ':', '!', '?')) end--
    return end
}
