package com.xjb.wenzhen.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

enum class InstallLaunchResult {
    STARTED,
    PERMISSION_REQUIRED,
    FAILED,
}

object UpdateInstaller {
    fun install(context: Context, apk: File): InstallLaunchResult {
        if (!apk.isFile) return InstallLaunchResult.FAILED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                InstallLaunchResult.PERMISSION_REQUIRED
            }.getOrDefault(InstallLaunchResult.FAILED)
        }

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallLaunchResult.STARTED
        } catch (error: ActivityNotFoundException) {
            InstallLaunchResult.FAILED
        } catch (error: IllegalArgumentException) {
            InstallLaunchResult.FAILED
        }
    }
}
