package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MedicalCase
import kotlin.math.ceil

/**
 * 题面规则分：覆盖、红旗、查体/检查、诊断匹配、治疗匹配全部由本地规则计算。
 * AI 只负责生成点评、优点和改进建议，不再决定总分。
 */
data class RuleScore(
    val coverage: Int,
    val technique: Int,
    val communication: Int,
    val diagnosis: Int,
    val treatment: Int,
    val evidence: List<ScoreEvidence>,
    val coveredQuestions: List<String>,
    val missedQuestions: List<String>,
    /** 规则分的补充说明（例如病例缺少参考治疗方案），会追加到点评的改进建议里。 */
    val notes: List<String> = emptyList(),
    val diagnosisClues: List<DiagnosisClue> = emptyList(),
) {
    val total: Int get() = (coverage + technique + communication + diagnosis + treatment).coerceIn(0, 100)
}

object RuleScoreCalculator {
    private const val COVERAGE_MAX = 35
    private const val TECHNIQUE_MAX = 15
    private const val COMMUNICATION_MAX = 15
    private const val DIAGNOSIS_MAX = 20
    private const val TREATMENT_MAX = 15

    /** 初步诊断归一化后至少 2 个字才参与名称匹配，单字（如“急”）不得分。 */
    internal const val MIN_DIAGNOSIS_LENGTH = 2

    /** 部分匹配时，重叠部分至少占标准诊断（或其组成部分）的 60%。 */
    private const val DIAGNOSIS_CLOSE_RATIO = 0.6

    /** 有效提问：去掉标点后至少 5 个字，过滤“嗯？”“还有吗？”这类空话。 */
    private const val SUBSTANTIVE_MIN_LENGTH = 5

    internal const val NOTE_NO_TREATMENT_PLAN = "该病例没有参考治疗方案，治疗方案分暂记 0 分。"
    internal const val NOTE_DIAGNOSIS_TOO_SHORT =
        "初步诊断过短或过于笼统（例如只写一个字、“急性”或“待查”），诊断名称不计分。"

    private val openQuestionMarkers = listOf("什么", "怎么", "怎样", "哪里", "哪儿", "如何", "描述", "说说", "讲讲", "具体")

    /** 只统计明确的安抚/共情短语；“需要”“建议”这类医嘱用词不再算沟通加分。 */
    private val empathyPhrases = listOf(
        "别担心", "不要担心", "不用担心", "请放心", "您放心", "理解您", "我理解", "能理解",
        "别紧张", "不要紧张", "慢慢说", "辛苦了", "谢谢您", "感谢您",
    )

    fun compute(
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        initialDiagnosis: String = "",
        diagnosisBasis: String = "",
        treatmentAnswer: String = "",
    ): RuleScore {
        val analysis = QuestionCoverageAnalyzer.analyze(medicalCase, messages)
        val weightTotal = analysis.sumOf { it.importance.coerceAtLeast(1) }
        val coveredWeight = analysis.filter { it.covered }.sumOf { it.importance.coerceAtLeast(1) }
        val coverage = if (weightTotal == 0) 0 else (coveredWeight * COVERAGE_MAX + weightTotal / 2) / weightTotal

        val coveredQuestions = analysis.filter { it.covered }.map { it.question }
        val missedQuestions = analysis.filterNot { it.covered }.map { it.question }
        val evidence = analysis.map { item ->
            ScoreEvidence(question = item.question, messageIndexes = item.messageIndexes)
        }

        // 与证据编号同一口径：transcriptEntries 的 1-based 下标，只含已送达的医生消息。
        val doctorMessages = transcriptEntries(messages).withIndex()
            .filter { isDoctorEvidence(it.index, it.value) }
            .map { DoctorMessage(index = it.index + 1, content = it.value.content) }
        val relevantIndexes = analysis.flatMap { it.messageIndexes }.toSet()
        val dialogue = dialogueStats(doctorMessages, relevantIndexes)

        val notes = mutableListOf<String>()
        val diagnosis = diagnosisNamePoints(medicalCase, initialDiagnosis, notes)
        val diagnosisClues = if (diagnosis > 0) DiagnosisBasisScoring.find(medicalCase, messages, diagnosisBasis) else emptyList()
        if (diagnosis > 0 && diagnosisBasis.isNotBlank() && diagnosisClues.isEmpty()) {
            notes += "诊断依据暂未和本次问诊及病例资料核对上，请写出具体的病史或检查所见。"
        }
        val treatment = treatmentScore(medicalCase, treatmentAnswer, notes)

        return RuleScore(
            coverage = coverage.coerceIn(0, COVERAGE_MAX),
            technique = techniqueScore(dialogue),
            communication = communicationScore(dialogue),
            diagnosis = (diagnosis + 2 * diagnosisClues.size).coerceIn(0, DIAGNOSIS_MAX),
            treatment = treatment,
            evidence = evidence,
            coveredQuestions = coveredQuestions,
            missedQuestions = missedQuestions,
            notes = notes,
            diagnosisClues = diagnosisClues,
        )
    }

    // ---------------------------------------------------------------- 问诊技巧与沟通

    private data class DoctorMessage(val index: Int, val content: String) {
        /** 去掉标点和空白后的正文，用于判断长度和重复。 */
        val normalized: String = content.lowercase().filter { it.isLetterOrDigit() }
        val questions = interviewClauses(content).filter { it.isQuestion }
    }

    private data class DialogueStats(
        val total: Int,
        val questionCount: Int,
        val relevantCount: Int,
        val openQuestionCount: Int,
        val substantiveCount: Int,
        val duplicateCount: Int,
        val averageLength: Int,
        val empathyCount: Int,
    )

    private fun dialogueStats(messages: List<DoctorMessage>, relevantIndexes: Set<Int>): DialogueStats {
        val distinct = messages.distinctBy { it.normalized }
        // 有效提问：命中了某个问诊要点，或者是去重后足够长的提问；“嗯？”“还有吗？”不算。
        val substantive = distinct.count { message ->
            message.index in relevantIndexes ||
                (message.questions.any { clause -> clause.text.count(Char::isLetterOrDigit) >= SUBSTANTIVE_MIN_LENGTH })
        }
        val text = messages.joinToString("\n") { it.content }
        return DialogueStats(
            total = messages.size,
            questionCount = messages.count { it.questions.isNotEmpty() },
            relevantCount = messages.count { it.index in relevantIndexes },
            openQuestionCount = distinct.count { message ->
                message.questions.any { clause -> openQuestionMarkers.any { clause.text.contains(it) } }
            },
            substantiveCount = substantive,
            duplicateCount = messages.size - distinct.size,
            averageLength = if (distinct.isEmpty()) 0 else distinct.sumOf { it.normalized.length } / distinct.size,
            empathyCount = empathyPhrases.count { text.contains(it) },
        )
    }

    /**
     * 技巧分 = 提问比例（≤4）+ 与问诊要点相关的提问比例（≤6）+ 开放式提问（≤3）+ 有效提问数量（≤2）
     * − 重复提问（每条 1 分，最多 3 分）。只发几条带问号的空话最多拿到提问比例那一项。
     */
    private fun techniqueScore(stats: DialogueStats): Int {
        if (stats.total == 0) return 0
        val questionRatio = stats.questionCount.toDouble() / stats.total
        val relevantRatio = stats.relevantCount.toDouble() / stats.total
        val questionPoints = when {
            questionRatio >= 0.6 -> 4
            questionRatio >= 0.4 -> 3
            questionRatio >= 0.2 -> 2
            else -> 1
        }
        val relevantPoints = when {
            relevantRatio >= 0.5 -> 6
            relevantRatio >= 0.35 -> 5
            relevantRatio >= 0.2 -> 3
            stats.relevantCount > 0 -> 1
            else -> 0
        }
        val openPoints = when {
            stats.openQuestionCount >= 3 -> 3
            stats.openQuestionCount == 2 -> 2
            stats.openQuestionCount == 1 -> 1
            else -> 0
        }
        val volumePoints = when {
            stats.substantiveCount >= 8 -> 2
            stats.substantiveCount >= 4 -> 1
            else -> 0
        }
        val penalty = stats.duplicateCount.coerceAtMost(3)
        return (questionPoints + relevantPoints + openPoints + volumePoints - penalty).coerceIn(0, TECHNIQUE_MAX)
    }

    /**
     * 沟通分 = 底分 3 + 共情/安抚短语（≤4）+ 平均提问长度适中（≤3）+ 有效提问数量（≤5）。
     */
    private fun communicationScore(stats: DialogueStats): Int {
        if (stats.total == 0) return 0
        val empathyPoints = when {
            stats.empathyCount >= 2 -> 4
            stats.empathyCount == 1 -> 2
            else -> 0
        }
        val lengthPoints = when {
            stats.averageLength in 8..120 -> 3
            stats.averageLength > 0 -> 1
            else -> 0
        }
        val volumePoints = when {
            stats.substantiveCount >= 8 -> 5
            stats.substantiveCount >= 5 -> 3
            stats.substantiveCount >= 2 -> 2
            stats.substantiveCount == 1 -> 1
            else -> 0
        }
        return (3 + empathyPoints + lengthPoints + volumePoints).coerceIn(0, COMMUNICATION_MAX)
    }

    // ---------------------------------------------------------------- 诊断

    private val bracketContent = Regex("\\([^()]*\\)|【[^【】]*】|\\[[^\\[\\]]*]")
    private val diagnosisNoise = Regex("[\\s,.。、;:·•/\\\\\\-—_~'\"“”‘’!?]+")
    private val diagnosisComponentSeparators = Regex("[,;:、/]|伴有|伴|合并")
    private val hedgePrefixes = listOf("初步诊断", "临床诊断", "拟诊", "诊断为", "诊断", "考虑为", "考虑", "疑似", "可能为", "可能是", "可能")
    private val diagnosisGenericTerms = setOf(
        "急性", "慢性", "亚急性", "待查", "感染", "炎症", "疾病", "综合征", "未知", "不知道", "不清楚", "不确定",
    )

    /** 比较重叠程度前去掉的修饰词，避免“急性胃肠炎”靠“急性”两个字和“急性阑尾炎”算作高度重合。 */
    private val diagnosisModifiers = listOf("亚急性", "急性", "慢性", "原发性", "继发性", "左侧", "右侧", "双侧")

    /** 全角转半角、转小写、去括号内容（可选）和标点空白。 */
    internal fun normalizeDiagnosis(text: String, keepBrackets: Boolean = false): String {
        var value = toHalfWidth(text).lowercase()
        if (!keepBrackets) value = withoutBrackets(value)
        return value.replace(diagnosisNoise, "").replace("(", "").replace(")", "")
    }

    private fun withoutBrackets(text: String): String {
        var result = text
        do {
            val previous = result
            result = result.replace(bracketContent, "")
        } while (result != previous)
        return result
    }

    /** 去掉“初步诊断”“考虑”“可能是”等前缀，可叠加出现（例如“初步诊断考虑……”）。 */
    private fun stripHedge(value: String): String {
        var result = value
        while (true) {
            val prefix = hedgePrefixes.firstOrNull { result.startsWith(it) && result.length > it.length } ?: break
            result = result.removePrefix(prefix)
        }
        return result
    }

    private fun toHalfWidth(text: String): String = buildString(text.length) {
        text.forEach { char ->
            append(
                when (char) {
                    '\u3000' -> ' '
                    in '\uFF01'..'\uFF5E' -> (char.code - 0xFEE0).toChar()
                    else -> char
                }
            )
        }
    }

    private val diagnosisClauses = Regex("[,;。!?\\r\\n]+")
    private val differentialStart = Regex(
        "^(?:鉴别(?:诊断)?|排查|待排|(?:仍|尚)?(?:需|须|应|待|不能|不可|可|已)?(?:进一步|完全)?(?:排除|除外)|" +
            "不(?:考虑|支持|符合|是)|否认|不作为(?:初步)?诊断)",
    )
    private val differentialEnd = Regex("(?:已排除|已除外|可排除|待排除|待除外|待排|需鉴别|不支持)$")
    private val differentialAnnotation = Regex("\\((待排除?|已排除|已除外|可排除|待除外|需鉴别|不支持)\\)")
    private val differentialAfterProposal = Regex("(?:同时|但|并)(?:仍|尚)?(?:需|须|应|要|待)?(?:排除|除外|鉴别)")
    private val differentialHeading = Regex("^(?:鉴别(?:诊断)?|排除项|排除诊断|待排诊断)\\s*:")
    private val proposalHeading = Regex("^(?:初步诊断|临床诊断|拟诊|诊断)\\s*:")

    private fun proposedDiagnosis(answer: String): String {
        // 保留直接修饰诊断的“（待排）”，其余括注在分句前去除，避免括号内逗号割裂主诊断。
        val clauses = withoutBrackets(toHalfWidth(answer).replace(differentialAnnotation, "$1"))
            .split(diagnosisClauses)
        var inDifferentialSection = false
        return buildString {
            for (part in clauses) {
                val clause = part.trim().trimStart('-', '•').trim()
                if (differentialHeading.containsMatchIn(clause)) inDifferentialSection = true
                if (proposalHeading.containsMatchIn(clause)) inDifferentialSection = false
                if (inDifferentialSection) continue

                val exclusion = differentialAfterProposal.find(clause)
                val proposal = stripHedge(normalizeDiagnosis(clause.substring(0, exclusion?.range?.first ?: clause.length)))
                if (!differentialStart.containsMatchIn(proposal) && !differentialEnd.containsMatchIn(proposal)) {
                    append(proposal)
                }
            }
        }
    }

    private fun diagnosisNamePoints(
        medicalCase: MedicalCase,
        initialDiagnosis: String,
        notes: MutableList<String>,
    ): Int {
        if (initialDiagnosis.isBlank()) return 0
        val proposed = proposedDiagnosis(initialDiagnosis)
        if (proposed.isEmpty() && normalizeDiagnosis(initialDiagnosis).isNotEmpty()) {
            notes += "请在初步诊断中写出倾向的诊断，排除项和待鉴别内容不计入匹配。"
            return 0
        }
        val nameScore = scoreDiagnosisName(medicalCase, proposed)
        if (nameScore == 0) {
            notes += NOTE_DIAGNOSIS_TOO_SHORT
            return 0
        }
        return nameScore
    }

    /**
     * 诊断名称分（满分 16）：
     * - 16：与标准诊断（去括号内容后）或病例别名完全一致；
     * - 14：完整包含标准诊断或别名，另有补充描述；
     * - 12：去掉“急性”“右侧”等修饰词后，与标准诊断或其组成部分一致或高度重合
     *   （重叠部分同时占双方的 60% 以上，且至少有 3 个字连续相同）；
     * - 8：部分重叠（去修饰词后连续 3 个字以上相同，或疾病名结尾至少 2 个字相同，例如“肺炎”）；
     * - 6：填写了合理长度但不相符的诊断（含“非 ST 段抬高”与“ST 段抬高”这类否定词不一致）；
     * - 0：归一化后不足 [MIN_DIAGNOSIS_LENGTH] 个字，或只有“急性”“待查”等笼统词。
     */
    internal fun diagnosisNameScore(medicalCase: MedicalCase, initialDiagnosis: String): Int =
        scoreDiagnosisName(medicalCase, proposedDiagnosis(initialDiagnosis))

    private fun scoreDiagnosisName(medicalCase: MedicalCase, actual: String): Int {
        if (actual.length < MIN_DIAGNOSIS_LENGTH || actual in diagnosisGenericTerms) return 0
        val core = normalizeDiagnosis(medicalCase.correctDiagnosis)
        if (core.isEmpty()) return 10
        val full = normalizeDiagnosis(medicalCase.correctDiagnosis, keepBrackets = true)
        val aliases = medicalCase.diagnosisAliases
            .map { stripHedge(normalizeDiagnosis(it)) }
            .filter { it.length >= MIN_DIAGNOSIS_LENGTH }
        if (actual == core || actual == full || actual in aliases) return 16
        val actualCore = stripModifiers(actual)
        if (actualCore.length < MIN_DIAGNOSIS_LENGTH) return 0
        if (negationConflict(actual, core)) return 6
        if (actual.contains(core) || aliases.any { actual.contains(it) }) return 14
        val components = toHalfWidth(medicalCase.correctDiagnosis).lowercase()
            .replace(bracketContent, "")
            .split(diagnosisComponentSeparators)
            .map { normalizeDiagnosis(it) }
        val targets = (listOf(core) + components + aliases)
            .map { stripModifiers(it) }
            .filter { it.length >= MIN_DIAGNOSIS_LENGTH }
            .distinct()
        if (targets.any { it == actualCore || closeMatch(actualCore, it) }) return 12
        val partial = targets.any { target ->
            longestCommonSubstring(actualCore, target) >= 3 || commonSuffixLength(actualCore, target) >= 2
        }
        return if (partial) 8 else 6
    }

    /** 一方把「非X」限定词放在另一方没有否定的同一部位时，视为诊断方向相反。 */
    private fun negationConflict(actual: String, core: String): Boolean =
        negationPair(actual)?.let { pair -> containsUnnegated(core, pair) } == true ||
            negationPair(core)?.let { pair -> containsUnnegated(actual, pair) } == true

    /** 取「非」后紧跟的两个字，例如“非ST”得到“st”。 */
    private fun negationPair(value: String): String? {
        val index = value.indexOf('非')
        if (index < 0 || index + 3 > value.length) return null
        return value.substring(index + 1, index + 3)
    }

    /** 对方文本是否含有这个词，且该次出现不是紧跟在「非」之后。 */
    private fun containsUnnegated(value: String, pair: String): Boolean {
        var fromIndex = 0
        while (true) {
            val index = value.indexOf(pair, fromIndex)
            if (index < 0) return false
            if (index == 0 || value[index - 1] != '非') return true
            fromIndex = index + 1
        }
    }

    private fun stripModifiers(value: String): String {
        var result = value
        diagnosisModifiers.forEach { result = result.replace(it, "") }
        if (result.length > 1 && result.first() in "左右双") result = result.drop(1)
        return result
    }

    private fun closeMatch(actual: String, target: String): Boolean {
        val overlap = longestCommonSubsequence(actual, target)
        return overlap >= ceil(target.length * DIAGNOSIS_CLOSE_RATIO).toInt() &&
            overlap >= ceil(actual.length * DIAGNOSIS_CLOSE_RATIO).toInt() &&
            longestCommonSubstring(actual, target) >= 3
    }

    private fun commonSuffixLength(left: String, right: String): Int {
        var length = 0
        while (length < left.length && length < right.length &&
            left[left.length - 1 - length] == right[right.length - 1 - length]
        ) {
            length++
        }
        return length
    }

    private fun longestCommonSubstring(left: String, right: String): Int {
        if (left.isEmpty() || right.isEmpty()) return 0
        var best = 0
        val previous = IntArray(right.length + 1)
        val current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            for (j in 1..right.length) {
                current[j] = if (left[i - 1] == right[j - 1]) previous[j - 1] + 1 else 0
                if (current[j] > best) best = current[j]
            }
            current.copyInto(previous)
        }
        return best
    }

    private fun longestCommonSubsequence(left: String, right: String): Int {
        if (left.isEmpty() || right.isEmpty()) return 0
        val previous = IntArray(right.length + 1)
        val current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            for (j in 1..right.length) {
                current[j] = if (left[i - 1] == right[j - 1]) {
                    previous[j - 1] + 1
                } else {
                    maxOf(previous[j], current[j - 1])
                }
            }
            current.copyInto(previous)
        }
        return previous[right.length]
    }

    // ---------------------------------------------------------------- 治疗方案

    private fun treatmentScore(medicalCase: MedicalCase, treatmentAnswer: String, notes: MutableList<String>): Int {
        if (treatmentAnswer.isBlank()) return 0
        val plan = medicalCase.treatmentPlan.trim()
        if (plan.isEmpty()) {
            notes += NOTE_NO_TREATMENT_PLAN
            return 0
        }
        val matched = TreatmentRubric.match(plan, treatmentAnswer)
        if (matched.mismatched) {
            notes += "请核对治疗作答中肯定或否定方向与参考方案不一致的要点。"
        }
        val managementHits = matched.managementHits
        if (matched.medications == 0) {
            return when {
                managementHits >= 4 -> TREATMENT_MAX
                managementHits == 3 -> 12
                managementHits == 2 -> 9
                managementHits == 1 -> 5
                else -> 0
            }
        }
        val drugHits = matched.medicationHits
        val drugRatio = drugHits.toDouble() / matched.medications
        val drugPoints = when {
            drugRatio >= 0.6 -> 10
            drugRatio >= 0.4 -> 8
            drugRatio >= 0.2 -> 5
            drugHits > 0 -> 3
            else -> 0
        }
        val managementPoints = when {
            managementHits >= 3 -> 5
            managementHits == 2 -> 4
            managementHits == 1 -> 2
            else -> 0
        }
        return (drugPoints + managementPoints).coerceIn(0, TREATMENT_MAX)
    }
}
