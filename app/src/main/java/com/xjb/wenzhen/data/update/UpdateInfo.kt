package com.xjb.wenzhen.data.update

import org.json.JSONObject

class UpdateParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val signingCertSha256: String,
    val notes: String,
    val minSdk: Int,
) {
    companion object {
        fun parse(raw: String): UpdateInfo {
            val json = try {
                JSONObject(raw)
            } catch (error: Exception) {
                throw UpdateParseException("更新清单不是合法的 JSON。", error)
            }

            fun text(vararg keys: String): String {
                keys.forEach { key ->
                    val value = json.opt(key)
                    if (value != null && value != JSONObject.NULL) {
                        val result = value.toString().trim()
                        if (result.isNotEmpty()) return result
                    }
                }
                return ""
            }

            val versionName = text("versionName")
            val versionCode = json.optInt("versionCode", 0)
            val apkUrl = text("apkUrl")
            val sha256 = text("sha256").lowercase()
            val signingCertSha256 = text("signingCertSha256").lowercase()
            val notes = text("notes", "releaseNotes")
            val minSdk = json.optInt("minSdk", 0)

            if (versionName.isBlank()) throw UpdateParseException("更新清单缺少版本名称。")
            if (versionCode <= 0) throw UpdateParseException("更新清单的 versionCode 无效。")
            if (!apkUrl.startsWith("https://")) throw UpdateParseException("APK 下载地址必须使用 HTTPS。")
            if (!HEX_64.matches(sha256)) throw UpdateParseException("APK SHA-256 格式无效。")
            if (!HEX_64.matches(signingCertSha256)) {
                throw UpdateParseException("签名证书 SHA-256 格式无效。")
            }
            if (minSdk <= 0) throw UpdateParseException("更新清单的 minSdk 无效。")

            return UpdateInfo(
                versionName = versionName,
                versionCode = versionCode,
                apkUrl = apkUrl,
                sha256 = sha256,
                signingCertSha256 = signingCertSha256,
                notes = notes,
                minSdk = minSdk,
            )
        }

        private val HEX_64 = Regex("^[a-f0-9]{64}$")
    }

    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode
}
