package com.xjb.wenzhen.data.api

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayAuthWatchingClientTest {

    @Test
    fun `测试连接返回鉴权失败时发出失效令牌`() = runBlocking {
        val client = RelayAuthWatchingClient(ResultFailingClient(LlmErrorKind.AUTH))
        val token = async(start = CoroutineStart.UNDISPATCHED) { client.invalidated.first() }

        val result = client.testConnection(relayConfig())

        assertEquals(LlmErrorKind.AUTH, (result as ConnectionTestResult.Failure).kind)
        assertEquals("session-token", token.await())
    }

    @Test
    fun `测试连接返回非鉴权失败时不发出失效令牌`() = runBlocking {
        val client = RelayAuthWatchingClient(ResultFailingClient(LlmErrorKind.NETWORK))
        var emitted: String? = null
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            emitted = client.invalidated.first()
        }

        val result = client.testConnection(relayConfig())

        assertEquals(LlmErrorKind.NETWORK, (result as ConnectionTestResult.Failure).kind)
        collector.cancel()
        assertNull(emitted)
    }

    private fun relayConfig() = LlmConfig(
        apiKey = "session-token",
        model = "deepseek-chat",
        baseUrl = "https://relay.example.com",
        deviceId = "device-1",
        useRelay = true,
    )

    private class ResultFailingClient(
        private val kind: LlmErrorKind,
    ) : LlmClient {
        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String = throw LlmException("请求失败", kind = kind)

        override suspend fun testConnection(config: LlmConfig): ConnectionTestResult =
            ConnectionTestResult.Failure("请求失败", kind)
    }
}
