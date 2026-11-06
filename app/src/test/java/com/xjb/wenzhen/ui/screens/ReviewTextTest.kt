package com.xjb.wenzhen.ui.screens

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.domain.ReferenceCarePlanBuilder
import com.xjb.wenzhen.domain.ScoreResult
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTextTest {
    private val medicalCase = BuiltInCases.byId("cardio-chest-pain")!!
    private val score = ScoreResult(
        totalScore = 80,
        coverageScore = 30,
        techniqueScore = 12,
        communicationScore = 12,
        diagnosisScore = 16,
        treatmentScore = 10,
        coveredPoints = listOf("询问了胸痛特点"),
        missedQuestions = emptyList(),
        strengths = listOf("重点明确"),
        improvements = listOf("补充危险因素"),
        correctDiagnosis = medicalCase.correctDiagnosis,
        diagnosisExplanation = "依据病例资料可支持诊断。",
    )

    @Test
    fun `复制的点评包含完整参考诊疗方案`() {
        val plan = ReferenceCarePlanBuilder.build(medicalCase, EncounterType.OUTPATIENT)

        val text = reviewAsText(score, plan)

        assertTrue(text.contains("正确诊疗方案（门诊）"))
        assertTrue(text.contains("正确诊断：${medicalCase.correctDiagnosis}"))
        assertTrue(text.contains("诊断依据："))
        assertTrue(text.contains("必要检查与结果："))
        assertTrue(text.contains("治疗与用药："))
        assertTrue(text.contains("药物："))
        assertTrue(text.contains("注意事项与随访："))
        assertTrue(text.contains("阿司匹林"))
    }

    @Test
    fun `缺少病例原始资料时点评明确说明无法重建方案`() {
        val text = reviewAsText(score, referenceCarePlan = null)

        assertTrue(text.contains("无法重建参考诊疗方案"))
    }
}
