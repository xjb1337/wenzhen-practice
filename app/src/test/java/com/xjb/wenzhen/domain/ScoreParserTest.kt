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
                "techniqueAndOrder": 17,
                "communication": 18,
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

        assertEquals(85, result.totalScore)
        assertEquals(34, result.coverageScore)
        assertEquals(17, result.techniqueScore)
        assertEquals(18, result.communicationScore)
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
                "communication": 16,
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

        assertEquals(72, result.totalScore)
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
                "diagnosisAccuracy": 16
              }
            }
        """.trimIndent()

        val result = ScoreParser.parse(raw)

        assertEquals(40, result.coverageScore)
        assertEquals(20, result.techniqueScore)
        assertEquals(94, result.totalScore)
    }
}
