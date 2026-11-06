package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.MedicalCase

/**
 * 病例级问诊要点的结构化视图。
 *
 * 内置病例的 keyQuestions 仍是便于编辑的纯文本；这里在运行时补齐稳定 ID、关键词、同义表达、
 * 重要程度、红旗标记和最低证据要求，让覆盖统计从“通用关键词”升级为“病例级要点”。
 *
 * 关键词按顿号、连接词（及/和/或/是否/有无……）切分要点文本得到，再去掉“近期”“情况”“史”等
 * 修饰性前后缀；不再生成任意 2 字切片，避免“时间”“开始”“持续”这类通用片段命中多个要点。
 */
data class KeyQuestionSpec(
    val id: String,
    val text: String,
    val synonyms: List<String> = emptyList(),
    val importance: Int = 2,
    val isRedFlag: Boolean = false,
    val minEvidence: Int = 1,
    val keywords: List<String> = emptyList(),
)

/** 同义词组：要点文本包含任一 [triggers] 时，医生说出任一 [synonyms] 都算问到。 */
private data class SynonymGroup(
    val triggers: List<String>,
    val synonyms: List<String>,
)

object KeyQuestionCatalog {
    private val redFlagTerms = listOf(
        "胸痛", "胸闷", "呼吸困难", "气促", "意识", "晕厥", "抽搐", "大出血",
        "呕血", "黑便", "高热", "持续发热", "剧烈头痛", "腹痛", "休克", "心悸",
        "便血", "血便", "肛门肿物", "里急后重", "体重下降", "进行性吞咽困难",
        "撕裂样胸背痛", "意识改变", "张口受限", "声音嘶哑", "吸入性损伤",
        "下肢肿胀", "间歇性跛行", "乳房肿块", "乳头溢血", "粒细胞减少",
        "创面感染", "皮疹", "血压下降",
    )

    /**
     * 通用词：单独出现时不能作为任何要点的覆盖证据。
     * 例如“这个多长时间了”里的“时间”、“情况怎么样”里的“情况”。
     */
    internal val genericTerms: Set<String> = setOf(
        "询问", "了解", "患者", "患儿", "是否", "有没有", "有无", "相关", "情况", "问题",
        "症状", "信息", "以及", "或者", "主要", "其他", "其它", "进行", "详细", "具体",
        "时间", "开始", "持续", "持续时间", "时长", "时点", "年限", "程度", "变化", "过程",
        "进展", "范围", "速度", "频率", "次数", "规律", "近期", "最近", "目前", "现在",
        "出现", "发生", "一般", "状态", "一般情况", "一般状态", "多久", "以前", "过去",
        "平时", "平素", "既往", "表现", "特点", "性状", "有关", "能否", "类似", "原因",
        "什么", "怎么", "怎样", "哪里", "多少", "加重", "缓解", "减轻", "发作", "病史",
        "疾病", "疼痛", "不适", "感觉", "结果", "经过", "方式", "本次", "发病", "起病",
        "治疗", "检查", "使用", "影响", "因素", "危险", "危险因素", "鉴别", "以资鉴别",
        "报警", "报警症状", "相关症状", "体力", "活动", "疗效", "异常", "改变", "可能",
        "情形", "女性", "男性",
    )

    /** 要点文本中的连接词，按它们切分出并列的关键词。 */
    private val splitWords = listOf(
        "以及", "或者", "是否", "有没有", "有无", "能否", "及", "和", "或", "与", "的", "等",
    )

    /** 关键词前面的修饰词，例如“近期用药”→“用药”、“是否伴大汗”→“大汗”。 */
    private val leadingModifiers = listOf(
        "询问", "了解", "患者", "患儿", "近期", "最近", "目前", "既往", "是否", "有没有", "有无",
        "平时", "平素", "本次", "发病前", "发病后", "伤前", "伤后", "长期", "自行", "出现",
        "女性", "男性", "向", "伴", "有",
    )

    /** 关键词后面的修饰词，例如“过敏史”→“过敏”、“腹泻开始时间”→“腹泻”。 */
    private val trailingModifiers = listOf(
        "情况", "病史", "史", "的时间", "时间", "开始", "起病", "出现", "变化", "过程", "情形",
        "程度", "特点", "表现", "症状", "后", "前", "时",
    )

    private val synonymGroups = listOf(
        SynonymGroup(
            triggers = listOf(
                "开始的时间", "开始时间", "起病时间", "起病", "病程", "多久",
                "出现的时间", "最早出现", "发病时间", "受伤时间",
            ),
            synonyms = listOf(
                "多久了", "多长时间", "几天了", "多少天", "几个小时", "什么时候开始",
                "什么时候出现", "什么时候发现", "哪天开始", "病程",
            ),
        ),
        SynonymGroup(
            triggers = listOf("诱因", "诱发"),
            synonyms = listOf("诱因", "诱发", "什么原因引起", "什么情况下发作", "什么情况下出现"),
        ),
        SynonymGroup(
            triggers = listOf("性质"),
            synonyms = listOf(
                "性质", "什么样的疼", "什么样的痛", "怎么个疼法", "怎么个痛法",
                "压榨", "刺痛", "绞痛", "胀痛", "隐痛", "烧灼",
            ),
        ),
        SynonymGroup(
            triggers = listOf("放射"),
            synonyms = listOf("放射", "牵涉", "串到", "窜到", "传到", "往别处", "肩膀", "后背", "下巴"),
        ),
        SynonymGroup(
            triggers = listOf("伴随"),
            synonyms = listOf("伴随", "同时还有", "同时伴有"),
        ),
        SynonymGroup(
            triggers = listOf("既往"),
            synonyms = listOf("以前有过", "以前得过", "过去有过", "过去得过", "以前有没有", "基础病", "以往"),
        ),
        SynonymGroup(
            triggers = listOf("过敏"),
            synonyms = listOf("过敏"),
        ),
        SynonymGroup(
            // 不用“服药”作触发词：它会误命中“含服药物后能否缓解”。
            triggers = listOf("用药", "服用"),
            synonyms = listOf("用药", "吃药", "服药", "吃什么药", "吃过什么药", "用过什么药", "用什么药", "吃过药"),
        ),
        SynonymGroup(
            triggers = listOf("吸烟", "抽烟"),
            synonyms = listOf("吸烟", "抽烟", "烟酒", "抽过烟", "香烟"),
        ),
        SynonymGroup(
            triggers = listOf("饮酒", "喝酒"),
            synonyms = listOf("饮酒", "喝酒", "烟酒", "喝过酒"),
        ),
        SynonymGroup(
            triggers = listOf("家族", "一级亲属", "家庭中", "亲属"),
            synonyms = listOf("家族", "家里人", "家人", "父母", "爸爸", "妈妈", "兄弟姐妹", "亲属", "遗传"),
        ),
        SynonymGroup(
            triggers = listOf("月经"),
            synonyms = listOf("月经", "例假", "大姨妈", "末次月经"),
        ),
        SynonymGroup(
            triggers = listOf("睡眠"),
            synonyms = listOf("睡眠", "睡得", "睡觉", "失眠"),
        ),
        SynonymGroup(
            triggers = listOf("食欲", "胃口"),
            synonyms = listOf("食欲", "胃口", "饭量"),
        ),
        SynonymGroup(
            triggers = listOf("发热", "体温"),
            synonyms = listOf("发热", "发烧", "体温"),
        ),
        SynonymGroup(
            triggers = listOf("体重"),
            synonyms = listOf("体重", "消瘦", "变瘦", "瘦了"),
        ),
    )

    private val cjkRun = Regex("[\\u4e00-\\u9fff]+")
    private val latinWord = Regex("[a-z][a-z0-9]+")
    private val splitPattern = Regex(
        splitWords.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) },
    )

    fun of(medicalCase: MedicalCase): List<KeyQuestionSpec> =
        medicalCase.keyQuestions.mapIndexed { index, question ->
            KeyQuestionSpec(
                id = "${medicalCase.id}-Q${index + 1}",
                text = question,
                synonyms = synonymsFor(question),
                importance = when {
                    isRedFlag(question) -> 3
                    index < 5 -> 3
                    index < 9 -> 2
                    else -> 1
                },
                isRedFlag = isRedFlag(question),
                minEvidence = 1,
                keywords = keywordsFor(question),
            )
        }

    /** 可以单独作为覆盖证据的词：至少 2 个字，且不是通用词。 */
    internal fun isUsableTerm(term: String): Boolean =
        term.length >= 2 && term !in genericTerms

    internal fun keywordsFor(question: String): List<String> {
        val normalized = question.trim().lowercase()
        val chinese = cjkRun.findAll(normalized)
            .flatMap { run -> run.value.split(splitPattern).asSequence() }
            .map { trimModifiers(it) }
            .filter { isUsableTerm(it) }
            .toList()
        val latin = latinWord.findAll(normalized).map { it.value }.toList()
        return (chinese + latin).distinct()
    }

    private fun synonymsFor(question: String): List<String> {
        val normalized = question.lowercase()
        return synonymGroups
            .filter { group -> group.triggers.any { normalized.contains(it) } }
            .flatMap { it.synonyms }
            .distinct()
            .filter { isUsableTerm(it) }
    }

    private fun trimModifiers(segment: String): String {
        var value = segment
        var changed = true
        while (changed && value.isNotEmpty()) {
            changed = false
            leadingModifiers.firstOrNull { value.startsWith(it) && value.length > it.length }?.let {
                value = value.removePrefix(it)
                changed = true
            }
            trailingModifiers.firstOrNull { value.endsWith(it) && value.length > it.length }?.let {
                value = value.removeSuffix(it)
                changed = true
            }
        }
        return value
    }

    private fun isRedFlag(question: String): Boolean =
        redFlagTerms.any { question.contains(it) }
}
