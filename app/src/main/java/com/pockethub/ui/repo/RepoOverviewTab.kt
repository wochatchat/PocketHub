package com.pockethub.ui.repo

// Repo overview tab: stats row + README/readme sections.
// Split out of RepoDetailScreen.kt for readability.

import com.pockethub.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.remember
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethub.data.model.Repository
import com.pockethub.ui.markdown.MarkdownText
import com.pockethub.ui.components.pressScale
import com.pockethub.ui.components.PhAsyncImage
import com.pockethub.util.formatCount

@Composable
internal fun StatsRow(
    data: Repository,
    onNavigateToUser: (String) -> Unit = {},
    isStarred: Boolean = false,
    isForking: Boolean = false,
    onToggleStar: () -> Unit = {},
    onFork: () -> Unit = {},
) {
    val userClickModifier = Modifier.clickable { onNavigateToUser(data.owner.login) }
    val starInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val forkInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhAsyncImage(
            model = data.owner.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(CircleShape).then(userClickModifier),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.stats_by, data.owner.login),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = userClickModifier,
        )
        Spacer(Modifier.weight(1f))
        // Star chip — tappable to toggle star. Filled star when starred.
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .pressScale(interactionSource = starInteraction)
                .clickable(interactionSource = starInteraction, indication = null, onClick = onToggleStar)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isStarred) stringResource(R.string.cd_unstar) else stringResource(R.string.cd_star),
                modifier = Modifier.size(20.dp),
                tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(data.stars.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        // Fork chip — tappable to fork. Shows loading state while forking.
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .pressScale(interactionSource = forkInteraction)
                .clickable(interactionSource = forkInteraction, indication = null, onClick = onFork, enabled = !isForking)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isForking) Icons.Outlined.ForkRight else Icons.Outlined.ForkRight,
                contentDescription = stringResource(R.string.action_fork),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${data.forks} ${stringResource(R.string.stat_forks)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewTab(
    owner: String,
    repo: String,
    repoData: Repository?,
    readme: String?,
    isLoading: Boolean,
    readmeMissing: Boolean = false,
    translatedReadme: String? = null,
    showTranslated: Boolean = false,
    isTranslating: Boolean = false,
    translateTarget: String? = null,
    onToggleTranslation: () -> Unit = {},
    onTopicClick: (String) -> Unit = {},
    onNavigateToRepo: (String, String) -> Unit = { _, _ -> },
    onLinkClick: (String, com.pockethub.ui.markdown.LinkKind) -> Unit,
) {
    if (isLoading && repoData == null) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.pockethub.ui.components.SkeletonBox(Modifier.fillMaxWidth(0.85f).height(18.dp))
            com.pockethub.ui.components.SkeletonBox(Modifier.fillMaxWidth(0.5f).height(14.dp))
            com.pockethub.ui.components.SkeletonBox(Modifier.fillMaxWidth().height(120.dp), cornerRadius = 18.dp)
            com.pockethub.ui.components.SkeletonBox(Modifier.fillMaxWidth().height(220.dp), cornerRadius = 18.dp)
        }
        return
    }
    repoData?.let { data ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Info card: owner, description, homepage, stats, topics ──
            com.pockethub.ui.components.PhCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Owner row — tap to open the profile. Trailing pill copies
                    // the repo URL to the clipboard (card top-right corner).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onLinkClick(data.owner.htmlUrl ?: "https://github.com/${data.owner.login}", com.pockethub.ui.markdown.LinkKind.GITHUB_USER) },
                        ) {
                        PhAsyncImage(
                            model = data.owner.avatarUrl,
                            contentDescription = data.owner.login,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = data.fullName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = data.owner.login,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        }
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        val copyContext = androidx.compose.ui.platform.LocalContext.current
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .clickable {
                                    val url = data.htmlUrl ?: "https://github.com/${data.fullName}"
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(url))
                                    android.widget.Toast.makeText(copyContext, copyContext.getString(R.string.copied_toast), android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                stringResource(R.string.copy_address),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (!data.description.isNullOrBlank()) {
                        Text(
                            data.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp,
                        )
                    }

                    // Fork source chip — navigates into the upstream repo.
                    if (data.fork && data.parent != null) {
                        val p = data.parent
                        val parentOwner = p.owner.login
                        val parentName = p.name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                .clickable { onNavigateToRepo(parentOwner, parentName) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.ForkRight,
                                null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.repo_forked_from, "$parentOwner/$parentName"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    // Homepage — clickable through the shared link handler.
                    if (!data.homepage.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onLinkClick(data.homepage!!, com.pockethub.ui.markdown.LinkKind.EXTERNAL) }
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Language,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                data.homepage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Stats strip — stars / forks / issues in one quiet row.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OverviewStat(Icons.Outlined.Star, formatCount(data.stars), MaterialTheme.colorScheme.tertiary)
                        OverviewStat(Icons.Outlined.ForkRight, formatCount(data.forks), MaterialTheme.colorScheme.secondary)
                        OverviewStat(Icons.Outlined.ErrorOutline, formatCount(data.openIssues), MaterialTheme.colorScheme.primary)
                        data.language?.let { language ->
                            Spacer(Modifier.weight(1f))
                            val color = com.pockethub.ui.components.parseColorHex(com.pockethub.ui.components.languageColorHex(language)) ?: MaterialTheme.colorScheme.outline
                            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(5.dp))
                            Text(language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Topics — quiet capsule chips.
                    if (data.topics.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            data.topics.take(12).forEach { topic ->
                                Text(
                                    topic,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                        .clickable { onTopicClick(topic) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                            if (data.topics.size > 12) {
                                Text(
                                    "+${data.topics.size - 12}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── README section ──
            // Empty state only when a fetch FINISHED and found nothing
            // (readmeMissing) — never while the request is still running.
            val showReadmeSection = readme != null || isLoading || (readmeMissing && repoData != null)
            if (showReadmeSection) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.readme_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (readme != null && translateTarget != null) {
                        // Capsule toggle: 原文 / 译文
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(4.dp))
                            }
                            // 原文 button
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (!showTranslated) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable(enabled = !isTranslating) { if (showTranslated) onToggleTranslation() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.translate_original),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (!showTranslated) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // 译文 button
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (showTranslated) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable(enabled = !isTranslating) { if (!showTranslated) onToggleTranslation() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.translate_translated),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (showTranslated) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                // README content — show translated or original
                val displayReadme = if (showTranslated && translatedReadme != null) translatedReadme else readme
                if (displayReadme != null) {
                    // Gallery of all image URLs in the rendered document — each
                    // raw src resolved exactly like MarkdownText resolves it, so
                    // the tapped URL matches an entry and the full-screen preview
                    // can swipe between them.
                    val imageResolver = com.pockethub.ui.markdown.rememberImageResolver("$owner/$repo", repoData?.defaultBranch)
                    val imageGallery = remember(displayReadme, repoData?.defaultBranch) {
                        Regex("""!\[[^\]]*\]\(\s*([^)\s]+)""")
                            .findAll(displayReadme)
                            .mapNotNull { m -> m.groupValues[1].takeIf { it.isNotBlank() } }
                            .map { imageResolver(it) }
                            .distinct()
                            .toList()
                    }
                    MarkdownText(
                        markdown = displayReadme,
                        modifier = Modifier.fillMaxWidth(),
                        repoContext = "$owner/$repo",
                        defaultBranch = repoData?.defaultBranch,
                        onLinkClick = onLinkClick,
                        imageGallery = imageGallery,
                    )
                } else if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.readme_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    // Empty state: centered in the remaining space, icon over text.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentHeightIfPossible(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.readme_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } // showReadmeSection
            Spacer(Modifier.height(40.dp))
        }
    }
}


/** Fill the space left under the info card so the empty state can center. */
@Composable
private fun Modifier.fillParentHeightIfPossible(): Modifier {
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    return height(screenH * 0.55f)
}

/** Small icon+value stat used in the overview info card. */
@Composable
private fun OverviewStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
    }
}

