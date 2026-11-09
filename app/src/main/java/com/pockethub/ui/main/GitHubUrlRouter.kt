package com.pockethub.ui.main

import android.net.Uri
import com.pockethub.ui.markdown.GitHubLinkTarget
import com.pockethub.ui.markdown.parseGitHubLink

/**
 * Issue #37: open github.com web links (browser / shared links) inside
 * PocketHub.
 *
 * Parsing is delegated to [parseGitHubLink] — the same battle-tested router
 * the markdown renderer uses (orgs, reserved segments, .git suffixes, workflow
 * runs, create-issue, blob file links). This class only converts the parsed
 * target into an internal [Routes] string so MainActivity can feed it through
 * the existing pockethub:// deep-link channel.
 */
object GitHubUrlRouter {

    /** True when this URI is a github.com web link we may want to claim. */
    fun isGithubUrl(uri: Uri): Boolean =
        (uri.scheme == "https" || uri.scheme == "http") &&
            uri.host?.lowercase() in setOf("github.com", "www.github.com")

    /**
     * Map a github.com URL to an internal [Routes] string, or null when the
     * app can't render it (caller should fall back to the system browser).
     */
    fun parse(uri: Uri): String? {
        if (!isGithubUrl(uri)) return null
        return when (val target = parseGitHubLink(uri.toString())) {
            is GitHubLinkTarget.Repo ->
                Routes.repoDetail(target.owner, target.repo, target.tab?.wire)
            is GitHubLinkTarget.Issue ->
                Routes.issueDetail(target.owner, target.repo, target.number)
            is GitHubLinkTarget.Pull ->
                Routes.prDetail(target.owner, target.repo, target.number)
            is GitHubLinkTarget.Commit ->
                Routes.commitDetail(target.owner, target.repo, target.sha)
            is GitHubLinkTarget.WorkflowRun ->
                Routes.workflowRunDetail(target.owner, target.repo, target.runId)
            is GitHubLinkTarget.User -> Routes.userDetail(target.login)
            is GitHubLinkTarget.CreateIssue ->
                Routes.createIssue(target.owner, target.repo)
            is GitHubLinkTarget.File ->
                Routes.fileViewer(target.owner, target.repo, target.path, target.ref)
            GitHubLinkTarget.Unknown -> null
        }
    }

    /**
     * Wrap an internal route into a synthetic pockethub:// URI so it flows
     * through the existing deep-link channel in [com.pockethub.ui.MainActivity]
     * (host + path + query reassemble into the original route string there).
     */
    fun toInternalUri(route: String): Uri = Uri.parse("pockethub://$route")
}
