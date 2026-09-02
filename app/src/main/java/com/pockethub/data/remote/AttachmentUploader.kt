package com.pockethub.data.remote

// Issue image upload via the self-hosted Cloudflare Worker "pockethub-issue".
//
// Images live in a KV namespace behind the worker (pockethub.hippo.ccwu.cc),
// NOT in any GitHub repo — attaching works on repos the user has no push
// access to and leaves no trace in their GitHub account. The worker
// authenticates callers by exchanging the bearer token against
// api.github.com/user, so no extra credential is needed.
//
// Worker contract (see /var/minis/workspace/pockethub-issue/worker.js):
//   POST /upload?name=<file>   Authorization: Bearer <gh token>
//                              Content-Type: image/*
//                              body: raw image bytes
//   -> 200 {url}               permanent public URL for issue markdown
//   -> 413 too large           415 not an image
//   -> 507 {error:"quota_full"} CF storage full — user must contact developer
//   -> 401 invalid token
//
// Images only: the file-attachment path is intentionally not wired up yet.

import com.pockethub.data.remote.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class AttachmentUploader @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val accounts: AccountRepository,
) {
    companion object {
        /** Self-hosted worker (custom domain; CN-reachable, unlike *.workers.dev). */
        const val UPLOAD_BASE_URL = "https://pockethub.hippo.ccwu.cc"

        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    }

    /** CF storage quota exhausted — surfaced with a dedicated message. */
    class StorageFullException : IOException("storage full")

    /** Raised when an upload fails; [fileName] names the culprit. */
    class UploadException(val fileName: String, message: String, cause: Throwable? = null) :
        IOException(message, cause)

    /**
     * Upload one image, returning its permanent public URL.
     * [onProgress] receives 0f..1f (throttle on the caller side).
     */
    suspend fun upload(
        displayName: String,
        mime: String,
        bytes: ByteArray,
        onProgress: (Float) -> Unit = {},
    ): String {
        if (!mime.startsWith("image/")) {
            throw UploadException(displayName, "Only image uploads are supported")
        }
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw UploadException(displayName, "Image exceeds the ${MAX_IMAGE_BYTES / 1024 / 1024} MB limit")
        }
        val token = accounts.getActiveToken()
        if (token.isBlank()) throw UploadException(displayName, "Not signed in")

        // Network must stay off the caller's (main) dispatcher — the raw
        // OkHttp execute() below does not hop threads on its own.
        return withContext(Dispatchers.IO) {
            val response = runCatching {
                client.newCall(
                    Request.Builder()
                        .url("$UPLOAD_BASE_URL/upload?name=${urlEncode(displayName)}")
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", mime)
                        .post(progressBody(bytes, onProgress))
                        .build()
                ).execute()
            }.getOrElse { e ->
                throw UploadException(
                    displayName,
                    "Upload failed: ${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }

            response.use { resp ->
                val bodyText = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> {
                        val url = runCatching {
                            json.decodeFromString(UploadResponse.serializer(), bodyText).url
                        }.getOrElse { e -> throw UploadException(displayName, "Bad upload response", e) }
                        if (url.isBlank()) throw UploadException(displayName, "Bad upload response")
                        url
                    }
                    resp.code == 507 || resp.code == 413 && bodyText.contains("quota_full") ->
                        throw StorageFullException()
                    resp.code == 413 ->
                        throw UploadException(displayName, "Image exceeds the ${MAX_IMAGE_BYTES / 1024 / 1024} MB limit")
                    resp.code == 415 ->
                        throw UploadException(displayName, "Only image uploads are supported")
                    else ->
                        throw UploadException(displayName, "Upload failed: HTTP ${resp.code}")
                }
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class UploadResponse(val url: String = "")

    /** Streams [bytes] in chunks so [onProgress] tracks real upload progress. */
    private fun progressBody(bytes: ByteArray, onProgress: (Float) -> Unit): RequestBody =
        object : RequestBody() {
            override fun contentType() = null // set explicitly on the request
            override fun contentLength() = bytes.size.toLong()
            override fun writeTo(sink: okio.BufferedSink) {
                val chunk = 16 * 1024
                var written = 0
                while (written < bytes.size) {
                    val n = minOf(chunk, bytes.size - written)
                    sink.write(bytes, written, n)
                    written += n
                    onProgress(written.toFloat() / bytes.size)
                }
            }
        }

    private fun urlEncode(name: String): String =
        java.net.URLEncoder.encode(name, "UTF-8")
}
