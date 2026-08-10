package com.pockethub.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.R
import com.pockethub.data.remote.feed.FeedSourceConfig
import com.pockethub.data.remote.feed.FeedSourceOption
import com.pockethub.data.remote.feed.FeedTab

/**
 * Picks the data source backing each Explore tab and (when supported) the
 * custom base URL. Saved state lives in [FeedSourcesViewModel] which writes
 * directly through to DataStore for every change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSourcesScreen(
    onBack: () -> Unit,
    vm: FeedSourcesViewModel = hiltViewModel(),
) {
    val trendingCfg by vm.trendingConfig.collectAsState()
    val featuredCfg by vm.featuredConfig.collectAsState()
    val followingCfg by vm.followingConfig.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_sources), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Text(
                stringResource(R.string.feed_sources_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            SectionHeader(stringResource(R.string.section_trending))
            SourceGroup(
                tab = FeedTab.TRENDING,
                config = trendingCfg,
                options = FeedSourceOption.optionsFor(FeedTab.TRENDING),
                onSelect = { src -> vm.selectSource(FeedTab.TRENDING, src, "") },
                onCustomUrlChange = { src, url -> vm.setCustomBaseUrl(FeedTab.TRENDING, src, url) },
                onGithubOptionsChange = { sort, min, max, archived -> vm.setGithubOptions(FeedTab.TRENDING, sort, min, max, archived) },
                onReset = { vm.resetTab(FeedTab.TRENDING) },
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.section_featured))
            SourceGroup(
                tab = FeedTab.FEATURED,
                config = featuredCfg,
                options = FeedSourceOption.optionsFor(FeedTab.FEATURED),
                onSelect = { src -> vm.selectSource(FeedTab.FEATURED, src, "") },
                onCustomUrlChange = { src, url -> vm.setCustomBaseUrl(FeedTab.FEATURED, src, url) },
                onGithubOptionsChange = { sort, min, max, archived -> vm.setGithubOptions(FeedTab.FEATURED, sort, min, max, archived) },
                onReset = { vm.resetTab(FeedTab.FEATURED) },
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.section_following))
            SourceGroup(
                tab = FeedTab.FOLLOWING,
                config = followingCfg,
                options = FeedSourceOption.optionsFor(FeedTab.FOLLOWING),
                onSelect = { src -> vm.selectSource(FeedTab.FOLLOWING, src, "") },
                onCustomUrlChange = { src, url -> vm.setCustomBaseUrl(FeedTab.FOLLOWING, src, url) },
                onGithubOptionsChange = { sort, min, max, archived -> vm.setGithubOptions(FeedTab.FOLLOWING, sort, min, max, archived) },
                onReset = { vm.resetTab(FeedTab.FOLLOWING) },
            )

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SourceGroup(
    tab: FeedTab,
    config: FeedSourceConfig,
    options: List<FeedSourceOption>,
    onSelect: (FeedSourceOption) -> Unit,
    onCustomUrlChange: (FeedSourceOption, String) -> Unit,
    onGithubOptionsChange: (String, Int, Int, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val selected = FeedSourceOption.fromId(config.sourceId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        options.forEach { option ->
            val isCurrent = option == selected
            val icon = when (option) {
                FeedSourceOption.GITHUB_SEARCH         -> Icons.Outlined.Storage
                FeedSourceOption.GITHUB_TRENDING_API   -> Icons.Outlined.Public
                FeedSourceOption.OSS_INSIGHT          -> Icons.Outlined.Public
                FeedSourceOption.HACKER_NEWS_SHOWHN   -> Icons.Outlined.Public
                FeedSourceOption.NPM_REGISTRY          -> Icons.Outlined.Storage
                FeedSourceOption.LOBSTERS              -> Icons.Outlined.Public
                FeedSourceOption.REDDIT_TOP           -> Icons.Outlined.Public
                FeedSourceOption.GITHUB_EVENTS        -> Icons.Outlined.Storage
            }
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelect(option) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            optionDisplayName(option),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            optionDescription(option),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    RadioButton(
                        selected = isCurrent,
                        onClick = { onSelect(option) },
                    )
                }

                if (option.urlModifiable && isCurrent) {
                    CustomBaseUrlField(
                        initial = config.customBaseUrl,
                        onDebouncedChange = { url -> onCustomUrlChange(option, url) },
                    )
                }
                if (option == FeedSourceOption.GITHUB_SEARCH && isCurrent) {
                    GithubSourceOptions(
                        config = config,
                        onChange = onGithubOptionsChange,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onReset) {
                Icon(Icons.Outlined.CleaningServices, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.feed_sources_reset), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun GithubSourceOptions(
    config: FeedSourceConfig,
    onChange: (String, Int, Int, Boolean) -> Unit,
) {
    var minStars by rememberSaveable(config.sourceId, config.githubMinStars, config.githubMaxStars) {
        mutableStateOf(config.githubMinStars.toString())
    }
    var maxStars by rememberSaveable(config.sourceId, config.githubMinStars, config.githubMaxStars) {
        mutableStateOf(config.githubMaxStars.toString())
    }
    val parsedMin = minStars.toIntOrNull()?.coerceAtLeast(0)
    val parsedMax = maxStars.toIntOrNull()?.coerceAtLeast(0)
    val canApply = parsedMin != null && parsedMax != null && parsedMax >= parsedMin

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.github_source_options),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "stars" to R.string.github_sort_stars,
                "forks" to R.string.github_sort_forks,
                "updated" to R.string.github_sort_updated,
            ).forEach { (value, label) ->
                FilterChip(
                    selected = config.githubSort == value,
                    onClick = {
                        onChange(
                            value,
                            config.githubMinStars,
                            config.githubMaxStars,
                            config.githubIncludeArchived,
                        )
                    },
                    label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = minStars,
                onValueChange = { minStars = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.github_min_stars)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxStars,
                onValueChange = { maxStars = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.github_max_stars)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = config.githubIncludeArchived,
                onCheckedChange = { checked ->
                    onChange(
                        config.githubSort,
                        parsedMin ?: config.githubMinStars,
                        parsedMax ?: config.githubMaxStars,
                        checked,
                    )
                },
            )
            Text(
                stringResource(R.string.github_include_archived),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    onChange(
                        config.githubSort,
                        parsedMin ?: config.githubMinStars,
                        parsedMax ?: config.githubMaxStars,
                        config.githubIncludeArchived,
                    )
                },
                enabled = canApply,
            ) {
                Text(stringResource(R.string.action_apply))
            }
        }
    }
}

@Composable
private fun CustomBaseUrlField(
    initial: String,
    onDebouncedChange: (String) -> Unit,
) {
    // The editor stores the in-progress value locally so typing never round-trips a
    // DataStore write on every keystroke. The Apply button commits; drafting in
    // progress is also surfaced live for those who just want to type and switch
    // tabs without tapping Apply.
    var url by rememberSaveable(initial) { mutableStateOf(initial) }
    var applied by remember { mutableStateOf(initial) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
    ) {
        Text(
            stringResource(R.string.feed_source_custom_url),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("https://your-trending-api.example/") },
                leadingIcon = { Icon(Icons.Outlined.Web, null, modifier = Modifier.size(16.dp)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = {
                    applied = url
                    onDebouncedChange(url.trim())
                },
                enabled = url != applied,
            ) {
                Text(stringResource(R.string.action_apply))
            }
        }
        if (url != applied) {
            Text(
                stringResource(R.string.feed_source_custom_url_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun optionDisplayName(option: FeedSourceOption): String = when (option) {
    FeedSourceOption.GITHUB_SEARCH         -> stringResource(R.string.source_name_github_search)
    FeedSourceOption.GITHUB_TRENDING_API   -> stringResource(R.string.source_name_github_trending_api)
    FeedSourceOption.OSS_INSIGHT          -> stringResource(R.string.source_name_oss_insight)
    FeedSourceOption.HACKER_NEWS_SHOWHN   -> stringResource(R.string.source_name_hn_showhn)
    FeedSourceOption.NPM_REGISTRY          -> stringResource(R.string.source_name_npm_registry)
    FeedSourceOption.LOBSTERS              -> stringResource(R.string.source_name_lobsters)
    FeedSourceOption.REDDIT_TOP           -> stringResource(R.string.source_name_reddit_top)
    FeedSourceOption.GITHUB_EVENTS        -> stringResource(R.string.source_name_github_events)
}

@Composable
private fun optionDescription(option: FeedSourceOption): String = when (option) {
    FeedSourceOption.GITHUB_SEARCH         -> stringResource(R.string.source_desc_github_search)
    FeedSourceOption.GITHUB_TRENDING_API   -> stringResource(R.string.source_desc_github_trending_api)
    FeedSourceOption.OSS_INSIGHT          -> stringResource(R.string.source_desc_oss_insight)
    FeedSourceOption.HACKER_NEWS_SHOWHN   -> stringResource(R.string.source_desc_hn_showhn)
    FeedSourceOption.NPM_REGISTRY          -> stringResource(R.string.source_desc_npm_registry)
    FeedSourceOption.LOBSTERS              -> stringResource(R.string.source_desc_lobsters)
    FeedSourceOption.REDDIT_TOP           -> stringResource(R.string.source_desc_reddit_top)
    FeedSourceOption.GITHUB_EVENTS        -> stringResource(R.string.source_desc_github_events)
}
