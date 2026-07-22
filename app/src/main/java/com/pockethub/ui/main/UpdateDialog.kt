package com.pockethub.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockethub.R
import com.pockethub.data.remote.UpdateChecker
import androidx.compose.ui.res.stringResource

/**
 * Shown when both:
 *  - a newer non-pre-release is available on GitHub Releases, and
 *  - the user has not ignored that specific version yet.
 *
 * Offer three actions: download now (opens the release page so the system
 * handles the APK install), ignore this version (won't prompt again until a
 * newer release ships), and remind me later (just dismiss for this session).
 */
@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    onDownload: () -> Unit,
    onIgnore: () -> Unit,
    onLater: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(
                stringResource(R.string.update_available_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.update_version_line, info.latestVersionName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                info.publishedAt?.let {
                    Text(
                        stringResource(R.string.update_published, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                info.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                    val preview = if (notes.length > 600) notes.take(600) + "…" else notes
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // The app uses GitHub Releases; "Download" opens the release page in
                // the browser so GitHub's CDN serves the APK and PackageInstaller runs.
                val target = info.downloadUrl ?: info.htmlUrl ?: return@TextButton
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                onDownload()
            }) { Text(stringResource(R.string.action_download)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onIgnore) { Text(stringResource(R.string.action_ignore_version)) }
                TextButton(onClick = onLater) { Text(stringResource(R.string.action_remind_later)) }
            }
        },
    )
}
