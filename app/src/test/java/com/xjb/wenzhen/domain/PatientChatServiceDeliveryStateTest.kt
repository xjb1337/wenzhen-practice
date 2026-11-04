package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归：用户反馈的「答非所问 / 回答滞后一轮」。
 * AppViewModel.sendMessage 把本轮医生提问标记为 SENDING，回复完成后才改成 SENT；
 * 若 PatientChatService 只保留 SENT，请求体末尾会是上一轮患者回复，模型回答上一轮问题。
 */
class PatientChatServiceDeliveryStateTest {

    private val config = LlmConfig("test-key", "deepseek-v4-flash", "https://api.deepseek.com")
    private val medicalCase = BuiltInCases.byId("cardio-chest-pain")!!

    @Test
    fun `本轮SENDING的医生提问必须作为请求体最后一条user消息`() = runTest {
        val client = CapturingLlmClient("好的，我回答这个问题。")
        val service = PatientChatService(client, PromptBuilder())
        val messages = listOf(
            ChatMessage(MessageRole.PATIENT, "医生，我胸口疼。"),
            ChatMessage(MessageRole.DOCTOR, "疼了多久了？"),
            ChatMessage(MessageRole.PATIENT, "大概三个小时。"),
            // 本轮：已追加到 UI 列表，但尚未收到回复 → SENDING
            ChatMessage(
                role = MessageRole.DOCTOR,
                content = "有没有高血压和糖尿病？",
                deliveryState = MessageDeliveryState.SENDING,
            ),
        )

        service.reply(config = config, medicalCase = medicalCase, messages = messages)

        val apiMessages = client.lastMessages
        assertEquals("system", apiMessages.first().role)
        assertEquals("user", apiMessages.last().role)
        assertEquals("有没有高血压和糖尿病？", apiMessages.last().content)
        // 3 条已送达的历史 + 1 条本轮提问 + 1 条 system
        assertEquals(5, apiMessages.size)
    }

    @Test
    fun `FAILED的医生消息不得进入上下文`() = runTest {
        val client = CapturingLlmClient("嗯。")
        val service = PatientChatService(client, PromptBuilder())
        val messages = listOf(
            ChatMessage(MessageRole.PATIENT, "医生，我胸口疼。"),
            ChatMessage(
                role = MessageRole.DOCTOR,
                content = "这条没发出去",
                deliveryState = MessageDeliveryState.FAILED,
            ),
            ChatMessage(
                role = MessageRole.DOCTOR,
                content = "现在还疼吗？",
                deliveryState = MessageDeliveryState.SENDING,
            ),
        )

        service.reply(config = config, medicalCase = medicalCase, messages = messages)

        val apiMessages = client.lastMessages
        assertFalse(apiMessages.any { it.content == "这条没发出去" })
        assertEquals("user", apiMessages.last().role)
        assertEquals("现在还疼吗？", apiMessages.last().content)
    }

    @Test
    fun `开场白与已送达历史保持顺序且SYSTEM卡片不入上下文`() = runTest {
        val client = CapturingLlmClient("好的。")
        val service = PatientChatService(client, PromptBuilder())
        val messages = listOf(
            ChatMessage(MessageRole.PATIENT, "开场白"),
            ChatMessage(MessageRole.DOCTOR, "第一问"),
            ChatMessage(MessageRole.PATIENT, "第一答"),
            ChatMessage(
                role = MessageRole.SYSTEM,
                content = "心电图示 ST 段抬高",
                systemTitle = "心电图",
            ),
            ChatMessage(
                role = MessageRole.DOCTOR,
                content = "第二问",
                deliveryState = MessageDeliveryState.SENDING,
            ),
        )

        service.reply(config = config, medicalCase = medicalCase, messages = messages)

        val contents = client.lastMessages.map { it.content }
        assertTrue("开场白" in contents)
        assertTrue("第一问" in contents)
        assertTrue("第一答" in contents)
        assertFalse("心电图示 ST 段抬高" in contents)
        assertEquals("第二问", contents.last())
        assertEquals(contents.indexOf("第一问") + 1, contents.indexOf("第一答"))
    }

    private class CapturingLlmClient(
        private val response: String,
    ) : LlmClient {
        var lastMessages: List<LlmMessage> = emptyList()
            private set

        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String {
            lastMessages = messages
            return response
        }
    }
}
