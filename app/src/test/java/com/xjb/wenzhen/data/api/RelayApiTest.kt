package com.xjb.wenzhen.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RelayApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: RelayClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = RelayClient(httpClient = OkHttpClient(), enforceHttps = false)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `兑换成功保存会话令牌`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"sessionToken":"session-abc","expiresAt":123}"""),
        )

        val result = client.redeem(server.url("/").toString(), " WZ-TEST ", "device-1")

        assertTrue(result is RedeemResult.Success)
        result as RedeemResult.Success
        assertEquals("session-abc", result.sessionToken)
        assertEquals(123_000L, result.expiresAt)
        val request = server.takeRequest()
        assertEquals("/v1/redeem", request.path)
        val payload = JSONObject(request.body.readUtf8())
        assertEquals("WZ-TEST", payload.getString("code"))
        assertEquals("device-1", payload.getString("deviceId"))
    }

    @Test
    fun `秒级过期时间归一化为毫秒`() {
        assertEquals(123_000L, normalizeEpochMillis(123L))
    }

    @Test
    fun `毫秒级过期时间保持不变`() {
        assertEquals(1_700_000_000_000L, normalizeEpochMillis(1_700_000_000_000L))
    }

    @Test
    fun `邀请码无效返回中文提示`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"error":{"code":"invite_invalid","message":"邀请码无效"}}"""),
        )

        val result = client.redeem(server.url("/").toString(), "WZ-BAD", "device-1")

        assertTrue(result is RedeemResult.Failure)
        result as RedeemResult.Failure
        assertEquals("邀请码无效", result.message)
        assertEquals("invite_invalid", result.code)
    }

    @Test
    fun `额度错误映射为今日额度已用完`() {
        val message = RelayErrorParser.messageFromBody(
            """{"error":{"code":"quota_device"}}""",
            403,
        )

        assertEquals("今日额度已用完", message)
    }

    @Test
    fun `服务端中文错误优先于默认提示`() {
        val message = RelayErrorParser.messageFromBody(
            """{"error":{"code":"quota_spend","message":"今日额度已用尽，请明天再来"}}""",
            429,
        )

        assertEquals("今日额度已用尽，请明天再来", message)
    }

    @Test
    fun `健康检查请求 v1 health`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val healthy = client.health(server.url("/").toString())

        assertTrue(healthy)
        assertEquals("/v1/health", server.takeRequest().path)
    }

    @Test
    fun `兑换响应缺少令牌时返回格式错误`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"expiresAt":123}"""))

        val result = client.redeem(server.url("/").toString(), "WZ-TEST", "device-1")

        assertTrue(result is RedeemResult.Failure)
        assertNotNull((result as RedeemResult.Failure).message)
    }
}
