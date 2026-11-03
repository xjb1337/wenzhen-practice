package com.xjb.wenzhen.data.api

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DeepSeekClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : LlmClient {

    override suspend fun complete(
        config: LlmConfig,
        messages: List<LlmMessage>,
        jsonMode: Boolean,
        temperature: Double,
    ): String = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            throw LlmException("请先在设置中填写 DeepSeek API Key。")
        }

        val payload = JSONObject().apply {
            put("model", config.model)
            put("temperature", temperature)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                    })
                }
            })
            if (jsonMode) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }
        val request = Request.Builder()
            .url(completionUrl(config.baseUrl))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw LlmException(httpErrorMessage(response.code, body))
                }
                parseContent(body)
            }
        } catch (error: LlmException) {
            throw error
        } catch (error: Exception) {
            throw LlmException("无法连接 AI 服务，请检查网络和 Base URL 后重试。", error)
        }
    }

    private fun parseContent(body: String): String {
        if (body.isBlank()) {
            throw LlmException("AI 服务返回了空内容，请稍后重试。")
        }
        return try {
            val choices = JSONObject(body).optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                throw LlmException("AI 服务未返回可用回复，请稍后重试。")
            }
            val content = choices.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
            if (content.isBlank()) {
                throw LlmException("AI 服务未返回可用回复，请稍后重试。")
            }
            content
        } catch (error: LlmException) {
            throw error
        } catch (error: Exception) {
            throw LlmException("AI 服务返回格式异常，请稍后重试。", error)
        }
    }

    private fun httpErrorMessage(code: Int, body: String): String {
        val providerMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("").take(240)
        return when {
            code == 401 || code == 403 -> "API Key 无效或没有访问权限，请检查设置。"
            code == 429 -> "AI 服务请求过于频繁，请稍后再试。"
            code >= 500 -> "AI 服务暂时不可用，请稍后再试。"
            providerMessage.isNotBlank() -> "AI 请求失败（HTTP $code）：$providerMessage"
            else -> "AI 请求失败（HTTP $code），请检查模型和 Base URL。"
        }
    }

    private fun completionUrl(baseUrl: String): String {
        val base = baseUrl.trim().ifBlank { "https://api.deepseek.com" }.trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build()
    }
}
