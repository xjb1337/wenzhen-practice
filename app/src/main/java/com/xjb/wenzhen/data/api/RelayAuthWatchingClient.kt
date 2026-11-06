package com.xjb.wenzhen.data.api

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 监听内置服务（relay）请求的鉴权失败，向外发出失效的 session token。
 * 仅当配置启用了中转且携带非空会话令牌时触发。
 */
class RelayAuthWatchingClient(
    private val delegate: LlmClient,
) : LlmClient {
    private val _invalidated = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val invalidated: SharedFlow<String> = _invalidated.asSharedFlow()

    override suspend fun complete(
        config: LlmConfig,
        messages: List<LlmMessage>,
        jsonMode: Boolean,
        temperature: Double,
    ): String = try {
        delegate.complete(config, messages, jsonMode, temperature)
    } catch (error: CancellationException) {
        throw error
    } catch (error: LlmException) {
        notifyIfAuthFailure(config, error.kind)
        throw error
    }

    override suspend fun stream(
        config: LlmConfig,
        messages: List<LlmMessage>,
        temperature: Double,
        onDelta: suspend (String) -> Unit,
    ): String = try {
        delegate.stream(config, messages, temperature, onDelta)
    } catch (error: CancellationException) {
        throw error
    } catch (error: LlmException) {
        notifyIfAuthFailure(config, error.kind)
        throw error
    }

    override suspend fun testConnection(config: LlmConfig): ConnectionTestResult {
        val result = try {
            delegate.testConnection(config)
        } catch (error: CancellationException) {
            throw error
        } catch (error: LlmException) {
            notifyIfAuthFailure(config, error.kind)
            throw error
        }
        if (result is ConnectionTestResult.Failure) {
            notifyIfAuthFailure(config, result.kind)
        }
        return result
    }

    private fun notifyIfAuthFailure(config: LlmConfig, kind: LlmErrorKind?) {
        if (config.useRelay &&
            kind == LlmErrorKind.AUTH &&
            config.apiKey.isNotBlank()
        ) {
            _invalidated.tryEmit(config.apiKey)
        }
    }
}
