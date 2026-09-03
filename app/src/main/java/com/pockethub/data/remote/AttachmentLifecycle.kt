package com.pockethub.data.remote

// Attachment lifecycle helper: keeps CF-stored images in sync with the
// GitHub content that references them.
//
// Scope: the app can only observe changes it makes itself. Closing/reopening
// an issue intentionally keeps images (content isn't gone, reopening must
// still render). Web-side deletions are invisible to the worker — that needs
// a periodic reconciliation job, out of scope for now.

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AttachmentLifecycle {

    /** Matches image URLs pointing at the PocketHub attachment worker. */
    private val WORKER_IMAGE = Regex(
        "!\\[[^\\]]*\\]\\((" + Regex.escape(AttachmentUploader.UPLOAD_BASE_URL) + "/a/[^)]+)\\)",
    )

    /** All worker-image URLs referenced by a markdown body, in order. */
    fun referencedUrls(body: String?): List<String> {
        if (body.isNullOrBlank()) return emptyList()
        return WORKER_IMAGE.findAll(body).map { it.groupValues[1] }.toList()
    }

    /**
     * Best-effort DELETE of [removed] URLs (images present before but gone
     * after an edit, or the whole body when a comment is deleted).
     * Never throws — orphaned images are a quota nuisance, not a user error.
     */
    suspend fun cleanupRemoved(
        uploader: AttachmentUploader,
        removed: List<String>,
    ) {
        if (removed.isEmpty()) return
        withContext(Dispatchers.IO) {
            for (url in removed) {
                runCatching { uploader.delete(url) }
            }
        }
    }
}
