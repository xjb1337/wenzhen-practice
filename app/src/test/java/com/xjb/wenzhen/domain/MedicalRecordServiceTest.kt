package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalRecordServiceTest {
    @Test
    fun `FakeLlmClient生成病历后系统回顾位于体格检查之后`() = runTest {
        val fakeResponse = """
            ## 一般情况
            王建国，男，58岁，退休工人。
            ## 主诉
            胸痛3小时。
            ## 现病史
            3小时前搬重物时出现胸骨后压榨样疼痛。
            ## 体格检查
            T 36.8℃，BP 150/92mmHg，心音稍低。
            ## 系统回顾
            无发热、咳嗽，无腹痛。
            ## 初步诊断
            急性ST段抬高型心肌梗死。
        """.trimIndent()
        val fakeClient = FakeLlmClient(fakeResponse)
        val service = MedicalRecordService(fakeClient, PromptBuilder())
        val medicalCase = BuiltInCases.byId("cardio-chest-pain")!!
        val messages = listOf(
            ChatMessage(MessageRole.PATIENT, medicalCase.openingLine),
            ChatMessage(MessageRole.DOCTOR, "您胸痛多久了？有没有向肩膀放射？"),
            ChatMessage(MessageRole.PATIENT, "差不多三个小时，左肩也酸胀。"),
        )

        val record = service.generate(
            config = LlmConfig("test-key", "deepseek-v4-flash", "https://api.deepseek.com"),
            medicalCase = medicalCase,
            messages = messages,
            initialDiagnosis = "急性心肌梗死",
            diagnosisBasis = "压榨性胸痛伴左肩放射",
        )

        val physicalExamIndex = record.indexOf("体格检查\n")
        val reviewOfSystemsIndex = record.indexOf("系统回顾\n")
        assertTrue(physicalExamIndex >= 0)
        assertTrue(reviewOfSystemsIndex > physicalExamIndex)
        assertTrue(record.contains("体格检查\nT 36.8℃"))
        assertTrue(record.contains("既往史\n未问及"))
        assertTrue(record.contains("初步诊断\n急性ST段抬高型心肌梗死"))
        assertEquals(2, fakeClient.lastMessages.size)
        assertTrue(fakeClient.lastMessages.first().content.contains("系统回顾必须写在体格检查之后"))
    }

    private class FakeLlmClient(
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
