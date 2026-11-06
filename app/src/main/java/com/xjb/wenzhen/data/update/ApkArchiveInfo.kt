package com.xjb.wenzhen.data.update

import android.content.Context
import android.os.Build
import java.io.File

data class ApkArchiveInfo(
    val packageName: String,
    val versionCode: Long,
)

object ApkArchiveInfoReader {
    fun read(context: Context, apk: File): ApkArchiveInfo? = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return null
        ApkArchiveInfo(info.packageName, info.versionCodeLong())
    }.getOrNull()

    fun installedVersionCode(context: Context): Long? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionCodeLong()
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun android.content.pm.PackageInfo.versionCodeLong(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }
}

/**
 * 校验下载 APK 的包名和版本号；返回错误文案，null 表示通过。
 */
internal fun validateArchive(
    expectedPackage: String,
    expectedVersion: Long,
    currentVersion: Long,
    actualPackage: String,
    actualVersion: Long,
): String? {
    if (actualPackage != expectedPackage) {
        return "更新包不属于本应用，已停止安装。"
    }
    if (actualVersion != expectedVersion || actualVersion <= currentVersion) {
        return "更新包版本与更新信息不一致，已停止安装。"
    }
    return null
}
