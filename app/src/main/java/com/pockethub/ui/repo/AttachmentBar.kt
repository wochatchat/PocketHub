package com.pockethub.ui.repo

// Attachment strip for issue editors: photo picker + file picker + queued
// chips with per-file progress. Pure UI — all state lives in the ViewModel.

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pockethub.R
import com.pockethub.ui.components.PhAsyncImage

@Composable
fun AttachmentBar(
    attachments: List<IssueAttachment>,
    enabled: Boolean,
    onAddImage: (android.net.Uri) -> Unit,
    onAddFile: (android.net.Uri) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Photo picker (no permission needed); screenshots live in the gallery.
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
    ) { uris -> uris.forEach(onAddImage) }

    // File attachments are not supported yet — the button stays visible but
    // explains instead of picking (worker only accepts images for now).
    var showFilesUnsupported by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.attachment_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = enabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.attachment_add_image),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = { showFilesUnsupported = true },
                enabled = enabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.attachment_add_file),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (showFilesUnsupported) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showFilesUnsupported = false },
                title = { Text(androidx.compose.ui.res.stringResource(R.string.attachment_files_unsupported_title)) },
                text = { Text(androidx.compose.ui.res.stringResource(R.string.attachment_files_unsupported_text)) },
                confirmButton = {
                    TextButton(onClick = { showFilesUnsupported = false }) {
                        Text(androidx.compose.ui.res.stringResource(android.R.string.ok))
                    }
                },
            )
        }

        if (attachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(attachments, key = { it.id }) { att ->
                    AttachmentChip(attachment = att, onRemove = { onRemove(att.id) })
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: IssueAttachment,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (attachment.state == AttachmentUploadState.FAILED) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.defaultMinSize(minHeight = 44.dp).padding(start = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (attachment.isImage) {
                PhAsyncImage(
                    model = attachment.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
            }
            Column {
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                )
                when (attachment.state) {
                    AttachmentUploadState.UPLOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            progress = { attachment.progress },
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${(attachment.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    AttachmentUploadState.FAILED -> Text(
                        text = attachment.error
                            ?: androidx.compose.ui.res.stringResource(R.string.attachment_failed_short),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(96.dp),
                    )
                    else -> Unit
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.action_remove),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
