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

/** 单个要点用于匹配的词：[keywords] 是关键词和同义词，[verbatim] 是要点原文（逐字照抄也算覆盖）。 */
internal data class QuestionMatchTerms(
    val spec: KeyQuestionSpec,
    val keywords: List<String>,
    val verbatim: String,
)

/**
 * 用病例自己的 keyQuestions 做一层保守的本地校验，纠正模型把明显问到的问题列为遗漏。
 * 它不替代教师或 LLM 的语义判断，只作为评分结果的安全下限。
 *
 * 匹配规则（保守）：
 * - 只使用 [KeyQuestionCatalog] 给出的关键词和同义词，不再生成任意 2 字切片；
 * - 同一病例里被 2 个及以上要点共用的词（例如“胸痛”同时出现在起病时间、部位、既往类似发作中）
 *   不能单独证明问到了其中任何一个，匹配时剔除；剔除后某个要点没有可用词时，退回它自己的关键词；
 * - 医生逐字照抄要点原文仍算覆盖。
 *
 * 证据编号统一使用 [PromptBuilder.transcriptEntries] 的 1-based 下标（与评分转写同一口径），
 * 不再使用全量 messages 的 0-based 绝对下标。
 */
object QuestionCoverageAnalyzer {
    private val trailingPunctuation = Regex("[\\s，,。；;：:、？?！!]+$")

    fun analyze(medicalCase: MedicalCase, messages: List<ChatMessage>): List<QuestionEvidence> {
        val transcript = transcriptEntries(messages)
        val usable = transcript.withIndex()
            .filter { it.value.role == MessageRole.DOCTOR }
            .map { IndexedValue(it.index, it.value.content.lowercase()) }
        return matchTerms(medicalCase).map { terms ->
            val candidates = (terms.keywords + terms.verbatim).filter { it.isNotEmpty() }
            val indexes = usable
                .filter { (_, content) -> candidates.any { content.contains(it) } }
                .map { it.index + 1 }
            QuestionEvidence(
                question = terms.spec.text,
                covered = indexes.isNotEmpty(),
                messageIndexes = indexes,
                questionId = terms.spec.id,
                importance = terms.spec.importance,
                isRedFlag = terms.spec.isRedFlag,
            )
        }
    }

    internal fun matchTerms(medicalCase: MedicalCase): List<QuestionMatchTerms> {
        val specs = KeyQuestionCatalog.of(medicalCase)
        val candidates = specs.map { spec ->
            (spec.keywords + spec.synonyms).map { it.lowercase() }.distinct()
        }
        val documentFrequency = candidates
            .flatMap { it.distinct() }
            .groupingBy { it }
            .eachCount()
        return specs.mapIndexed { index, spec ->
            val distinctive = candidates[index].filter { documentFrequency[it] == 1 }
            val keywords = distinctive.ifEmpty { spec.keywords.map { it.lowercase() } }
                .sortedByDescending { it.length }
            QuestionMatchTerms(
                spec = spec,
                keywords = keywords,
                verbatim = spec.text.trim().lowercase().replace(trailingPunctuation, ""),
            )
        }
    }
}
