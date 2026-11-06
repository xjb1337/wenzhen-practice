package com.xjb.wenzhen.data.api

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage

interface LlmClient {
    suspend fun complete(
        config: LlmConfig,
        messages: List<LlmMessage>,
        jsonMode: Boolean = false,
        temperature: Double = 0.7,
    ): String

    suspend fun stream(
        config: LlmConfig,
        messages: List<LlmMessage>,
        temperature: Double = 0.7,
        onDelta: suspend (String) -> Unit,
    ): String {
        val result = complete(
            config = config,
            messages = messages,
            jsonMode = false,
            temperature = temperature,
        )
        onDelta(result)
        return result
    }

    suspend fun testConnection(config: LlmConfig): ConnectionTestResult =
        runCatching {
            complete(
                config = config,
                messages = listOf(LlmMessage("user", "请只回复：ok")),
                temperature = 0.0,
            )
        }.fold(
            onSuccess = { ConnectionTestResult.Success },
            onFailure = {
                ConnectionTestResult.Failure(
                    it.message?.takeIf(String::isNotBlank) ?: "连接测试失败，请稍后重试。",
                )
            },
        )
}

/**
 * 可重试的失败类型。只有明确安全、且未产生有效生成结果的错误才允许自动重试，
 * 避免“超时后重试”把同一场问诊重复提交给模型。
 */
enum class LlmErrorKind {
    RATE_LIMITED,
    SERVER_UNAVAILABLE,
    SERVICE_DISABLED,
    TIMEOUT,
    NETWORK,
    AUTH,
    QUOTA,
    BAD_REQUEST,
    BAD_RESPONSE,
    UNKNOWN,
}

class LlmException(
    message: String,
    cause: Throwable? = null,
    val kind: LlmErrorKind = LlmErrorKind.UNKNOWN,
    val retryAfterMillis: Long? = null,
) : Exception(message, cause) {
    val retryable: Boolean
        get() = kind == LlmErrorKind.RATE_LIMITED ||
            kind == LlmErrorKind.SERVER_UNAVAILABLE ||
            kind == LlmErrorKind.NETWORK
}

sealed interface ConnectionTestResult {
    data object Success : ConnectionTestResult
    data class Failure(val message: String) : ConnectionTestResult
}
