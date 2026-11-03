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

enum class PatientPersonality(
    val label: String,
    val description: String,
    val promptInstruction: String,
) {
    COOPERATIVE(
        label = "配合",
        description = "表达清楚，愿意提供信息",
        promptInstruction = "你态度配合、表达清楚，会如实回答医生问到的问题，但仍不会主动补充未问内容。",
    ),
    ANXIOUS(
        label = "焦虑",
        description = "担心病情，语气紧张",
        promptInstruction = "你比较焦虑，常担心病情严重，会反复确认“要不要紧”；回答可带紧张语气，但不能主动透露诊断或检查结论。",
    ),
    UNCLEAR(
        label = "表达不清",
        description = "描述含糊，需要医生追问",
        promptInstruction = "你文化程度一般，描述症状时较含糊、常用“就是不舒服”“说不上来”等表达；只有医生具体追问时才逐步说清。",
    ),
    ELDERLY(
        label = "老年人（听力差、啰嗦）",
        description = "听力下降，容易跑题",
        promptInstruction = "你是老年人，听力较差，可能需要医生重复或放大声音；回答容易绕到家常事上，但被明确追问时仍会提供准确病史。",
    ),
    IMPATIENT(
        label = "不耐烦",
        description = "希望尽快结束，回答简短",
        promptInstruction = "你性格急躁、赶时间，回答尽量简短，偶尔催促医生；不要主动说出完整病史，也不能泄露诊断。",
    ),
    EVASIVE(
        label = "隐瞒（需追问才说）",
        description = "对敏感信息有所保留",
        promptInstruction = "你对吸烟、饮酒、性生活、用药或家族史等敏感信息有所隐瞒，首次被问时轻描淡写或说“没有”；医生具体、温和地追问后，才逐步说出部分实情。",
    ),
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
    const val RESPIRATORY = "呼吸内科"
    const val GASTROENTEROLOGY = "消化内科"
    const val NEUROLOGY = "神经内科"
    const val ENDOCRINOLOGY = "内分泌科"
    const val NEPHROLOGY = "肾内科"
    const val HEMATOLOGY = "血液科"
    const val RHEUMATOLOGY = "风湿免疫科"
    const val INFECTIOUS_DISEASE = "感染科"
    const val GENERAL_SURGERY = "普外科"
    const val ORTHOPEDICS = "骨科"
    const val UROLOGY = "泌尿外科"
    const val OBSTETRICS_GYNECOLOGY = "妇产科"
    const val GYNECOLOGY = "妇产科"
    const val PEDIATRICS = "儿科"
    const val PSYCHIATRY = "精神心理科"
    const val ENT = "耳鼻喉科"
    const val OPHTHALMOLOGY = "眼科"
    const val DERMATOLOGY = "皮肤科"

    val all: List<String> = listOf(
        ALL,
        CARDIOLOGY,
        RESPIRATORY,
        GASTROENTEROLOGY,
        NEUROLOGY,
        ENDOCRINOLOGY,
        NEPHROLOGY,
        HEMATOLOGY,
        RHEUMATOLOGY,
        INFECTIOUS_DISEASE,
        GENERAL_SURGERY,
        ORTHOPEDICS,
        UROLOGY,
        OBSTETRICS_GYNECOLOGY,
        PEDIATRICS,
        PSYCHIATRY,
        ENT,
        OPHTHALMOLOGY,
        DERMATOLOGY,
    )
}
