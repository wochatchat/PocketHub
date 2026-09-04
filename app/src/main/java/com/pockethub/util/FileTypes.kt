package com.pockethub.util

/**
 * Shared file-type helpers for the code browser and file viewers.
 * One source of truth so CodeTab, FileViewerScreen and FullScreenFileViewer
 * agree on what is an image, what is binary, and what is plain text.
 */
object FileTypes {

    /** Image extensions renderable by Coil from raw bytes. */
    val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "avif")

    /** Text formats that are source/viewable but not syntax-highlighted. */
    val PLAIN_TEXT_EXTS = setOf(
        "txt", "log", "csv", "tsv", "diff", "patch", "lock", "editorconfig",
        "gitignore", "gitattributes", "env", "bazelrc", "npmrc", "nvmrc",
    )

    /** Font / archive / media binaries we never try to show as text. */
    val KNOWN_BINARY_EXTS = setOf(
        "apk", "jar", "zip", "gz", "tgz", "bz2", "xz", "7z", "rar", "tar",
        "ttf", "otf", "woff", "woff2", "eot",
        "mp4", "mov", "mkv", "webm", "mp3", "wav", "ogg", "flac", "m4a",
        "pdf", "psd", "ai", "sketch", "exe", "dll", "so", "dylib", "bin", "iso", "img",
        "class", "dex", "o", "a", "lib", "pyc", "png~",
        "jks", "keystore", "p12", "pem.bak",
    )

    fun ext(path: String): String = path.substringAfterLast('.', "").lowercase()

    fun isImage(path: String): Boolean = ext(path) in IMAGE_EXTS

    fun isKnownBinary(path: String): Boolean = ext(path) in KNOWN_BINARY_EXTS

    /** Heuristic binary sniff for extension-less / ambiguous names. */
    fun looksBinary(content: String): Boolean {
        if (content.isEmpty()) return false
        val sample = content.take(4000)
        var suspicious = 0
        val limit = (sample.length * 0.10).toInt().coerceAtLeast(4)
        for (ch in sample) {
            if (ch == '\uFFFD') return true
            val code = ch.code
            if (code < 9 || (code in 14..31)) suspicious++   // control chars except \t \n \r
            if (suspicious > limit) return true
        }
        return false
    }
}
