package com.krstechsolutions.krslocation.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.krstechsolutions.krslocation.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads a release APK with DownloadManager, verifies it (SHA-256 from GitHub and the
 * signing key against the installed app) and hands it to the package installer.
 * The user still confirms the install in the system UI.
 */
object UpdateInstaller {

    sealed class Status {
        data object Idle : Status()
        data class Downloading(val release: ReleaseInfo) : Status()
        data class Verifying(val release: ReleaseInfo) : Status()
        data class Ready(val release: ReleaseInfo, val file: File) : Status()
        data class HashMismatch(val release: ReleaseInfo) : Status()
        /** The APK is fine but signed with another key; Android refuses to update in place. */
        data class SignatureMismatch(val release: ReleaseInfo, val file: File, val installedIsDebug: Boolean) : Status()
        data class Failed(val release: ReleaseInfo?, val reason: String) : Status()
    }

    private const val TAG = "UpdateInstaller"
    private const val APK_MIME = "application/vnd.android.package-archive"

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingDownloadId: Long = -1L
    private var pendingFile: File? = null
    private var receiver: BroadcastReceiver? = null

    fun reset() {
        _status.value = Status.Idle
    }

    fun download(context: Context, release: ReleaseInfo) {
        val url = release.apkUrl ?: run {
            openInBrowser(context, release.htmlUrl)
            return
        }
        val app = context.applicationContext
        val dm = app.getSystemService(DownloadManager::class.java)
        val fileName = release.apkName ?: "krs-location-${release.version}.apk"
        val target = File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(app.getString(R.string.update_download_title, release.version))
            .setDescription(app.getString(R.string.app_name))
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName)

        registerReceiver(app)
        pendingFile = target
        pendingDownloadId = dm.enqueue(request)
        _status.value = Status.Downloading(release)
    }

    /** Launches the system installer for a verified APK. */
    fun install(context: Context, file: File) {
        val app = context.applicationContext
        val dm = app.getSystemService(DownloadManager::class.java)
        val uri = dm.getUriForDownloadedFile(pendingDownloadId)
            ?: androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            app.startActivity(install)
        } catch (e: Exception) {
            _status.value = Status.Failed(null, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Opens the system uninstall dialog; needed before installing an APK signed with another key. */
    fun uninstallSelf(context: Context) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private fun onDownloaded(app: Context, release: ReleaseInfo, file: File) {
        _status.value = Status.Verifying(release)
        scope.launch {
            val result = withContext(Dispatchers.IO) { verify(app, release, file) }
            _status.value = result
            if (result is Status.Ready) install(app, file)
        }
    }

    private fun verify(app: Context, release: ReleaseInfo, file: File): Status {
        if (!file.exists() || file.length() == 0L) {
            return Status.Failed(release, app.getString(R.string.update_download_failed))
        }
        // 1. Integrity: compare with the digest GitHub reports for the asset.
        val expected = release.apkSha256
        if (expected != null) {
            val actual = SigningInfo.sha256Of(file)
            if (!actual.equals(expected, ignoreCase = true)) {
                Log.w(TAG, "SHA-256 mismatch: expected $expected got $actual")
                file.delete()
                return Status.HashMismatch(release)
            }
        }
        // 2. Signing key: Android only updates in place when the key matches.
        val installed = SigningInfo.installed(app)
        val archive = SigningInfo.ofArchive(app, file)
        if (installed != null && archive != null && installed.sha256 != archive.sha256) {
            return Status.SignatureMismatch(release, file, installed.isDebugKey)
        }
        return Status.Ready(release, file)
    }

    private fun registerReceiver(app: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != pendingDownloadId) return
                val release = (_status.value as? Status.Downloading)?.release
                val file = pendingFile
                if (release == null || file == null) return
                val dm = context.getSystemService(DownloadManager::class.java)
                val uri = dm.getUriForDownloadedFile(id)
                if (uri == null) {
                    _status.value = Status.Failed(release, context.getString(R.string.update_download_failed))
                    return
                }
                onDownloaded(context.applicationContext, release, file)
            }
        }
        receiver = r
        // DOWNLOAD_COMPLETE is a system broadcast, so the receiver must be exported on API 33+.
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(app, r, filter, ContextCompat.RECEIVER_EXPORTED)
    }
}
