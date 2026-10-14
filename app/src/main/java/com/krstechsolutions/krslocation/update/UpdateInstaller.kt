package com.krstechsolutions.krslocation.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.krstechsolutions.krslocation.R

/**
 * Downloads a release APK with DownloadManager and hands it to the package installer
 * when the download finishes. The user still confirms the install in the system UI.
 */
object UpdateInstaller {

    private var pendingDownloadId: Long = -1L
    private var receiver: BroadcastReceiver? = null

    fun download(context: Context, release: ReleaseInfo) {
        val url = release.apkUrl ?: run {
            openInBrowser(context, release.htmlUrl)
            return
        }
        val app = context.applicationContext
        val dm = app.getSystemService(DownloadManager::class.java)
        val fileName = "krs-location-${release.version}.apk"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(app.getString(R.string.update_download_title, release.version))
            .setDescription(app.getString(R.string.app_name))
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName)

        registerReceiver(app)
        pendingDownloadId = dm.enqueue(request)
        Toast.makeText(app, R.string.update_download_started, Toast.LENGTH_SHORT).show()
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private fun registerReceiver(app: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != pendingDownloadId) return
                val dm = context.getSystemService(DownloadManager::class.java)
                val uri = dm.getUriForDownloadedFile(id)
                if (uri == null) {
                    Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                    return
                }
                val install = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    context.startActivity(install)
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
        receiver = r
        // DOWNLOAD_COMPLETE is a system broadcast, so the receiver must be exported on API 33+.
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(app, r, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private const val APK_MIME = "application/vnd.android.package-archive"
}
