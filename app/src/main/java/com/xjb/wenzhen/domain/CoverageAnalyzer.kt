package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.MessageRole

enum class HistoryTopic(val label: String) {
    CHIEF_COMPLAINT("主诉"),
    PRESENT_ILLNESS("现病史"),
    PAST_HISTORY("既往史/过敏/用药"),
    PERSONAL_HISTORY("个人史"),
    MARITAL_HISTORY("婚育史"),
    MENSTRUAL_HISTORY("月经史"),
    FAMILY_HISTORY("家族史"),
    REVIEW_OF_SYSTEMS("系统回顾"),
}

data class TopicCoverage(
    val topic: HistoryTopic,
    val covered: Boolean,
)

data class CoverageReport(
    val topics: List<TopicCoverage>,
) {
    val coveredCount: Int
        get() = topics.count { it.covered }

    val missing: List<HistoryTopic>
        get() = topics.filterNot { it.covered }.map { it.topic }
}

object CoverageAnalyzer {
    private val outpatientTopics = listOf(
        HistoryTopic.CHIEF_COMPLAINT,
        HistoryTopic.PRESENT_ILLNESS,
        HistoryTopic.PAST_HISTORY,
    )

    private val keywords: Map<HistoryTopic, List<String>> = mapOf(
        HistoryTopic.CHIEF_COMPLAINT to listOf(
            "哪里不舒服", "怎么不舒服", "什么不舒服", "什么症状", "主诉", "多久了",
            "什么时候开始", "从什么时候", "哪里疼", "哪里痛", "怎么了",
            "哪儿不舒服", "哪不舒服", "哪儿疼", "哪儿痛", "什么问题", "为什么来", "来看什么",
            "几天了", "多长时间", "多少天", "多久", "怎么回事", "啥情况", "啥问题",
        ),
        HistoryTopic.PRESENT_ILLNESS to listOf(
            "诱因", "怎么开始", "突然", "加重", "缓解", "减轻", "持续", "性质",
            "放射", "伴随", "有没有", "发热", "恶心", "呕吐", "出汗", "头晕",
            "疼痛", "发作", "频率", "程度", "变化", "什么样的", "哪种", "位置",
            "部位", "检查过", "看过医生", "治疗过", "吃过什么药", "用过什么药", "效果",
        ),
        HistoryTopic.PAST_HISTORY to listOf(
            "既往", "以前", "过去", "病史", "慢性病", "高血压", "糖尿病", "手术",
            "住院", "过敏", "药物", "吃药", "输血", "肝炎", "结核",
            "平时身体", "身体怎么样", "基础病", "什么病", "开过刀", "动过刀", "打过疫苗", "疫苗",
            "长期服用", "在吃什么药", "冠心病", "心脏病",
        ),
        HistoryTopic.PERSONAL_HISTORY to listOf(
            "吸烟", "抽烟", "饮酒", "喝酒", "职业", "工作", "出生", "居住",
            "作息", "饮食", "运动", "毒品", "接触", "宠物",
            "烟", "酒", "做什么工作", "干什么的", "去过哪", "旅行", "外地", "疫区",
        ),
        HistoryTopic.MARITAL_HISTORY to listOf(
            "结婚", "婚姻", "配偶", "伴侣", "生育", "孩子", "子女", "怀孕",
            "妊娠", "性生活", "避孕", "成家", "爱人", "老公", "老婆", "丈夫", "妻子", "小孩",
        ),
        HistoryTopic.MENSTRUAL_HISTORY to listOf(
            "月经", "末次月经", "经期", "痛经", "绝经", "例假", "大姨妈", "来事",
        ),
        HistoryTopic.FAMILY_HISTORY to listOf(
            "家族", "父母", "父亲", "母亲", "兄弟", "姐妹", "遗传", "家人",
            "家里人", "家里有人", "亲属", "亲戚", "爸爸", "妈妈", "爸妈",
        ),
        HistoryTopic.REVIEW_OF_SYSTEMS to listOf(
            "睡眠", "食欲", "体重", "大便", "小便", "尿", "腹泻", "便秘",
            "咳嗽", "咳痰", "胸闷", "心慌", "视力", "听力", "皮疹", "关节",
            "情绪", "精神", "睡得", "胃口", "吃饭", "瘦了", "胖了", "大小便", "排便", "解手", "心情",
        ),
    )

    fun analyze(
        messages: List<ChatMessage>,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
        gender: String? = null,
        age: String? = null,
    ): CoverageReport {
        val doctorText = messages
            .filter { it.role == MessageRole.DOCTOR }
            .joinToString(separator = "\n") { it.content }
        return analyzeText(doctorText, encounterType, gender, age)
    }

    fun analyzeText(
        doctorText: String,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
        gender: String? = null,
        age: String? = null,
    ): CoverageReport {
        val text = doctorText.lowercase()
        val topics = if (encounterType == EncounterType.OUTPATIENT) {
            outpatientTopics
        } else {
            outpatientTopics + listOfNotNull(
                HistoryTopic.PERSONAL_HISTORY,
                HistoryTopic.MARITAL_HISTORY,
                HistoryTopic.MENSTRUAL_HISTORY.takeIf {
                    gender?.contains("女") == true && ageInYears(age)?.let { it in 12..55 } == true
                },
                HistoryTopic.FAMILY_HISTORY,
                HistoryTopic.REVIEW_OF_SYSTEMS,
            )
        }
        return CoverageReport(
            topics = topics.map { topic ->
                TopicCoverage(
                    topic = topic,
                    covered = keywords[topic].orEmpty().any { keyword ->
                        text.contains(keyword.lowercase())
                    },
                )
            },
        )
    }

    private fun ageInYears(age: String?): Int? = age
        ?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
}
