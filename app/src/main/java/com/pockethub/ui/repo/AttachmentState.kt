package com.pockethub.ui.repo

// Reusable attachment queue shared by the issue editor and the comment box:
// pick metadata resolution, per-file upload state, and markdown generation.
// VMs own an instance and expose [attachments]/[add]/[remove] plus call
// [uploadAll] right before posting.
//
// Images only for now: non-image picks are rejected at [add] time (the UI
// intercepts file picks and explains, so this is defense in depth).

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.pockethub.data.remote.AttachmentUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class AttachmentState(
    private val appContext: Context,
    private val uploader: AttachmentUploader,
    /** Emitted when a pick is rejected locally (e.g. over [AttachmentUploader.MAX_IMAGE_BYTES]). */
    private val onPickRejected: (Int) -> Unit = {},
) {
    private val _attachments = MutableStateFlow<List<IssueAttachment>>(emptyList())
    val attachments: StateFlow<List<IssueAttachment>> = _attachments.asStateFlow()

    private var nextId = 1L

    /** Resolve pick metadata (display name / size / mime) and queue the image. */
    fun add(uri: Uri) {
        val resolver = appContext.contentResolver
        var name = "image.png"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0) ?: name
                        size = if (!c.isNull(1)) c.getLong(1) else -1L
                    }
                }
        }
        // Persistable grant survives config changes / process recreation for
        // document-picker results; photo-picker URIs reject it and stay
        // session-scoped (fine — the whole queue dies with the process anyway).
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val mime = runCatching { resolver.getType(uri) }.getOrNull() ?: "application/octet-stream"
        // Images only (worker enforces this too; catch it early for a clean chip)
        if (!mime.startsWith("image/")) return
        // App-side size gate: >2MB screenshots waste upload time and blow up
        // load times on slow links — reject here so the user never queues them.
        if (size > AttachmentUploader.MAX_IMAGE_BYTES) {
            onPickRejected(if (size > 0) size.toInt() else -1)
            return
        }
        _attachments.update { list ->
            list + IssueAttachment(
                id = nextId++,
                uri = uri,
                displayName = name,
                mime = mime,
                size = size,
            )
        }
    }

    fun remove(id: Long) {
        _attachments.update { list -> list.filterNot { it.id == id } }
    }

    /** Reset the queue after a successful post (the comment box is reused). */
    fun clearForNext() {
        _attachments.value = emptyList()
    }

    private fun update(id: Long, transform: (IssueAttachment) -> IssueAttachment) {
        _attachments.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    /**
     * Upload every not-yet-uploaded attachment (sequentially, so progress and
     * failures stay readable). Returns the markdown block to append to the
     * issue/comment body, or null when there are no attachments. Throws
     * [AttachmentUploader.UploadException] on the first failure — the post is
     * NOT sent in that case; DONE entries keep their URLs for the next try.
     *
     * [AttachmentUploader.StorageFullException] propagates unchanged so VMs
     * can show the dedicated "CF storage full" message.
     */
    suspend fun uploadAll(): String? {
        val list = _attachments.value
        if (list.isEmpty()) return null

        for (att in list) {
            if (att.state == AttachmentUploadState.DONE && att.remoteUrl != null) continue
            update(att.id) {
                it.copy(state = AttachmentUploadState.UPLOADING, progress = 0f, error = null)
            }
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(att.uri)?.use { it.readBytes() }
                        ?: throw AttachmentUploader.UploadException(att.displayName, "Image is no longer readable")
                }
                var lastReported = 0f
                val url = uploader.upload(att.displayName, att.mime, bytes) { p ->
                    // Only push ~5% steps into Compose state to avoid recomposition spam.
                    if (p - lastReported >= 0.05f || p >= 1f) {
                        lastReported = p
                        update(att.id) { it.copy(progress = p) }
                    }
                }
                update(att.id) {
                    it.copy(state = AttachmentUploadState.DONE, progress = 1f, remoteUrl = url)
                }
            } catch (e: AttachmentUploader.UploadException) {
                update(att.id) {
                    it.copy(state = AttachmentUploadState.FAILED, error = e.message)
                }
                throw e
            }
        }

        val done = _attachments.value.filter { it.remoteUrl != null }
        if (done.isEmpty()) return null
        return done.joinToString("\n") { a ->
            "![${a.displayName}](${a.remoteUrl})"
        }
    }
}
