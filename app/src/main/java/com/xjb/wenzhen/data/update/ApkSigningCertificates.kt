package com.xjb.wenzhen.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object ApkSigningCertificates {
    fun installedSha256(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, signatureFlags())
        sha256(info)
    }.getOrNull()

    fun archiveSha256(context: Context, apk: File): String? = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, signatureFlags())
            ?: return null
        sha256(info)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun sha256(info: PackageInfo): String? {
        val signingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo else null
        val signatures = if (signingInfo != null) {
            signingInfo.apkContentsSigners
        } else {
            info.signatures
        } ?: return null
        if (signatures.size != 1) return null
        return MessageDigest.getInstance("SHA-256")
            .digest(signatures[0].toByteArray())
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
}
