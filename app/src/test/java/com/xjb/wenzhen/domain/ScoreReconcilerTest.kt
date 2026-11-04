package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScoreReconcilerTest {
    private val medicalCase = BuiltInCases.all.first()

    @Test
    fun `旧版缺治疗分项的模型结果仍必须被本地规则分覆盖`() {
        // v1.1 及以前的模型 JSON 没有 treatment 分项 → isLegacy = true。
        // 它只能决定“历史记录怎么显示”，不能让模型自报分绕过本地规则分。
        val legacy = ScoreResult(
            totalScore = 92,
            coverageScore = 38,
            techniqueScore = 18,
            communicationScore = 18,
            diagnosisScore = 18,
            coveredPoints = listOf("模型自报覆盖"),
            missedQuestions = emptyList(),
            strengths = emptyList(),
            improvements = emptyList(),
            correctDiagnosis = medicalCase.correctDiagnosis,
            diagnosisExplanation = "",
            treatmentScore = 0,
            isLegacy = true,
        )
        val rule = RuleScore(
            coverage = 20,
            technique = 12,
            communication = 9,
            diagnosis = 16,
            treatment = 10,
            evidence = listOf(ScoreEvidence("问诊要点", listOf(2))),
            coveredQuestions = listOf("问诊要点"),
            missedQuestions = listOf("遗漏点"),
        )

        val reconciled = ScoreReconciler.reconcile(
            score = legacy,
            medicalCase = medicalCase,
            messages = listOf(ChatMessage(role = MessageRole.DOCTOR, content = "您这个情况多久了？")),
            rule = rule,
        )

        assertEquals(20, reconciled.coverageScore)
        assertEquals(12, reconciled.techniqueScore)
        assertEquals(9, reconciled.communicationScore)
        assertEquals(16, reconciled.diagnosisScore)
        assertEquals(10, reconciled.treatmentScore)
        assertEquals(rule.total, reconciled.totalScore)
        assertFalse("规则分覆盖后必须回到现行满分口径", reconciled.isLegacy)
        assertEquals(35, reconciled.coverageMax)
        assertEquals(listOf("遗漏点"), reconciled.missedQuestions)
    }
}
