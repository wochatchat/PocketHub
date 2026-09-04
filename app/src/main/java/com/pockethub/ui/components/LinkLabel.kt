package com.pockethub.ui.components

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.pockethub.ui.theme.semanticColors

/**
 * Small clickable hyperlink built from a single line of text. Opens [url] in a
 * Custom Tab. The link text is rendered in the primary color with an underline,
 * matching GitHub mobile's login helper style.
 */
@Composable
fun LinkLabel(
    url: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val annotated = buildAnnotatedString {
        pushStringAnnotation(tag = "URL", annotation = url)
        addStyle(
    // Off-github links use green — same rule as EXTERNAL markdown links in
    // MarkdownText, so in-app vs external stays consistent app-wide.
    SpanStyle(color = semanticColors().success, textDecoration = TextDecoration.Underline),
            start = 0,
            end = text.length,
        )
        append(text)
        pop()
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier,
        onClick = { _ ->
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(context, Uri.parse(url))
        },
    )
}
