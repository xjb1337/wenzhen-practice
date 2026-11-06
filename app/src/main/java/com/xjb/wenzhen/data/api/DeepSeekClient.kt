package com.xjb.wenzhen.data.api

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.ThinkingLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

@OptIn(InternalCoroutinesApi::class)
class DeepSeekClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val enforceHttps: Boolean = true,
    private val relayClient: RelayClient = RelayClient(httpClient, enforceHttps),
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
            currentCoroutineContext().ensureActive()
            try {
                return@withContext executeCall(httpClientFor(config).newCall(request)) { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw httpException(response, body, config.useRelay)
                    }
                    parseContent(body)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: LlmException) {
                lastError = error
                if (attempt < MAX_ATTEMPTS && error.retryable) {
                    delay(retryDelayMillis(attempt, error))
                } else {
                    throw error
                }
            } catch (error: Exception) {
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("请求已取消", error)
                }
                val typed = LlmException(
                    message = networkErrorMessage(error),
                    cause = error,
                    kind = networkErrorKind(error),
                )
                lastError = typed
                if (attempt < MAX_ATTEMPTS && typed.retryable) {
                    delay(retryDelayMillis(attempt, typed))
                } else {
                    throw typed
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
            executeCall(httpClientFor(config).newCall(request)) { response ->
                if (!response.isSuccessful) {
                    throw httpException(
                        response,
                        response.body?.string().orEmpty(),
                        config.useRelay,
                    )
                }
                val source = response.body?.source()
                    ?: throw LlmException(
                        message = "AI 服务没有返回内容，请稍后重试。",
                        kind = LlmErrorKind.BAD_RESPONSE,
                    )
                val answer = StringBuilder()
                var finished = false
                while (!finished) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    when (val event = SseParser.parseLine(line)) {
                        is SseEvent.Delta -> {
                            answer.append(event.text)
                            onDelta(event.text)
                        }
                        SseEvent.Done -> finished = true
                        SseEvent.Ignore -> Unit
                        is SseEvent.Error -> throw LlmException(
                            message = "AI 服务返回错误：${event.message}",
                            kind = LlmErrorKind.BAD_RESPONSE,
                        )
                    }
                }
                val result = answer.toString().trim()
                if (result.isBlank()) {
                    throw LlmException(
                        message = "AI 服务未返回可用回复，请稍后重试。",
                        kind = LlmErrorKind.BAD_RESPONSE,
                    )
                }
                result
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: LlmException) {
            throw error
        } catch (error: Exception) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("请求已取消", error)
            }
            throw LlmException(networkErrorMessage(error), error, networkErrorKind(error))
        }
    }

    override suspend fun testConnection(config: LlmConfig): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            if (config.apiKey.isBlank()) {
                return@withContext ConnectionTestResult.Failure(
                    if (config.useRelay) "请先兑换邀请码。" else "请先填写 DeepSeek API Key。",
                )
            }
            if (config.useRelay) {
                // health 只说明服务是否开启，并不校验会话；Healthy 后继续用 1-token ping 验证 token。
                when (val health = relayClient.checkHealth(
                    baseUrl = config.baseUrl,
                    sessionToken = config.apiKey,
                    deviceId = config.deviceId,
                )) {
                    RelayHealthResult.Healthy -> Unit
                    RelayHealthResult.Missing -> Unit
                    is RelayHealthResult.Failure ->
                        return@withContext ConnectionTestResult.Failure(health.message)
                }
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
                        ConnectionTestResult.Failure(
                            httpErrorMessage(response.code, body, config.useRelay),
                        )
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
            .url(completionUrl(config))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", accept)
            .apply {
                if (config.deviceId.isNotBlank() || config.useRelay) {
                    header("X-Device-Id", config.deviceId.trim())
                }
            }
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

    private fun requireApiKey(config: LlmConfig) {
        if (config.apiKey.isBlank()) {
            throw LlmException(
                message = if (config.useRelay) "请先兑换邀请码。" else "请先在设置中填写 DeepSeek API Key。",
                kind = LlmErrorKind.AUTH,
            )
        }
    }

    private fun parseContent(body: String): String {
        if (body.isBlank()) {
            throw LlmException(
                message = "AI 服务返回了空内容，请稍后重试。",
                kind = LlmErrorKind.BAD_RESPONSE,
            )
        }
        return try {
            val choices = JSONObject(body).optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                throw LlmException(
                    message = "AI 服务未返回可用回复，请稍后重试。",
                    kind = LlmErrorKind.BAD_RESPONSE,
                )
            }
            val content = choices.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
            if (content.isBlank()) {
                throw LlmException(
                    message = "AI 服务未返回可用回复，请稍后重试。",
                    kind = LlmErrorKind.BAD_RESPONSE,
                )
            }
            content
        } catch (error: LlmException) {
            throw error
        } catch (error: Exception) {
            throw LlmException(
                message = "AI 服务返回格式异常，请稍后重试。",
                cause = error,
                kind = LlmErrorKind.BAD_RESPONSE,
            )
        }
    }

    private fun httpErrorMessage(code: Int, body: String, useRelay: Boolean = false): String {
        if (useRelay) {
            return RelayErrorParser.messageFromBody(body, code)
        }
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

    private fun httpException(response: Response, body: String, useRelay: Boolean): LlmException {
        val code = response.code
        val relayCode = if (useRelay) RelayErrorParser.codeFromBody(body) else null
        val kind = when {
            relayCode == "quota_device" || relayCode == "quota_spend" -> LlmErrorKind.QUOTA
            relayCode == "invite_used" || relayCode == "invite_invalid" -> LlmErrorKind.AUTH
            code == 401 || code == 403 -> LlmErrorKind.AUTH
            code == 402 -> LlmErrorKind.QUOTA
            code == 429 -> LlmErrorKind.RATE_LIMITED
            code == 408 -> LlmErrorKind.TIMEOUT
            code >= 500 -> LlmErrorKind.SERVER_UNAVAILABLE
            code >= 400 -> LlmErrorKind.BAD_REQUEST
            else -> LlmErrorKind.UNKNOWN
        }
        return LlmException(
            message = httpErrorMessage(code, body, useRelay),
            kind = kind,
            retryAfterMillis = response.header("Retry-After")
                ?.trim()
                ?.toLongOrNull()
                ?.times(1000L),
        )
    }

    private fun networkErrorMessage(error: Exception): String = when (networkErrorKind(error)) {
        LlmErrorKind.TIMEOUT -> "连接超时，请检查网络或稍后重试。"
        LlmErrorKind.NETWORK -> "网络错误，无法连接 DeepSeek 服务，请检查网络后重试。"
        else -> "AI 请求失败，请稍后重试。"
    }

    /**
     * 只把“连接阶段失败”视为可安全重试：请求还没送到服务端，重试不会产生重复生成。
     * 读超时、连接中断等可能已经产生部分结果，交给用户确认后手动重试。
     */
    private fun networkErrorKind(error: Exception): LlmErrorKind = when (error) {
        is ConnectException,
        is UnknownHostException,
        is NoRouteToHostException -> LlmErrorKind.NETWORK

        is SocketTimeoutException -> {
            val message = error.message.orEmpty()
            if (message.contains("connect", ignoreCase = true)) {
                LlmErrorKind.NETWORK
            } else {
                LlmErrorKind.TIMEOUT
            }
        }

        is InterruptedIOException -> LlmErrorKind.TIMEOUT
        else -> LlmErrorKind.UNKNOWN
    }

    private fun retryDelayMillis(attempt: Int, error: LlmException): Long =
        (error.retryAfterMillis ?: (800L * attempt)).coerceIn(0L, 10_000L)

    /**
     * 执行阻塞式 OkHttp 调用，并让协程取消立即变成 [Call.cancel]。
     * 仅调用 job.cancel() 无法唤醒阻塞在 socket read 上的线程，旧实现会继续读到完整响应，
     * 甚至把迟到的片段写进下一次重试的气泡。
     */
    private suspend fun <T> executeCall(call: Call, block: suspend (Response) -> T): T {
        val cancellation = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true) {
            call.cancel()
        }
        try {
            return call.execute().use { response -> block(response) }
        } finally {
            cancellation.dispose()
        }
    }

    private fun httpClientFor(config: LlmConfig): OkHttpClient =
        if (config.thinkingLevel == ThinkingLevel.MAX) {
            httpClient.newBuilder()
                .readTimeout(LONG_RUNNING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(LONG_RUNNING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        } else {
            httpClient
        }

    private fun completionUrl(config: LlmConfig): String {
        if (config.useRelay) {
            return RelayEndpoints.chatCompletions(config.baseUrl, enforceHttps)
        }
        val base = config.baseUrl.trim().ifBlank { "https://api.deepseek.com" }.trimEnd('/')
        if (enforceHttps && !base.startsWith("https://", ignoreCase = true)) {
            throw LlmException("接口地址必须以 https:// 开头，以免 API Key 明文传输。")
        }
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val LONG_RUNNING_TIMEOUT_SECONDS = 300L
        internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build()
    }
}
