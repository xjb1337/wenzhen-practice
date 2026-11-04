package com.xjb.wenzhen.data.api

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.ThinkingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekClientTest {
    private val client = DeepSeekClient()
    private val messages = listOf(LlmMessage("user", "ping"))

    @Test
    fun `关闭思考时发送 disabled 和 temperature`() {
        val payload = client.payload(config(ThinkingLevel.OFF), messages, temperature = 0.6)

        assertEquals("disabled", payload.getJSONObject("thinking").getString("type"))
        assertTrue(payload.has("temperature"))
        assertEquals(0.6, payload.getDouble("temperature"), 0.0001)
        assertFalse(payload.has("reasoning_effort"))
    }

    @Test
    fun `低和最高思考强度发送 enabled 与 reasoning_effort 且不发送 temperature`() {
        listOf(
            ThinkingLevel.LOW to "low",
            ThinkingLevel.MAX to "max",
        ).forEach { (level, expectedEffort) ->
            val payload = client.payload(config(level), messages, temperature = 0.6)

            assertEquals("enabled", payload.getJSONObject("thinking").getString("type"))
            assertEquals(expectedEffort, payload.getString("reasoning_effort"))
            assertFalse(payload.has("temperature"))
        }
    }

    private fun config(level: ThinkingLevel) = LlmConfig(
        apiKey = "test-key",
        model = "deepseek-flash",
        baseUrl = "https://api.deepseek.com",
        thinkingLevel = level,
    )
}
