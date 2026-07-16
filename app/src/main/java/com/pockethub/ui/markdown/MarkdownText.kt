package com.pockethub.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A lightweight, dependency-free Markdown renderer for V1.
 *
 * Supports: H1–H6, bold, italic, inline code, code blocks, unordered lists,
 * ordered lists, blockquotes, horizontal rules, paragraphs, and links (as plain text).
 *
 * Does NOT support: tables, images, footnotes, math, task lists (V2).
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = parseMarkdown(markdown)
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.displaySmall
                        2 -> MaterialTheme.typography.headlineMedium
                        3 -> MaterialTheme.typography.headlineSmall
                        4 -> MaterialTheme.typography.titleLarge
                        5 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = block.text,
                        style = style.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = renderInline(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                is MdBlock.CodeBlock -> {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                is MdBlock.Blockquote -> {
                    Text(
                        text = renderInline(block.text),
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                is MdBlock.ListItem -> {
                    val bullet = if (block.ordered) "${block.index}. " else "• "
                    Text(
                        text = bullet + renderInline(block.text).toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                is MdBlock.HorizontalRule -> {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
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
    data class ListItem(val text: String, val ordered: Boolean, val index: Int) : MdBlock()
    object HorizontalRule : MdBlock()
}

private fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = src.lines()
    var i = 0
    var orderedIndex = 0

    while (i < lines.size) {
        val line = lines[i]

        // Empty line
        if (line.isBlank()) { i++; continue }

        // Horizontal rule
        if (line.matches(Regex("^-{3,}\\s*$")) || line.matches(Regex("^\\*{3,}\\s*$"))) {
            blocks.add(MdBlock.HorizontalRule); i++; continue
        }

        // Heading
        val headingMatch = Regex("^(#{1,6})\\s+(.+)").matchEntire(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            blocks.add(MdBlock.Heading(level, headingMatch.groupValues[2].trim()))
            i++; continue
        }

        // Code block ```
        if (line.trim().startsWith("```")) {
            val lang = line.trim().removePrefix("```").trim().ifBlank { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            i++ // skip closing ```
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n"), lang))
            continue
        }

        // Blockquote
        if (line.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].startsWith(">")) {
                quoteLines.add(lines[i].removePrefix(">").trim())
                i++
            }
            blocks.add(MdBlock.Blockquote(quoteLines.joinToString("\n")))
            continue
        }

        // Ordered list
        if (line.matches(Regex("^\\d+\\.\\s+.+"))) {
            orderedIndex = 0
            while (i < lines.size && lines[i].matches(Regex("^\\d+\\.\\s+.+"))) {
                orderedIndex++
                val text = lines[i].substringAfter(". ").trim()
                blocks.add(MdBlock.ListItem(text, ordered = true, index = orderedIndex))
                i++
            }
            continue
        }

        // Unordered list
        if (line.matches(Regex("^[-*+]\\s+.+"))) {
            while (i < lines.size && lines[i].matches(Regex("^[-*+]\\s+.+"))) {
                val text = lines[i].substringAfter(" ").trim()
                blocks.add(MdBlock.ListItem(text, ordered = false, index = 0))
                i++
            }
            continue
        }

        // Paragraph (collect consecutive non-empty, non-special lines)
        val paraLines = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank() &&
               !lines[i].startsWith("#") && !lines[i].trim().startsWith("```") &&
               !lines[i].startsWith(">") && !lines[i].matches(Regex("^[-*+]\\s+.+")) &&
               !lines[i].matches(Regex("^\\d+\\.\\s+.+")) &&
               !lines[i].matches(Regex("^-{3,}\\s*$")) && !lines[i].matches(Regex("^\\*{3,}\\s*$"))) {
            paraLines.add(lines[i])
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
        }
    }
    return blocks
}

/** Render inline markdown (bold, italic, code) into an AnnotatedString. */
private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
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
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.15f))) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1; continue
            }
        }
        // Link [text](url) — render text only
        if (text[i] == '[') {
            val closeBracket = text.indexOf(']', i + 1)
            if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', closeBracket + 2)
                if (closeParen != -1) {
                    val linkText = text.substring(i + 1, closeBracket)
                    withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF7C8BFF))) { append(linkText) }
                    i = closeParen + 1; continue
                }
            }
        }
        append(text[i]); i++
    }
}
