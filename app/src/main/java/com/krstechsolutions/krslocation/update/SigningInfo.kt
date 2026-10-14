package com.krstechsolutions.krslocation.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Signing certificate helpers used to tell debug builds apart and to vet downloaded updates. */
object SigningInfo {

    data class Cert(val sha256: String, val subject: String) {
        /** The Android SDK's default debug key has subject "CN=Android Debug, O=Android, C=US". */
        val isDebugKey: Boolean get() = subject.contains("Android Debug", ignoreCase = true)
        val fingerprint: String get() = sha256.chunked(2).joinToString(":").uppercase()
    }

    fun installed(context: Context): Cert? = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        firstCert(info)
    }.getOrNull()

    /** Signing certificate of an APK file that is not installed. */
    fun ofArchive(context: Context, apk: File): Cert? = runCatching {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return null
        firstCert(info)
    }.getOrNull()

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun firstCert(info: PackageInfo): Cert? {
        val signature: Signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return null
            val sigs = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
            sigs?.firstOrNull() ?: return null
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull() ?: return null
        }
        val bytes = signature.toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val subject = runCatching {
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(bytes.inputStream()) as X509Certificate
            cert.subjectX500Principal.name
        }.getOrDefault("")
        return Cert(sha, subject)
    }
}
