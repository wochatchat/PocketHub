package com.pockethub.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    vm: LoginViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var token by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var expandedToken by rememberSaveable { mutableStateOf(false) }

    // Launch OAuth URL via CustomTabs
    LaunchedEffect(ui.oauthUrl) {
        ui.oauthUrl?.let {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, Uri.parse(it))
            vm.clearOAuthUrl()
        }
    }

    // Handle login success
    LaunchedEffect(ui.success) {
        if (ui.success) onLoginSuccess()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 80.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Brand ──────────────────────────────────────────
                BrandBlock()

                Spacer(Modifier.height(16.dp))

                // ── Primary: GitHub OAuth ─────────────────────────
                Button(
                    onClick = { vm.startOAuth() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !ui.isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    GitHubLogo(Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Sign in with GitHub",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                // ── Secondary: login via GitHub website (username/password handled by GitHub itself)
                TextButton(
                    onClick = {
                        val url = "https://github.com/login"
                        val customTabsIntent = CustomTabsIntent.Builder().build()
                        customTabsIntent.launchUrl(context, Uri.parse(url))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Sign in via GitHub website",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── Divider ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        "  or  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                // ── Tertiary: Personal Access Token (collapsible) ──
                OutlinedButton(
                    onClick = { expandedToken = !expandedToken },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use a Personal Access Token", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (expandedToken) Icons.AutoMirrored.Filled.ArrowDropUp
                        else Icons.AutoMirrored.Filled.ArrowDropDown,
                        null,
                        modifier = Modifier.size(20.dp),
                    )
                }

                AnimatedVisibility(
                    visible = expandedToken,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Personal Access Token") },
                            placeholder = { Text("github_pat_… or ghp_…") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "Toggle visibility",
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.colors(
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                        Text(
                            "创建 Token：GitHub Settings → Developer settings → Personal access tokens，选 repo / read:user / user:email / read:org / read:notifications 权限。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { vm.signInWithToken(token.trim()) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = token.isNotBlank() && !ui.isLoading,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            if (ui.isLoading) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Sign in with Token")
                            }
                        }
                    }
                }

                // ── Error banner ───────────────────────────────────
                AnimatedVisibility(
                    visible = ui.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ui.error?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { vm.clearError() }) { Text("Dismiss") }
                            }
                        }
                    }
                }

                // ── Privacy footer ────────────────────────────────
                Spacer(Modifier.height(20.dp))
                Text(
                    "PocketHub 只在你授权的 GitHub 账号范围内读取数据，\n不会上传或分享你的凭证给第三方。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Center loading overlay
            if (ui.isLoading && !expandedToken) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                    ) {
                        Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// ── Composable building blocks ───────────────────────────────────────

@Composable
private fun BrandBlock() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(1.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GitHubLogo(Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text("PocketHub", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            "Your pocket GitHub client · works with any GitHub account",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * GitHub octocat logo — using Material's Pets icon as a stand-in cat silhouette.
 * Pure-Compose vector Octocat paths produce unreliable results at small sizes.
 */
@Composable
private fun GitHubLogo(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        imageVector = Icons.Filled.Pets,
        contentDescription = "GitHub",
        tint = tint,
        modifier = modifier,
    )
}
