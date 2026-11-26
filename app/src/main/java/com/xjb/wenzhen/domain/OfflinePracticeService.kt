package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.ClinicalRequestType
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.model.PatientPersonality
import java.text.Normalizer

/** 内置病例的离线教学路径，不向任何网络服务发送内容。 */
object OfflinePracticeService {
    private const val UNKNOWN_ANSWER = "这个我说不太清楚，您再具体问问我。"

    fun patientReply(
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
    ): String {
        // 当前问题仍处于发送中。此前只有成功发送的提问可以作为追问依据。
        val questionIndex = messages.indexOfLast {
            it.role == MessageRole.DOCTOR && it.deliveryState != MessageDeliveryState.FAILED
        }
        val current = messages.getOrNull(questionIndex) ?: return UNKNOWN_ANSWER
        if (!isDoctorEvidence(questionIndex, current)) return UNKNOWN_ANSWER
        val question = current.content
        val earlierQuestions = messages.subList(0, questionIndex).mapIndexedNotNull { index, message ->
            message.content.takeIf {
                message.deliveryState == MessageDeliveryState.SENT && isDoctorEvidence(index, message)
            }
        }
        val availablePersonality = personality.takeIf { it.availableFor(medicalCase.patient.age) }
            ?: PatientPersonality.COOPERATIVE

        if (availablePersonality == PatientPersonality.ELDERLY && earlierQuestions.isEmpty()) {
            return "医生，我刚才没听清，您能再说一遍吗？"
        }
        if (availablePersonality == PatientPersonality.EVASIVE) {
            val topic = SensitiveTopic.entries.firstOrNull { it.matches(question) }
            if (topic != null) {
                if (earlierQuestions.none(topic::matches)) return "这个……我有点不好意思说。"
                return topic.replyFrom(medicalCase).ifBlank { UNKNOWN_ANSWER }
            }
        }

        val sentenceLimit = if (availablePersonality == PatientPersonality.IMPATIENT) 1 else 2
        val matched = when {
            listOf("多久", "什么时候", "几天", "多长").any(question::contains) ->
                concise(medicalCase.presentIllness, sentences = 1)
            listOf("既往", "以前", "病史", "慢性病", "手术", "过敏", "用药", "服药", "吃药").any(question::contains) ->
                concise(medicalCase.pastHistory, sentences = sentenceLimit)
            SensitiveTopic.SMOKING.matches(question) || SensitiveTopic.ALCOHOL.matches(question) ||
                listOf("职业", "工作").any(question::contains) ->
                concise(medicalCase.personalHistory, sentences = sentenceLimit)
            SensitiveTopic.FAMILY.matches(question) ->
                concise(medicalCase.familyHistory, sentences = sentenceLimit)
            SensitiveTopic.REPRODUCTIVE.matches(question) ->
                concise(medicalCase.maritalHistory, sentences = sentenceLimit)
            else -> relevantIllnessSentence(medicalCase, question)
        }
        val answer = matched?.ifBlank { UNKNOWN_ANSWER }
            ?: "我能想到的就是${medicalCase.chiefComplaint.trimEnd('。', '；', '，')}，其他情况请您具体问我。"
        return when (availablePersonality) {
            PatientPersonality.COOPERATIVE, PatientPersonality.EVASIVE -> answer
            PatientPersonality.ANXIOUS -> if (earlierQuestions.size % 2 == 0) "${answer}医生，我这要不要紧啊？" else answer
            PatientPersonality.UNCLEAR -> matched?.ifBlank { UNKNOWN_ANSWER }
                ?: "就是不舒服，我也说不上来。您能问得具体一点吗？"
            PatientPersonality.ELDERLY -> "您问的是这个呀。$answer"
            PatientPersonality.IMPATIENT -> if (earlierQuestions.size % 2 == 0) "${answer}医生，能快一点吗？" else answer
        }
    }

    private fun relevantIllnessSentence(medicalCase: MedicalCase, question: String): String? {
        val questions = interviewClauses(question).filter(InterviewClause::isQuestion)
        if (questions.isEmpty()) return null
        val words = KeyQuestionCatalog.of(medicalCase)
            .flatMap { it.keywords + it.synonyms }
            .distinct()
            .filter { word -> questions.any { it.text.contains(word) } }
        if (words.isEmpty()) return null
        val sourceTerms = words.flatMapTo(mutableSetOf(), KeyQuestionCatalog::equivalentTerms)
        // 摘录整句，保留并列否定、既往发生时间等上下文。
        return medicalCase.presentIllness.split('。')
            .map(String::trim)
            .firstOrNull { sentence -> sourceTerms.any { sentence.contains(it, ignoreCase = true) } }
            ?.plus("。")
    }

    private enum class SensitiveTopic(private vararg val terms: String) {
        SMOKING("吸烟", "抽烟", "戒烟", "烟酒", "烟龄"),
        ALCOHOL("饮酒", "喝酒", "戒酒", "烟酒"),
        FAMILY("家族", "父母", "父亲", "母亲", "家里人", "家人"),
        MEDICINE("用药", "服药", "吃药", "药物"),
        REPRODUCTIVE("月经", "例假", "怀孕", "生育", "性生活", "婚育");

        fun matches(text: String): Boolean = terms.any(text::contains)

        fun replyFrom(medicalCase: MedicalCase): String = when (this) {
            SMOKING, ALCOHOL -> medicalCase.personalHistory.split('。', '；')
                .map(String::trim)
                .firstOrNull(::matches)
                ?.plus("。").orEmpty()
            FAMILY -> concise(medicalCase.familyHistory, sentences = 1)
            MEDICINE -> concise(medicalCase.pastHistory, sentences = 1)
            REPRODUCTIVE -> concise(medicalCase.maritalHistory, sentences = 1)
        }
    }

    /** 只回答与当前问题相关的前几段，避免离线模式一次性倾倒整份病例资料。 */
    private fun concise(text: String, sentences: Int): String =
        text.split('。').map(String::trim).filter(String::isNotEmpty).take(sentences)
            .joinToString(separator = "。").let { if (it.isBlank()) "" else "$it。" }

    private val findingSeparators = Regex("[；;。\r\n]+")
    private val vitalNotation = Regex("""(?:^|[\s，,、])(?:T|P|R|BP|SpO[2₂])\s*[:：]?\s*\d""", RegexOption.IGNORE_CASE)
    private val latinTerm = Regex("[a-z0-9-]+")
    private val findingTerms = mapOf(
        "生命体征" to listOf("体温", "脉搏", "呼吸频率", "血压", "血氧饱和度"),
        "一般情况" to listOf("一般状态", "神志", "意识", "精神状态"),
        "心肺听诊" to listOf("心音", "心律", "杂音", "心脏听诊", "呼吸音", "啰音", "双肺"),
        "腹部检查" to listOf("腹部查体", "腹部", "腹软", "腹平", "肠鸣音", "反跳痛", "腹肌紧张"),
        "神经系统检查" to listOf("神经系统", "肌力", "肌张力", "腱反射", "病理征", "脑膜刺激征", "瞳孔"),
        "心电图" to listOf("ecg", "ekg"),
        "胸部x线" to listOf("胸片", "x线胸片", "胸部x光"),
        "腹部超声" to listOf("腹部b超", "腹部彩超"),
        "肝肾功能" to listOf("肝功能", "肾功能", "肌酐", "尿素氮", "转氨酶"),
        "电解质" to listOf("血钾", "血钠", "血氯", "血钙"),
        "血糖" to listOf("葡萄糖", "糖化血红蛋白", "hba1c"),
        "心肌酶" to listOf("肌酸激酶", "ck-mb"),
        "肌钙蛋白" to listOf("ctni", "ctnt", "hs-ctn"),
    )

    /** 返回病例已经记录的对应条目。缺失时由界面说明结果未记录。 */
    fun clinicalResult(medicalCase: MedicalCase, requestType: ClinicalRequestType, item: String): String? {
        val requested = normalizeFinding(item)
        if (requested.isEmpty()) return null
        val canonicalItem = when (requested) {
            "ecg", "ekg" -> "心电图"
            "胸片" -> "胸部x线"
            "腹部b超", "腹部彩超" -> "腹部超声"
            else -> requested
        }
        val terms = listOf(canonicalItem) + findingTerms[canonicalItem].orEmpty()
        val latinPatterns = terms.filter(latinTerm::matches).map { term ->
            Regex("(?<![a-z0-9])${Regex.escape(term)}(?![a-z0-9])")
        }
        val textTerms = terms.filterNot(latinTerm::matches)
        val findings = when (requestType) {
            ClinicalRequestType.PHYSICAL_EXAM -> medicalCase.physicalExam
            ClinicalRequestType.LAB_OR_IMAGING -> medicalCase.auxiliaryExams
        }
        return findings.split(findingSeparators)
            .map { it.trim() }
            .filter { finding ->
                val normalized = Normalizer.normalize(finding, Normalizer.Form.NFKC).lowercase()
                val compact = normalized.filterNot(Char::isWhitespace)
                textTerms.any { compact.contains(it) } ||
                    latinPatterns.any { it.containsMatchIn(normalized) } ||
                    (canonicalItem == "生命体征" && vitalNotation.containsMatchIn(finding))
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("；")
    }

    private fun normalizeFinding(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).filterNot(Char::isWhitespace).lowercase()

    fun score(
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        initialDiagnosis: String,
        treatmentAnswer: String,
        encounterType: EncounterType,
        diagnosisBasis: String = "",
    ): ScoreResult {
        val rule = RuleScoreCalculator.compute(
            medicalCase = medicalCase,
            messages = messages,
            initialDiagnosis = initialDiagnosis,
            diagnosisBasis = diagnosisBasis,
            treatmentAnswer = treatmentAnswer,
        )
        val scopeNote = if (encounterType == EncounterType.INPATIENT) "住院问诊还应补充系统回顾、个人史、婚育史和家族史。" else ""
        return ScoreResult(
            totalScore = rule.total,
            coverageScore = rule.coverage,
            techniqueScore = rule.technique,
            communicationScore = rule.communication,
            diagnosisScore = rule.diagnosis,
            treatmentScore = rule.treatment,
            coveredPoints = rule.coveredQuestions,
            missedQuestions = rule.missedQuestions,
            strengths = listOf("已完成离线基础训练流程。"),
            improvements = listOfNotNull(scopeNote.takeIf { it.isNotBlank() }) + rule.notes,
            correctDiagnosis = medicalCase.correctDiagnosis,
            diagnosisExplanation = "离线模式使用内置病例资料和本地规则评分，适合熟悉问诊流程。",
            evidence = rule.evidence,
            diagnosisClues = rule.diagnosisClues,
        )
    }

    fun record(
        medicalCase: MedicalCase,
        initialDiagnosis: String,
        diagnosisBasis: String,
        treatmentAnswer: String,
        encounterType: EncounterType,
    ): String = buildString {
        appendLine("【参考病历】以下内容来自病例标准资料，不代表本次问诊采集到的信息。")
        appendLine("一般情况")
        appendLine("${medicalCase.patient.name}，${medicalCase.patient.gender}，${medicalCase.patient.age}，${medicalCase.patient.occupation}。")
        appendLine("主诉")
        appendLine(medicalCase.chiefComplaint)
        appendLine("现病史")
        appendLine(medicalCase.presentIllness)
        appendLine("既往史")
        appendLine(medicalCase.pastHistory)
        appendLine("体格检查")
        appendLine(medicalCase.physicalExam)
        if (encounterType == EncounterType.INPATIENT) {
            appendLine("系统回顾")
            appendLine(medicalCase.reviewOfSystems)
        }
        appendLine("初步诊断")
        appendLine(initialDiagnosis.ifBlank { "未填写" })
        appendLine("诊断依据")
        appendLine(diagnosisBasis.ifBlank { "未填写" })
        appendLine(if (encounterType == EncounterType.OUTPATIENT) "处理意见" else "诊疗计划")
        appendLine(treatmentAnswer.ifBlank { "未填写" })
    }
}

