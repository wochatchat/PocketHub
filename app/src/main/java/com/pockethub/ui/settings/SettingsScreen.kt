package com.pockethub.ui.settings

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness2
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.GTranslate
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.BuildConfig
import com.pockethub.ui.theme.AppStyle
import com.pockethub.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.pockethub.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToFeedSources: () -> Unit = {},
    onSignOut: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by vm.themeMode.collectAsState()
    val appStyle by vm.appStyle.collectAsState()
    val followSystemTheme by vm.followSystemTheme.collectAsState()
    val appLocale by vm.appLocale.collectAsState()
    val customClientId by vm.customClientId.collectAsState()
    val downloadMirrorPrefix by vm.downloadMirrorPrefix.collectAsState()
    val customClientSecret by vm.customClientSecret.collectAsState()
    val oauthBackendUrl by vm.oauthBackendUrl.collectAsState()
    val dohUrl by vm.dohUrl.collectAsState()
    val accountCount by vm.accountCount.collectAsState()
    val cacheSizeBytes by vm.cacheSizeBytes.collectAsState()
    val translateTarget by vm.translateTarget.collectAsState()
    var showStyleSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showTranslateSheet by remember { mutableStateOf(false) }
    var showOAuthSheet by remember { mutableStateOf(false) }
    var showMirrorSheet by remember { mutableStateOf(false) }
    var showDohSheet by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val issueCount by vm.issueCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Manual update check — independent Hilt VM so the same UpdateDialog is reused.
    val updateVm: com.pockethub.ui.main.UpdateViewModel = hiltViewModel()
    val updateState by updateVm.state.collectAsState()

    // Compute cache size once on entry so the user sees current disk usage.
    LaunchedEffect(Unit) {
        val bytes = withContext(Dispatchers.IO) { appCacheSize(context.cacheDir) }
        vm.setCacheSize(bytes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.section_appearance))
            com.pockethub.ui.components.PhCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), cornerRadius = 18.dp) {
                Column {
            // Single appearance entry — combines the old "theme mode"
            // (Dark/Light/System) and "app style" pickers into one coherent
            // list. The available styles double as dark/light theme presets.
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.app_style)) },
                supportingContent = { Text(styleLabel(appStyle)) },
                modifier = Modifier.clickable { showStyleSheet = true },
            )
            // Follow-system night mode: when on, the OS entering dark mode
            // forces the built-in dark style; leaving it restores the style
            // chosen above. The persisted style preference is never modified.
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Brightness6, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.follow_system_theme)) },
                supportingContent = { Text(stringResource(R.string.follow_system_theme_desc)) },
                trailingContent = {
                    Checkbox(
                        checked = followSystemTheme,
                        onCheckedChange = { vm.setFollowSystemTheme(it) },
                    )
                },
                modifier = Modifier.clickable { vm.setFollowSystemTheme(!followSystemTheme) },
            )
                }
            }

            SectionHeader(stringResource(R.string.section_language))
            com.pockethub.ui.components.PhCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), cornerRadius = 18.dp) {
                Column {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Translate, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.app_language)) },
                supportingContent = { Text(localeLabel(appLocale)) },
                modifier = Modifier.clickable { showLanguageSheet = true },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.GTranslate, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.translate_readme)) },
                supportingContent = {
                    Text(when (translateTarget) {
                        "zh" -> stringResource(R.string.translate_to_chinese)
                        "en" -> stringResource(R.string.translate_to_english)
                        else -> stringResource(R.string.translate_off)
                    })
                },
                modifier = Modifier.clickable { showTranslateSheet = true },
            )
                }
            }

            SectionHeader(stringResource(R.string.section_notifications))
            com.pockethub.ui.components.PhCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), cornerRadius = 18.dp) {
                Column {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Brightness2, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.system_notification_settings)) },
                supportingContent = { Text(stringResource(R.string.system_notification_settings_summary)) },
                modifier = Modifier.clickable { openAppNotificationSettings(context) },
            )
                }
            }

            SectionHeader(stringResource(R.string.section_explore))
            com.pockethub.ui.components.PhCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), cornerRadius = 18.dp) {
                Column {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Public, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.feed_sources)) },
                supportingContent = { Text(stringResource(R.string.feed_sources_intro)) },
                modifier = Modifier.clickable { onNavigateToFeedSources() },
            )
                }
            }

            SectionHeader(stringResource(R.string.section_account))
            com.pockethub.ui.components.PhCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), cornerRadius = 18.dp) {
                Column {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.ManageAccounts, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.accounts)) },
                supportingContent = { Text(stringResource(R.string.accounts_summary, accountCount)) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.custom_oauth_client)) },
                supportingContent = { Text(if (customClientId.isBlank()) stringResource(R.string.custom_oauth_client_not_configured) else stringResource(R.string.custom_oauth_client_configured, customClientId.take(8))) },
                modifier = Modifier.clickable { showOAuthSheet = true },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.RocketLaunch, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.mirror_prefix_title)) },
                supportingContent = { Text(if (downloadMirrorPrefix.isBlank()) stringResource(R.string.mirror_prefix_not_set) else stringResource(R.string.mirror_prefix_set, downloadMirrorPrefix)) },
                modifier = Modifier.clickable { showMirrorSheet = true },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Public, contentDescription = null) },
                headlineContent = { Text("加密 DNS（DoH）") },
                supportingContent = { Text(dohUrl) },
                modifier = Modifier.clickable { showDohSheet = true },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.clear_cache)) },
                supportingContent = { Text(stringResource(R.string.clear_cache_summary, formatBytes(cacheSizeBytes))) },
                modifier = Modifier.clickable {
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) { clearCache(context.cacheDir) }
                        vm.setCacheSize(bytes)
                        snackbarHostState.showSnackbar(context.getString(R.string.cache_cleared))
                    }
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Logout, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.action_sign_out)) },
                supportingContent = { Text(stringResource(R.string.sign_out_summary)) },
                modifier = Modifier.clickable { showSignOutDialog = true },
            )
                }
            }

            SectionHeader(stringResource(R.string.section_severe_issues))
            com.pockethub.ui.components.PhCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), cornerRadius = 18.dp) {
                Column {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.severe_issues_send_now)) },
                supportingContent = {
                    Text(stringResource(R.string.severe_issues_one_tap_summary, issueCount))
                },
                modifier = Modifier.clickable(enabled = issueCount > 0) {
                    scope.launch {
                        val events = vm.issueEvents()
                        if (events.isEmpty()) {
                            snackbarHostState.showSnackbar(context.getString(R.string.severe_issues_none_local))
                        } else {
                            sendIssueReportByEmail(context, events)
                            vm.clearIssueLog()
                        }
                    }
                },
            )
                }
            }


            SectionHeader(stringResource(R.string.section_about))
            com.pockethub.ui.components.PhCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), cornerRadius = 18.dp) {
                Column {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.update_check_now)) },
                supportingContent = {
                    Text(
                        when (updateState) {
                            is com.pockethub.ui.main.UpdateViewModel.State.Checking -> stringResource(R.string.update_checking)
                            is com.pockethub.ui.main.UpdateViewModel.State.UpToDate -> stringResource(R.string.update_uptodate)
                            is com.pockethub.ui.main.UpdateViewModel.State.Error -> stringResource(R.string.update_check_failed)
                            is com.pockethub.ui.main.UpdateViewModel.State.UpdateAvailable -> {
                                val v = (updateState as com.pockethub.ui.main.UpdateViewModel.State.UpdateAvailable).info.latestVersionName
                                "${v} →"
                            }
                            else -> stringResource(R.string.update_check_summary)
                        }
                    )
                },
                modifier = Modifier.clickable { updateVm.manualCheck() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.about_pockethub)) },
                supportingContent = { Text(stringResource(R.string.version_template, BuildConfig.VERSION_NAME)) },
                modifier = Modifier.clickable { showAbout = true },
            )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showStyleSheet) {
        // Curated list — drops the old "Default (follows theme)" row so every
        // option is a concrete style. `appStyle == null` (legacy / first-run)
        // shows up as Linear Dark selected, because resolveStyle maps null +
        // ThemeMode.Dark to AppStyle.LinearDark. Removing rows vs. removing the
        // enum entries themselves keeps any already-persisted preference
        // (Lavender / Forest) working — those users still see their baseline.
        val visibleStyles = listOf(
            AppStyle.LinearDark,
            AppStyle.PrimerLight,
            AppStyle.Paper,
            AppStyle.Neon,
        )
        ModalBottomSheet(onDismissRequest = { showStyleSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(stringResource(R.string.app_style), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                visibleStyles.forEach { style ->
                    // Null appStyle resolves to LinearDark under the default
                    // (Dark) theme mode — show that as selected so first-run
                    // users land on a highlighted row.
                    val isSelected = appStyle == style || (appStyle == null && style == AppStyle.LinearDark)
                    StyleOption(style, isSelected, followSystemTheme) {
                        vm.setAppStyle(style)
                        // Keep themeMode consistent: dark styles → Dark, light styles → Light,
                        // so the legacy status-bar / system-bar tint logic stays correct.
                        vm.setThemeMode(if (com.pockethub.ui.theme.styleDef(style).isDark) ThemeMode.Dark else ThemeMode.Light)
                        showStyleSheet = false
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                FollowSystemOption(followSystemTheme) { vm.setFollowSystemTheme(it) }
            }
        }
    }

    if (showLanguageSheet) {
        ModalBottomSheet(onDismissRequest = { showLanguageSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                AppLocale.entries.forEach { locale ->
                    LanguageOption(
                        label = localeLabel(locale),
                        selected = appLocale == locale,
                        onClick = { vm.setAppLocale(locale); showLanguageSheet = false },
                    )
                }
            }
        }
    }

    if (showTranslateSheet) {
        ModalBottomSheet(onDismissRequest = { showTranslateSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(stringResource(R.string.translate_readme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                TranslateOption(stringResource(R.string.translate_off), translateTarget == null) { vm.setTranslateTarget(null); showTranslateSheet = false }
                TranslateOption(stringResource(R.string.translate_to_chinese), translateTarget == "zh") { vm.setTranslateTarget("zh"); showTranslateSheet = false }
                TranslateOption(stringResource(R.string.translate_to_english), translateTarget == "en") { vm.setTranslateTarget("en"); showTranslateSheet = false }
            }
        }
    }

    if (showOAuthSheet) {
        OAuthClientSheet(
            initialId = customClientId,
            initialSecret = customClientSecret,
            initialBackendUrl = oauthBackendUrl,
            onDismiss = { showOAuthSheet = false },
            onSave = { id, secret, backendUrl -> vm.setCustomOAuthClient(id, secret); vm.setOAuthBackendUrl(backendUrl); showOAuthSheet = false },
        )
    }

    if (showMirrorSheet) {
        MirrorPrefixSheet(
            initial = downloadMirrorPrefix,
            onDismiss = { showMirrorSheet = false },
            onSave = { prefix -> vm.setDownloadMirrorPrefix(prefix); showMirrorSheet = false },
        )
    }

    if (showDohSheet) {
        DohSettingsSheet(initial = dohUrl, onDismiss = { showDohSheet = false }, onSave = { url -> vm.setDohUrl(url); showDohSheet = false })
    }
    if (showAbout) {
        ModalBottomSheet(onDismissRequest = { showAbout = false }, sheetState = rememberModalBottomSheetState()) {
            AboutContent()
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.sign_out_dialog_title)) },
            text = { Text(stringResource(R.string.sign_out_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    onSignOut() // Navigation handled by AppNavigation observing signedOut.
                }) { Text(stringResource(R.string.action_sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    // Manual update check results.
    val updateDownload by updateVm.download.collectAsState()
    when (val s = updateState) {
        is com.pockethub.ui.main.UpdateViewModel.State.UpdateAvailable ->
            com.pockethub.ui.main.UpdateDialog(
                info = s.info,
                downloadState = updateDownload,
                onDownload = { updateVm.startDownload(s.info) },
                onCancel = { updateVm.cancelDownload() },
                onInstall = { path -> updateVm.install(context, path) },
                onRetry = { updateVm.startDownload(s.info) },
                onIgnore = { updateVm.ignoreVersion(s.info.latestVersionName) },
                onLater = { updateVm.dismiss() },
            )
        is com.pockethub.ui.main.UpdateViewModel.State.UpToDate -> {
            LaunchedEffect(s) {
                snackbarHostState.showSnackbar(context.getString(R.string.update_uptodate))
                updateVm.dismiss()
            }
        }
        is com.pockethub.ui.main.UpdateViewModel.State.Error -> {
            LaunchedEffect(s) {
                snackbarHostState.showSnackbar(context.getString(R.string.update_check_failed))
                updateVm.dismiss()
            }
        }
        else -> Unit
    }
}
private data class DohOption(val name: String, val url: String)
private val builtInDoh = listOf(
    DohOption("阿里 DNS（国内）", "https://dns.alidns.com/dns-query"),
    DohOption("腾讯 DNSPod（国内）", "https://doh.pub/dns-query"),
    DohOption("360 安全 DNS（国内）", "https://doh.360.cn"),
    DohOption("18Bit DNS（国内）", "https://doh.18bit.cn/dns-query"),
    DohOption("Cloudflare", "https://1.1.1.1/dns-query"),
    DohOption("Google Public DNS", "https://dns.google/dns-query"),
    DohOption("Quad9", "https://dns.quad9.net/dns-query"),
    DohOption("AdGuard DNS", "https://dns.adguard-dns.com/dns-query"),
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DohSettingsSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(initial) }
    var custom by rememberSaveable { mutableStateOf(if (builtInDoh.none { it.url == initial }) initial else "") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(16.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("选择加密 DNS（DoH）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("优先使用国内服务。修改后重启应用，使新的 DNS 解析器生效。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            builtInDoh.forEach { option ->
                ListItem(headlineContent = { Text(option.name) }, supportingContent = { Text(option.url) }, leadingContent = { RadioButton(selected == option.url, onClick = { selected = option.url; custom = "" }) }, modifier = Modifier.clickable { selected = option.url; custom = "" })
            }
            OutlinedTextField(value = custom, onValueChange = { custom = it; selected = it }, label = { Text("自定义 DoH 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(enabled = selected.startsWith("https://"), onClick = { onSave(selected.trim()) }) { Text("保存") }
            }
        }
    }
}

private data class MirrorOption(val name: String, val prefix: String)
private data class MirrorSpeed(val option: MirrorOption, val bytesPerSecond: Long, val code: Int)

private val builtInMirrors = listOf(
    MirrorOption("gh-proxy.com", "https://gh-proxy.com/"),
    MirrorOption("v6.gh-proxy.org", "https://v6.gh-proxy.org/"),
    MirrorOption("gh.1s.fan", "https://gh.1s.fan/"),
    MirrorOption("ghproxy.net", "https://ghproxy.net/"),
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MirrorPrefixSheet(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(initial) }
    var testing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<MirrorSpeed>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val sample = "https://github.com/Wxjxpp/PocketHub_Next/archive/refs/heads/main.zip"
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.mirror_prefix_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("选择加速站，测速会实际下载测试数据，而不是只测延迟。测试结果仅代表当前网络。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            builtInMirrors.forEach { option ->
                val result = results.firstOrNull { it.option == option }
                ListItem(
                    headlineContent = { Text(option.name) },
                    supportingContent = { Text(result?.let { if (it.code in 200..299 && it.bytesPerSecond > 0) "${formatSpeed(it.bytesPerSecond)} · HTTP ${it.code}" else "不可用 · HTTP ${it.code}" } ?: option.prefix) },
                    leadingContent = { RadioButton(selected == option.prefix, onClick = { selected = option.prefix }) },
                    modifier = Modifier.clickable { selected = option.prefix },
                )
            }
            ListItem(
                headlineContent = { Text("直连（关闭加速）") },
                leadingContent = { RadioButton(selected.isBlank(), onClick = { selected = "" }) },
                modifier = Modifier.clickable { selected = "" },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !testing, onClick = {
                    testing = true
                    scope.launch {
                        results = withContext(Dispatchers.IO) { builtInMirrors.map { testMirror(it, sample) } }
                        testing = false
                    }
                }) {
                    if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("测速")
                }
                Button(onClick = { onSave(selected) }) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(onClick = { onSave("") }) { Text(stringResource(R.string.action_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun testMirror(option: MirrorOption, original: String): MirrorSpeed {
    val started = System.nanoTime()
    var bytes = 0L
    var code = 0
    runCatching {
        val connection = java.net.URL(option.prefix + original).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.instanceFollowRedirects = true
        code = connection.responseCode
        if (code in 200..299) connection.inputStream.use { input ->
            val buffer = ByteArray(32 * 1024)
            while (System.nanoTime() - started < 5_000_000_000L) {
                val count = input.read(buffer)
                if (count <= 0) break
                bytes += count
            }
        }
        connection.disconnect()
    }
    val seconds = ((System.nanoTime() - started).coerceAtLeast(1L)) / 1_000_000_000.0
    return MirrorSpeed(option, (bytes / seconds).toLong(), code)
}

private fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_048_576 -> "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)
    bytesPerSecond >= 1024 -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
    else -> "$bytesPerSecond B/s"
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OAuthClientSheet(
    initialId: String,
    initialSecret: String,
    initialBackendUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var id by rememberSaveable { mutableStateOf(initialId) }
    var secret by rememberSaveable { mutableStateOf(initialSecret) }
    var backendUrl by rememberSaveable { mutableStateOf(initialBackendUrl) }
    var showSecret by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.custom_oauth_client_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.custom_oauth_client_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                label = { Text(stringResource(R.string.client_id)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.VpnKey, null, modifier = Modifier.size(18.dp)) },
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(stringResource(R.string.client_secret)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    TextButton(onClick = { showSecret = !showSecret }) {
                        Text(stringResource(if (showSecret) R.string.action_hide else R.string.action_show), style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                label = { Text("OAuth 后端地址") },
                placeholder = { Text("https://你的-worker.workers.dev") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(id.trim(), secret.trim(), backendUrl.trim()) },
                    enabled = backendUrl.trim().startsWith("https://"),
                ) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(onClick = { onSave("", "", "") }) { Text(stringResource(R.string.action_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.height(0.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun styleLabel(style: AppStyle?): String = when (style) {
    // null = legacy / first-run. resolveStyle maps null + ThemeMode.Dark (the
    // default) to AppStyle.LinearDark, so the list-row supporting text mirrors
    // that instead of showing a now-retired "Default" label.
    null, AppStyle.LinearDark -> stringResource(R.string.style_linear_dark)
    AppStyle.PrimerLight -> stringResource(R.string.style_primer_light)
    AppStyle.Paper -> stringResource(R.string.style_paper)
    AppStyle.Neon -> stringResource(R.string.style_neon)
    AppStyle.Lavender -> stringResource(R.string.style_lavender)
    AppStyle.Forest -> stringResource(R.string.style_forest)
}

/** Visual style picker row — shows the style's palette as swatches plus a shape hint. */
@Composable
private fun StyleOption(style: AppStyle, selected: Boolean, followSystemTheme: Boolean = false, onClick: () -> Unit) {
    val def = com.pockethub.ui.theme.styleDef(style)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        // Palette swatches: background / surfaceVariant / primary / secondary / tertiary
        Row(Modifier.padding(start = 12.dp)) {
            val swatches = listOf(
                def.colors.background, def.colors.surfaceVariant,
                def.colors.primary, def.colors.secondary, def.colors.tertiary,
            )
            swatches.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .padding(start = if (i == 0) 0.dp else 4.dp)
                        .size(20.dp)
                        .background(c, def.shapes.small)
                        .border(1.dp, def.colors.outline.copy(alpha = 0.4f), def.shapes.small)
                )
            }
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(styleLabel(style), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (def.isDark) stringResource(R.string.theme_dark) else stringResource(R.string.theme_light),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Light styles double as the night-mode baseline: with "follow
            // system" on, the OS dark mode temporarily overrides this style.
            if (!def.isDark && followSystemTheme) {
                Text(
                    stringResource(R.string.follow_system_theme_style_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * "Follow system dark mode" toggle at the bottom of the style sheet. When on,
 * the system entering night mode forces the built-in dark style over any
 * selected style; leaving night mode restores the user's choice.
 */
@Composable
private fun FollowSystemOption(checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onChecked(it) })
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(R.string.follow_system_theme), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.follow_system_theme_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.height(0.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun TranslateOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.height(0.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun AboutContent() {
    Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.version_template, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        // Privacy & security notes moved here from the removed settings section.
        Text(stringResource(R.string.section_privacy_security), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("• " + stringResource(R.string.token_storage_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("• " + stringResource(R.string.analytics_telemetry_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.open_source_licenses), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OpenSourceLicensesList()
    }
}

@Composable
private fun OpenSourceLicensesList() {
    val libs = listOf(
        Triple("Kotlin", "JetBrains", "Apache License 2.0"),
        Triple("Jetpack Compose (Material 3)", "Google / AOSP", "Apache License 2.0"),
        Triple("Kotlinx Coroutines", "JetBrains", "Apache License 2.0"),
        Triple("Kotlinx Serialization", "JetBrains", "Apache License 2.0"),
        Triple("Hilt / Dagger", "Google", "Apache License 2.0"),
        Triple("Room", "Google / AOSP", "Apache License 2.0"),
        Triple("Retrofit", "Square, Inc.", "Apache License 2.0"),
        Triple("OkHttp", "Square, Inc.", "Apache License 2.0"),
        Triple("Coil", "coil-kt", "Apache License 2.0"),
        Triple("AndroidX Browser / Custom Tabs", "Google / AOSP", "Apache License 2.0"),
        Triple("androidx-datastore", "Google / AOSP", "Apache License 2.0"),
        Triple("androidx-navigation-compose", "Google / AOSP", "Apache License 2.0"),
        Triple("androidx-lifecycle-runtime-ktx", "Google / AOSP", "Apache License 2.0"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        libs.forEach { (name, author, license) ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.by_author_license, author, license), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pockethub_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── helpers ────────────────────────────────────────────────────────────

/** Severe-issue reports always go to the developer's inbox. */
internal const val DEVELOPER_EMAIL = "wochatchat@gmail.com"

/**
 * Build a well-formatted severe-issue report from the local ring buffer.
 * Returns null when there is nothing to report (caller shows a reminder).
 */

private fun appCacheSize(cacheDir: File): Long {
    if (!cacheDir.exists()) return 0
    return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

/** Best-effort cache clear — must run on a background thread. */
private suspend fun clearCache(dir: File): Long {
    if (dir.exists()) dir.walkTopDown().forEach { runCatching { it.delete() } }
    return 0L
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L          -> "%.1f KB".format(bytes / 1024.0)
    else                   -> "$bytes B"
}
