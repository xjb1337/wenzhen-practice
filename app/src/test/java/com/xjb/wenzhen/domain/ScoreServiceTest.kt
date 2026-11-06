package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.api.LlmException
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C-1：评分先算本地规则分，LLM 点评失败时仍返回规则分并标记失败。 */
class ScoreServiceTest {
    private val medicalCase = BuiltInCases.byId("cardio-chest-pain")!!
    private val messages = listOf(
        ChatMessage(MessageRole.DOCTOR, "胸口疼了多久了？"),
        ChatMessage(MessageRole.PATIENT, "三个小时了。"),
        ChatMessage(MessageRole.DOCTOR, "抽烟喝酒吗？"),
        ChatMessage(MessageRole.PATIENT, "抽了三十年烟。"),
    )
    private val diagnosis = "急性ST段抬高型心肌梗死"
    private val basis = "压榨样胸痛伴大汗"
    private val treatment = "阿司匹林300mg嚼服，尽快PCI"

    private fun expectedRule() = RuleScoreCalculator.compute(
        medicalCase = medicalCase,
        messages = messages,
        initialDiagnosis = diagnosis,
        diagnosisBasis = basis,
        treatmentAnswer = treatment,
    )

    private suspend fun evaluate(client: LlmClient): ScoreResult =
        ScoreService(client, PromptBuilder()).evaluate(
            config = LlmConfig("test-key", "deepseek-flash", "https://api.deepseek.com"),
            medicalCase = medicalCase,
            messages = messages,
            initialDiagnosis = diagnosis,
            diagnosisBasis = basis,
            treatmentAnswer = treatment,
        )

    private fun assertRuleScore(result: ScoreResult) {
        val rule = expectedRule()
        assertEquals(rule.total, result.totalScore)
        assertEquals(rule.coverage, result.coverageScore)
        assertEquals(rule.technique, result.techniqueScore)
        assertEquals(rule.communication, result.communicationScore)
        assertEquals(rule.diagnosis, result.diagnosisScore)
        assertEquals(rule.treatment, result.treatmentScore)
        assertEquals(rule.missedQuestions.distinct(), result.missedQuestions)
        assertEquals(rule.evidence, result.evidence)
        assertEquals(medicalCase.correctDiagnosis, result.correctDiagnosis)
    }

    @Test
    fun `LLM 超时或限流时仍返回规则分并标记 AI 点评失败`() = runTest {
        val result = evaluate(FakeClient { throw LlmException("请求超时，请稍后重试。") })

        assertRuleScore(result)
        assertEquals(AiCommentStatus.FAILED, result.aiCommentStatus)
        assertEquals("请求超时，请稍后重试。", result.aiCommentError)
        assertTrue(result.strengths.isEmpty())
        assertTrue(result.coveredPoints.isNotEmpty())
    }

    @Test
    fun `LLM 返回损坏 JSON 时仍返回规则分`() = runTest {
        val result = evaluate(FakeClient { "{\"totalScore\": 88, \"breakdown\": { 这不是合法JSON }" })

        assertRuleScore(result)
        assertEquals(AiCommentStatus.FAILED, result.aiCommentStatus)
        assertNotNull(result.aiCommentError)
    }

    @Test
    fun `LLM 正常时分数取规则分点评取模型`() = runTest {
        val result = evaluate(
            FakeClient {
                """
                {
                  "totalScore": 99,
                  "breakdown": {
                    "questioningCoverage": 35, "techniqueAndOrder": 15, "communication": 15,
                    "diagnosisAccuracy": 20, "treatment": 15
                  },
                  "strengths": ["围绕主诉展开"],
                  "improvements": ["补充既往史"],
                  "correctDiagnosis": "模型给出的诊断",
                  "diagnosisExplanation": "典型压榨样胸痛伴 ST 段抬高。"
                }
                """.trimIndent()
            },
        )

        assertRuleScore(result)
        assertEquals(AiCommentStatus.OK, result.aiCommentStatus)
        assertNull(result.aiCommentError)
        assertEquals(listOf("围绕主诉展开"), result.strengths)
        assertTrue(result.improvements.contains("补充既往史"))
        assertEquals("典型压榨样胸痛伴 ST 段抬高。", result.diagnosisExplanation)
    }

    @Test
    fun `协程取消不会被当作点评失败吞掉`() = runTest {
        var thrown: Throwable? = null
        try {
            evaluate(FakeClient { throw CancellationException("用户取消") })
        } catch (error: CancellationException) {
            thrown = error
        }
        assertNotNull(thrown)
    }

    private class FakeClient(private val respond: () -> String) : LlmClient {
        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String = respond()
    }
}
