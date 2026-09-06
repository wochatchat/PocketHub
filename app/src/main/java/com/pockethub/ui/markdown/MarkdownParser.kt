package com.pockethub.ui.markdown

// Markdown preprocessing + block parser: cleaning, table detection,
// truncation and block segmentation. Split out of MarkdownText.kt.

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

internal fun cleanMarkdown(markdown: String): String {
    // Normalize CRLF first so line-based parsing (headings, lists, tables)
    // doesn't trip over stray \r characters from Windows-edited READMEs.
    val normalized = markdown.replace("\r\n", "\n")
    // Protect fenced code blocks — HTML cleaning / entity decoding must not
    // rewrite code samples (a `<!-- -->` or `<b>` inside a fence is content).
    // Backreference so an opening fence of N+ markers only closes at a run of
    // the same length — nested triple-backtick fences inside quadruple fences
    // (common in "how to write markdown" docs) stay protected as content.
    val fenceRegex = Regex("(`{3,})[\\s\\S]*?\\1|(~{3,})[\\s\\S]*?\\2")
    val parts = mutableListOf<String>()
    var last = 0
    for (m in fenceRegex.findAll(normalized)) {
        parts.add(cleanSegment(normalized.substring(last, m.range.first)))
        parts.add(m.value)
        last = m.range.last + 1
    }
    parts.add(cleanSegment(normalized.substring(last)))
    return parts.joinToString("")
}

private fun cleanSegment(markdown: String): String {
    return markdown
            // Strip HTML comments (<!-- … -->) — README sections use them to
            // organize badge blocks; they must never surface as literal text.
            .replace(Regex("<!--[\\s\\S]*?-->"), "")
            // ── HTML <picture> — resolved BEFORE <img> handling so the dark/
            // light variant picker picks the right source per theme. GitHub
            // swaps these by prefers-color-scheme; we mark the theme side with
            // an alt suffix ("#dark"/"#light") that the renderer resolves.
            // Structure: <picture><source media="(prefers-color-scheme: dark)"
            // srcset="…"/><source …light…/><img src="fallback"></picture>
            .replace(
                Regex(
                    "<picture\\b[^>]*>(.*?)</\\s*picture\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
            ) { m ->
                val inner = m.groupValues[1]
                val dark = Regex("<source\\b[^>]*media\\s*=\\s*[\"'][^\"']*dark[^\"']*[\"'][^>]*srcset\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                    .find(inner)?.groupValues?.getOrNull(1)
                    ?: Regex("<source\\b[^>]*srcset\\s*=\\s*[\"']([^\"']+)[\"'][^>]*media\\s*=\\s*[\"'][^\"']*dark[^\"']*[\"']", RegexOption.IGNORE_CASE)
                        .find(inner)?.groupValues?.getOrNull(1)
                val light = Regex("<source\\b[^>]*media\\s*=\\s*[\"'][^\"']*light[^\"']*[\"'][^>]*srcset\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                    .find(inner)?.groupValues?.getOrNull(1)
                    ?: Regex("<source\\b[^>]*srcset\\s*=\\s*[\"']([^\"']+)[\"'][^>]*media\\s*=\\s*[\"'][^\"']*light[^\"']*[\"']", RegexOption.IGNORE_CASE)
                        .find(inner)?.groupValues?.getOrNull(1)
                val fallback = Regex("<img\\b[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                    .find(inner)?.groupValues?.getOrNull(1)
                // First <img> inside the picture may carry width/height hints —
                // the generic <img> conversion below no longer sees this block,
                // so harvest them here into the alt-hint suffix.
                val firstImg = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).find(inner)?.value
                val dim = firstImg?.let {
                    fun sizeOf(attr: String): String? {
                        val m = Regex("$attr\\s*=\\s*[\"']([^\"']*)[\"']|$attr\\s*=\\s*([^\\s>]+)", RegexOption.IGNORE_CASE).find(it) ?: return null
                        val v = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim()
                        if (v.isEmpty() || v.endsWith("%")) return null
                        return v.removeSuffix("px").toFloatOrNull()?.roundToInt()?.coerceIn(1, 4000)?.toString()
                    }
                    val w = sizeOf("width")
                    val h = sizeOf("height")
                    if (w != null && h != null) "\u0001${w}x${h}" else ""
                }.orEmpty()
                val src = dark ?: light ?: fallback
                val altSrc = when {
                    dark != null && light != null -> "$dark\u0002$light"
                    else -> null
                }
                if (src != null) "![picture$dim\u0003${altSrc.orEmpty()}](${src})" else ""
            }
            // ── GitHub <g-emoji> custom element — GitHub's web frontend wraps
            // emoji in <g-emoji alias="bulb" fallback-src="…">💡</g-emoji>. No
            // cleaner rule covered it, so the raw tag markup leaked into list
            // items (logseq-style TOCs). Keep the inner emoji (it is already a
            // literal char); for an empty element fall back to the alias via
            // the shortcode map. Must run before the <a> conversion so links
            // like [<g-emoji>🚀</g-emoji> Title](#anchor) arrive with clean
            // link text.
            .replace(
                Regex("<g-emoji\\b[^>]*>([\\s\\S]*?)</\\s*g-emoji\\s*>", RegexOption.IGNORE_CASE),
            ) { m ->
                val inner = m.groupValues[1].trim()
                if (inner.isNotEmpty()) {
                    inner
                } else {
                    Regex("alias\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                        .find(m.value)?.groupValues?.getOrNull(1)
                        ?.let { EMOJI_SHORTCODES[it] } ?: ""
                }
            }
            // <img src> → markdown, PRESERVING the HTML width/height hints as an
            // alt-suffix ("alt|WxH") — the renderer uses them to size the image
            // like the web page did (banners at ~250dp, badges at ~20dp) instead
            // of blowing every image up to phone-screen width.
            .replace(
                Regex(
                    "<\\s*img\\s+[^>]*?src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?(?:alt\\s*=\\s*[\"']([^\"']*)[\"'])?[^>]*?/?>",
                    RegexOption.IGNORE_CASE,
                )
            ) { m ->
                val src = m.groupValues[1]
                val alt = m.groupValues[2]
                val tag = m.value
                // Size hint extraction with VALUE-LEVEL parsing: pull the full
                // attribute value first ("100%", "300", "40px", "122.4") and
                // then classify. NOTE a bare regex with a (?!%) guard silently
                // BACKTRACKS on "100%" ((\d+) shrinks to "10", next char '0'
                // isn't '%' → matches) — which turned a full-width banner into
                // a 10dp speck. Percent values mean "fill the column": no
                // hint, the intrinsic bucket lays them out like the web.
                // ONE dimension alone also applies (width-only is MORE common
                // than w+h: 580 vs 192 in the corpus). Missing side → 0.
                fun sizeOf(attr: String): String? {
                    val m = Regex("$attr\\s*=\\s*[\"']([^\"']*)[\"']|$attr\\s*=\\s*([^\\s>]+)", RegexOption.IGNORE_CASE).find(tag) ?: return null
                    val v = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim()
                    if (v.isEmpty() || v.endsWith("%")) return null
                    val num = v.removeSuffix("px").toFloatOrNull() ?: return null
                    return num.roundToInt().coerceIn(1, 4000).toString()
                }
                val w = sizeOf("width")
                val h = sizeOf("height")
                val altOut = when {
                    w != null && h != null -> "$alt\u0001${w}x${h}"
                    w != null -> "$alt\u0001${w}x0"
                    h != null -> "$alt\u00010x${h}"
                    else -> alt
                }
                "![${altOut}](${src})"
            }
            // Strip common HTML block/inline tags (leave text between pairs) — but keep <a href>
            // as markdown so we don't lose navigation context for legacy README HTML.
            .replace(
                Regex(
                    "<\\s*a\\s+[^>]*?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>(.*?)<\\s*/\\s*a\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )
            ) { m ->
                // Collapse inner whitespace/newlines so multi-line anchors like
                // <a href="…">\n  <img …>\n</a> become a single-line markdown
                // link the inline tokenizer can match.
                "[${m.groupValues[2].replace(Regex("\\s+"), " ").trim()}](${m.groupValues[1]})"
            }
            // ── Decorator-wrapped image links: [**![flag](src) Label**](href)
            // (hiddify-style language switchers). Emphasis markers around the
            // image inside the link text defeat BOTH the wrapped-image
            // tokenizer (WRAPPED_IMG_PATTERN is adjacency-strict, so the
            // standalone-image pre-pass tears the image out of the link and
            // the surrounding "[**"/"](**" leak as raw text) and the link
            // branch (which would dump "![…]" raw inside link text).
            // Normalize to the flat wrapped form plus a separately linked
            // label: [![flag](src)](href) **[Label](href)** — visually the
            // same as GitHub (image + bold label, both clickable).
            .replace(
                Regex("\\[[ \\t]*([*_~]{1,2})[ \\t]*(!\\[[^\\]\n]*\\]\\([^)\n]*\\))[ \\t]*([^\\]\n]*?)[ \\t]*\\1[ \\t]*\\]\\(([^)\n]*)\\)")
            ) { m ->
                val dec = m.groupValues[1]
                val img = m.groupValues[2]
                val label = m.groupValues[3].trim()
                val href = m.groupValues[4]
                if (label.isEmpty()) "[$img]($href)" else "[$img]($href) ${dec}[$label]($href)$dec"
            }
            // ── New block-level handlers for tags the original cleaner did not
            // cover. Converting them to markdown keeps README prose scannable
            // instead of leaking literal <h1>/<li>/<blockquote> etc. Runs before
            // the inline-emphasis step so nested <strong>/<em> inside a heading
            // still get styled — the heading text is processed right after.
            // <h1>…<h6> → ATX headings ("# title").
            .replace(
                Regex(
                    "<\\s*h([1-6])\\b[^>]*>(.*?)<\\s*/\\s*h[1-6]\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )
            ) { m ->
                val level = m.groupValues[1].toInt()
                "\n${"#".repeat(level.coerceIn(1, 6))} ${m.groupValues[2].trim()}\n"
            }
            // <blockquote> → markdown "> " prefix per line so the existing
            // blockquote block parser picks it up.
            .replace(
                Regex(
                    "<\\s*blockquote\\b[^>]*>(.*?)<\\s*/\\s*blockquote\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )
            ) { m ->
                m.groupValues[1].trimIndent().lines().joinToString("\n") { "> ${it}".trimEnd() }
            }
            // <li> → markdown list item ("- item"). The <ol>/<ul> wrappers are
            // stripped later; this keeps each bullet on its own line so the
            // bullet parser renders it.
            .replace(
                Regex(
                    "<\\s*li\\b[^>]*>(.*?)<\\s*/\\s*li\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                )
            ) { m -> "\n- ${m.groupValues[1].trim()}" }
            // <script>/<style> → drop block + contents so raw JS/CSS doesn't
            // leak into rendered text. Must come before the catch-all tag strip.
            .replace(
                Regex(
                    "<\\s*(?:script|style)\\b[^>]*>.*?<\\s*/\\s*(?:script|style)\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
                "",
            )
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
            // ── HTML tables → native pipe-table blocks. 13% of popular READMEs
            // lay out sponsor walls / screenshot grids / comparison charts as
            // real <table> markup — flattening them to bare text destroyed
            // both the grid layout and the 4k+ images they embed. Runs AFTER
            // the <img>/<a> conversions so cells arrive as ready markdown.
            .replace(
                Regex(
                    "<table\\b[^>]*>([\\s\\S]*?)</\\s*table\\s*>",
                    setOf(RegexOption.IGNORE_CASE),
                ),
            ) { m -> htmlTableToMarkdown(m.value) }
            // <dl>/<dt>/<dd> → "**term**" + ": definition" lines (definition
            // lists appear in specs' READMEs; previously the tags were stripped
            // and the term/definition pairing collapsed into run-on text).
            .replace(
                Regex(
                    "<dt\\b[^>]*>(.*?)</\\s*dt\\s*>\\s*<dd\\b[^>]*>(.*?)</\\s*dd\\s*>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
            ) { m ->
                val term = m.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
                val def = m.groupValues[2].replace(Regex("\\s+"), " ").trim()
                "\n**$term**\n: $def"
            }
            // Any leftover <a> tags (anchors like <a name="readme-top"></a>,
            // or href links the conversion above couldn't parse) — drop the
            // tag, keep inner text. Also <picture>/<source> wrappers (the
            // inner <img> is converted separately).
            .replace(Regex("<\\s*/?\\s*a\\b[^>]*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<\\s*/?\\s*(?:picture|source)\\b[^>]*/?>", RegexOption.IGNORE_CASE), "")
            // Inline tags with no markdown equivalent — drop the tag, keep inner text.
            .replace(Regex("<\\s*/?(?:u|mark|small|big|font|sub|sup)\\b[^>]*>", RegexOption.IGNORE_CASE), "")
            // Block-level line breaks / rules → markdown forms (before the void-tag strip below).
            // br/hp open AND stray close forms (</br> appears in the wild —
            // logseq README): both are just a line break.
            .replace(Regex("<\\s*/?\\s*br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<\\s*/?\\s*hr\\s*/?>", RegexOption.IGNORE_CASE), "\n\n---\n\n")
            .replace(
                Regex("<\\s*(/?)\\s*(div|span|p|details|summary|center|section|article|figure|figcaption|picture|source|video|audio|table|thead|tbody|tr|td|th|pre|ol|ul|dl|dt|dd|caption|address)(\\s[^>]*)?>", RegexOption.IGNORE_CASE),
                "",
            )
            // Self-closing / void tags (img/br/hr already converted above; keep others stripped)
            .replace(
                Regex("<\\s*(br|hr|input|meta|link|area|base|col|embed|param|track|wbr)(\\s[^>]*)?/?>", RegexOption.IGNORE_CASE),
                "",
            )
            // ── New — strip the remaining common HTML block/inline tags the
            // original list above didn't cover, so the Overview README never
            // shows literal tags. We name them explicitly rather than using a
            // broad `<tag>` catch-all so that README prose like "if (a <b) …"
            // isn't misinterpreted as a tag and stripped. HTML in fenced code
            // blocks stays visible because cleanMarkdown's list doesn't
            // include the bare-forward bracket rule.
            .replace(
                Regex(
                    "<\\s*/?(?:iframe|canvas|noscript|ruby|rp|rt|form|fieldset|legend|label|button|select|option|optgroup|object|embed|var|samp|cite|q|abbr|dfn|time|ins|datalist|output|progress|meter|template|slot|dialog|menu|nav|header|footer|main|aside|hgroup|bdi|bdo|wbr|colgroup|col|map|area|math|svg|use|template|portal|slot)\\b[^>]*>",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            // ── Named-entity decoding — WHITELIST ONLY. The old approach
            // (&amp;→& first, then &lt;/&gt;/&quot;) ran ampersand-decode
            // before the bracket entities, corrupting every README link that
            // legitimately contains "&" in its URL: ?a=1&amp;b=2 became
            // ?a=1&b=2 → &b=2 → "&b=2" got re-consumed and deep links /
            // UTM-tagged sponsor URLs lost their query params. Decode the
            // bracket entities FIRST (no overlap with &amp;), then &amp; last,
            // and only touch a known named set — unknown entities (&#x1F600;
            // handled below, &xabc; etc.) stay literal, exactly like GitHub.
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&rarr;", "→")
            .replace("&larr;", "←")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&nbsp;", " ")
            .replace("&hellip;", "…")
            .replace("&times;", "×")
            .replace("&divide;", "÷")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace("&trade;", "™")
            .replace("&bull;", "•")
            .replace("&middot;", "·")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&ldquo;", "\u201C")
            .replace("&rdquo;", "\u201D")
            .replace("&lsquo;", "\u2018")
            .replace("&rsquo;", "\u2019")
            // ── GitHub emoji shortcodes (":tada:" → 🎉). 27% of popular
            // READMEs use them; GitHub renders the emoji. Only the known-name
            // whitelist converts — unknown ":foo:" stays literal.
            .replace(EMOJI_SHORTCODE_REGEX) { m -> EMOJI_SHORTCODES[m.groupValues[1]] ?: m.value }
            // ── Centered containers (<div align="center"> / <p align="center">)
            // — the standard hero/badge-wall wrapper (55% of corpus uses
            // align=). Content between the \u0006 markers renders centered;
            // images inside have already been converted to markdown above.
            .replace(
                Regex(
                    "<(?:div|p)\\b[^>]*align\\s*=\\s*[\"']center[\"'][^>]*>([\\s\\S]*?)</\\s*(?:div|p)\\s*>",
                    setOf(RegexOption.IGNORE_CASE),
                ),
            ) { m -> "\n\u0006${m.groupValues[1].trim()}\u0006\n" }
            // ── New numeric / hex entity decode (&#8230; / &#x2026;) — single
            // pass; out-of-range codepoints fall back to the original text.
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.let { code ->
                    if (code in 0..0x10FFFF) {
                        runCatching { Char(code).toString() }.getOrDefault(m.value)
                    } else {
                        m.value
                    }
                } ?: m.value
            }
            .replace(Regex("&#x([0-9A-Fa-f]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.let { code ->
                    if (code in 0..0x10FFFF) {
                        runCatching { Char(code).toString() }.getOrDefault(m.value)
                    } else {
                        m.value
                    }
                } ?: m.value
            }
            // Collapse multiple blank lines left by tag removal
            .replace(Regex("\\n\\s*\\n\\s*\\n"), "\n\n")
}

// ── HTML table conversion ────────────────────────────────────────────

private val RX_TR = Regex("<tr\\b[^>]*>([\\s\\S]*?)</\\s*tr\\s*>", setOf(RegexOption.IGNORE_CASE))
private val RX_TD = Regex("<t([dh])\\b([^>]*)>([\\s\\S]*?)</\\s*t[hd]\\s*>", setOf(RegexOption.IGNORE_CASE))
private val RX_ALIGN_ATTR = Regex("align\\s*=\\s*[\"']?(center|right|left)", RegexOption.IGNORE_CASE)
private val RX_ALIGN_STYLE = Regex("text-align\\s*:\\s*(center|right|left)", RegexOption.IGNORE_CASE)
private val RX_TAG_STRIP = Regex("<[^>]+>")
private val RX_CELL_HEADING = Regex("(?m)^\\s*#{1,6}\\s+")
private val RX_CELL_BULLET = Regex("(?m)^\\s*[-*+]\\s+")
private val RX_WS = Regex("\\s+")

/** `:shortcode:` — whitelist-checked against [EMOJI_SHORTCODES]. */
private val EMOJI_SHORTCODE_REGEX = Regex(":([a-z0-9_+-]+):")

/**
 * Convert a raw `<table>…</table>` HTML block into a GitHub pipe table.
 * Runs after the <img>/<a> conversions, so cells arrive as markdown text and
 * survive as real inline content (images, links, code) instead of being
 * flattened to garbage. Returns "" for tables with no parseable rows.
 */
internal fun htmlTableToMarkdown(tableHtml: String): String {
    val rows = RX_TR.findAll(tableHtml).toList()
    if (rows.isEmpty()) return ""
    var header: List<String>? = null
    val alignments = mutableListOf<Int>()
    val body = mutableListOf<List<String>>()
    for (tr in rows) {
        val cells = RX_TD.findAll(tr.groupValues[1]).toList()
        if (cells.isEmpty()) continue
        val rowAlign = mutableListOf<Int>()
        val texts = cells.map { c ->
            val attrs = c.groupValues[2]
            rowAlign.add(when {
                RX_ALIGN_STYLE.find(attrs) != null -> when (RX_ALIGN_STYLE.find(attrs)!!.groupValues[1]) {
                    "center" -> 1; "right" -> 2; else -> 0
                }
                RX_ALIGN_ATTR.find(attrs) != null -> when (RX_ALIGN_ATTR.find(attrs)!!.groupValues[1]) {
                    "center" -> 1; "right" -> 2; else -> 0
                }
                else -> 0
            })
            // Strip leftover tags, collapse whitespace; markdown links/images
            // produced upstream are preserved verbatim. NOTE: the cell text
            // keeps newlines out via \s+ collapse; escaped pipe keeps cell-
            // internal `|` from breaking the row split. HTML cells sometimes
            // carry BLOCK-level markdown (### headings, - bullets) — strip the
            // markers so they don't leak as literal "###" inside the cell.
            c.groupValues[3].replace(RX_TAG_STRIP, " ")
                .replace(RX_CELL_HEADING, "")
                .replace(RX_CELL_BULLET, "")
                .replace(RX_WS, " ")
                .trim()
                .replace("|", "\\|")
        }
        // Header = a row built from <th> cells ONLY. Screenshot/logo grids
        // (<table><tr><td><img …) have no header at all — promoting their
        // first image row to a bold header row was wrong; those render
        // headerless.
        val isHeader = header == null && cells.all { it.groupValues[1].lowercase() == "h" }
        if (isHeader) {
            header = texts
            alignments.addAll(rowAlign)
        } else {
            if (header == null && alignments.isEmpty()) alignments.addAll(rowAlign)
            body.add(texts)
        }
    }
    // Headerless grid: keep all rows in body, no synthetic header.
    val headers = header ?: emptyList()
    if (headers.isEmpty() && body.isEmpty()) return ""
    val colCount = (body.maxOfOrNull { it.size } ?: 0).coerceAtLeast(headers.size)
    val sb = StringBuilder("\n")
    if (headers.isNotEmpty()) {
        sb.append("| ").append(headers.joinToString(" | ")).append(" |\n")
        val al = MutableList(colCount) { alignments.getOrNull(it) ?: 0 }
        sb.append("| ").append(al.joinToString(" | ") { a ->
            when (a) {
                1 -> ":---:"; 2 -> "---:"; else -> "---"
            }
        }).append(" |\n")
    } else {
        // Headerless marker row: every cell "\u0005" → renderer reads
        // hasHeader=false. Alignment still carried per column.
        val al = MutableList(colCount) { alignments.getOrNull(it) ?: 0 }
        sb.append("| ").append(al.joinToString(" | ") { a -> "\u0005" + when (a) {
            1 -> ":---:"; 2 -> "---:"; else -> "---"
        } }).append(" |\n")
    }
    for (row in body) {
        val padded = row + List((colCount - row.size).coerceAtLeast(0)) { "" }
        sb.append("| ").append(padded.take(colCount).joinToString(" | ")).append(" |\n")
    }
    sb.append("\n")
    return sb.toString()
}

// ── Parsing ─────────────────────────────────────────────────────────
// Hot-path patterns compiled ONCE. parseMarkdown runs per line over docs up
// to 200K chars; inline Regex(...) construction per call site used to compile
// the same patterns thousands of times per document (measurable on 1MB
// awesome-lists, and pure waste).

private val RX_HR_DASH = Regex("^-{3,}\\s*$")
private val RX_HR_STAR = Regex("^\\*{3,}\\s*$")
private val RX_HEADING = Regex("^(#{1,6})\\s+(.+)")
private val RX_SETEXT_H1 = Regex("^=+\\s*$")
private val RX_SETEXT_H2 = Regex("^-+\\s*$")
private val RX_FENCE_OPEN_LINE = Regex("^\\s*(`{3,}|~{3,})(.*)$")
private val RX_FENCE_CLOSE = Regex("^\\s*(`{3,}|~{3,})\\s*$")
private val RX_OL_ITEM = Regex("^\\s*(\\d+)\\.\\s+(.+)")
private val RX_UL_ITEM = Regex("^\\s*[-*+]\\s+.+")
private val RX_UL_RAW = Regex("^\\s*[-*+]\\s+")
private val RX_TASK = Regex("^\\[([ xX])]\\s+(.*)")
private val RX_BLOCKSTART_FENCE = Regex("^[`~]{3,}")
private val RX_BLOCKSTART_OL = Regex("^\\s*\\d+\\.\\s+.+")
private val RX_BLOCKSTART_UL = Regex("^\\s*[-*+]\\s+.+")

internal val TABLE_SEP_REGEX = Regex("^\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?$")

internal fun isTableSeparator(line: String): Boolean {
    val l = stripHeaderlessMark(line.trim())
    return l.contains("-") && l.contains("|") && TABLE_SEP_REGEX.matches(l)
}

internal fun looksLikeTableRow(line: String): Boolean {
    val l = line.trim()
    return l.isNotBlank() && (l.startsWith("|") || l.count { it == '|' } >= 2)
}

internal fun splitTableRow(line: String): List<String> {
    val raw = line.trim()
    // GFM: a pipe inside a cell must be escaped as \| — hide it before
    // splitting on |, restore afterwards. (Very common in code-sample tables.)
    val masked = raw.replace("\\|", "\u0001")
    val hasLeading = masked.startsWith("|")
    val hasTrailing = masked.endsWith("|")
    var cells = masked.split("|").map { it.trim().replace("\u0001", "|") }
    if (hasLeading && cells.isNotEmpty()) cells = cells.drop(1)
    if (hasTrailing && cells.isNotEmpty()) cells = cells.dropLast(1)
    return cells
}

/** Max raw characters fed into the renderer; larger docs are truncated. */
private const val MAX_MARKDOWN_CHARS = 200_000

/**
 * Hard cap for pathological documents (e.g. 600KB+ awesome lists). The cap is
 * generous enough for any normal README/issue body; oversized docs keep their
 * beginning (title + intro + usually the first content sections) and get a
 * visible truncation marker instead of stalling the UI.
 */
internal fun truncateOversized(markdown: String): String {
    if (markdown.length <= MAX_MARKDOWN_CHARS) return markdown
    // Cut at a line boundary so we don't split a construct mid-way.
    val cut = markdown.lastIndexOf('\n', MAX_MARKDOWN_CHARS).takeIf { it > 0 }
        ?: MAX_MARKDOWN_CHARS
    return markdown.substring(0, cut) + "\n\n<!-- truncated -->\n\n*[Content too large — showing the first part]*"
}

internal fun parseMarkdown(src: String): List<MdBlock> = parseMarkdownInner(src, 0)

/** [depth] bounds blockquote re-parsing recursion ("> > > ..." pathological docs). */
private fun parseMarkdownInner(src: String, depth: Int): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = resolveReferenceLinks(src).lines()
    var i = 0

    fun listLevel(line: String): Int {
        val leading = line.takeWhile { it == ' ' }.length
        return (leading / 2) + 1
    }

    /** A table header begins at [idx] when [idx] is a pipe row and [idx]+1 is a separator. */
    fun isTableHeaderAt(idx: Int): Boolean =
        idx + 1 < lines.size && looksLikeTableRow(lines[idx]) && isTableSeparator(lines[idx + 1])

    val isBlockStart: (String) -> Boolean = { l ->
        l.isBlank() || l.startsWith("#") || RX_BLOCKSTART_FENCE.containsMatchIn(l.trim()) ||
            l.trimStart().startsWith(">") ||
            RX_BLOCKSTART_UL.containsMatchIn(l) || RX_BLOCKSTART_OL.containsMatchIn(l) ||
            RX_HR_DASH.matches(l) || RX_HR_STAR.matches(l)
    }

    while (i < lines.size) {
        val line = lines[i]

        if (line.isBlank()) { i++; continue }

        if (RX_HR_DASH.matches(line) || RX_HR_STAR.matches(line)) {
            blocks.add(MdBlock.HorizontalRule); i++; continue
        }

        val headingMatch = RX_HEADING.matchEntire(line.replace("\u0006", ""))
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            // Closed ATX heading: strip a trailing " ###" sequence (GFM).
            val text = headingMatch.groupValues[2].trim().replace(Regex("\\s+#+\\s*$"), "")
            blocks.add(MdBlock.Heading(level, text, line.contains("\u0006")))
            i++; continue
        }

        // Setext heading: non-blank line followed by === (H1) or --- (H2).
        // The dash form must NOT fire when the content line is a table row —
        // a two-column table like `| a | b |` + `|---|---|` (offset by one
        // blank line from the separator) would otherwise eat the header row
        // as a "heading". GitHub's rule: table row + dashes = table, not text.
        if (i + 1 < lines.size && line.isNotBlank() && !line.startsWith("#") &&
            !looksLikeTableRow(line)
        ) {
            val next = lines[i + 1]
            if (next.matches(RX_SETEXT_H1) && line.isNotBlank()) {
                blocks.add(MdBlock.Heading(1, line.trim()))
                i += 2; continue
            }
            if (next.matches(RX_SETEXT_H2) && line.isNotBlank() && !RX_HR_DASH.matches(line)) {
                blocks.add(MdBlock.Heading(2, line.trim()))
                i += 2; continue
            }
        }

        // GFM fenced code block: backtick or tilde fences. The closing fence
        // must be the same character and at least as long as the opening one,
        // so a ``` inside a ```` block stays content. Info string params after
        // the language (e.g. ```js hl_lines=3) are dropped.
        val fenceMatch = RX_FENCE_OPEN_LINE.find(line)
        if (fenceMatch != null) {
            val marker = fenceMatch.groupValues[1]
            val fenceChar = marker[0]
            val lang = fenceMatch.groupValues[2].trim().substringBefore(' ').ifBlank { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size) {
                val close = RX_FENCE_CLOSE.find(lines[i])
                if (close != null && close.groupValues[1][0] == fenceChar &&
                    close.groupValues[1].length >= marker.length
                ) break
                codeLines.add(lines[i])
                i++
            }
            i++ // skip closing fence (or run past EOF for unterminated blocks)
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n"), lang))
            continue
        }

        // Indented code block (GFM): 4+ space lines outside any list context
        // (license headers, console transcripts, ASCII tables). A previous
        // ListItem block means we're in "wrapped list item" territory — the
        // list loop already absorbed its continuations, don't steal these.
        val prevBlock = blocks.lastOrNull()
        if (line.length > 4 && line[0] == ' ' && line[3] == ' ' && line.getOrNull(4) != ' ' &&
            prevBlock !is MdBlock.ListItem
        ) {
            val codeLines = mutableListOf<String>()
            while (i < lines.size) {
                val l = lines[i]
                when {
                    l.startsWith("    ") && l.trim().isNotEmpty() -> { codeLines.add(l.substring(4)); i++ }
                    // Blank line inside: only continue if the next non-blank is still indented.
                    l.isBlank() && lines.getOrNull(i + 1)?.startsWith("    ") == true -> { codeLines.add(""); i++ }
                    else -> break
                }
            }
            if (codeLines.isNotEmpty()) {
                blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n"), null))
                continue
            }
        }

        if (line.trimStart().startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                // Strip exactly one level of ">" per line (">" + optional one
                // space) so nested quotes ("> > x") keep their inner marker
                // and the text pass can see it instead of leaking "> ".
                quoteLines.add(lines[i].trimStart().replaceFirst(Regex("^> ?"), ""))
                i++
            }
            // GFM alert: the first quote line is a bare [!KIND] marker → themed card
            val alert = ALERT_KIND_REGEX.matchEntire(quoteLines.firstOrNull()?.trim() ?: "")
            if (alert != null) {
                val body = quoteLines.drop(1).joinToString("\n").trim()
                blocks.add(MdBlock.Alert(alert.groupValues[1].uppercase(), stripCenterMark(body)))
                continue
            }
            // GFM: blockquote content is re-parsed as blocks — sub-lists,
            // headings, fences inside a quote keep their structure instead of
            // collapsing into one run-on line. Bound the recursion.
            val inner = quoteLines.joinToString("\n")
            blocks.add(if (depth < 2 && inner.contains('\n') &&
                Regex("^(?:\\s*(?:[-*+]|\\d+\\.)\\s|#{1,6}\\s|`{3,})", RegexOption.MULTILINE).containsMatchIn(inner)
            ) {
                val sub = parseMarkdownInner(inner, depth + 1)
                MdBlock.QuoteBlocks(sub)
            } else {
                MdBlock.Blockquote(stripCenterMark(inner))
            })
            continue
        }

        // Ordered list. Source numbering is PRESERVED (GFM start-number rule):
        // a fenced code block mid-list splits the run, and restarting at 1 made
        // "1. 2. ``` 3." render as "1. 2. ``` 1." — keep the author's numbers
        // when they form a sane sequence, otherwise fall back to +1 counting.
        RX_OL_ITEM.find(line)?.let { olStart ->
            var expected = olStart.groupValues[1].toIntOrNull() ?: 1
            var index = 0
            while (i < lines.size) {
                val m = RX_OL_ITEM.find(lines[i]) ?: break
                index++
                val num = m.groupValues[1].toIntOrNull() ?: expected
                val display = if (index == 1 || num == expected) num else expected
                expected = display + 1
                val text = stripCenterMark(m.groupValues[2].trim())
                blocks.add(MdBlock.ListItem(text, ordered = true, index = display, level = listLevel(lines[i])))
                i++
            }
            i = absorbContinuation(blocks, lines, i)
        }
        if (RX_OL_ITEM.containsMatchIn(line)) continue

        // Unordered list (with optional GitHub task-list checkbox)
        if (line.matches(RX_UL_ITEM)) {
            while (i < lines.size && lines[i].matches(RX_UL_ITEM)) {
                val raw = RX_UL_RAW.replaceFirst(lines[i].trim(), "").trim()
                val taskMatch = RX_TASK.matchEntire(raw)
                val (text, task) = if (taskMatch != null) {
                    val checked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                    taskMatch.groupValues[2] to (if (checked) 'x' else ' ')
                } else {
                    raw to null
                }
                blocks.add(MdBlock.ListItem(stripCenterMark(text), ordered = false, index = 0, level = listLevel(lines[i]), task = task))
                i++
            }
            i = absorbContinuation(blocks, lines, i)
            continue
        }

        // GitHub-style pipe table
        val headerlessRow = lines.getOrNull(i)?.contains(HEADERLESS_MARK) == true &&
            looksLikeTableRow(lines[i]) && isTableSeparator(stripHeaderlessMark(lines[i]))
        if (headerlessRow || isTableHeaderAt(i)) {
            val hasHeader = !headerlessRow
            val headers = if (hasHeader) splitTableRow(lines[i]) else emptyList()
            // GFM column alignment from the separator row: :-- left, :--: center, --: right
            val alignments = splitTableRow(lines[i + 1]).map { sep -> alignmentOfSep(sep) }
            i += if (hasHeader) 2 else 1 // header + separator, or marker separator only
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && looksLikeTableRow(lines[i]) && !isTableSeparator(lines[i]) && !lines[i].isBlank()) {
                rows.add(splitTableRow(lines[i]))
                i++
            }
            blocks.add(MdBlock.Table(headers, rows, alignments, hasHeader))
            continue
        }

        // Paragraph: the \u0006 (centered-container) marker survives into the
        // block so the renderer can center it.
        val paraLines = mutableListOf<String>()
        while (i < lines.size && !isBlockStart(lines[i]) && !isTableHeaderAt(i)) {
            paraLines.add(lines[i])
            i++
        }
        if (paraLines.isNotEmpty()) {
            val joined = joinParagraphLines(paraLines)
            blocks.add(MdBlock.Paragraph(joined, centered = joined.contains("\u0006")))
        }
    }
    return blocks
}

/**
 * GFM list continuation: indented non-block lines following a list item belong
 * to that item ("wrapped" list items), not to a new paragraph. Returns the new
 * line index after absorbing.
 */
private fun absorbContinuation(blocks: MutableList<MdBlock>, lines: List<String>, start: Int): Int {
    var i = start
    while (i < lines.size && !lines[i].isBlank() && lines[i].startsWith("  ")) {
        val t = lines[i].trimStart()
        // Nested items/fences/headings/quotes are their own blocks — stop there.
        if (LIST_ITEM_START.containsMatchIn(t) || t.startsWith("```") || t.startsWith("~~~") ||
            t.startsWith("#") || t.startsWith(">")
        ) break
        val last = blocks.lastOrNull()
        if (last is MdBlock.ListItem) {
            blocks[blocks.size - 1] = last.copy(text = last.text + " " + t.trim())
        }
        i++
    }
    return i
}

private val LIST_ITEM_START = Regex("^(?:[-*+]|\\d+\\.)\\s+")

/** GFM hard line breaks: two trailing spaces or a trailing backslash. */
internal fun joinParagraphLines(paraLines: List<String>): String =
    paraLines.joinToString("") { raw ->
        val l = raw.trimEnd()
        when {
            raw.endsWith("  ") -> "$l\n"
            l.endsWith("\\") -> "${l.dropLast(1)}\n"
            else -> "$l "
        }
    }.trim()

/** Bare `[!NOTE]`-style marker on a blockquote's first line (GFM alerts). */
private val ALERT_KIND_REGEX =
    Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*$", RegexOption.IGNORE_CASE)

/**
 * Marker cell in a separator row produced by [htmlTableToMarkdown] for
 * headerless HTML tables (screenshot/logo grids). "\u0005" + alignment code;
 * the block parser strips it and sets hasHeader=false.
 */
internal const val HEADERLESS_MARK = "\u0005"

/** Centered-container wrap marker written by cleanSegment (see cleanSegment). */
internal const val CENTER_MARK = "\u0006"

internal fun stripCenterMark(s: String): String = s.replace(CENTER_MARK, "")

internal fun stripHeaderlessMark(sep: String): String = sep.replace(HEADERLESS_MARK, "")

internal fun alignmentOfSep(sepRaw: String): Int {
    val sep = stripHeaderlessMark(sepRaw).trim()
    return when {
        sep.startsWith(":") && sep.endsWith(":") && sep.length > 2 -> 1
        sep.endsWith(":") -> 2
        else -> 0
    }
}

private val REF_DEF_REGEX =
    Regex("^\\s{0,3}\\[([^\\]]+)]:\\s*(?:<([^<>]+)>|(\\S+))[^\\n]*$")

private val REF_IMAGE_USE = Regex("!\\[([^\\]]*)\\]\\[([^\\]]*)\\]")
private val REF_LINK_USE = Regex("(?<!\\!)\\[([^\\]\\[]+)\\]\\[([^\\]]*)\\]")
private val REF_SHORT_USE = Regex("(?<!\\!)\\[([^\\]\\[()]+)](?!\\()")
private val FENCE_OPEN = Regex("^(`{3,}|~{3,})")

/**
 * GFM reference-style link support: collect `[ref]: url` definitions, then
 * rewrite `![alt][ref]`, `[text][ref]`, `[text][]` and `[text]` shorthand into
 * plain inline form so the existing inline tokenizers handle them. Definition
 * lines are dropped. Fenced code blocks pass through untouched. Returns the
 * input unchanged when it contains no reference definitions.
 */
internal fun resolveReferenceLinks(src: String): String {
    if (!src.contains('[')) return src
    val defs = HashMap<String, String>()
    var found = false
    var fence: String? = null
    // First pass: collect definitions (fence-aware), remember whether any exist.
    for (line in src.lines()) {
        val t = line.trim()
        val open = FENCE_OPEN.find(t)
        if (fence == null) {
            if (open != null) fence = open.groupValues[1]
        } else if (open != null && open.groupValues[1][0] == fence[0] &&
            open.groupValues[1].length >= fence.length
        ) {
            fence = null
        }
        if (fence != null || open != null) continue
        val def = REF_DEF_REGEX.matchEntire(line)
        if (def != null) {
            val url = (def.groupValues[2].ifEmpty { def.groupValues[3] }).trim()
            if (url.startsWith("http") || url.startsWith("/") || url.startsWith("#")) {
                // First definition wins (GFM); label matching is case-insensitive.
                defs.putIfAbsent(def.groupValues[1].trim().lowercase(), url)
                found = true
            }
        }
    }
    if (!found) return src

        fun rewrite(line: String): String {
        if (!line.contains('[')) return line
        var l = line
        // Nested reference-wrapped images FIRST, before the flat rewrites can
        // partially consume their inner brackets:
        //   [![alt][imgref]][linkref] → [![alt](imgurl)](linkurl)
        //   [![alt][imgref]](inline-url) → [![alt](imgurl)](inline-url)
        // This is the ubiquitous README hero-banner / language-shield pattern.
        l = Regex("\\[!\\[([^\\]]*)\\]\\[([^\\]]*)\\]\\]\\[([^\\]]*)\\]").replace(l) { m ->
            val alt = m.groupValues[1]
            val imgKey = m.groupValues[2].ifBlank { alt }.trim().lowercase()
            val img = defs[imgKey] ?: return@replace m.value
            val link = defs[m.groupValues[3].trim().lowercase()] ?: return@replace m.value
            "[![$alt]($img)]($link)"
        }
        l = Regex("\\[!\\[([^\\]]*)\\]\\[([^\\]]*)\\]\\]\\(").replace(l) { m ->
            val alt = m.groupValues[1]
            val imgKey = m.groupValues[2].ifBlank { alt }.trim().lowercase()
            val img = defs[imgKey] ?: return@replace m.value
            "[![$alt]($img)]("
        }
        l = REF_IMAGE_USE.replace(l) { m ->
            val key = m.groupValues[2].ifBlank { m.groupValues[1] }.trim().lowercase()
            defs[key]?.let { "![${m.groupValues[1]}]($it)" } ?: m.value
        }
        l = REF_LINK_USE.replace(l) { m ->
            val key = m.groupValues[2].ifBlank { m.groupValues[1] }.trim().lowercase()
            defs[key]?.let { "[${m.groupValues[1]}]($it)" } ?: m.value
        }
        l = REF_SHORT_USE.replace(l) { m ->
            defs[m.groupValues[1].trim().lowercase()]?.let { "[${m.groupValues[1]}]($it)" } ?: m.value
        }
        return l
    }

    // Second pass: drop definition lines, rewrite uses — fences pass through raw.
    val out2 = StringBuilder()
    fence = null
    for (line in src.lines()) {
        val t = line.trim()
        val open = FENCE_OPEN.find(t)
        if (fence == null) {
            if (open != null) fence = open.groupValues[1]
        } else if (open != null && open.groupValues[1][0] == fence[0] &&
            open.groupValues[1].length >= fence.length
        ) {
            fence = null
        }
        if (fence != null || open != null) { out2.append(line).append('\n'); continue }
        if (REF_DEF_REGEX.matchEntire(line) != null) continue // definition line → dropped
        out2.append(rewrite(line)).append('\n')
    }
    return out2.toString().removeSuffix("\n")
}

// ── Link resolver ────────────────────────────────────────────────────
