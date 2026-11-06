package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.MedicalCase

data class CarePlanSection(
    val label: String,
    val content: String,
)

data class ReferenceCarePlan(
    val diagnosis: String,
    val differentialDiagnoses: List<String>,
    val encounterType: EncounterType,
    val diagnosisBasis: List<String>,
    val necessaryExams: List<String>,
    val treatmentSections: List<CarePlanSection>,
    val precautions: List<String>,
) {
    val encounterLabel: String
        get() = if (encounterType == EncounterType.OUTPATIENT) "门诊" else "住院"
}

/**
 * 仅根据病例自带字段整理参考诊疗方案，不调用模型、不补充病例之外的信息。
 * 这样同一病例在在线/离线、首次结果/历史详情中的方案保持一致。
 */
object ReferenceCarePlanBuilder {
    private const val MISSING_TREATMENT = "病例资料未提供明确的治疗与用药信息，请结合带教老师意见复盘。"
    private const val MISSING_PRECAUTIONS = "病例资料未提供明确的随访安排，请结合带教老师意见确认。"

    fun build(
        medicalCase: MedicalCase,
        encounterType: EncounterType,
    ): ReferenceCarePlan {
        val sections = parseTreatmentSections(medicalCase.treatmentPlan)
        val treatmentSections = sections
            .filterNot { isFollowUpSection(it.label) }
            .ifEmpty {
                listOf(CarePlanSection(label = "诊疗方案", content = MISSING_TREATMENT))
            }
        val precautions = sections
            .filter { isFollowUpSection(it.label) }
            .map { "${it.label}：${it.content}" }
            .ifEmpty { listOf(MISSING_PRECAUTIONS) }

        return ReferenceCarePlan(
            diagnosis = medicalCase.correctDiagnosis.trim(),
            differentialDiagnoses = medicalCase.differentialDiagnoses
                .map(String::trim)
                .filter(String::isNotEmpty),
            encounterType = encounterType,
            diagnosisBasis = buildDiagnosisBasis(medicalCase, encounterType),
            necessaryExams = splitExamItems(medicalCase.auxiliaryExams),
            treatmentSections = treatmentSections,
            precautions = precautions,
        )
    }

    private fun buildDiagnosisBasis(
        medicalCase: MedicalCase,
        encounterType: EncounterType,
    ): List<String> = buildList {
        if (encounterType == EncounterType.OUTPATIENT) {
            addSection("主诉", medicalCase.chiefComplaint)
            addSection("关键病史", medicalCase.presentIllness)
            addSection("体格检查", medicalCase.physicalExam)
        } else {
            addSection("主诉", medicalCase.chiefComplaint)
            addSection("现病史", medicalCase.presentIllness)
            addSection("既往史", medicalCase.pastHistory)
            addSection("个人史", medicalCase.personalHistory)
            addSection("体格检查", medicalCase.physicalExam)
            addSection("系统回顾", medicalCase.reviewOfSystems)
        }
    }

    private fun MutableList<String>.addSection(label: String, content: String) {
        if (content.isNotBlank()) add("$label：${content.trim()}")
    }

    private fun splitExamItems(raw: String): List<String> =
        raw.split(Regex("[；;\\n]"))
            .map(String::trim)
            .filter(String::isNotEmpty)
            .ifEmpty { listOf("病例资料未提供检查结果。") }

    internal fun parseTreatmentSections(raw: String): List<CarePlanSection> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()

        val matches = SECTION_PATTERN.findAll(text).toList()
        if (matches.isEmpty()) {
            return text.lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { CarePlanSection(label = "诊疗方案", content = it) }
                .toList()
        }

        return buildList {
            matches.forEachIndexed { index, match ->
                val contentStart = match.range.last + 1
                val contentEnd = matches.getOrNull(index + 1)?.range?.first ?: text.length
                val content = text.substring(contentStart, contentEnd)
                    .trim()
                    .trimEnd('；', ';')
                    .trim()
                if (content.isNotEmpty()) {
                    add(CarePlanSection(label = match.groupValues[1], content = content))
                }
            }
        }
    }

    private fun isFollowUpSection(label: String): Boolean =
        label.contains("随访") || label.contains("复诊") || label.contains("注意事项")

    private val SECTION_PATTERN = Regex(
        "(?m)^\\s*(治疗原则|药物|处理意见|处理|诊疗计划|手术|其他治疗|随访与注意事项|注意事项|随访|复诊)\\s*[：:]",
    )
}
