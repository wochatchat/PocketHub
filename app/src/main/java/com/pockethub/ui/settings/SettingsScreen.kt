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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by vm.themeMode.collectAsState()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // ── Theme ──────────────────────────────────────
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                headlineContent = { Text("Theme") },
                supportingContent = { Text(when (themeMode) { ThemeMode.Dark -> "Dark (Linear-inspired)"; ThemeMode.Light -> "Light (Primer-inspired)"; ThemeMode.System -> "Follow system" }) },
                modifier = Modifier.clickable { showThemeSheet = true },
            )

            HorizontalDivider()

            // ── OAuth Custom Client ──
            ListItem(
                headlineContent = { Text("OAuth Client") },
                supportingContent = { Text("Custom OAuth Client ID and Secret") },
                modifier = Modifier.clickable { /* TODO: OAuth client config sheet */ },
            )

            HorizontalDivider()

            // ── About ──────────────────────────────────────
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                headlineContent = { Text("About PocketHub") },
                supportingContent = { Text("Version, open source licenses") },
                modifier = Modifier.clickable { showAbout = true },
            )
        }
    }

    // Theme picker bottom sheet
    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                ThemeOption("Dark (Linear-inspired)", themeMode == ThemeMode.Dark) { vm.setThemeMode(ThemeMode.Dark); showThemeSheet = false }
                ThemeOption("Light (Primer-inspired)", themeMode == ThemeMode.Light) { vm.setThemeMode(ThemeMode.Light); showThemeSheet = false }
                ThemeOption("Follow system", themeMode == ThemeMode.System) { vm.setThemeMode(ThemeMode.System); showThemeSheet = false }
            }
        }
    }

    // About bottom sheet
    if (showAbout) {
        ModalBottomSheet(onDismissRequest = { showAbout = false }, sheetState = rememberModalBottomSheetState()) {
            AboutContent()
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
private fun AboutContent() {
    Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PocketHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Version 0.1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("A well-crafted open-source GitHub client for Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Open Source Licenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OpenSourceLicensesList()
    }
}

@Composable
private fun OpenSourceLicensesList() {
    // Hand-curated list matching project dependencies
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
                Text("by $author · $license", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "PocketHub itself is licensed under the Apache License 2.0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
