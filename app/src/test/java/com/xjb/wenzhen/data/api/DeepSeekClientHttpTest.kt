package com.xjb.wenzhen.data.api

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DeepSeekClientHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DeepSeekClient
    private val messages = listOf(LlmMessage("user", "ping"))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DeepSeekClient(httpClient = OkHttpClient(), enforceHttps = false)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config() = LlmConfig(
        apiKey = "test-key",
        model = "deepseek-chat",
        baseUrl = server.url("/v1").toString().trimEnd('/'),
    )

    private fun successBody(content: String = "ok") =
        """{"choices":[{"message":{"content":"$content"}}]}"""

    @Test
    fun `限流后重试并返回成功结果`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"rate limited"}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody()))

        val result = client.complete(config(), messages)

        assertEquals("ok", result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `服务端错误重试三次后抛出可读错误`() = runBlocking {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}"""))
        }

        try {
            client.complete(config(), messages)
            fail("应当抛出 LlmException")
        } catch (error: LlmException) {
            assertTrue(error.message.orEmpty().contains("暂时不可用"))
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun `流式响应逐段回调并拼接完整内容`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n" +
                        "data: [DONE]\n\n",
                ),
        )
        val deltas = mutableListOf<String>()

        val result = client.stream(config(), messages) { deltas += it }

        assertEquals("你好", result)
        assertEquals(listOf("你", "好"), deltas)
    }
}
