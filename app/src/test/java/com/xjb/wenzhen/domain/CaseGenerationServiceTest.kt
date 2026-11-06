package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.cases.CaseParseException
import com.xjb.wenzhen.data.cases.CaseJsonCodec
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.PatientPersonality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun `解析 AI 病例时缺失治疗方案会被拒绝`() {
        val baseCase = BuiltInCases.byId("cardio-chest-pain")!!.copy(treatmentPlan = "")

        assertThrows(CaseParseException::class.java) {
            CaseJsonCodec.parseGenerated(
                raw = CaseJsonCodec.encode(baseCase),
                department = "心内科",
                difficulty = Difficulty.MEDIUM,
            )
        }
    }

    @Test
    fun `读取更早保存的病例时仍允许缺少治疗方案`() {
        val baseCase = BuiltInCases.byId("cardio-chest-pain")!!.copy(treatmentPlan = "")

        val decoded = CaseJsonCodec.decode(CaseJsonCodec.encode(baseCase))

        assertEquals("", decoded.treatmentPlan)
    }


    @Test
    fun `生成病例请求会把就诊类型写入提示词`() = runTest {
        val baseCase = BuiltInCases.byId("cardio-chest-pain")!!
        val fake = CapturingLlmClient(CaseJsonCodec.encode(baseCase))
        val service = CaseGenerationService(fake, PromptBuilder())

        service.generate(
            config = config(),
            department = "心内科",
            difficulty = Difficulty.HARD,
            personality = PatientPersonality.COOPERATIVE,
            encounterType = EncounterType.INPATIENT,
        )

        val system = fake.messages.first { it.role == "system" }.content
        val user = fake.messages.first { it.role == "user" }.content
        assertTrue(system.contains(EncounterType.INPATIENT.label))
        assertTrue(system.contains("按住院场景编写"))
        assertTrue(system.contains("治疗原则："))
        assertTrue(system.contains("药物："))
        assertTrue(system.contains("处理："))
        assertTrue(system.contains("随访："))
        assertTrue(user.contains(EncounterType.INPATIENT.label))
    }

    @Test
    fun `AI病例生成后可按住院场景重建完整参考方案`() = runTest {
        val baseCase = BuiltInCases.byId("cardio-chest-pain")!!
        val service = CaseGenerationService(
            FakeLlmClient(CaseJsonCodec.encode(baseCase)),
            PromptBuilder(),
        )

        val (generatedCase, _) = service.generate(
            config = config(),
            department = "心内科",
            difficulty = Difficulty.MEDIUM,
            encounterType = EncounterType.INPATIENT,
        )
        val plan = ReferenceCarePlanBuilder.build(generatedCase, EncounterType.INPATIENT)

        assertTrue(generatedCase.isAiGenerated)
        assertEquals(baseCase.correctDiagnosis, plan.diagnosis)
        assertEquals("住院", plan.encounterLabel)
        assertTrue(plan.diagnosisBasis.any { it.startsWith("既往史：") })
        assertTrue(plan.necessaryExams.any { it.contains("心电图") })
        assertTrue(plan.treatmentSections.any { it.label == "药物" && it.content.contains("阿司匹林") })
        assertTrue(plan.precautions.any { it.contains("双联抗血小板12个月") })
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
    private class CapturingLlmClient(private val response: String) : LlmClient {
        lateinit var messages: List<LlmMessage>
            private set

        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String {
            this.messages = messages
            return response
        }

        override suspend fun stream(
            config: LlmConfig,
            messages: List<LlmMessage>,
            temperature: Double,
            onDelta: suspend (String) -> Unit,
        ): String = error("流式接口不应在病例生成测试中调用")
    }
}
