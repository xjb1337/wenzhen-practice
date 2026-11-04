package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.cases.CaseJsonCodec
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.PatientPersonality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CaseGenerationServiceTest {
    @Test
    fun `生成病例年龄不满足所选性格时回退为配合`() = runTest {
        val baseCase = BuiltInCases.byId("cardio-chest-pain")!!
            .copy(patient = BuiltInCases.byId("cardio-chest-pain")!!.patient.copy(age = "46岁"))
        val service = CaseGenerationService(
            FakeLlmClient(CaseJsonCodec.encode(baseCase)),
            PromptBuilder(),
        )

        val (_, personality) = service.generate(
            config = config(),
            department = "心内科",
            difficulty = Difficulty.MEDIUM,
            personality = PatientPersonality.ELDERLY,
        )

        assertEquals(PatientPersonality.COOPERATIVE, personality)
    }

    @Test
    fun `生成病例年龄满足所选性格时保留该性格`() = runTest {
        val baseCase = BuiltInCases.byId("cardio-chest-pain")!!
            .copy(patient = BuiltInCases.byId("cardio-chest-pain")!!.patient.copy(age = "65岁"))
        val service = CaseGenerationService(
            FakeLlmClient(CaseJsonCodec.encode(baseCase)),
            PromptBuilder(),
        )

        val (_, personality) = service.generate(
            config = config(),
            department = "心内科",
            difficulty = Difficulty.MEDIUM,
            personality = PatientPersonality.ELDERLY,
        )

        assertEquals(PatientPersonality.ELDERLY, personality)
    }

    @Test
    fun `解析 AI 病例时缺失治疗方案返回空字符串`() {
        val baseCase = BuiltInCases.byId("cardio-chest-pain")!!.copy(treatmentPlan = "")

        val parsed = CaseJsonCodec.parseGenerated(
            raw = CaseJsonCodec.encode(baseCase),
            department = "心内科",
            difficulty = Difficulty.MEDIUM,
        )

        assertEquals("", parsed.treatmentPlan)
    }

    private fun config() = LlmConfig(
        apiKey = "test-key",
        model = "deepseek-flash",
        baseUrl = "https://api.deepseek.com",
    )

    private class FakeLlmClient(private val response: String) : LlmClient {
        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String = response

        override suspend fun stream(
            config: LlmConfig,
            messages: List<LlmMessage>,
            temperature: Double,
            onDelta: suspend (String) -> Unit,
        ): String = error("流式接口不应在病例生成测试中调用")
    }
}
