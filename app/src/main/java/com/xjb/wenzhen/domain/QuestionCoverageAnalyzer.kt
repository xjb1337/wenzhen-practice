package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MedicalCase
import java.text.Normalizer

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
 * - 关键词须出现在提问或明确邀请患者描述的句子中。单独陈述、医嘱不提供证据。
 * - 医生单独发出要点原文仍算覆盖。
 *
 * 证据编号统一使用 [transcriptEntries] 的 1-based 下标（与评分转写同一口径），
 * 不再使用全量 messages 的 0-based 绝对下标。
 */
object QuestionCoverageAnalyzer {
    private val familySubject = Regex("家族|家里人|家里有|家庭中|家中有|家人|父母|爸妈|爸爸|妈妈|父亲|母亲|兄弟|姐妹|亲属|亲戚")
    private val familyHealth = Regex("家族史|病|癌|瘤|遗传|高血压|血糖|结石|哮喘|过敏|手术|住院|晕倒|猝死|早逝|去世|类似")
    private val personalHistory = Regex("病史|既往|过敏|用药|手术")
    private val radiation = Regex("放射|牵涉|串到|窜到|传到|跑到|延伸到|扩散到|往别处|(?:往|向).*(?:串|窜|传|延伸|扩散)")
    private val radiationLocations = setOf(
        "肩膀", "肩部", "左肩", "右肩", "后背", "背部", "下巴", "下颌", "会阴部", "耳前区", "耳颞部",
    )

    private data class DoctorTurn(val number: Int, val text: String, val clauses: List<InterviewClause>)

    private fun matchesQuestion(clause: InterviewClause, terms: QuestionMatchTerms): Boolean {
        val text = clause.text
        if (!clause.isQuestion) return false
        val asksAboutFamily = familySubject.containsMatchIn(text)
        val familyTopic = terms.verbatim.contains("家族史") || terms.verbatim.contains("家族中")
        if (familyTopic && (!asksAboutFamily || !familyHealth.containsMatchIn(text))) return false
        if (!familyTopic && asksAboutFamily && personalHistory.containsMatchIn(terms.verbatim)) return false

        return terms.keywords.any { keyword ->
            text.contains(keyword) && (keyword !in radiationLocations ||
                !terms.verbatim.contains("放射") || radiation.containsMatchIn(text))
        }
    }

    fun analyze(medicalCase: MedicalCase, messages: List<ChatMessage>): List<QuestionEvidence> {
        val transcript = transcriptEntries(messages)
        val usable = transcript.withIndex()
            .filter { isDoctorEvidence(it.index, it.value) }
            .map { (index, message) ->
                DoctorTurn(index + 1, normalizedInterviewText(message.content), interviewClauses(message.content))
            }
        return matchTerms(medicalCase).map { terms ->
            val indexes = usable
                .filter { turn ->
                    turn.text == terms.verbatim ||
                        turn.clauses.any { it.text == terms.verbatim || matchesQuestion(it, terms) }
                }
                .map { it.number }
            val covered = indexes.size >= terms.spec.minEvidence
            QuestionEvidence(
                question = terms.spec.text,
                covered = covered,
                messageIndexes = if (covered) indexes else emptyList(),
                questionId = terms.spec.id,
                importance = terms.spec.importance,
                isRedFlag = terms.spec.isRedFlag,
            )
        }
    }

    internal fun matchTerms(medicalCase: MedicalCase): List<QuestionMatchTerms> {
        val specs = KeyQuestionCatalog.of(medicalCase)
        val candidates = specs.map { spec ->
            (spec.keywords + spec.synonyms).map(::normalizedInterviewText).distinct()
        }
        val documentFrequency = candidates
            .flatMap { it.distinct() }
            .groupingBy { it }
            .eachCount()
        return specs.mapIndexed { index, spec ->
            val distinctive = candidates[index].filter { documentFrequency[it] == 1 }
            val keywords = distinctive.ifEmpty { spec.keywords.map(::normalizedInterviewText) }
                .sortedByDescending { it.length }
            QuestionMatchTerms(
                spec = spec,
                keywords = keywords,
                verbatim = normalizedInterviewText(spec.text),
            )
        }
    }
}

internal data class InterviewClause(val text: String, val isQuestion: Boolean)

private val interviewPunctuation = Regex("[\\s,。.;:、?!，；：！？]+$")
private val interviewClausePattern = Regex("""[^。.!?！？；;,，\r\n]+[?]?""")
private val questionWords = listOf(
    "是否", "有没有", "有无", "是不是", "能否", "能不能", "会不会", "要不要", "对不对",
    "什么", "怎么", "怎样", "哪里", "哪儿", "如何", "多久", "多长时间", "多少",
    "几天", "几小时", "几次", "几年", "几岁", "哪天",
    "请问", "请描述", "请告诉", "能说说", "能讲讲", "说说", "讲讲",
)
private val repeatedVerbQuestion = Regex("""([\u4e00-\u9fff])(?:不|没)\1""")
private val negativeConfirmation = Regex("""(?:没有|没|未曾|未出现|不曾|无).+吧$""")

private fun normalizedInterviewText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC).trim().lowercase().replace(interviewPunctuation, "")

internal fun interviewClauses(text: String): List<InterviewClause> =
    interviewClausePattern.findAll(Normalizer.normalize(text, Normalizer.Form.NFKC))
        .mapNotNull { match ->
            val value = match.value.trim().lowercase()
            val clause = value.replace(interviewPunctuation, "")
            if (clause.isBlank()) return@mapNotNull null
            val repeatsVerb = repeatedVerbQuestion.containsMatchIn(clause)
            val asks = value.endsWith('?') || clause.last() in "吗呢么" ||
                questionWords.any { clause.contains(it) } || repeatsVerb ||
                negativeConfirmation.containsMatchIn(clause) ||
                (clause.startsWith("有") && clause.endsWith("没") && clause.length > 2)
            val expanded = if (repeatsVerb) repeatedVerbQuestion.replace(clause, "$1") else clause
            InterviewClause(expanded, asks)
        }
        .toList()
