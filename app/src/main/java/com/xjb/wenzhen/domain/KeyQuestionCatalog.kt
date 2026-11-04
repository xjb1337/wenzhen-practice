package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.MedicalCase

/**
 * 病例级问诊要点的结构化视图。
 *
 * 内置病例的 keyQuestions 仍是便于编辑的纯文本；这里在运行时补齐稳定 ID、同义表达、
 * 重要程度、红旗标记和最低证据要求，让覆盖统计从“通用关键词”升级为“病例级要点”。
 */
data class KeyQuestionSpec(
    val id: String,
    val text: String,
    val synonyms: List<String> = emptyList(),
    val importance: Int = 2,
    val isRedFlag: Boolean = false,
    val minEvidence: Int = 1,
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

    private val synonymGroups = listOf(
        listOf("多久", "多长时间", "几天", "什么时候开始", "病程"),
        listOf("诱因", "什么情况下", "怎么开始", "发作诱因"),
        listOf("性质", "什么样的", "怎么个疼法", "疼痛特点"),
        listOf("放射", "牵涉", "往哪里串"),
        listOf("伴随", "有没有同时", "还有哪里不舒服"),
        listOf("既往", "以前", "过去", "病史", "基础病"),
        listOf("过敏", "药物过敏", "过敏史"),
        listOf("用药", "吃药", "服药", "长期服用"),
        listOf("吸烟", "抽烟", "烟"),
        listOf("饮酒", "喝酒", "酒"),
        listOf("家族", "父母", "家里人", "亲属", "遗传"),
        listOf("月经", "例假", "大姨妈", "末次月经"),
        listOf("睡眠", "睡得", "睡觉", "失眠"),
        listOf("食欲", "胃口", "吃饭"),
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
            )
        }

    private fun synonymsFor(question: String): List<String> =
        synonymGroups
            .filter { group -> group.any { question.contains(it) } }
            .flatten()
            .distinct()

    private fun isRedFlag(question: String): Boolean =
        redFlagTerms.any { question.contains(it) }
}
