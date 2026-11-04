package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageRole

data class QuestionEvidence(
    val question: String,
    val covered: Boolean,
    val messageIndexes: List<Int>,
    val questionId: String = "",
    val importance: Int = 2,
    val isRedFlag: Boolean = false,
)

/**
 * 用病例自己的 keyQuestions 做一层保守的本地校验，纠正模型把明显问到的问题列为遗漏。
 * 它不替代教师或 LLM 的语义判断，只作为评分结果的安全下限。
 *
 * 证据编号统一使用 [PromptBuilder.transcriptEntries] 的 1-based 下标（与评分转写同一口径），
 * 不再使用全量 messages 的 0-based 绝对下标。
 */
object QuestionCoverageAnalyzer {
    fun analyze(medicalCase: MedicalCase, messages: List<ChatMessage>): List<QuestionEvidence> {
        val transcript = transcriptEntries(messages)
        val usable = transcript.withIndex()
            .filter { it.value.role == MessageRole.DOCTOR }
        val text = usable.joinToString("\n") { it.value.content.lowercase() }
        return KeyQuestionCatalog.of(medicalCase).map { spec ->
            val question = spec.text
            val terms = (termsFor(question) + spec.synonyms.map { it.lowercase() }).distinct()
                .sortedByDescending { it.length }
            val covered = terms.any { text.contains(it) }
            val indexes = if (covered) {
                usable.filter { (_, message) -> terms.any { message.content.lowercase().contains(it) } }
                    .map { it.index + 1 }
            } else {
                emptyList()
            }
            QuestionEvidence(
                question = question,
                covered = covered,
                messageIndexes = indexes,
                questionId = spec.id,
                importance = spec.importance,
                isRedFlag = spec.isRedFlag,
            )
        }
    }

    private fun termsFor(question: String): List<String> {
        val normalized = question.lowercase()
        val aliases = aliasTerms.entries
            .filter { normalized.contains(it.key) }
            .flatMap { it.value }
        val chunks = Regex("[\\u4e00-\\u9fff]{2,}")
            .findAll(normalized)
            .flatMap { match ->
                val value = match.value
                sequence {
                    yield(value)
                    if (value.length >= 2) {
                        for (index in 0..value.length - 2) yield(value.substring(index, index + 2))
                    }
                }
            }
            .filter { it !in stopWords && it.length >= 2 }
            .toList()
        return (aliases + chunks).distinct().sortedByDescending { it.length }
    }

    private val stopWords = setOf(
        "询问", "了解", "患者", "是否", "有没有", "有无", "相关", "情况", "问题",
        "症状", "信息", "以及", "或者", "主要", "其他", "进行", "详细",
    )

    private val aliasTerms = mapOf(
        "睡眠" to listOf("睡眠", "睡得", "睡觉"),
        "家族" to listOf("家族", "家里人", "父母", "爸爸", "妈妈", "亲属"),
        "吸烟" to listOf("吸烟", "抽烟", "烟"),
        "饮酒" to listOf("饮酒", "喝酒", "酒"),
        "月经" to listOf("月经", "例假", "大姨妈", "末次月经"),
        "过敏" to listOf("过敏", "药物过敏"),
        "用药" to listOf("用药", "吃药", "服药", "药物"),
    )
}

