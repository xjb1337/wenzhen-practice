package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.ClinicalRequestType
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageRole

/** 内置病例的离线教学路径，不向任何网络服务发送内容。 */
object OfflinePracticeService {
    fun patientReply(medicalCase: MedicalCase, messages: List<ChatMessage>): String {
        val question = messages.lastOrNull { it.role == MessageRole.DOCTOR }?.content.orEmpty()
        val matched = when {
            listOf("多久", "什么时候", "几天", "多长").any(question::contains) -> medicalCase.presentIllness
            listOf("既往", "以前", "病史", "慢性病", "手术", "过敏", "用药").any(question::contains) -> medicalCase.pastHistory
            listOf("吸烟", "抽烟", "饮酒", "喝酒", "职业", "工作").any(question::contains) -> medicalCase.personalHistory
            listOf("家族", "父母", "家里人", "家人").any(question::contains) -> medicalCase.familyHistory
            listOf("月经", "例假", "怀孕", "生育").any(question::contains) -> medicalCase.maritalHistory
            else -> "我能想到的就是${medicalCase.chiefComplaint}，其他情况请您具体问我。"
        }
        return matched.ifBlank { medicalCase.presentIllness }
    }

    fun clinicalResult(medicalCase: MedicalCase, requestType: ClinicalRequestType): String =
        when (requestType) {
            ClinicalRequestType.PHYSICAL_EXAM -> medicalCase.physicalExam
            ClinicalRequestType.LAB_OR_IMAGING -> medicalCase.auxiliaryExams
        }

    fun score(
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        initialDiagnosis: String,
        treatmentAnswer: String,
        encounterType: EncounterType,
        diagnosisBasis: String = "",
    ): ScoreResult {
        val rule = RuleScoreCalculator.compute(
            medicalCase = medicalCase,
            messages = messages,
            initialDiagnosis = initialDiagnosis,
            diagnosisBasis = diagnosisBasis,
            treatmentAnswer = treatmentAnswer,
        )
        val scopeNote = if (encounterType == EncounterType.INPATIENT) "住院问诊还应补充系统回顾、个人史、婚育史和家族史。" else ""
        return ScoreResult(
            totalScore = rule.total,
            coverageScore = rule.coverage,
            techniqueScore = rule.technique,
            communicationScore = rule.communication,
            diagnosisScore = rule.diagnosis,
            treatmentScore = rule.treatment,
            coveredPoints = rule.coveredQuestions,
            missedQuestions = rule.missedQuestions,
            strengths = listOf("已完成离线基础训练流程。"),
            improvements = listOfNotNull(scopeNote.takeIf { it.isNotBlank() }),
            correctDiagnosis = medicalCase.correctDiagnosis,
            diagnosisExplanation = "离线模式使用内置病例资料和本地规则评分，适合熟悉问诊流程。",
            evidence = rule.evidence,
        )
    }

    fun record(
        medicalCase: MedicalCase,
        initialDiagnosis: String,
        diagnosisBasis: String,
        treatmentAnswer: String,
        encounterType: EncounterType,
    ): String = buildString {
        appendLine("一般情况")
        appendLine("${medicalCase.patient.name}，${medicalCase.patient.gender}，${medicalCase.patient.age}，${medicalCase.patient.occupation}。")
        appendLine("主诉")
        appendLine(medicalCase.chiefComplaint)
        appendLine("现病史")
        appendLine(medicalCase.presentIllness)
        appendLine("既往史")
        appendLine(medicalCase.pastHistory)
        appendLine("体格检查")
        appendLine(medicalCase.physicalExam)
        if (encounterType == EncounterType.INPATIENT) {
            appendLine("系统回顾")
            appendLine(medicalCase.reviewOfSystems)
        }
        appendLine("初步诊断")
        appendLine(initialDiagnosis.ifBlank { "未填写" })
        appendLine("诊断依据")
        appendLine(diagnosisBasis.ifBlank { "未填写" })
        appendLine(if (encounterType == EncounterType.OUTPATIENT) "处理意见" else "诊疗计划")
        appendLine(treatmentAnswer.ifBlank { "未填写" })
    }

    private fun String.containsAny(first: String, others: List<String>): Boolean =
        contains(first, ignoreCase = true) || others.any { contains(it, ignoreCase = true) }
}

