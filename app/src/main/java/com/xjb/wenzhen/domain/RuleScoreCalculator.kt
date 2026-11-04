package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole

/**
 * 题面规则分：覆盖、红旗、查体/检查、诊断匹配、治疗匹配全部由本地规则计算。
 * AI 只负责生成点评、优点和改进建议，不再决定总分。
 */
data class RuleScore(
    val coverage: Int,
    val technique: Int,
    val communication: Int,
    val diagnosis: Int,
    val treatment: Int,
    val evidence: List<ScoreEvidence>,
    val coveredQuestions: List<String>,
    val missedQuestions: List<String>,
) {
    val total: Int get() = (coverage + technique + communication + diagnosis + treatment).coerceIn(0, 100)
}

object RuleScoreCalculator {
    private const val COVERAGE_MAX = 35
    private const val TECHNIQUE_MAX = 15
    private const val COMMUNICATION_MAX = 15
    private const val DIAGNOSIS_MAX = 20
    private const val TREATMENT_MAX = 15

    private val questionMarkers = listOf("？", "?", "吗", "呢", "是否", "有没有", "有无", "多少", "多久", "怎样", "哪里", "什么")
    private val empathyMarkers = listOf("别担心", "请放心", "理解", "感谢", "建议", "需要", "注意", "配合")

    fun compute(
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        initialDiagnosis: String = "",
        diagnosisBasis: String = "",
        treatmentAnswer: String = "",
    ): RuleScore {
        val doctorMessages = messages.withIndex()
            .filter { it.value.role == MessageRole.DOCTOR && it.value.deliveryState == MessageDeliveryState.SENT }
        val usableText = doctorMessages.joinToString("\n") { it.value.content.lowercase() }

        val analysis = QuestionCoverageAnalyzer.analyze(medicalCase, messages)
        val weightTotal = analysis.sumOf { it.importance.coerceAtLeast(1) }
        val coveredWeight = analysis.filter { item ->
            item.covered && (item.messageIndexes.size >= minEvidenceFor(medicalCase, item.question))
        }.sumOf { it.importance.coerceAtLeast(1) }
        val coverage = if (weightTotal == 0) 0 else (coveredWeight * COVERAGE_MAX + weightTotal / 2) / weightTotal

        val coveredQuestions = analysis.filter { it.covered }.map { it.question }
        val missedQuestions = analysis.filterNot { it.covered }.map { it.question }
        val evidence = analysis.map { item ->
            ScoreEvidence(question = item.question, messageIndexes = item.messageIndexes)
        }

        val questionCount = doctorMessages.count { message ->
            questionMarkers.any { message.value.content.contains(it) }
        }
        val questionRatio = if (doctorMessages.isEmpty()) 0.0 else questionCount.toDouble() / doctorMessages.size
        val technique = when {
            doctorMessages.isEmpty() -> 0
            questionRatio >= 0.6 -> TECHNIQUE_MAX
            questionRatio >= 0.4 -> 12
            questionRatio >= 0.2 -> 9
            else -> 6
        }

        val empathy = if (empathyMarkers.any { usableText.contains(it) }) 2 else 0
        val averageLength = if (doctorMessages.isEmpty()) 0 else doctorMessages.sumOf { it.value.content.length } / doctorMessages.size
        val communication = (7 + empathy + when {
            averageLength in 8..120 -> 3
            averageLength > 0 -> 1
            else -> 0
        } + if (doctorMessages.size >= 4) 3 else 1).coerceIn(0, COMMUNICATION_MAX)

        val diagnosis = diagnosisScore(medicalCase, initialDiagnosis, diagnosisBasis)
        val treatment = treatmentScore(medicalCase, treatmentAnswer)

        return RuleScore(
            coverage = coverage.coerceIn(0, COVERAGE_MAX),
            technique = technique,
            communication = communication,
            diagnosis = diagnosis,
            treatment = treatment,
            evidence = evidence,
            coveredQuestions = coveredQuestions,
            missedQuestions = missedQuestions,
        )
    }

    private fun minEvidenceFor(medicalCase: MedicalCase, question: String): Int =
        KeyQuestionCatalog.of(medicalCase).firstOrNull { it.text == question }?.minEvidence ?: 1

    private fun diagnosisScore(medicalCase: MedicalCase, initialDiagnosis: String, diagnosisBasis: String): Int {
        val expected = medicalCase.correctDiagnosis.trim().lowercase()
        val actual = initialDiagnosis.trim().lowercase()
        if (actual.isEmpty()) return 0
        val nameScore = when {
            expected.isEmpty() -> 10
            actual == expected -> 16
            actual.contains(expected) || expected.contains(actual) -> 14
            else -> 6
        }
        val basisScore = if (diagnosisBasis.trim().length >= 6) 4 else if (diagnosisBasis.isNotBlank()) 2 else 0
        return (nameScore + basisScore).coerceIn(0, DIAGNOSIS_MAX)
    }

    private fun treatmentScore(medicalCase: MedicalCase, treatmentAnswer: String): Int {
        val answer = treatmentAnswer.trim()
        if (answer.isEmpty()) return 0
        val plan = medicalCase.treatmentPlan
        val keywords = plan.split(Regex("[，,。；;、\\s]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
        if (keywords.isEmpty()) return if (answer.length >= 12) 11 else 7
        val hits = keywords.count { answer.contains(it) }
        val ratio = hits.toDouble() / keywords.size
        return when {
            ratio >= 0.6 -> TREATMENT_MAX
            ratio >= 0.35 -> 11
            ratio >= 0.15 -> 8
            else -> 4
        }
    }
}
