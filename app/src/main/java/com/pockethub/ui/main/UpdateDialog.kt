package com.pockethub.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pockethub.R
import com.pockethub.data.remote.GoogleTranslate
import com.pockethub.data.remote.UpdateChecker
import com.pockethub.ui.theme.LocalStyleTokens
import com.pockethub.util.humanBytes
import com.pockethub.ui.theme.semanticColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale

/**
 * In-place updater flow: prompt → download (with progress) → install, without
 * leaving the app. The dialog never opens the browser; the APK is fetched into
 * cache and handed to the system PackageInstaller via a FileProvider URI.
 *
 * Visual language matches the app's design system: themed surface + hairline
 * border + accent-gradient hero plate + corner radius scaled by the active
 * style's [LocalStyleTokens] tokens.
 */
@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    downloadState: UpdateViewModel.DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: (path: String) -> Unit,
    onRetry: () -> Unit,
    onIgnore: () -> Unit,
    onLater: () -> Unit,
) {
    val tokens = LocalStyleTokens.current
    // Follow the active style's corner language, capped so it stays dialog-like.
    val dialogRadius = (22f * tokens.cornerScale).coerceIn(2f, 30f).dp
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(dialogRadius),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(dialogRadius))
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                        1f to Color.Transparent,
                    )
                )
                .border(1.dp, Brush.verticalGradient(listOf(borderColor, borderColor.copy(alpha = 0.35f))), RoundedCornerShape(dialogRadius)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                UpdateHeader(info, downloadState)

                info.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                    ChangelogSection(notes)
                }

                when (val ds = downloadState) {
                    is UpdateViewModel.DownloadState.Running -> DownloadProgress(ds)
                    is UpdateViewModel.DownloadState.Done -> ReadyBanner()
                    is UpdateViewModel.DownloadState.Failed -> FailedBanner(ds.message)
                    else -> Unit
                }

                UpdateActions(
                    downloadState = downloadState,
                    onCancel = onCancel,
                    onInstall = onInstall,
                    onRetry = onRetry,
                    onIgnore = onIgnore,
                    onLater = onLater,
                    onDownload = onDownload,
                )
            }
        }
    }
}

@Composable
private fun UpdateHeader(info: UpdateChecker.UpdateInfo, downloadState: UpdateViewModel.DownloadState) {
    val tokens = LocalStyleTokens.current
    val busy = downloadState is UpdateViewModel.DownloadState.Running
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Accent-gradient hero plate with a rocket glyph.
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape((14f * tokens.cornerScale).coerceIn(2f, 22f).dp))
                .background(
                    Brush.linearGradient(listOf(tokens.accentA, tokens.accentB))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.update_available_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Version capsule — the version is the hero metadata here.
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = "v" + info.latestVersionName.removePrefix("v"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                if (busy) {
                    Text(
                        text = stringResource(R.string.update_downloading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    info.publishedAt?.let {
                        Text(
                            text = formatPublishedDate(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogSection(notes: String) {
    val isZh = Locale.getDefault().language == "zh"

    // Preferred source: the structured bilingual block the CI embeds in the
    // release body at build time (<!--pockethub-changelog {json}-->).
    // Content is already locale-split and polished — no runtime translation.
    val structured = remember(notes) { parseStructuredChangelog(notes) }
    val tags = ChangelogTags(
        summary = stringResource(R.string.tag_summary),
        new = stringResource(R.string.tag_new),
        fix = stringResource(R.string.tag_fix),
        improved = stringResource(R.string.tag_improved),
        faster = stringResource(R.string.tag_faster),
        reverted = stringResource(R.string.tag_reverted),
        update = stringResource(R.string.tag_update),
    )

    val items: List<ChangeItem> = when {
        structured.isNotEmpty() -> structured.mapNotNull { entry ->
            val text = if (isZh) entry.optString("zh", "") else entry.optString("en", "")
            val text2 = text.ifBlank { entry.optString("en", entry.optString("zh", "")) }
            if (text2.isBlank()) return@mapNotNull null
            val tagText = when (entry.optString("type", "improve")) {
                "feat" -> tags.new
                "fix" -> tags.fix
                "perf" -> tags.faster
                "revert" -> tags.reverted
                else -> tags.improved
            }
            ChangeItem(tagText, text2, changelogTagColor(entry.optString("type", "improve")))
        }
        // Legacy releases without the block: fall back to parsing commit
        // subjects; zh locale gets runtime translation with a vague-summary
        // fallback if every provider fails.
        else -> {
            val parsed = parseChangelogItems(notes, tags).take(8)
            if (parsed.isEmpty()) return
            val zhTexts by produceState<List<String>?>(parsed.map { it.text }, parsed, isZh) {
                if (!isZh) return@produceState
                value = try {
                    coroutineScope {
                        parsed.map { item ->
                            async {
                                if (GoogleTranslate.detectLanguage(item.text) == "zh") {
                                    item.text
                                } else {
                                    GoogleTranslate.translate(item.text, "zh-CN")
                                }
                            }
                        }.awaitAll()
                    }
                } catch (_: Exception) {
                    null
                }
            }
            when {
                zhTexts != null -> parsed.mapIndexed { i, item -> item.copy(text = zhTexts!![i]) }
                isZh -> listOf(
                    ChangeItem(
                        tag = parsed.first().tag,
                        text = stringResource(R.string.update_changelog_generic),
                        tagColor = parsed.first().tagColor,
                    ),
                )
                else -> parsed
            }
        }
    }
    if (items.isEmpty()) return
    val display = buildList {
        parseStructuredSummary(notes, isZh)?.let {
            add(ChangeItem(tag = tags.summary, text = it, tagColor = changelogTagColor("feat")))
        }
        addAll(items)
    }

    Column {
        Text(
            text = stringResource(R.string.update_changelog_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            display.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = item.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = item.tagColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(item.tagColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgress(ds: UpdateViewModel.DownloadState.Running) {
    val animated by animateFloatAsState(
        targetValue = ds.progressPct / 100f,
        animationSpec = tween(220),
        label = "update_progress",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(LocalStyleTokens.current.accentA, LocalStyleTokens.current.accentB)
                        )
                    ),
            )
        }
        Text(
            text = if (ds.totalBytes > 0) {
                "${humanBytes(ds.downloadedBytes)} / ${humanBytes(ds.totalBytes)}  ·  ${ds.progressPct}%"
            } else {
                "${humanBytes(ds.downloadedBytes)}  ·  ${ds.progressPct}%"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadyBanner() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = stringResource(R.string.update_downloaded_ready),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FailedBanner(message: String) {
    Text(
        text = stringResource(R.string.update_download_failed, message),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun UpdateActions(
    downloadState: UpdateViewModel.DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: (String) -> Unit,
    onRetry: () -> Unit,
    onIgnore: () -> Unit,
    onLater: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (val ds = downloadState) {
            is UpdateViewModel.DownloadState.Running -> {
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
            is UpdateViewModel.DownloadState.Done -> {
                Button(
                    onClick = { onInstall(ds.path) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(stringResource(R.string.action_install), modifier = Modifier.padding(vertical = 4.dp))
                }
                TextButton(onClick = onLater, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.action_remind_later))
                }
            }
            is UpdateViewModel.DownloadState.Failed -> {
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.action_retry), modifier = Modifier.padding(vertical = 4.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onLater) { Text(stringResource(R.string.action_remind_later)) }
                    TextButton(onClick = onIgnore) { Text(stringResource(R.string.action_ignore_version)) }
                }
            }
            else -> {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.action_download), modifier = Modifier.padding(vertical = 4.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onLater) { Text(stringResource(R.string.action_remind_later)) }
                    TextButton(onClick = onIgnore) { Text(stringResource(R.string.action_ignore_version)) }
                }
            }
        }
    }
}

/** Parse an ISO-8601 timestamp into a short localized "yyyy-MM-dd HH:mm" string. */
private fun formatPublishedDate(iso: String): String = try {
    val zdt = java.time.OffsetDateTime.parse(iso.trim().replace("Z", "+00:00")).toInstant()
        .atZone(java.time.ZoneId.systemDefault())
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(zdt)
} catch (_: Exception) {
    iso.take(16).replace('T', ' ')
}

/** A skimmable changelog line shown in the update dialog. */
private data class ChangeItem(
    val tag: String,
    val text: String,
    val tagColor: Color,
)

/**
 * Composable bridge to the theme's semantic hues for changelog tags —
 * parse helpers stay pure by receiving colors resolved at call time.
 */
private object SemanticChangeColors {
    val success @Composable get() = semanticColors().success
    val running @Composable get() = semanticColors().running
    val warning @Composable get() = semanticColors().warning
    val neutral @Composable get() = semanticColors().neutral
}

/** Color per changelog item type (mirrors parseChangelogItems' palette). */
@Composable
private fun changelogTagColor(type: String): Color = when (type) {
    "feat" -> SemanticChangeColors.success
    "fix", "revert" -> SemanticChangeColors.running
    "perf", "refactor" -> SemanticChangeColors.warning
    else -> SemanticChangeColors.neutral
}

/**
 * Extract the structured bilingual block the CI embeds in the release body:
 * <!--pockethub-changelog {"items":[{"type":"feat","zh":"…","en":"…"}]}-->
 * Returns the items array, or null when the block is absent/corrupt.
 */
private fun parseStructuredChangelog(notes: String): List<org.json.JSONObject> {
    val marker = "<!--pockethub-changelog"
    val start = notes.indexOf(marker)
    if (start == -1) return emptyList()
    val jsonStart = notes.indexOf('{', start)
    val end = notes.indexOf("-->", jsonStart)
    if (jsonStart == -1 || end == -1) return emptyList()
    return runCatching {
        val arr = org.json.JSONObject(notes.substring(jsonStart, end).trim()).optJSONArray("items")
            ?: return emptyList()
        List(arr.length()) { i -> arr.optJSONObject(i) }.filterNotNull()
    }.getOrDefault(emptyList())
}

/** One-line theme of the release ("本次更新聚焦…"), localized like the items. */
private fun parseStructuredSummary(notes: String, isZh: Boolean): String? {
    val marker = "<!--pockethub-changelog"
    val start = notes.indexOf(marker)
    if (start == -1) return null
    val jsonStart = notes.indexOf('{', start)
    val end = notes.indexOf("-->", jsonStart)
    if (jsonStart == -1 || end == -1) return null
    return runCatching {
        val obj = org.json.JSONObject(notes.substring(jsonStart, end).trim())
        val sm = obj.optJSONObject("summary") ?: return@runCatching null
        val text = (if (isZh) sm.optString("zh", "") else sm.optString("en", ""))
            .ifBlank { sm.optString("en", sm.optString("zh", "")) }
        text.ifBlank { null }
    }.getOrNull()
}

/** Localized changelog category tags (resolved from string resources). */
private data class ChangelogTags(
    val summary: String,
    val new: String,
    val fix: String,
    val improved: String,
    val faster: String,
    val reverted: String,
    val update: String,
)

/**
 * Parse raw release notes into a flat list of short skimmable items.
 *
 * Recognises the conventional-commit prefix (`feat(scope):` / `fix:` / `chore:` …)
 * and converts each line into a friendly category tag plus the rest of
 * the message. Lines without a recognisable prefix get a "更新" tag.
 *
 * Only bullet / `- ` lines or pure-message lines are kept; HTML or section
 * headers (lines starting with `#`) are dropped, since we want a tall vertical
 * list rather than a markdown essay.
 */
@Composable
private fun parseChangelogItems(notes: String, tags: ChangelogTags): List<ChangeItem> {
    val featColor = semanticColors().success
    val fixColor = semanticColors().running
    val refactorColor = semanticColors().warning
    val choreColor = semanticColors().neutral
    val otherColor = semanticColors().neutral

    return notes.lines().mapNotNull { rawLine ->
        // Strip leading "- " / "* " bullet.
        val line = rawLine.trim().removePrefix("-").removePrefix("*").trim()
        if (line.isEmpty()) return@mapNotNull null
        // Drop markdown section headers like "## v0.1.44".
        if (line.startsWith("#")) return@mapNotNull null

        // Conventional-commit prefix match: "type(scope): message" or "type: message".
        val match = Regex("^(feat|fix|chore|refactor|docs|style|test|perf|build|ci|revert)(\\([^)]+\\))?[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(line)
        if (match != null) {
            val type = match.groupValues[1].lowercase()
            val msg = match.groupValues[3].trim()
            // Friendly, plain-language tags instead of dev jargon — users should
            // see what kind of change it is at a glance.
            val tagText = when (type) {
                "feat" -> tags.new
                "fix" -> tags.fix
                "refactor", "refact" -> tags.improved
                "perf" -> tags.faster
                "revert" -> tags.reverted
                else -> tags.improved
            }
            val color = when (type) {
                "feat" -> featColor
                "fix" -> fixColor
                "refactor", "perf" -> refactorColor
                "chore", "docs", "style", "test", "ci", "build" -> choreColor
                "revert" -> fixColor
                else -> otherColor
            }
            return@mapNotNull ChangeItem(tagText, msg, color)
        }
        // Plain line — show with a neutral tag.
        ChangeItem(tags.update, line, otherColor)
    }
}
