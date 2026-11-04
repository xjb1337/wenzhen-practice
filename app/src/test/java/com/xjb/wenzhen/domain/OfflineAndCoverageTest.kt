package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAndCoverageTest {
    private val medicalCase = BuiltInCases.all.first()

    private fun doctor(content: String, state: MessageDeliveryState = MessageDeliveryState.SENT) =
        ChatMessage(role = MessageRole.DOCTOR, content = content, deliveryState = state)

    private fun patient(content: String) = ChatMessage(role = MessageRole.PATIENT, content = content)

    @Test
    fun `失败发送的医生消息不应作为问诊覆盖证据`() {
        val failed = doctor(medicalCase.keyQuestions.first(), MessageDeliveryState.FAILED)
        val evidence = QuestionCoverageAnalyzer.analyze(medicalCase, listOf(failed))
        assertTrue(evidence.none { it.covered })
        assertTrue(evidence.all { it.messageIndexes.isEmpty() })
    }

    @Test
    fun `已发送的医生问题应被识别为覆盖并给出消息索引`() {
        val question = medicalCase.keyQuestions.first()
        val messages = listOf(
            doctor("请问您哪里不舒服？"),
            doctor(question),
            patient("我不太清楚"),
        )
        val evidence = QuestionCoverageAnalyzer.analyze(medicalCase, messages)
        val hit = evidence.first { it.question == question }
        assertTrue(hit.covered)
        assertTrue(hit.messageIndexes.contains(1))
    }

    @Test
    fun `离线评分总分等于分项相加且在合法范围内`() {
        val messages = listOf(
            doctor("您这个情况多久了？"),
            patient(medicalCase.presentIllness),
        )
        val score = OfflinePracticeService.score(
            medicalCase = medicalCase,
            messages = messages,
            initialDiagnosis = medicalCase.correctDiagnosis,
            treatmentAnswer = "建议进一步检查",
            encounterType = EncounterType.OUTPATIENT,
        )
        val sum = score.coverageScore + score.techniqueScore + score.communicationScore +
            score.diagnosisScore + score.treatmentScore
        assertEquals(sum, score.totalScore)
        assertTrue(score.totalScore in 0..100)
        // 诊断名称完全匹配得 16 分；未填写诊断依据，因此不足 20 分满分。
        assertTrue(score.diagnosisScore in 14..20)
    }

    @Test
    fun `离线病历包含患者信息与初步诊断`() {
        val record = OfflinePracticeService.record(
            medicalCase = medicalCase,
            initialDiagnosis = "待查",
            diagnosisBasis = "症状典型",
            treatmentAnswer = "随访",
            encounterType = EncounterType.OUTPATIENT,
        )
        assertTrue(record.contains(medicalCase.patient.name))
        assertTrue(record.contains("初步诊断"))
        assertTrue(record.contains("待查"))
    }

    @Test
    fun `评分校正不应把已覆盖问题再次列为遗漏`() {
        val question = medicalCase.keyQuestions.first()
        val messages = listOf(doctor(question))
        val raw = ScoreResult(
            totalScore = 60,
            coverageScore = 10,
            techniqueScore = 12,
            communicationScore = 12,
            diagnosisScore = 16,
            coveredPoints = emptyList(),
            missedQuestions = listOf(question, "另一个问题"),
            strengths = emptyList(),
            improvements = emptyList(),
            correctDiagnosis = medicalCase.correctDiagnosis,
            diagnosisExplanation = "",
        )
        val reconciled = ScoreReconciler.reconcile(raw, medicalCase, messages)
        assertFalse(reconciled.missedQuestions.contains(question))
        assertTrue(reconciled.coverageScore in 0..35)
        assertEquals(
            reconciled.coverageScore + reconciled.techniqueScore + reconciled.communicationScore +
                reconciled.diagnosisScore + reconciled.treatmentScore,
            reconciled.totalScore,
        )
    }
}
