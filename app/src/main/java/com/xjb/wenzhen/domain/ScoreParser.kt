package com.xjb.wenzhen.domain

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class ScoreParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ScoreResult(
    val totalScore: Int,
    val coverageScore: Int,
    val techniqueScore: Int,
    val communicationScore: Int,
    val diagnosisScore: Int,
    val coveredPoints: List<String>,
    val missedQuestions: List<String>,
    val strengths: List<String>,
    val improvements: List<String>,
    val correctDiagnosis: String,
    val diagnosisExplanation: String,
) {
    val gradeLabel: String
        get() = when {
            totalScore >= 90 -> "优秀"
            totalScore >= 80 -> "良好"
            totalScore >= 70 -> "合格"
            totalScore >= 60 -> "有待提高"
            else -> "需要加强"
        }
}

object ScoreParser {
    fun parse(raw: String): ScoreResult {
        val json = extractJsonObject(raw)
        val breakdown = json.optJSONObject("breakdown")
            ?: json.optJSONObject("分项")
            ?: json

        val coverage = findScore(
            breakdown,
            "questioningCoverage", "coverage", "问诊要点覆盖", "问诊要点覆盖分数",
        ) ?: findScore(json, "问诊要点覆盖分数")
        val technique = findScore(
            breakdown,
            "techniqueAndOrder", "technique", "问诊技巧与顺序", "问诊技巧与顺序分数",
        ) ?: findScore(json, "问诊技巧与顺序分数")
        val communication = findScore(
            breakdown,
            "communication", "医患沟通", "医患沟通分数",
        ) ?: findScore(json, "医患沟通分数")
        val diagnosis = findScore(
            breakdown,
            "diagnosisAccuracy", "diagnosis", "诊断准确", "诊断准确分数",
        ) ?: findScore(json, "诊断准确分数")

        val explicitTotal = findScore(json, "totalScore", "total", "总分")
        if (explicitTotal == null && listOf(coverage, technique, communication, diagnosis).all { it == null }) {
            throw ScoreParseException("评分 JSON 缺少总分和分项分数，请重试。")
        }

        val coverageValue = (coverage ?: 0).coerceIn(0, 40)
        val techniqueValue = (technique ?: 0).coerceIn(0, 20)
        val communicationValue = (communication ?: 0).coerceIn(0, 20)
        val diagnosisValue = (diagnosis ?: 0).coerceIn(0, 20)
        val computedTotal = coverageValue + techniqueValue + communicationValue + diagnosisValue
        val total = (explicitTotal ?: computedTotal).coerceIn(0, 100)

        return ScoreResult(
            totalScore = total,
            coverageScore = coverageValue,
            techniqueScore = techniqueValue,
            communicationScore = communicationValue,
            diagnosisScore = diagnosisValue,
            coveredPoints = readStringList(json, "coveredPoints", "coveredKeyPoints", "已覆盖要点"),
            missedQuestions = readStringList(
                json,
                "missedQuestions", "missedPoints", "遗漏问题", "遗漏要点",
            ),
            strengths = readStringList(json, "strengths", "优点"),
            improvements = readStringList(json, "improvements", "suggestions", "改进建议"),
            correctDiagnosis = readText(json, "correctDiagnosis", "正确诊断"),
            diagnosisExplanation = readText(
                json,
                "diagnosisExplanation", "correctDiagnosisExplanation", "diagnosisAnalysis",
                "正确诊断解析", "诊断解析",
            ),
        )
    }

    fun encode(score: ScoreResult): String = JSONObject().apply {
        put("totalScore", score.totalScore)
        put("breakdown", JSONObject().apply {
            put("questioningCoverage", score.coverageScore)
            put("techniqueAndOrder", score.techniqueScore)
            put("communication", score.communicationScore)
            put("diagnosisAccuracy", score.diagnosisScore)
        })
        put("coveredPoints", JSONArray(score.coveredPoints))
        put("missedQuestions", JSONArray(score.missedQuestions))
        put("strengths", JSONArray(score.strengths))
        put("improvements", JSONArray(score.improvements))
        put("correctDiagnosis", score.correctDiagnosis)
        put("diagnosisExplanation", score.diagnosisExplanation)
    }.toString()

    private fun extractJsonObject(raw: String): JSONObject {
        val cleaned = raw.lineSequence()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw ScoreParseException("未能从 AI 回复中解析出评分 JSON，请重试。")
        }
        return try {
            JSONObject(cleaned.substring(start, end + 1))
        } catch (error: Exception) {
            throw ScoreParseException("评分 JSON 格式损坏，请重试。", error)
        }
    }

    private fun findScore(json: JSONObject, vararg keys: String): Int? {
        keys.forEach { key ->
            if (!json.has(key) || json.isNull(key)) return@forEach
            val value = json.opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.trim().removeSuffix("分").toDoubleOrNull()
                else -> null
            }
            if (parsed != null && !parsed.isNaN()) return parsed.roundToInt()
        }
        return null
    }

    private fun readText(json: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = json.opt(key)
            if (value != null && value != JSONObject.NULL && value !is JSONArray) {
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
                        ?.removePrefix("-")?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }
            }
            if (value is String && value.isNotBlank()) {
                return value.split(Regex("[\\n；;、]"))
                    .map { it.trim().removePrefix("-").trim() }
                    .filter { it.isNotEmpty() }
            }
        }
        return emptyList()
    }
}
