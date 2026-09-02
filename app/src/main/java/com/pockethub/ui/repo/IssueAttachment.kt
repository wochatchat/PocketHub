package com.pockethub.ui.repo

// Client-side attachment model for issue creation. File bytes stay on disk
// until submit; only metadata (name/size/mime) is resolved at pick time.

import android.net.Uri

enum class AttachmentUploadState { READY, UPLOADING, DONE, FAILED }

data class IssueAttachment(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mime: String,
    val size: Long,
    val state: AttachmentUploadState = AttachmentUploadState.READY,
    /** 0f..1f while UPLOADING. */
    val progress: Float = 0f,
    /** browser_download_url once DONE. */
    val remoteUrl: String? = null,
    /** Short failure text for the chip tooltip / error line. */
    val error: String? = null,
) {
    val isImage: Boolean get() = mime.startsWith("image/")
}
