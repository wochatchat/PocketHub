package com.pockethub.data.download

import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import com.pockethub.util.humanBytes

/**
 * Extracts a downloaded workflow-artifact zip into a directory, with the
 * safety guards a client-side unzipper needs for untrusted CI output:
 *
 *  - **zip-bomb guard**: caps total extracted bytes, per-file size and entry
 *    count, so a hostile or buggy artifact cannot fill disk / RAM.
 *  - **path-traversal guard**: rejects entries that escape the target dir
 *    (`../`, absolute paths), and strips drive-style/backslash paths.
 *  - **encoding fallback**: CI zips are usually UTF-8; some tools (Windows
 *    runners, older zip tools) still emit GBK names. We detect replacement
 *    chars from a UTF-8 decode attempt and re-extract with GBK.
 *
 * Returns the extracted files (relative name + absolute path + size), which
 * callers can hand to the shared file opener (APK install / ACTION_VIEW).
 */
@Singleton
class ArtifactExtractor @Inject constructor() {

    data class ExtractedFile(
        val name: String,        // path relative to the extraction root
        val path: String,        // absolute path on disk
        val size: Long,
    )

    /**
     * Extract [zipFile] into [destDir] (created if missing).
     * Any previous contents of [destDir] are left untouched — callers should
     * pass a fresh dir (or clear it) to avoid stale files being reported.
     * @throws IOException on guard violations or corrupt zip.
     */
    fun extract(zipFile: File, destDir: File): List<ExtractedFile> {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw IOException("Cannot create extraction dir: ${destDir.absolutePath}")
        }
        // Try UTF-8 first; fall back to GBK when names show replacement chars.
        val (result, hadReplacement) = tryExtract(zipFile, destDir, Charsets.UTF_8)
        if (!hadReplacement) return result
        destDir.deleteRecursively()
        if (!destDir.mkdirs()) throw IOException("Cannot recreate extraction dir")
        return tryExtract(zipFile, destDir, Charset.forName("GBK")).first
    }

    /**
     * List files previously extracted into [destDir] (deepest-first so parent
     * dirs sort after their children). Returns null when [destDir] doesn't
     * exist or holds no files, letting callers fall back to a re-extract.
     */
    fun listExtracted(destDir: File): List<ExtractedFile>? {
        if (!destDir.isDirectory) return null
        val files = destDir.walkTopDown().filter { it.isFile }
            .map { f ->
                val rel = f.relativeTo(destDir).path.replace(File.separatorChar, '/')
                ExtractedFile(name = rel, path = f.absolutePath, size = f.length())
            }
            .toList()
        // Deepest first so parent dirs sort after their children.
        files.sortByDescending { it.name.count { c -> c == '/' } }
        return files.ifEmpty { null }
    }

    private fun tryExtract(
        zipFile: File,
        destDir: File,
        charset: Charset,
    ): Pair<List<ExtractedFile>, Boolean> {
        val root = destDir.canonicalPath
        val result = mutableListOf<ExtractedFile>()
        var hadReplacement = false
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(zipFile.inputStream().buffered(), charset).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ENTRIES) {
                    throw IOException("Artifact zip has too many entries ($entryCount) — aborting")
                }
                val rawName = entry.name
                if (rawName.contains('\uFFFD')) hadReplacement = true

                val name = sanitizeEntryName(rawName)
                    ?: throw IOException("Unsafe path in artifact zip: $rawName")

                if (name.isEmpty()) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                val target = File(destDir, name)
                if (!target.canonicalPath.startsWith(root + File.separator)) {
                    throw IOException("Entry escapes extraction dir: $rawName")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    var size = 0L
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zis.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            size += n
                            totalBytes += n
                            if (size > MAX_SINGLE_FILE || totalBytes > MAX_TOTAL_BYTES) {
                                throw IOException("Artifact zip expands too large (guard: ${humanBytes(totalBytes)})")
                            }
                        }
                    }
                    result += ExtractedFile(name, target.absolutePath, size)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return result to hadReplacement
    }

    /**
     * Normalize a zip entry name to a safe relative path:
     *  - backslashes → slashes, leading "/" and "./" stripped
     *  - any ".." segment is rejected (traversal)
     * Returns null for unsafe names, "" for pure-directory markers.
     */
    private fun sanitizeEntryName(raw: String): String? {
        var n = raw.replace('\\', '/')
        n = n.trimStart('/')
        while (n.startsWith("./")) n = n.removePrefix("./")
        if (n.isBlank()) return ""
        if (n.split('/').contains("..")) return null
        return n
    }


    private companion object {
        const val MAX_ENTRIES = 5_000
        const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024   // 2 GiB
        const val MAX_SINGLE_FILE = 1L * 1024 * 1024 * 1024   // 1 GiB
    }
}
