package com.xjb.wenzhen.data.api

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.ThinkingLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class DeepSeekClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val enforceHttps: Boolean = true,
) : LlmClient {

    override suspend fun complete(
        config: LlmConfig,
        messages: List<LlmMessage>,
        jsonMode: Boolean,
        temperature: Double,
    ): String = withContext(Dispatchers.IO) {
        requireApiKey(config)
        val payload = payload(config, messages, temperature).apply {
            if (jsonMode) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }
        val request = request(config, payload, accept = "application/json")
        var lastError: LlmException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return@withContext httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw LlmException(httpErrorMessage(response.code, body))
                    }
                    parseContent(body)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: LlmException) {
                lastError = error
                if (attempt < MAX_ATTEMPTS && isRetryable(error)) {
                    delay(backoffMillis(attempt))
                } else {
                    throw error
                }
            } catch (error: Exception) {
                lastError = LlmException(networkErrorMessage(error), error)
                if (attempt < MAX_ATTEMPTS) {
                    delay(backoffMillis(attempt))
                } else {
                    throw lastError!!
                }
            }
        }
        throw lastError ?: LlmException("AI 请求失败，请稍后重试。")
    }

    override suspend fun stream(
        config: LlmConfig,
        messages: List<LlmMessage>,
        temperature: Double,
        onDelta: suspend (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        requireApiKey(config)
        val payload = payload(config, messages, temperature).put("stream", true)
        val request = request(config, payload, accept = "text/event-stream")
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw LlmException(httpErrorMessage(response.code, response.body?.string().orEmpty()))
                }
                val source = response.body?.source()
                    ?: throw LlmException("AI 服务没有返回内容，请稍后重试。")
                val answer = StringBuilder()
                var finished = false
                while (!finished) {
                    val line = source.readUtf8Line() ?: break
                    when (val event = SseParser.parseLine(line)) {
                        is SseEvent.Delta -> {
                            answer.append(event.text)
                            onDelta(event.text)
                        }
                        SseEvent.Done -> finished = true
                        SseEvent.Ignore -> Unit
                        is SseEvent.Error -> throw LlmException("AI 服务返回错误：${event.message}")
                    }
                }
                val result = answer.toString().trim()
                if (result.isBlank()) {
                    throw LlmException("AI 服务未返回可用回复，请稍后重试。")
                }
                result
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: LlmException) {
            throw error
        } catch (error: Exception) {
            throw LlmException(networkErrorMessage(error), error)
        }
    }

    override suspend fun testConnection(config: LlmConfig): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            if (config.apiKey.isBlank()) {
                return@withContext ConnectionTestResult.Failure("请先填写 DeepSeek API Key。")
            }
            val payload = JSONObject().apply {
                put("model", config.model)
                put("thinking", JSONObject().put("type", "disabled"))
                put("temperature", 0)
                put("max_tokens", 1)
                put("stream", false)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                }))
            }
            val request = try {
                request(config, payload, accept = "application/json")
            } catch (error: LlmException) {
                return@withContext ConnectionTestResult.Failure(error.message ?: "接口地址无效。")
            }
            try {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        ConnectionTestResult.Success
                    } else {
                        ConnectionTestResult.Failure(httpErrorMessage(response.code, body))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ConnectionTestResult.Failure(networkErrorMessage(error))
            }
        }

    internal fun payload(
        config: LlmConfig,
        messages: List<LlmMessage>,
        temperature: Double,
    ): JSONObject = JSONObject().apply {
        put("model", config.model)
        if (config.thinkingLevel == ThinkingLevel.OFF) {
            put("thinking", JSONObject().put("type", "disabled"))
            put("temperature", temperature)
        } else {
            put("thinking", JSONObject().put("type", "enabled"))
            config.thinkingLevel.apiValue?.let { put("reasoning_effort", it) }
        }
        put("messages", JSONArray().apply {
            messages.forEach { message ->
                put(JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
        })
    }

    private fun request(config: LlmConfig, payload: JSONObject, accept: String): Request =
        Request.Builder()
            .url(completionUrl(config.baseUrl))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", accept)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

    private fun requireApiKey(config: LlmConfig) {
        if (config.apiKey.isBlank()) {
            throw LlmException("请先在设置中填写 DeepSeek API Key。")
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
            code == 402 -> "DeepSeek 账户余额不足，请充值后重试。"
            code == 429 -> "请求过于频繁，已触发限流，请稍后再试。"
            code >= 500 -> "AI 服务暂时不可用，请稍后再试。"
            providerMessage.isNotBlank() -> "AI 请求失败（HTTP $code）：$providerMessage"
            else -> "AI 请求失败（HTTP $code），请检查模型和 Base URL。"
        }
    }

    private fun networkErrorMessage(error: Exception): String = when (error) {
        is SocketTimeoutException, is InterruptedIOException ->
            "连接超时，请检查网络或稍后重试。"
        else -> "网络错误，无法连接 DeepSeek 服务，请检查网络后重试。"
    }

    private fun isRetryable(error: LlmException): Boolean {
        val message = error.message.orEmpty()
        return message.contains("限流") ||
            message.contains("暂时不可用") ||
            message.contains("超时") ||
            message.contains("网络错误")
    }

    private fun backoffMillis(attempt: Int): Long = 800L * attempt

    private fun completionUrl(baseUrl: String): String {
        val base = baseUrl.trim().ifBlank { "https://api.deepseek.com" }.trimEnd('/')
        if (enforceHttps && !base.startsWith("https://", ignoreCase = true)) {
            throw LlmException("接口地址必须以 https:// 开头，以免 API Key 明文传输。")
        }
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build()
    }
}
