package com.pockethub.data.offline

import android.content.Context
import com.pockethub.data.local.DownloadEntity
import com.pockethub.data.download.DownloadManager
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Text files above this size are not previewed (OOM guard for the viewer).
 *  1MB matches what the GitHub Contents API returns online — bigger files
 *  would blow up the viewer's prepare/layout cost. */
private const val MAX_TEXT_BYTES = 1024L * 1024

/**
 * Offline repository browsing: turns finished zip downloads (release source
 * archives, workflow artifacts) into browsable, fully-offline code trees.
 *
 * Extraction is lazy — a zip is unpacked into `filesDir/offline/<key>/` the
 * first time the user opens it, and kept for subsequent visits. Everything
 * below runs on local storage only; no network call ever happens here, so
 * the screens built on this manager work with the radio off.
 */
@Singleton
class OfflineRepoManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val downloads: DownloadManager,
) {

    /** Stable per-URL key used as the extraction directory name. */
    fun keyFor(url: String): String =
        MessageDigest.getInstance("MD5").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    /** DONE downloads whose file is a zip archive (still on disk). */
    fun offlineZipFlow(): Flow<List<DownloadEntity>> =
        downloads.doneFlow().map { list ->
            list.filter { it.fileName.endsWith(".zip", ignoreCase = true) }
        }

    fun dirFor(url: String): File = File(File(appContext.filesDir, "offline"), keyFor(url))

    /**
     * Extract the zip behind [url] (idempotent) and return the effective
     * source root. Source archives wrap everything in a single top-level
     * folder (`Repo-1.2.3/…`) — that folder is stripped so the tree starts
     * at the repository root. Returns null when the record/zip is missing
     * or extraction failed.
     */
    suspend fun ensureExtracted(url: String): File? = withContext(Dispatchers.IO) {
        val entity = downloads.get(url) ?: return@withContext null
        val zip = File(entity.localPath)
        if (!zip.exists()) return@withContext null

        val dir = dirFor(url)
        val marker = File(dir, ".extracted")
        if (!marker.exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
            val ok = runCatching {
                ZipInputStream(zip.inputStream().buffered()).use { zin ->
                    val targetPrefix = dir.canonicalPath + File.separator
                    var entry = zin.nextEntry
                    while (entry != null) {
                        val out = File(dir, entry.name)
                        // Zip-slip guard: entries must stay inside the target dir.
                        if (!out.canonicalPath.startsWith(targetPrefix)) {
                            throw SecurityException("zip entry escapes target dir: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zin.copyTo(it) }
                        }
                        entry = zin.nextEntry
                    }
                }
                marker.writeText("ok")
            }.isSuccess
            if (!ok) {
                dir.deleteRecursively()
                return@withContext null
            }
        }

        val children = dir.listFiles().orEmpty().filter { it.name != ".extracted" }
        if (children.size == 1 && children[0].isDirectory) children[0] else dir
    }

    /**
     * Recursive file listing in the shape the shared code viewer's tree panel
     * already consumes ([GitHubApi.GitTreeEntry]) — directories first handled
     * by the UI, ordering is done there. Runs off the main thread: a full repo
     * zip is thousands of stat() calls.
     */
    suspend fun treeOf(root: File): List<GitHubApi.GitTreeEntry> = withContext(Dispatchers.IO) {
        root.walkTopDown()
            .filter { it != root }
            .map { f ->
                GitHubApi.GitTreeEntry(
                    path = f.toRelativeString(root).replace(File.separatorChar, '/'),
                    type = if (f.isDirectory) "tree" else "blob",
                    size = if (f.isFile) f.length() else 0L,
                )
            }
            .toList()
    }

    /** Decoded text of an extracted file, or null for binary / oversized files. */
    suspend fun readText(root: File, path: String): String? = withContext(Dispatchers.IO) {
        val f = File(root, path).canonicalFile
        // Belt-and-suspenders: never read outside the extraction root.
        if (!f.canonicalPath.startsWith(root.canonicalPath + File.separator)) return@withContext null
        if (!f.isFile || f.length() > MAX_TEXT_BYTES) return@withContext null
        val bytes = f.readBytes()
        // Crude binary sniff: a NUL byte in the head marks binary content.
        if (bytes.take(8192).any { it == 0.toByte() }) return@withContext null
        String(bytes, Charsets.UTF_8)
    }
}
