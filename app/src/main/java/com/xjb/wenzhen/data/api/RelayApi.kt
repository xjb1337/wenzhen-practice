package com.xjb.wenzhen.data.api

import com.xjb.wenzhen.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

internal object RelayEndpoints {
    fun chatCompletions(baseUrl: String, enforceHttps: Boolean): String {
        val base = normalize(baseUrl, enforceHttps)
        return when {
            base.endsWith("/v1/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    fun redeem(baseUrl: String, enforceHttps: Boolean): String =
        versionedUrl(baseUrl, "redeem", enforceHttps)

    fun health(baseUrl: String, enforceHttps: Boolean): String =
        versionedUrl(baseUrl, "health", enforceHttps)

    private fun versionedUrl(baseUrl: String, endpoint: String, enforceHttps: Boolean): String {
        val base = normalize(baseUrl, enforceHttps)
        return if (base.endsWith("/v1")) "$base/$endpoint" else "$base/v1/$endpoint"
    }

    private fun normalize(baseUrl: String, enforceHttps: Boolean): String {
        val base = baseUrl.trim()
            .ifBlank { AppSettings.RELAY_BASE_URL }
            .trimEnd('/')
        if (enforceHttps && !base.startsWith("https://", ignoreCase = true)) {
            throw LlmException("内置服务地址必须使用 HTTPS。")
        }
        return base
    }
}

object RelayErrorParser {
    private val relayCodes = setOf(
        "invite_used",
        "invite_invalid",
        "quota_device",
        "quota_spend",
        "relay_disabled",
    )

    fun codeFromBody(body: String): String? = runCatching {
        JSONObject(body).optJSONObject("error")
            ?.optString("code")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    fun messageFromBody(body: String, httpCode: Int): String {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val code = error?.optString("code")?.trim().orEmpty()
        val serverMessage = error?.optString("message")
            .orEmpty()
            .trim()
            .take(240)
        val fallback = when (code) {
            "invite_used", "invite_invalid" -> "邀请码无效或已使用"
            "quota_device", "quota_spend" -> "今日额度已用完"
            "relay_disabled" -> "内置服务暂不可用"
            else -> when {
                httpCode == 401 || httpCode == 403 -> "登录状态已失效，请重新兑换邀请码。"
                httpCode == 429 -> "请求过于频繁，请稍后再试。"
                httpCode >= 500 -> "内置服务暂时不可用，请稍后再试。"
                else -> "内置服务请求失败（HTTP $httpCode），请稍后再试。"
            }
        }
        if (code in relayCodes) {
            return serverMessage.takeIf(::containsChinese) ?: fallback
        }
        return if (serverMessage.isNotBlank() && containsChinese(serverMessage)) {
            "内置服务请求失败（HTTP $httpCode）：$serverMessage"
        } else {
            fallback
        }
    }

    private fun containsChinese(value: String): Boolean = value.any { it.code in 0x4E00..0x9FFF }
}

sealed interface RedeemResult {
    data class Success(val sessionToken: String, val expiresAt: Long? = null) : RedeemResult
    data class Failure(val message: String, val code: String? = null) : RedeemResult
}

internal sealed interface RelayHealthResult {
    data object Healthy : RelayHealthResult
    data object Missing : RelayHealthResult
    data class Failure(val message: String) : RelayHealthResult
}

class RelayClient(
    private val httpClient: OkHttpClient = DeepSeekClient.defaultHttpClient(),
    private val enforceHttps: Boolean = true,
) {
    suspend fun redeem(baseUrl: String, code: String, deviceId: String): RedeemResult =
        withContext(Dispatchers.IO) {
            val endpoint = try {
                RelayEndpoints.redeem(baseUrl, enforceHttps)
            } catch (error: LlmException) {
                return@withContext RedeemResult.Failure(error.message ?: "内置服务地址无效。")
            }
            val payload = JSONObject().apply {
                put("code", code.trim())
                put("deviceId", deviceId.trim())
            }
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(DeepSeekClient.JSON_MEDIA_TYPE))
                .build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext RedeemResult.Failure(
                            message = RelayErrorParser.messageFromBody(body, response.code),
                            code = RelayErrorParser.codeFromBody(body),
                        )
                    }
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val token = json?.optString("sessionToken").orEmpty().trim()
                    if (token.isBlank()) {
                        return@withContext RedeemResult.Failure("兑换响应格式异常，请稍后重试。")
                    }
                    val expiresAt = if (json != null && json.has("expiresAt") && !json.isNull("expiresAt")) {
                        json.optLong("expiresAt").takeIf { it > 0 }
                    } else {
                        null
                    }
                    RedeemResult.Success(sessionToken = token, expiresAt = expiresAt)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                RedeemResult.Failure(networkErrorMessage(error))
            }
        }

    suspend fun health(baseUrl: String): Boolean =
        checkHealth(baseUrl, sessionToken = "", deviceId = "") is RelayHealthResult.Healthy

    internal suspend fun checkHealth(
        baseUrl: String,
        sessionToken: String = "",
        deviceId: String = "",
    ): RelayHealthResult = withContext(Dispatchers.IO) {
        val endpoint = try {
            RelayEndpoints.health(baseUrl, enforceHttps)
        } catch (error: LlmException) {
            return@withContext RelayHealthResult.Failure(error.message ?: "内置服务地址无效。")
        }
        val builder = Request.Builder().url(endpoint).get()
        if (sessionToken.isNotBlank()) {
            builder.header("Authorization", "Bearer ${sessionToken.trim()}")
        }
        if (deviceId.isNotBlank()) {
            builder.header("X-Device-Id", deviceId.trim())
        }
        try {
            httpClient.newCall(builder.build()).execute().use { response ->
                when {
                    response.isSuccessful -> RelayHealthResult.Healthy
                    response.code == 404 || response.code == 405 || response.code == 501 ->
                        RelayHealthResult.Missing
                    else -> RelayHealthResult.Failure(
                        RelayErrorParser.messageFromBody(response.body?.string().orEmpty(), response.code),
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            RelayHealthResult.Failure(networkErrorMessage(error))
        }
    }

    private fun networkErrorMessage(error: Exception): String = when (error) {
        is SocketTimeoutException, is InterruptedIOException ->
            "连接超时，请检查网络或稍后重试。"
        else -> "网络错误，无法连接内置服务，请检查网络后重试。"
    }
}
