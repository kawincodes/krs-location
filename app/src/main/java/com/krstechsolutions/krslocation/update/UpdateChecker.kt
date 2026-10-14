package com.krstechsolutions.krslocation.update

import com.krstechsolutions.krslocation.BuildConfig
import com.krstechsolutions.krslocation.net.Http
import org.json.JSONObject

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val name: String,
    val notes: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
) {
    val isNewerThanInstalled: Boolean
        get() = SemVer.isNewer(version, BuildConfig.VERSION_NAME)
}

/** Reads the latest GitHub Release of this repository. */
object UpdateChecker {

    const val REPO_OWNER = "kawincodes"
    const val REPO_NAME = "krs-location"
    const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
    const val RELEASES_URL = "$REPO_URL/releases"

    private const val API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    suspend fun fetchLatest(): ReleaseInfo {
        val json = JSONObject(Http.getText(API, accept = "application/vnd.github+json"))
        val tag = json.getString("tag_name")
        var apkUrl: String? = null
        var apkSize = 0L
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    apkSize = asset.optLong("size", 0L)
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
            apkSizeBytes = apkSize,
            publishedAt = json.optString("published_at"),
        )
    }
}
