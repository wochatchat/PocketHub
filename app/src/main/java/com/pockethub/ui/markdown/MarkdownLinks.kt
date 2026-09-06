package com.pockethub.ui.markdown

// Link classification, resolvers and badge detection. Split out of MarkdownText.kt.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class LinkKind {
    /** Same-host GitHub repository — `https://github.com/<owner>/<repo>` or `.../<owner>/<repo>/…` */
    GITHUB_REPO,

    /** Same-host GitHub user/organization profile page. */
    GITHUB_USER,

    /** Issue or PR on GitHub. */
    GITHUB_ISSUE,

    /** Commit on GitHub. */
    GITHUB_COMMIT,

    /** Direct asset a user can download — `.apk`/`.zip`/`.tar.gz`/`.dmg`/… or
     *  `raw.githubusercontent.com` / `releases/download/…` URLs. */
    DOWNLOADABLE,

    /** An image that was wrapped in a link and is being clicked via its container. */
    IMAGE,

    /** Bare image URL — clickable to open the image in browser. */
    IMAGE_URL,

    /** Everything else (websites, gists, markdown files, etc.). */
    EXTERNAL,
}

/**
 * Classify an absolute URL into a [LinkKind]. URL is assumed already absolute (http/https).
 */
internal fun classifyLink(url: String): LinkKind {
    val u = url.lowercase()
    if (u.startsWith("https://github.com/") || u.startsWith("http://github.com/")) {
        val path = u.substringAfter("github.com/", "").removePrefix("/").trimEnd('/')
        val parts = path.split('/').filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> LinkKind.EXTERNAL
            parts.size == 1 -> LinkKind.GITHUB_USER
            parts.size == 2 -> LinkKind.GITHUB_REPO
            parts.size >= 3 -> when (parts[2]) {
                "issues", "pull" -> LinkKind.GITHUB_ISSUE
                "commit", "commits", "tree", "blob" -> LinkKind.GITHUB_REPO
                "pulls" -> LinkKind.GITHUB_ISSUE
                else -> LinkKind.GITHUB_REPO
            }
            else -> LinkKind.EXTERNAL
        }
    }
    if (u.startsWith("https://raw.githubusercontent.com/")) return LinkKind.DOWNLOADABLE
    if (u.contains("/releases/download/")) return LinkKind.DOWNLOADABLE
    val ext = u.substringBefore('?', u).substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
    if (ext in DOWNLOADABLE_EXTS) return LinkKind.DOWNLOADABLE
    if (ext in IMAGE_EXTS) return LinkKind.IMAGE_URL
    return LinkKind.EXTERNAL
}

internal val DOWNLOADABLE_EXTS = setOf(
    "apk", "zip", "gz", "tgz", "tar", "7z", "rar", "bz2", "xz",
    "dmg", "pkg", "deb", "rpm", "msi", "exe", "ipa",
    "jar", "aar", "war",
    "pdf", "epub", "mobi", "azw3",
)
internal val IMAGE_EXTS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico",
)

/**
 * Resolve a raw GitHub reference to an absolute URL.
 *  - absolute http(s) → returned as-is
 *  - `#123`            → https://github.com/<owner/repo>/issues/123  (needs repoContext)
 *  - `@user`           → https://github.com/<user>
 *  - `owner/repo` or `owner/repo#123` → https://github.com/...
 *  - 40-hex-char SHA   → https://github.com/<repo>/commit/<sha>  (needs repoContext)
 *  - otherwise         → null (will be rendered as plain text)
 */
fun interface LinkResolver {
    operator fun invoke(ref: String): String?
}

@Composable
internal fun rememberLinkResolver(repoContext: String?): LinkResolver = LinkResolver { ref ->
    val raw = ref.trim()
    if (raw.isEmpty()) return@LinkResolver null
    if (raw.startsWith("http://") || raw.startsWith("https://")) return@LinkResolver raw
    if (raw.startsWith("mailto:")) return@LinkResolver raw
    val gh = "https://github.com"
    if (raw.startsWith("#")) {
        val num = raw.removePrefix("#").trim()
        if (repoContext != null && num.matches(Regex("\\d+"))) return@LinkResolver "$gh/$repoContext/issues/$num"
        return@LinkResolver null
    }
    if (raw.startsWith("@")) {
        val user = raw.removePrefix("@")
        if (user.matches(Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$"))) return@LinkResolver "$gh/$user"
        return@LinkResolver null
    }
    // Treat the URL as relative to repo if it starts with `/` or `./` or `../`
    if ((raw.startsWith("/") || raw.startsWith("./") || raw.startsWith("../")) && repoContext != null) {
        return@LinkResolver "$gh/$repoContext/${raw.removePrefix("./")}"
    }
    // Relative doc/file links without a ./ prefix: `README_zh-CN.md`,
    // `docs/faq.md`. The old owner/repo regex mis-parsed `docs/faq.md` as
    // repo "faq.md" of owner "docs". Disambiguate by document extension and
    // segment count: exactly one slash without a doc extension is a repo
    // slug ("owner/repo", incl. dotted names like "mrdoob/three.js").
    val lastSegment = raw.substringAfterLast('/')
    val docExt = Regex("\\.(md|markdown|txt|rst|adoc)$", RegexOption.IGNORE_CASE)
    if (repoContext != null && !raw.contains(':') && !raw.startsWith("#") && !raw.startsWith("@") &&
        raw.matches(Regex("^(?:\\.{0,2}/)?[A-Za-z0-9_./\\-]+$"))
    ) {
        val slashCount = raw.count { it == '/' }
        val isFile = (slashCount <= 1 && docExt.containsMatchIn(lastSegment)) || slashCount >= 2
        if (isFile) {
            return@LinkResolver "$gh/$repoContext/${raw.removePrefix("./")}"
        }
    }
    val repoIssue = Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:#(\\d+))?$").matchEntire(raw)
    if (repoIssue != null) {
        val (owner, name, num) = repoIssue.destructured
        return@LinkResolver if (num.isNotEmpty()) "$gh/$owner/$name/issues/$num" else "$gh/$owner/$name"
    }
    if (raw.matches(Regex("^[0-9a-f]{40}$")) && repoContext != null) {
        return@LinkResolver "$gh/$repoContext/commit/$raw"
    }
    null
}

// ── Image resolver ───────────────────────────────────────────────────

/**
 * Resolve an image `src` to an absolute, Coil-loadable URL.
 *  - absolute http(s) / `//` / `data:` → returned (near-)as-is
 *  - relative path (e.g. `docs/shot.png`, `./a/b.gif`, `/assets/x.png`)
 *    → `https://raw.githubusercontent.com/<owner>/<repo>/<defaultBranch>/<path>`
 *    so README screenshots that use repo-relative URLs actually load.
 */
fun interface ImageResolver {
    operator fun invoke(src: String): String
}

@Composable
internal fun rememberImageResolver(repoContext: String?, defaultBranch: String?): ImageResolver = ImageResolver { src ->
    val raw = src.trim()
    if (raw.isEmpty()) return@ImageResolver raw
    if (raw.startsWith("http://") || raw.startsWith("https://")) return@ImageResolver raw
    if (raw.startsWith("//")) return@ImageResolver "https:$raw"
    if (raw.startsWith("data:")) return@ImageResolver raw
    if (repoContext.isNullOrBlank()) return@ImageResolver raw
    val parts = repoContext.split("/")
    val owner = parts.getOrNull(0)
    val repo = parts.getOrNull(1)
    if (owner.isNullOrBlank() || repo.isNullOrBlank()) return@ImageResolver raw
    val branch = defaultBranch?.ifBlank { null } ?: "main"
    val path = raw.removePrefix("./").removePrefix("/").replace(Regex("(?:\\.\\./)+"), "")
    "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
}

/** Heuristic: does this URL look like a tiny status badge (shields.io, CI, etc.)? */
internal fun isBadgeUrl(url: String): Boolean {
    val u = url.lowercase()
    if (u.contains("img.shields.io") || u.contains("shields.io")) return true
    if (u.contains("badge.fury.io")) return true
    if (u.contains("travis-ci.org") || u.contains("travis-ci.com")) return true
    if (u.contains("codecov.io") || u.contains("coveralls.io")) return true
    if (u.contains("circleci.com") || u.contains("badgen.net")) return true
    if (u.contains("gitter.im")) return true
    if (u.contains("/badge/")) return true
    if (u.contains("/buildstatus") || u.contains("/status-badge")) return true
    if (u.contains("actions/workflows") && u.contains("badge")) return true
    if ((u.contains("opencollective.com") || u.contains("snyk.io") || u.contains("app.codacy.com") || u.contains("deepscan.io")) && u.contains("badge")) return true
    if (u.contains("lgtm.com") || u.contains("lgtm.app")) return true
    return false
}

// ── Rich inline rendering ───────────────────────────────────────────

// Patterns pre-compiled once per rendering call. Each uses the *anchor at start*
// semantic by requiring the match to begin at position 0 of the substring passed.
// In the loop we slice off the part from i onward and try matching.
// Whitespace-tolerant (incl. newlines) so multi-line badge/link markup like
// [ \n ![alt](src) \n ](href) — produced by HTML <a><img></a> READMEs — matches.
internal val WRAPPED_IMG_PATTERN = Regex("^\\[\\s*!\\[([^\\]]*)\\]\\(\\s*([^)]+?)\\s*\\)\\s*\\]\\(\\s*([^)]+?)\\s*\\)")
internal val STANDALONE_IMG_PATTERN = Regex("^!\\[([^\\]]*)\\]\\(\\s*([^)]+?)\\s*\\)")

/** Optional GFM destination title: `(src "tooltip")` / `(src 'tooltip')`. */
private val LINK_TITLE_SUFFIX = Regex("\\s+[\"'][^\"']*[\"']\\s*$")

internal fun stripLinkTitle(url: String): String = url.replace(LINK_TITLE_SUFFIX, "")
