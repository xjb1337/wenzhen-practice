package com.xjb.wenzhen.data.update

import android.content.Context
import com.xjb.wenzhen.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface UpdateCheckResult {
    data class Available(val update: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
}

class UpdateChecker(
    context: Context,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    private val appContext = context.applicationContext

    suspend fun checkForUpdate(
        currentVersionCode: Int,
        currentVersionName: String,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(UPDATE_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "WenzhenPractice-Android")
            .get()
            .build()
        val raw = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw UpdateParseException("检查更新失败（HTTP ${response.code}），请稍后重试。")
                }
                response.body?.string().orEmpty()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: UpdateParseException) {
            throw error
        } catch (error: Exception) {
            throw UpdateParseException("网络错误，暂时无法检查更新，请检查网络后重试。", error)
        }
        val update = UpdateInfo.parse(raw)
        if (!update.apkUrl.startsWith(APK_URL_PREFIX)) {
            throw UpdateParseException("更新 APK 来源不可信，已停止更新。")
        }
        if (update.isNewerThan(currentVersionCode, currentVersionName)) {
            UpdateCheckResult.Available(update)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    suspend fun downloadApk(
        update: UpdateInfo,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "wenzhen-practice-${update.versionCode}.apk")
        val partial = File(directory, "${target.name}.part")
        partial.delete()
        onProgress(0)

        val request = Request.Builder()
            .url(update.apkUrl)
            .header("User-Agent", "WenzhenPractice-Android")
            .get()
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw UpdateParseException("下载更新失败（HTTP ${response.code}），请稍后重试。")
                }
                val body = response.body ?: throw UpdateParseException("更新下载内容为空。")
                val totalBytes = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            downloaded += read
                            if (downloaded > MAX_APK_BYTES) {
                                throw UpdateParseException("更新包大小异常，已停止下载。")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            if (totalBytes > 0) {
                                onProgress(((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
                val actualSha = digest.digest().toHex()
                if (!actualSha.equals(update.sha256, ignoreCase = true)) {
                    throw UpdateParseException("更新包 SHA-256 校验失败，文件可能已损坏。")
                }
            }

            val installedCertificate = ApkSigningCertificates.installedSha256(appContext)
            val archiveCertificate = ApkSigningCertificates.archiveSha256(appContext, partial)
            if (installedCertificate == null || archiveCertificate == null) {
                throw UpdateParseException("无法读取 APK 签名，已停止安装。")
            }
            if (!installedCertificate.equals(archiveCertificate, ignoreCase = true) ||
                !installedCertificate.equals(update.signingCertSha256, ignoreCase = true)
            ) {
                throw UpdateParseException("更新包签名与本机版本不一致，已停止安装。")
            }
            if (!BuildConfig.DEBUG &&
                !archiveCertificate.equals(BuildConfig.RELEASE_CERT_SHA256, ignoreCase = true)
            ) {
                throw UpdateParseException("更新包签名不是官方发布证书，已停止安装。")
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            onProgress(100)
            target
        } catch (error: CancellationException) {
            partial.delete()
            throw error
        } catch (error: UpdateParseException) {
            partial.delete()
            throw error
        } catch (error: Exception) {
            partial.delete()
            throw UpdateParseException("下载更新失败，请检查网络后重试。", error)
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(Locale.ROOT, it) }

    companion object {
        const val UPDATE_URL =
            "https://github.com/xjb1337/wenzhen-practice/releases/latest/download/update.json"
        private const val APK_URL_PREFIX =
            "https://github.com/xjb1337/wenzhen-practice/releases/download/"
        private const val MAX_APK_BYTES = 200L * 1024L * 1024L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
