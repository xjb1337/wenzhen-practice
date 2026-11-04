package com.xjb.wenzhen.data.model

data class LlmMessage(
    val role: String,
    val content: String,
)

data class LlmConfig(
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
)

enum class ThinkingLevel(val label: String, val apiValue: String?) {
    OFF("关", null),
    LOW("低", "low"),
    HIGH("高", "high"),
    MAX("最高", "max");

    companion object {
        fun from(value: String?): ThinkingLevel = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true) || it.label == value?.trim()
        } ?: OFF
    }
}

enum class EncounterType(val label: String) {
    OUTPATIENT("门诊病人"),
    INPATIENT("住院病人");

    companion object {
        fun from(value: String?): EncounterType = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true) || it.label == value?.trim()
        } ?: OUTPATIENT
    }
}

enum class RecordType { OUTPATIENT, INPATIENT }
