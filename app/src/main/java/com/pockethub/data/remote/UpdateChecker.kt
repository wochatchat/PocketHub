package com.pockethub.data.remote

import com.pockethub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the latest release from the project's GitHub repo and decides whether an
 * update is available relative to the currently installed [BuildConfig.VERSION_NAME].
 *
 * The repo path is hard-coded to the public PocketHub repository so the check works
 * even before the user signs in (the GitHubApi spans api.github.com; the releases
 * endpoint is public so an empty auth token is fine).
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val api: GitHubApi,
) {

    data class UpdateInfo(
        val latestVersionName: String,
        val latestVersionCode: Int,
        val downloadUrl: String?,
        val downloadAssetName: String?,
        val assetSizeBytes: Long,
        val htmlUrl: String?,
        val releaseNotes: String?,
        val isPreRelease: Boolean,
        val publishedAt: String?,
    )

    /** Fetch the latest non-draft release (pre-releases excluded by default). */
    suspend fun fetchLatest(owner: String, repo: String, includePre: Boolean = false): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val releases = api.getReleases(owner, repo, perPage = 20)
                val pick = releases
                    .filter { !it.draft }
                    .filter { includePre || !it.prerelease }
                    .firstOrNull()
                    ?: return@runCatching null

                // Prefer an .apk asset; fall back to the first asset if none matches.
                val apkAsset = pick.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: pick.assets.firstOrNull()
                val tagName = pick.tagName.removePrefix("v").trim()
                UpdateInfo(
                    latestVersionName = tagName,
                    latestVersionCode = 0,
                    downloadUrl = apkAsset?.browserDownloadUrl,
                    downloadAssetName = apkAsset?.name,
                    assetSizeBytes = apkAsset?.size ?: 0L,
                    htmlUrl = pick.htmlUrl,
                    releaseNotes = pick.body,
                    isPreRelease = pick.prerelease,
                    publishedAt = pick.publishedAt ?: pick.createdAt,
                )
            }.getOrNull()
        }

    /** True when [remote] is strictly newer than the installed version. */
    fun isNewer(remote: UpdateInfo): Boolean {
        val installed = BuildConfig.VERSION_NAME.removePrefix("v").trim()
        return compareVersions(remote.latestVersionName, installed) > 0
    }

    /** Major, minor, patch comparison; non-numeric segments treated as 0. Returns >0 if a>b. */
    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val ai = pa.getOrElse(i) { 0 }
            val bi = pb.getOrElse(i) { 0 }
            if (ai != bi) return ai - bi
        }
        return 0
    }
}
