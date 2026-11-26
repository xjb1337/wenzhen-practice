package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.ClinicalRequestType
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import java.text.Normalizer

data class DiagnosisClue(val text: String, val messageNumber: Int)

internal fun isDiagnosisClueSource(message: ChatMessage): Boolean =
    message.deliveryState == MessageDeliveryState.SENT &&
        (message.role == MessageRole.PATIENT || clinicalResultType(message) != null)

private fun clinicalResultType(message: ChatMessage): ClinicalRequestType? =
    if (message.role != MessageRole.SYSTEM) null else ClinicalRequestType.entries.firstOrNull {
        message.systemTitle?.substringBefore('·')?.trim() == it.label
    }

/** 仅匹配在本次问诊已出现、也可在病例相应资料中核对的作答线索。 */
internal object DiagnosisBasisScoring {
    private const val MAX_CLUES = 2
    private val sentenceBreak = Regex("[：:,，。；;！？!?\\r\\n]+|但(?:是)?")
    private val detailBreak = Regex("、|并伴有|同时伴有|伴有|伴(?!随)")
    private val leadingContext = Regex(
        "^(?:(?:该患者|患者|病人|我|最近|目前|后来|现在|本次|主诉|依据|根据|心电图|ecg|ekg|查体|检查|显示|提示|发现|结果|示)\\s*)+",
    )
    private val negativeLead = Regex("^(?:并?没有|未出现|未发生|未查见|未闻及|未见|未发现|否认|不是|不伴|不见|无|没)")
    private val positiveLead = Regex("^(?:仍有|有|出现|存在|可见)")
    private val compactNoise = Regex("[\\s()（）\\[\\]【】“”‘’\"']")
    private val nonSpecific = setOf(
        "患者", "出现", "发现", "诊断", "病史", "从病史", "临床表现", "检查", "检查结果", "检查提示",
        "基本情况", "查体", "结果", "依据", "症状", "体征", "符合", "明显", "异常", "正常",
        "升高", "降低", "阳性", "阴性", "存在", "提示", "左右", "近期", "开始",
        "体温", "血压", "脉搏", "血糖", "心率", "心律", "呼吸频率", "血常规", "心电图",
        "影像", "腹部超声", "肌钙蛋白", "心肌酶", "st段",
    )
    private val onlyQuantity = Regex("^[约大概]*\\d+(?:[./～~×-]\\d+)*(?:天|日|小时|分钟|年|周|个月|岁|次|度|分|%|[a-z]+)?$")
    private val aliases = listOf(
        Regex("胸口(?:疼痛|疼|痛)(?:了)?") to "胸痛",
        Regex("肚子(?:疼痛|疼|痛)(?:了)?") to "腹痛",
        Regex("喘不上气|喘不过气|透不过气|上不来气|气短|气促") to "呼吸困难",
        Regex("晕倒|昏倒|昏过去") to "晕厥",
        Regex("发烧") to "发热",
    )
    private val embeddedNegation = Regex("(?:非|未|无|没有|不是|不伴|否认|未见)$")
    private val uncertainty = Regex("是否|不详|不清楚|不确定|未能证实|未证实|不能确认|无法确认|待查|待排")
    private val extendedNumber = Regex("^(?:\\d|[./]\\d)")
    private val broaderFindings = mapOf(
        "胸痛" to Regex("(?:胸骨后|胸前区|胸口|左胸|右胸)[\\u4e00-\\u9fff]{0,12}(?:疼痛|闷痛|刺痛|疼|痛)"),
        "st段抬高" to Regex("st段(?:弓背向上|向上|弓背样)抬高"),
    )
    private val internalAbsence = Regex("未|无|没有|没|不伴|否认|未见")
    private val approximateTimeEnd = Regex("\\d(?:天|小时|周|年|个月)$")

    private data class Finding(val body: String, val negative: Boolean, val label: String)
    private data class Observed(val number: Int, val ordinal: Int, val finding: Finding, val exam: ClinicalRequestType?)
    private data class Span(val start: Int, val end: Int, val internalNegation: Boolean = false)
    private data class Candidate(val clue: DiagnosisClue, val observation: Observed, val span: Span, val specificity: Int)

    fun find(case: MedicalCase, messages: List<ChatMessage>, answer: String): List<DiagnosisClue> {
        val claims = findings(answer).filter(::isSpecific).distinctBy { it.body to it.negative }
        if (claims.isEmpty()) return emptyList()
        val observed = transcriptEntries(messages).flatMapIndexed { index, message ->
            if (!isDiagnosisClueSource(message)) emptyList()
            else findings(message.content).mapIndexed { ordinal, finding ->
                Observed(index + 1, ordinal, finding, clinicalResultType(message))
            }
        }
        if (observed.isEmpty()) return emptyList()
        val history = listOf(
            case.openingLine, case.chiefComplaint, case.presentIllness, case.pastHistory,
            case.personalHistory, case.maritalHistory, case.familyHistory, case.reviewOfSystems,
        ).flatMap(::findings)
        val reference = mapOf(
            null to history,
            ClinicalRequestType.PHYSICAL_EXAM to findings(case.physicalExam),
            ClinicalRequestType.LAB_OR_IMAGING to findings(case.auxiliaryExams),
        )

        val candidates = claims.mapNotNull { claim ->
            val latest = observed.asReversed().firstNotNullOfOrNull { observation ->
                locate(observation.finding.body, claim.body)?.let { observation to it }
            } ?: return@mapNotNull null
            val (source, span) = latest
            if (uncertainty.containsMatchIn(source.finding.body) ||
                negativeAt(source.finding, span) != claim.negative
            ) return@mapNotNull null
            val confirmedByCase = reference[source.exam].orEmpty().any { finding ->
                !uncertainty.containsMatchIn(finding.body) &&
                    locate(finding.body, claim.body)?.let { negativeAt(finding, it) == claim.negative } == true
            }
            if (!confirmedByCase) return@mapNotNull null
            Candidate(DiagnosisClue(claim.label, source.number), source, span, claim.body.length)
        }

        val selected = mutableListOf<Candidate>()
        // Prefer the more specific claim when two phrases cite the same part of a finding.
        for (candidate in candidates.sortedByDescending { it.specificity }) {
            val repeated = selected.any { item ->
                item.observation.number == candidate.observation.number &&
                    item.observation.ordinal == candidate.observation.ordinal &&
                    candidate.span.start < item.span.end && item.span.start < candidate.span.end
            }
            if (!repeated) selected += candidate
            if (selected.size == MAX_CLUES) break
        }
        return selected.sortedWith(compareBy({ it.observation.number }, { it.observation.ordinal }, { it.span.start }))
            .map { it.clue }
    }

    private fun findings(text: String): List<Finding> = buildList {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        for (sentence in normalized.split(sentenceBreak)) {
            var inheritedNegative = false
            val parts = detailBreak.findAll(sentence).toList()
            var start = 0
            for (index in 0..parts.size) {
                val end = parts.getOrNull(index)?.range?.first ?: sentence.length
                val display = sentence.substring(start, end).trim().trimStart('-', '•', '·').trim()
                var body = display.lowercase().replace(leadingContext, "")
                val negation = negativeLead.find(body)
                val positive = positiveLead.find(body)
                val coordinated = parts.getOrNull(index - 1)?.value == "、"
                val negative = negation != null || (positive == null && coordinated && inheritedNegative)
                start = (parts.getOrNull(index)?.range?.last ?: end) + 1
                if (negation != null) body = body.drop(negation.value.length)
                else if (positive != null) body = body.drop(positive.value.length)
                inheritedNegative = negative
                body = body.replace(compactNoise, "").trimEnd('了', '吗', '吧', '呢')
                for ((pattern, alias) in aliases) body = body.replace(pattern, alias)
                if (body.isNotBlank()) {
                    add(Finding(body, negative, if (negative && negation == null) "无$display" else display))
                }
            }
        }
    }

    private fun isSpecific(finding: Finding): Boolean =
        finding.body.length >= 2 && finding.body.any { it in '\u4e00'..'\u9fff' } &&
            finding.body !in nonSpecific && !onlyQuantity.matches(finding.body)

    private fun locate(source: String, claim: String): Span? {
        var offset = source.indexOf(claim)
        while (offset >= 0) {
            val end = offset + claim.length
            val suffix = source.substring(end)
            val gluedLeft = claim.first().isAsciiWord() && source.getOrNull(offset - 1)?.isAsciiWord() == true
            val gluedRight = claim.last().isAsciiWord() && source.getOrNull(end)?.isAsciiWord() == true
            val partialNumber = claim.last().isDigit() && extendedNumber.containsMatchIn(suffix) ||
                approximateTimeEnd.containsMatchIn(claim) && suffix.firstOrNull() in listOf('半', '余', '多')
            if (!gluedLeft && !gluedRight && !partialNumber) return Span(offset, end)
            offset = source.indexOf(claim, end)
        }
        val match = broaderFindings[claim]?.find(source) ?: return null
        return Span(match.range.first, match.range.last + 1, internalAbsence.containsMatchIn(match.value))
    }

    private fun negativeAt(finding: Finding, span: Span) =
        finding.negative || span.internalNegation || embeddedNegation.containsMatchIn(finding.body.take(span.start))

    private fun Char.isAsciiWord() = this in 'a'..'z' || this in '0'..'9'
}
