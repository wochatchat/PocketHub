package com.pockethub.ui.main

import android.net.Uri

/**
 * Parses a github.com web URL into an internal Compose route (see [Routes]).
 *
 * Supported shapes (issue #37 — open GitHub links in PocketHub):
 *   https://github.com/{owner}                          → user profile
 *   https://github.com/{owner}/{repo}                   → repo detail
 *   https://github.com/{owner}/{repo}/issues/{number}   → issue detail
 *   https://github.com/{owner}/{repo}/pull/{number}     → PR detail
 *   https://github.com/{owner}/{repo}/pulls/{number}    → PR detail
 *   https://github.com/{owner}/{repo}/commit/{sha}      → commit detail
 *   https://github.com/{owner}/{repo}/commits/{sha}     → commit detail
 *   https://github.com/{owner}/{repo}/blob/{ref}/{path} → file viewer
 *   https://github.com/{owner}/{repo}/tree/{ref}[/{path}] → repo detail
 *
 * Returns null for anything unrecognized (gists, orgs, settings, ...) —
 * the caller should fall back to the system browser.
 */
object GitHubUrlRouter {

    private val GITHUB_HOSTS = setOf("github.com", "www.github.com")

    /** True when this URI is a github.com web link we may want to claim. */
    fun isGithubUrl(uri: Uri): Boolean =
        (uri.scheme == "https" || uri.scheme == "http") && uri.host?.lowercase() in GITHUB_HOSTS

    /**
     * Map a github.com URL to an internal [Routes] string, or null when the
     * URL shape is unsupported. The returned value is an app-internal route
     * (same strings the NavHost registers), NOT a pockethub:// URI.
     */
    fun parse(uri: Uri): String? {
        if (!isGithubUrl(uri)) return null
        val segments = uri.pathSegments ?: return null
        if (segments.isEmpty()) return null // github.com homepage

        return when (segments.size) {
            1 -> Routes.userDetail(segments[0])
            else -> parseRepoUrl(segments)
        }
    }

    /** segments = [owner, repo, ...rest], rest.size >= 1 */
    private fun parseRepoUrl(segments: List<String>): String? {
        val owner = segments[0]
        val repo = segments[1]
        val rest = segments.drop(2)

        // Reserved single-segment paths are repo views, not sub-pages.
        val reserved = setOf("issues", "pulls", "pulse", "graphs", "network", "projects", "wiki", "archive")
        if (rest.size == 1 && rest[0] in reserved) return Routes.repoDetail(owner, repo)

        return when (rest[0]) {
            "issues", "pulls" -> rest.getOrNull(1)?.toIntOrNull()?.let {
                if (rest[0] == "issues") Routes.issueDetail(owner, repo, it)
                else Routes.prDetail(owner, repo, it)
            } ?: Routes.repoDetail(owner, repo, tab = rest[0])
            "pull" -> rest.getOrNull(1)?.toIntOrNull()?.let { Routes.prDetail(owner, repo, it) }
                ?: Routes.repoDetail(owner, repo)
            "commit", "commits" -> rest.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { Routes.commitDetail(owner, repo, it) }
                ?: Routes.repoDetail(owner, repo, tab = "commits")
            "blob" -> parseBlob(owner, repo, rest.drop(1))
            "tree" -> Routes.repoDetail(owner, repo) // tree = folder view; repo page covers it
            "releases", "tags", "branches", "actions", "stargazers", "forks", "watchers" ->
                Routes.repoDetail(owner, repo, tab = rest[0])
            else -> Routes.repoDetail(owner, repo) // unknown sub-path: safest landing is repo home
        }
    }

    /** blob/{ref}/{path...} — ref is the first segment, path is everything after. */
    private fun parseBlob(owner: String, repo: String, rest: List<String>): String? {
        val ref = rest.getOrNull(0) ?: return Routes.repoDetail(owner, repo)
        val path = rest.drop(1).joinToString("/")
        if (path.isBlank()) return Routes.repoDetail(owner, repo)
        return Routes.fileViewer(owner, repo, path, ref)
    }

    /**
     * Wrap an internal route into a synthetic pockethub:// URI so it flows
     * through the existing deep-link channel in [com.pockethub.ui.MainActivity]
     * (host + path + query reassemble into the original route string there).
     */
    fun toInternalUri(route: String): Uri = Uri.parse("pockethub://$route")
}
