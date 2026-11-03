package com.xjb.wenzhen.data.model

enum class Difficulty(val label: String) {
    EASY("简单"),
    MEDIUM("中等"),
    HARD("困难");

    companion object {
        fun from(value: String): Difficulty =
            values().firstOrNull { it.label == value.trim() || it.name.equals(value.trim(), true) }
                ?: MEDIUM
    }
}

data class PatientInfo(
    val name: String,
    val age: String,
    val gender: String,
    val occupation: String,
)

data class MedicalCase(
    val id: String,
    val title: String,
    val department: String,
    val difficulty: Difficulty,
    val patient: PatientInfo,
    val openingLine: String,
    val chiefComplaint: String,
    val presentIllness: String,
    val pastHistory: String,
    val personalHistory: String,
    val maritalHistory: String,
    val familyHistory: String,
    val reviewOfSystems: String,
    val physicalExam: String,
    val auxiliaryExams: String,
    val keyQuestions: List<String>,
    val correctDiagnosis: String,
    val differentialDiagnoses: List<String>,
    val isAiGenerated: Boolean = false,
) {
    val patientSummary: String
        get() = "${patient.name} · ${patient.gender} · ${patient.age} · ${patient.occupation}"
}

object Departments {
    const val ALL = "全部"
    const val CARDIOLOGY = "心内科"
    const val GASTROENTEROLOGY = "消化内科"
    const val RESPIRATORY = "呼吸内科"
    const val NEUROLOGY = "神经内科"
    const val ENDOCRINOLOGY = "内分泌科"
    const val UROLOGY = "泌尿外科"
    const val PEDIATRICS = "儿科"
    const val GYNECOLOGY = "妇科"

    val all: List<String> = listOf(
        ALL,
        CARDIOLOGY,
        GASTROENTEROLOGY,
        RESPIRATORY,
        NEUROLOGY,
        ENDOCRINOLOGY,
        UROLOGY,
        PEDIATRICS,
        GYNECOLOGY,
    )
}
