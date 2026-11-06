package com.xjb.wenzhen.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScoreParserTest {
    @Test
    fun `正常JSON可解析分数和列表`() {
        val raw = """
            {
              "totalScore": 85,
              "breakdown": {
                "questioningCoverage": 34,
                "techniqueAndOrder": 14,
                "communication": 14,
                "diagnosisAccuracy": 16
              },
              "coveredPoints": ["问到了起病时间", "问到了伴随症状"],
              "missedQuestions": ["未询问吸烟史"],
              "strengths": ["沟通自然"],
              "improvements": ["补充家族史"],
              "correctDiagnosis": "急性心肌梗死",
              "diagnosisExplanation": "典型压榨性胸痛伴ST段抬高"
            }
        """.trimIndent()

        val result = ScoreParser.parse(raw)

        // 总分以分项相加为准（34+14+14+16=78），不再采信与分项不一致的 totalScore。
        assertEquals(78, result.totalScore)
        assertEquals(34, result.coverageScore)
        assertEquals(14, result.techniqueScore)
        assertEquals(14, result.communicationScore)
        assertEquals(16, result.diagnosisScore)
        assertEquals(listOf("问到了起病时间", "问到了伴随症状"), result.coveredPoints)
        assertEquals(listOf("未询问吸烟史"), result.missedQuestions)
        assertEquals("急性心肌梗死", result.correctDiagnosis)
    }

    @Test
    fun `带Markdown围栏和前后说明的JSON仍可解析`() {
        val raw = """
            下面是评分结果：
            ```json
            {
              "totalScore": 72,
              "breakdown": {
                "questioningCoverage": 30,
                "techniqueAndOrder": 14,
                "communication": 15,
                "diagnosisAccuracy": 12
              },
              "coveredPoints": ["询问了现病史"],
              "missedQuestions": ["遗漏既往史"],
              "strengths": ["态度温和"],
              "improvements": ["注意问诊顺序"],
              "correctDiagnosis": "急性阑尾炎",
              "diagnosisExplanation": "转移性右下腹痛是重要线索"
            }
            ```
            请继续练习。
        """.trimIndent()

        val result = ScoreParser.parse(raw)

        // 分项相加 30+14+15+12=71，与模型给出的 72 不一致时以分项为准。
        assertEquals(71, result.totalScore)
        assertEquals(30, result.coverageScore)
        assertEquals("急性阑尾炎", result.correctDiagnosis)
    }

    @Test
    fun `损坏JSON抛出明确异常`() {
        val raw = "{\"totalScore\": 88, \"breakdown\": { 这不是合法JSON }"

        assertThrows(ScoreParseException::class.java) {
            ScoreParser.parse(raw)
        }
    }

    @Test
    fun `缺少总分时可由分项求和并限制分项上限`() {
        val raw = """
            {
              "breakdown": {
                "questioningCoverage": 50,
                "techniqueAndOrder": 25,
                "communication": 18,
                "diagnosisAccuracy": 16,
                "treatment": 99
              }
            }
        """.trimIndent()

        val result = ScoreParser.parse(raw)

        assertEquals(35, result.coverageScore)
        assertEquals(15, result.techniqueScore)
        assertEquals(15, result.treatmentScore)
        assertEquals(96, result.totalScore)
    }

    @Test
    fun `AI 点评状态随 scoreJson 往返，旧记录默认按成功解析`() {
        val failed = ScoreResult(
            totalScore = 50,
            coverageScore = 20,
            techniqueScore = 8,
            communicationScore = 7,
            diagnosisScore = 10,
            treatmentScore = 5,
            coveredPoints = emptyList(),
            missedQuestions = listOf("遗漏点"),
            strengths = emptyList(),
            improvements = emptyList(),
            correctDiagnosis = "急性阑尾炎",
            diagnosisExplanation = "",
            aiCommentStatus = AiCommentStatus.FAILED,
            aiCommentError = "请求超时",
        )

        val decoded = ScoreParser.parse(ScoreParser.encode(failed))

        assertEquals(AiCommentStatus.FAILED, decoded.aiCommentStatus)
        assertEquals(50, decoded.totalScore)
        // 失败原因只用于当次提示，不写入历史。
        assertEquals(null, decoded.aiCommentError)

        val legacy = ScoreParser.parse(
            """{"breakdown": {"questioningCoverage": 30, "techniqueAndOrder": 12, "communication": 12, "diagnosisAccuracy": 16, "treatment": 10}}""",
        )
        assertEquals(AiCommentStatus.OK, legacy.aiCommentStatus)
    }

    @Test
    fun `旧版无治疗分项的记录沿用旧满分不截断`() {
        val raw = """{"totalScore": 92, "breakdown": {"questioningCoverage": 38, "techniqueAndOrder": 18, "communication": 18, "diagnosisAccuracy": 18}}"""
        val result = ScoreParser.parse(raw)
        assertEquals(true, result.isLegacy)
        assertEquals(38, result.coverageScore)
        assertEquals(18, result.techniqueScore)
        assertEquals(40, result.coverageMax)
        assertEquals(92, result.totalScore)
    }
}
