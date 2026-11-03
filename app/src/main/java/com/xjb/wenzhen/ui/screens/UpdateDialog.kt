package com.xjb.wenzhen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.data.update.UpdateInfo
import com.xjb.wenzhen.ui.components.NoticeBanner
import com.xjb.wenzhen.ui.theme.TealContainer

@Composable
fun UpdateDialog(
    update: UpdateInfo,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Int? = null,
    isDownloaded: Boolean = false,
    errorMessage: String? = null,
) {
    val downloading = progress != null && !isDownloaded
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("发现新版本 ${update.versionName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = TealContainer.copy(alpha = 0.65f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "versionCode ${update.versionCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "最低 Android SDK ${update.minSdk}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text("更新说明", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = update.notes.ifBlank { "本版本包含体验优化与问题修复。" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (downloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        LinearProgressIndicator(
                            progress = { (progress ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "正在下载并校验 APK… ${progress ?: 0}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isDownloaded) {
                    NoticeBanner(
                        message = "APK 已通过 SHA-256 与签名证书校验，可以安装。",
                        isError = false,
                    )
                }
                if (errorMessage != null) {
                    NoticeBanner(message = errorMessage)
                }
                Text(
                    text = "覆盖安装会保留本机历史记录。如系统提示不允许安装，请按提示允许本应用安装未知来源应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            when {
                isDownloaded -> Button(onClick = onInstall) {
                    Text("安装更新", fontWeight = FontWeight.SemiBold)
                }
                downloading -> Button(onClick = {}, enabled = false) {
                    Text("下载中")
                }
                else -> Button(onClick = onDownload) {
                    Text(if (errorMessage == null) "下载更新" else "重新下载")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) {
                Text("稍后")
            }
        },
    )
}
