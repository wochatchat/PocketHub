package com.pockethub.ui.auth

import com.pockethub.R
import com.pockethub.ui.components.LinkLabel

import androidx.compose.ui.res.stringResource

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

/**
 * Small clickable hyperlink built from a single line of text. Opens [url] in a
 * Custom Tab. The link text is rendered in the primary color with an underline,
 * matching GitHub mobile's login helper style.
 */
/** Render the launcher icon (foreground vector over launcher background) as a
 *  48px bitmap for the Custom Tab close button. Bounds are expanded by 25% to
 *  compensate for the adaptive-icon safe-zone padding baked into the vector. */
private fun oauthCloseIcon(context: android.content.Context): android.graphics.Bitmap {
    val size = 48
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.parseColor("#161B22"))
    val d = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
    d?.setBounds(-size / 4, -size / 4, size + size / 4, size + size / 4)
    d?.draw(canvas)
    return bmp
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
) {
    // Activity-scoped VM: MainActivity exchanges the OAuth code on the SAME
    // instance, so the UI actually observes isLoading/success/error. The
    // default (navigation-scoped) instance splits the brain — the exchange
    // succeeds on one VM while the screen stares at another. (Ported from
    // #32 by @Wxjxpp.)
    val activity = LocalContext.current as ComponentActivity
    val vm: LoginViewModel = hiltViewModel(activity)
    val ui by vm.ui.collectAsState()
    val customClientId by vm.customClientId.collectAsState()
    val customClientSecret by vm.customClientSecret.collectAsState()
    val oauthBackendUrl by vm.oauthBackendUrl.collectAsState()
    val context = LocalContext.current
    var token by rememberSaveable { mutableStateOf("") }
    var showSelfHostSheet by rememberSaveable { mutableStateOf(false) }
    var showToken by rememberSaveable { mutableStateOf(false) }

    // Handle OAuth URL
    LaunchedEffect(ui.oauthUrl) {
        ui.oauthUrl?.let {
            // Brand the Custom Tab: launcher background color for the toolbar
            // and the app icon as the close button — makes the authorization
            // page clearly feel like PocketHub's flow, not a random browser.
            val customTabsIntent = CustomTabsIntent.Builder()
                .setToolbarColor(android.graphics.Color.parseColor("#161B22"))
                .setCloseButtonIcon(oauthCloseIcon(context))
                .build()
            customTabsIntent.launchUrl(context, Uri.parse(it))
            vm.clearOAuthUrl()
        }
    }

    // Handle success
    LaunchedEffect(ui.success) {
        if (ui.success) {
            onLoginSuccess()
            vm.consumeLoginSuccess()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Entrance: whole card fades and settles upward with a spring.
        var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
        val entrance by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "login_entrance",
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entrance
                    translationY = (1f - entrance) * 48f
                }
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(48.dp))
            // Logo plate — centered header on a left-aligned body.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(84.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(84.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(40.dp))

            // Token input
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.login_token_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = stringResource(R.string.cd_toggle_visibility),
                        )
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            val scopeText = stringResource(R.string.login_description_scopes)
            val tokenLinkText = stringResource(R.string.login_get_token_link_text)
            val tokenLinkUrl = stringResource(R.string.login_get_token_link)
            val scopeWithLink = buildAnnotatedString {
                append(scopeText)
                append(" ")
                pushStringAnnotation(tag = "URL", annotation = tokenLinkUrl)
                addStyle(
                    SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
                    start = scopeText.length + 1,
                    end = scopeText.length + 1 + tokenLinkText.length,
                )
                append(tokenLinkText)
                pop()
            }
            ClickableText(
                text = scopeWithLink,
                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                onClick = { offset ->
                    scopeWithLink.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                        CustomTabsIntent.Builder().build()
                            .launchUrl(context, Uri.parse(it.item))
                    }
                },
            )
            Spacer(Modifier.height(12.dp))

            // Sign in with token
            Button(
                onClick = { vm.signInWithToken(token.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = token.isNotBlank() && !ui.isLoading,
            ) {
                if (ui.isLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.login_with_token))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Divider — hairline lines flanking centered "or"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    "  or  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            Spacer(Modifier.height(16.dp))

            // OAuth button
            OutlinedButton(
                onClick = { vm.startOAuth() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !ui.isLoading,
            ) {
                Text(stringResource(R.string.login_with_oauth))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.login_oauth_description),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Third method — separated by a hairline divider so it reads as its
            // own group rather than a stray label under the OAuth button.
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.login_selfhost_description),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SheetLink(
                text = stringResource(R.string.login_selfhost_link_text),
                onClick = { showSelfHostSheet = true },
            )
            Spacer(Modifier.height(40.dp))

            if (showSelfHostSheet) {
                OAuthClientSheet(
                    initialId = customClientId,
                    initialSecret = customClientSecret,
                    initialBackendUrl = oauthBackendUrl,
                    onDismiss = { showSelfHostSheet = false },
                    onSave = { id, secret, backendUrl ->
                        vm.setCustomOAuthClient(id, secret)
                        vm.setOAuthBackendUrl(backendUrl)
                        showSelfHostSheet = false
                    },
                    onStartLogin = { id, secret, backendUrl ->
                        showSelfHostSheet = false
                        vm.saveAndStartSelfHostedOAuth(id, secret, backendUrl)
                    },
                )
            }

            // Error message
            if (ui.error != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            ui.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { vm.clearError() }) { Text(stringResource(R.string.action_dismiss)) }
                    }
                }
            }
        }
    }
}

/** Small link-styled text that opens the self-hosted OAuth sheet. */
@Composable
private fun SheetLink(text: String, onClick: () -> Unit) {
    val annotated = buildAnnotatedString {
        addStyle(
            SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
            start = 0, end = text.length,
        )
        append(text)
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.labelMedium,
        onClick = { onClick() },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OAuthClientSheet(
    initialId: String,
    initialSecret: String,
    initialBackendUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onStartLogin: (String, String, String) -> Unit,
) {
    var id by rememberSaveable { mutableStateOf(initialId) }
    var secret by rememberSaveable { mutableStateOf(initialSecret) }
    var backendUrl by rememberSaveable { mutableStateOf(initialBackendUrl) }
    var showSecret by rememberSaveable { mutableStateOf(false) }

    // windowInsets = ime: the sheet rides above the keyboard (the default
    // bottom-only insets leave the fields behind it); skipPartiallyExpanded
    // avoids the half-open state where inputs are already clipped.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets.ime },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.custom_oauth_client_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                LinkLabel(
                    url = stringResource(R.string.oauth_app_new_link),
                    text = stringResource(R.string.oauth_app_new_link_text),
                )
            }
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
                label = { Text(stringResource(R.string.oauth_backend_url)) },
                supportingText = { Text(stringResource(R.string.oauth_backend_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onStartLogin(id.trim(), secret.trim(), backendUrl.trim()) },
                    enabled = id.isNotBlank(),
                ) { Text(stringResource(R.string.action_login)) }
                OutlinedButton(onClick = { onSave("", "", "") }) { Text(stringResource(R.string.action_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
