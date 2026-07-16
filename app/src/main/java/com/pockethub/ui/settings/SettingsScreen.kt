package com.pockethub.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness2
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.ui.main.AppStartupViewModel
import com.pockethub.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    appVm: AppStartupViewModel = hiltViewModel(),
) {
    val themeMode by vm.themeMode.collectAsState()
    val customClientId by vm.customClientId.collectAsState()
    val customClientSecret by vm.customClientSecret.collectAsState()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showOAuthSheet by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Appearance")
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                headlineContent = { Text("Theme") },
                supportingContent = {
                    Text(when (themeMode) { ThemeMode.Dark -> "Dark (Linear-inspired)"; ThemeMode.Light -> "Light (Primer-inspired)"; ThemeMode.System -> "Follow system" })
                },
                modifier = Modifier.clickable { showThemeSheet = true },
            )
            HorizontalDivider()

            SectionHeader("Account")
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Logout, contentDescription = null) },
                headlineContent = { Text("Sign out") },
                supportingContent = { Text("Sign out of the current account and return to login") },
                modifier = Modifier.clickable { showSignOutDialog = true },
            )
            HorizontalDivider()

            SectionHeader("Authentication")
            ListItem(
                leadingContent = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                headlineContent = { Text("Custom OAuth Client") },
                supportingContent = { Text(if (customClientId.isBlank()) "Not configured (using PAT-only)" else "Client ID ${customClientId.take(8)}…") },
                modifier = Modifier.clickable { showOAuthSheet = true },
            )
            HorizontalDivider()

            SectionHeader("About")
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                headlineContent = { Text("About PocketHub") },
                supportingContent = { Text("Version, open source licenses") },
                modifier = Modifier.clickable { showAbout = true },
            )
            Spacer(Modifier.height(48.dp))
        }
    }

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

    if (showOAuthSheet) {
        OAuthClientSheet(
            initialId = customClientId,
            initialSecret = customClientSecret,
            onDismiss = { showOAuthSheet = false },
            onSave = { id, secret -> vm.setCustomOAuthClient(id, secret); showOAuthSheet = false },
        )
    }

    if (showAbout) {
        ModalBottomSheet(onDismissRequest = { showAbout = false }, sheetState = rememberModalBottomSheetState()) {
            AboutContent()
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out") },
            text = { Text("Sign out of the current account? You'll need to log in again to access PocketHub.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    appVm.signOut() // Navigation handled by AppNavigation observing signedOut.
                }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") } },
        )
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OAuthClientSheet(
    initialId: String,
    initialSecret: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var id by rememberSaveable { mutableStateOf(initialId) }
    var secret by rememberSaveable { mutableStateOf(initialSecret) }
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
            Text("Custom OAuth Client", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Configure your own OAuth App if you don't want to use PAT login.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                label = { Text("Client ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.VpnKey, null, modifier = Modifier.size(18.dp)) },
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("Client Secret") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    TextButton(onClick = { showSecret = !showSecret }) {
                        Text(if (showSecret) "Hide" else "Show", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(id.trim(), secret.trim()) },
                    enabled = id.isNotBlank(),
                ) { Text("Save") }
                OutlinedButton(onClick = { onSave("", "") }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
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
private fun AboutContent() {
    Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PocketHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Version 0.1.3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("A well-crafted open-source GitHub client for Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Open Source Licenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
