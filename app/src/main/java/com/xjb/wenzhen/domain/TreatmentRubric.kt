package com.xjb.wenzhen.domain

import java.text.Normalizer

/**
 * 从病例参考方案提取本地评分要点。药名按完整名称匹配，措施的中英文别名共享同一个要点。
 * 参考与作答使用相同的肯定/否定识别。
 */
internal object TreatmentRubric {
    enum class Direction { PROPOSE, WITHHOLD }

    data class Match(
        val medications: Int,
        val medicationHits: Int,
        val managementHits: Int,
        val mismatched: Boolean,
    )

    private data class Item(val terms: Set<String>, val directions: Set<Direction>)

    private val planSection = Regex("(治疗原则|药物|处理意见|处理|随访)[：:]")
    private val clauseBoundary = Regex("[,;。!?\\r\\n]+|(?:但是|但|而是|改用|改为|同时(?=继续|给予|安排|实施|采用|进行)|并(?=继续|给予|安排|实施|采用|进行))")
    private val inlineAbbreviation = Regex("\\(\\s*([a-z][a-z0-9]{1,11})\\s*\\)")
    private val conditionalStart = Regex("^(?:若|如果|如有|当|仅当|不能及时|无法及时)")
    private val withholding = Regex(
        "暂缓|暂停|暂不|停用|停止|拒绝|禁用|禁止|避免|(?:暂)?未(?:给予|使用|采用|安排|实施|做)|不再(?:给|用|做|实施)|" +
            "不(?:予|给|用|做|使用|服用|采用|进行|安排|实施|推荐|建议|考虑|需要|必|宜|应|得)|" +
            "不能(?:用|做|使用|采用|进行|安排|实施)|需(?:要)?停用|无需|无须",
    )
    private val continueInsteadOfStopping = Regex(
        "(?:无需|无须|不必|不应|不要|不建议|不用|不得|不能)\\s*(?:停用|停止|暂停|禁用|暂缓|(?=停.+)停)",
    )
    private val suffixLead = Regex("^(?:\\s|均|一律|也|应|须|必须|先|可|：|:|[(（])*")

    private val dose = Regex(
        "\\d+(?:\\.\\d+)?(?:\\s*(?:至|到|~|–|-)\\s*\\d+(?:\\.\\d+)?)?" +
            "\\s*(?:mg|ml|μg|ug|iu|万u|g|u|片|粒|枚|支|吸|喷)(?![a-z])",
    )
    private val dosedDrug = Regex("([\\u4e00-\\u9fffa-z]{2,})\\s*" + dose.pattern)
    private val dosageAnnotation = Regex("\\(\\s*\\d+\\s*:\\s*\\d+\\s*\\)(?=\\s*\\d)")
    private val drugLead = Regex(
        "^(?:首选|改用|改为|继续|同时|随后|联合|静脉推注|静脉滴注|静脉注射|" +
            "肌内注射|肌注|舌下含服|皮下注射|口服|给予|使用|服用|予以|加用|可用|推荐|加|用|予|或)+",
    )
    private val drugContext = Regex("(?<=时|后|期|明显|偏高|升高|疼痛|出血|加重)(?:可|需|应|则)?(?:给予|予以|合用|联合|使用|用|加|可)")
    private val drugTail = Regex(
        "(?:口服溶液|注射液|混悬液|缓释胶囊|缓释片|肠溶片|胶囊|颗粒|软膏|乳膏|凝胶|滴眼液|" +
            "气雾剂|片|栓|首剂|负荷|维持量|每次|总量|起始量|剂量|用量|口服|静脉|静推|静滴|肌注|注射)$",
    )
    private val notDrug = setOf(
        "小时", "分钟", "体重", "成人", "儿童", "最大", "最高", "单次", "不超过", "最多", "体表面积",
    )

    private val managementSplit = Regex("[、/＋+：:（）()]|并|及|和|或|与|则|后|时")
    private val managementLeading = Regex("^(?:直接|积极|早期|急诊|常规|继续|尽快|立即|首选|及时|进行|给予|安排|实施|采用|必要时|可用|行)+")
    private val managementGeneric = setOf(
        "疼痛", "治疗", "处理", "患者", "症状", "必要", "情况", "明显", "加重", "监测", "监护", "评估", "观察",
        "复查", "注意", "避免", "不能", "直接", "尽早", "完善", "本例", "包括", "急诊", "门诊", "控制",
        "防治", "恢复", "结束", "无效", "加量", "剂量", "感染", "出血", "呼吸", "心率", "体温", "感觉",
        "睡眠", "压力", "基线", "定位", "共病", "记录", "第一", "重症", "生命", "引起", "低于", "休克",
    )
    private val managementLatin = Regex("[a-z][a-z0-9]+")
    private val managementLatinNoise = Regex("mg|ml|kg|min|ug|iu|spo2|bp|hr|cm|mm|mmhg")

    fun match(plan: String, answer: String): Match {
        val sections = splitPlanSections(plan)
        val drugText = sections["药物"] ?: plan
        val managementText = listOfNotNull(sections["治疗原则"], sections["处理"], sections["处理意见"])
            .joinToString("；")
            .ifBlank { if (sections.isEmpty()) plan else "" }
        val medications = groupAliases(medicationItems(drugText))
        val medicationTerms = medications.flatMap { it.terms }.toSet()
        val actions = groupAliases(managementItems(managementText).mapNotNull { item ->
            val nonMedicationTerms = item.terms.filterNot { term -> medicationTerms.any { it.contains(term) } }.toSet()
            item.copy(terms = nonMedicationTerms).takeIf { nonMedicationTerms.isNotEmpty() }
        })
        val answers = clauses(answer)

        var mismatched = false
        fun hits(items: List<Item>, allowsCompactDose: Boolean = false): Int = items.count { item ->
            val offered = answers.flatMap { clause ->
                item.terms.flatMap { term ->
                    occurrences(clause, term, allowsCompactDose).map { start -> direction(clause, start, start + term.length) }.toList()
                }
            }.toSet()
            val consistent = offered.isNotEmpty() && item.directions.containsAll(offered)
            if (offered.isNotEmpty() && !consistent) mismatched = true
            consistent
        }

        val medicationHits = hits(medications, allowsCompactDose = true)
        val managementHits = hits(actions)
        return Match(medications.size, medicationHits, managementHits, mismatched)
    }

    internal fun medicationNames(text: String): Set<String> =
        medicationItems(splitPlanSections(text)["药物"] ?: text).flatMap { it.terms }.toSet()

    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()

    private fun clauses(text: String): List<String> =
        normalize(text)
            .replace(inlineAbbreviation, " $1 ")
            .replace(continueInsteadOfStopping, "继续")
            .split(clauseBoundary)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (conditionalStart.containsMatchIn(it)) it.substringAfter('则') else it }

    private fun splitPlanSections(plan: String): Map<String, String> {
        val matches = planSection.findAll(plan).toList()
        return matches.mapIndexed { index, section ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: plan.length
            section.groupValues[1] to plan.substring(section.range.last + 1, end)
        }.toMap()
    }

    private fun medicationItems(text: String): List<Item> = clauses(text).flatMap { part ->
        val clause = part.replace(dosageAnnotation, "")
        dosedDrug.findAll(clause).mapNotNull { match ->
            val captured = match.groups[1] ?: return@mapNotNull null
            var name = captured.value.let { word ->
                val lead = (withholding.findAll(word) + drugContext.findAll(word)).maxOfOrNull { it.range.last + 1 } ?: 0
                word.drop(lead).replace(drugLead, "")
            }
            val beforeTail = name
            do {
                val previous = name
                name = name.replace(drugTail, "")
            } while (name != previous)
            // Some drug names themselves end in “片”; keep the captured name if stripping
            // would erase the full name rather than just its dosage form.
            if (name.length < 2) name = beforeTail
            if (name.length < 2 || name in notDrug || name.contains("每日") || name.contains("小时")) {
                return@mapNotNull null
            }
            val start = captured.range.first + captured.value.lastIndexOf(name)
            Item(setOf(name), setOf(direction(clause, start, start + name.length)))
        }.toList()
    }

    private fun managementItems(text: String): List<Item> = clauses(text).flatMap { clause ->
        val items = mutableListOf<Item>()
        fun append(start: Int, end: Int) {
            val fragment = clause.substring(start, end)
            val core = fragment.replace(withholding, "").trim().replace(managementLeading, "")
            val chinese = core.filter { it in '\u4e00'..'\u9fff' }.takeLast(4)
                .takeIf { it.length >= 2 && it !in managementGeneric }
            val latin = managementLatin.findAll(core).map { it.value }.filterNot { managementLatinNoise.matches(it) }
            val terms = (listOfNotNull(chinese) + latin).filter { fragment.contains(it) }.toSet()
            if (terms.isNotEmpty()) {
                val first = terms.minOf { start + fragment.indexOf(it) }
                val last = terms.maxOf { start + fragment.lastIndexOf(it) + it.length }
                items += Item(terms, setOf(direction(clause, first, last)))
            }
        }

        var start = 0
        for (delimiter in managementSplit.findAll(clause)) {
            append(start, delimiter.range.first)
            start = delimiter.range.last + 1
        }
        append(start, clause.length)
        items
    }

    private fun groupAliases(items: List<Item>): List<Item> {
        val groups = mutableListOf<Item>()
        for (item in items) {
            val overlaps = groups.filter { group -> item.terms.any { it in group.terms } }
            groups.removeAll(overlaps.toSet())
            groups += Item(
                terms = item.terms + overlaps.flatMap { it.terms },
                directions = item.directions + overlaps.flatMap { it.directions },
            )
        }
        return groups
    }

    private fun direction(clause: String, start: Int, end: Int): Direction {
        val prefix = clause.substring(0, start)
        val suffix = clause.substring(end).replace(suffixLead, "")
        val withheld = withholding.containsMatchIn(prefix) || withholding.find(suffix)?.range?.first == 0
        return if (withheld) Direction.WITHHOLD else Direction.PROPOSE
    }

    private fun Char.isAsciiWord() = this in 'a'..'z' || this in '0'..'9' || this == '_'

    private fun occurrences(text: String, term: String, allowsCompactDose: Boolean): Sequence<Int> = sequence {
        var start = text.indexOf(term)
        while (start >= 0) {
            val end = start + term.length
            val embeddedLeft = term.first().isAsciiWord() && text.getOrNull(start - 1)?.isAsciiWord() == true
            val embeddedRight = term.last().isAsciiWord() && text.getOrNull(end)?.isAsciiWord() == true
            val doseFollows = embeddedRight && allowsCompactDose && dose.matchAt(text, end) != null
            if (!embeddedLeft && (!embeddedRight || doseFollows)) yield(start)
            start = text.indexOf(term, end)
        }
    }
}
