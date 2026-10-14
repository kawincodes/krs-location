package com.krstechsolutions.krslocation.update

import com.krstechsolutions.krslocation.BuildConfig
import com.krstechsolutions.krslocation.net.Http
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val name: String,
    val notes: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long,
    /** Lower-case hex SHA-256 of the APK asset as reported by GitHub, when available. */
    val apkSha256: String?,
    val publishedAt: String,
    val prerelease: Boolean,
) {
    val isNewerThanInstalled: Boolean
        get() = SemVer.isNewer(version, BuildConfig.VERSION_NAME)

    val isInstalledVersion: Boolean
        get() = SemVer.compare(version, BuildConfig.VERSION_NAME) == 0

    /** "2026-10-14" from the ISO timestamp GitHub returns. */
    val publishedDate: String get() = publishedAt.substringBefore('T')

    val apkSizeLabel: String
        get() = when {
            apkSizeBytes <= 0 -> ""
            apkSizeBytes < 1024 * 1024 -> "${apkSizeBytes / 1024} KB"
            else -> String.format(java.util.Locale.US, "%.1f MB", apkSizeBytes / (1024.0 * 1024.0))
        }
}

/** Reads releases of this repository from the GitHub API. */
object UpdateChecker {

    const val REPO_OWNER = "kawincodes"
    const val REPO_NAME = "krs-location"
    const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
    const val RELEASES_URL = "$REPO_URL/releases"

    private const val API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"
    private const val ACCEPT = "application/vnd.github+json"

    suspend fun fetchLatest(): ReleaseInfo =
        parse(JSONObject(Http.getText("$API/latest", accept = ACCEPT)))

    /** The release for a specific tag, e.g. "v1.2.0"; used for the post-update changelog. */
    suspend fun fetchByTag(tag: String): ReleaseInfo =
        parse(JSONObject(Http.getText("$API/tags/$tag", accept = ACCEPT)))

    suspend fun fetchHistory(limit: Int = 15): List<ReleaseInfo> {
        val array = JSONArray(Http.getText("$API?per_page=$limit", accept = ACCEPT))
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .filter { !it.optBoolean("draft", false) }
            .map(::parse)
    }

    private fun parse(json: JSONObject): ReleaseInfo {
        val tag = json.getString("tag_name")
        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L
        var apkSha: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    apkName = name
                    apkSize = asset.optLong("size", 0L)
                    apkSha = asset.optString("digest").takeIf { it.startsWith("sha256:") }
                        ?.removePrefix("sha256:")?.lowercase()
                    break
                }
            }
        }
        return ReleaseInfo(
            tag = tag,
            version = tag.removePrefix("v"),
            name = json.optString("name").ifEmpty { tag },
            notes = json.optString("body"),
            htmlUrl = json.optString("html_url", RELEASES_URL),
            apkUrl = apkUrl,
            apkName = apkName,
            apkSizeBytes = apkSize,
            apkSha256 = apkSha,
            publishedAt = json.optString("published_at"),
            prerelease = json.optBoolean("prerelease", false),
        )
    }
}
