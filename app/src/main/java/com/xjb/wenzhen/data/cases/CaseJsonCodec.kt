package com.xjb.wenzhen.data.cases

import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.PatientInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CaseParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object CaseJsonCodec {
    fun encode(medicalCase: MedicalCase): String = JSONObject().apply {
        put("id", medicalCase.id)
        put("title", medicalCase.title)
        put("department", medicalCase.department)
        put("difficulty", medicalCase.difficulty.label)
        put("patient", JSONObject().apply {
            put("name", medicalCase.patient.name)
            put("age", medicalCase.patient.age)
            put("gender", medicalCase.patient.gender)
            put("occupation", medicalCase.patient.occupation)
        })
        put("openingLine", medicalCase.openingLine)
        put("chiefComplaint", medicalCase.chiefComplaint)
        put("presentIllness", medicalCase.presentIllness)
        put("pastHistory", medicalCase.pastHistory)
        put("personalHistory", medicalCase.personalHistory)
        put("maritalHistory", medicalCase.maritalHistory)
        put("familyHistory", medicalCase.familyHistory)
        put("reviewOfSystems", medicalCase.reviewOfSystems)
        put("physicalExam", medicalCase.physicalExam)
        put("auxiliaryExams", medicalCase.auxiliaryExams)
        put("keyQuestions", JSONArray(medicalCase.keyQuestions))
        put("correctDiagnosis", medicalCase.correctDiagnosis)
        put("differentialDiagnoses", JSONArray(medicalCase.differentialDiagnoses))
        put("treatmentPlan", medicalCase.treatmentPlan)
        put("isAiGenerated", medicalCase.isAiGenerated)
    }.toString()

    fun decode(raw: String): MedicalCase {
        val json = extractJsonObject(raw)
        return parseObject(json)
    }

    fun parseGenerated(raw: String, department: String, difficulty: Difficulty): MedicalCase {
        val json = extractJsonObject(raw)
        val parsed = parseObject(json)
        return parsed.copy(
            id = "ai-${UUID.randomUUID()}",
            department = department,
            difficulty = difficulty,
            isAiGenerated = true,
        )
    }

    private fun parseObject(json: JSONObject): MedicalCase {
        val patientJson = json.optJSONObject("patient") ?: json.optJSONObject("患者信息")
        val name = patientJson?.readText("name", "姓名") ?: ""
        val age = patientJson?.readText("age", "年龄") ?: ""
        val gender = patientJson?.readText("gender", "性别") ?: ""
        val occupation = patientJson?.readText("occupation", "职业") ?: ""

        val keyQuestions = readStringList(json, "keyQuestions", "问诊要点")
            .distinct()
        if (keyQuestions.size !in 10..15) {
            throw CaseParseException("AI 返回的问诊要点数量不正确，需要10至15条，请重试。")
        }

        val differentialDiagnoses = readStringList(json, "differentialDiagnoses", "鉴别诊断")

        val medicalCase = MedicalCase(
            id = json.readText("id").ifBlank { "case-${UUID.randomUUID()}" },
            title = json.readText("title", "标题"),
            department = json.readText("department", "科室"),
            difficulty = Difficulty.from(json.readText("difficulty", "难度")),
            patient = PatientInfo(
                name = name,
                age = normalizeAge(age),
                gender = gender,
                occupation = occupation.ifBlank { "未提供" },
            ),
            openingLine = json.readText("openingLine", "开场白", "主诉口语"),
            chiefComplaint = json.readText("chiefComplaint", "主诉"),
            presentIllness = json.readText("presentIllness", "现病史"),
            pastHistory = json.readText("pastHistory", "既往史"),
            personalHistory = json.readText("personalHistory", "个人史"),
            maritalHistory = json.readText("maritalHistory", "婚育史"),
            familyHistory = json.readText("familyHistory", "家族史"),
            reviewOfSystems = json.readText("reviewOfSystems", "系统回顾"),
            physicalExam = json.readText("physicalExam", "体格检查", "查体"),
            auxiliaryExams = json.readText("auxiliaryExams", "辅助检查"),
            keyQuestions = keyQuestions,
            correctDiagnosis = json.readText("correctDiagnosis", "正确诊断", "诊断"),
            differentialDiagnoses = differentialDiagnoses,
            isAiGenerated = json.optBoolean("isAiGenerated", false),
            treatmentPlan = json.readText("treatmentPlan", "治疗方案"),
        )

        val missing = buildList {
            if (medicalCase.title.isBlank()) add("病例标题")
            if (medicalCase.patient.name.isBlank()) add("患者姓名")
            if (medicalCase.patient.age.isBlank()) add("患者年龄")
            if (medicalCase.patient.gender.isBlank()) add("患者性别")
            if (medicalCase.openingLine.isBlank()) add("开场白")
            if (medicalCase.chiefComplaint.isBlank()) add("主诉")
            if (medicalCase.presentIllness.isBlank()) add("现病史")
            if (medicalCase.pastHistory.isBlank()) add("既往史")
            if (medicalCase.personalHistory.isBlank()) add("个人史")
            if (medicalCase.maritalHistory.isBlank()) add("婚育史")
            if (medicalCase.familyHistory.isBlank()) add("家族史")
            if (medicalCase.reviewOfSystems.isBlank()) add("系统回顾")
            if (medicalCase.physicalExam.isBlank()) add("体格检查")
            if (medicalCase.auxiliaryExams.isBlank()) add("辅助检查")
            if (medicalCase.correctDiagnosis.isBlank()) add("正确诊断")
            if (medicalCase.differentialDiagnoses.isEmpty()) add("鉴别诊断")
        }
        if (missing.isNotEmpty()) {
            throw CaseParseException("AI 返回的病例缺少${missing.joinToString("、")}，请重试。")
        }
        return medicalCase
    }

    private fun extractJsonObject(raw: String): JSONObject {
        val withoutFences = raw.lineSequence()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
        val start = withoutFences.indexOf('{')
        val end = withoutFences.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw CaseParseException("未能从 AI 回复中解析出病例 JSON，请重试。")
        }
        return try {
            JSONObject(withoutFences.substring(start, end + 1))
        } catch (error: Exception) {
            throw CaseParseException("AI 返回的病例 JSON 格式不正确，请重试。", error)
        }
    }

    private fun JSONObject.readText(vararg keys: String): String {
        keys.forEach { key ->
            val value = opt(key)
            if (value != null && value != JSONObject.NULL) {
                val text = value.toString().trim()
                if (text.isNotEmpty()) return text
            }
        }
        return ""
    }

    private fun readStringList(json: JSONObject, vararg keys: String): List<String> {
        keys.forEach { key ->
            val value = json.opt(key)
            if (value is JSONArray) {
                return (0 until value.length()).mapNotNull { index ->
                    value.opt(index)?.takeIf { it != JSONObject.NULL }?.toString()?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }
            }
            if (value is String && value.isNotBlank()) {
                return value.split(Regex("[\\n；;、]"))
                    .map { it.trim().removePrefix("-") }
                    .filter { it.isNotEmpty() }
            }
        }
        return emptyList()
    }

    private fun normalizeAge(value: String): String {
        val age = value.trim()
        if (age.isEmpty() || age.endsWith("岁") || age.contains("月") || age.contains("天")) return age
        return "${age}岁"
    }
}
