package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MessageRole

enum class HistoryTopic(val label: String) {
    CHIEF_COMPLAINT("主诉"),
    PRESENT_ILLNESS("现病史"),
    PAST_HISTORY("既往史"),
    PERSONAL_HISTORY("个人史"),
    MARITAL_HISTORY("婚育史"),
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
    private val keywords: Map<HistoryTopic, List<String>> = mapOf(
        HistoryTopic.CHIEF_COMPLAINT to listOf(
            "哪里不舒服", "怎么不舒服", "什么不舒服", "什么症状", "主诉", "多久了",
            "什么时候开始", "从什么时候", "哪里疼", "哪里痛", "怎么了",
        ),
        HistoryTopic.PRESENT_ILLNESS to listOf(
            "诱因", "怎么开始", "突然", "加重", "缓解", "减轻", "持续", "性质",
            "放射", "伴随", "有没有", "发热", "恶心", "呕吐", "出汗", "头晕",
            "疼痛", "发作", "频率", "程度", "变化",
        ),
        HistoryTopic.PAST_HISTORY to listOf(
            "既往", "以前", "过去", "病史", "慢性病", "高血压", "糖尿病", "手术",
            "住院", "过敏", "药物", "吃药", "输血", "肝炎", "结核",
        ),
        HistoryTopic.PERSONAL_HISTORY to listOf(
            "吸烟", "抽烟", "饮酒", "喝酒", "职业", "工作", "出生", "居住",
            "作息", "饮食", "运动", "毒品", "接触", "宠物",
        ),
        HistoryTopic.MARITAL_HISTORY to listOf(
            "结婚", "婚姻", "配偶", "伴侣", "生育", "孩子", "子女", "怀孕",
            "妊娠", "月经", "末次月经", "性生活", "避孕",
        ),
        HistoryTopic.FAMILY_HISTORY to listOf(
            "家族", "父母", "父亲", "母亲", "兄弟", "姐妹", "遗传", "家人",
        ),
        HistoryTopic.REVIEW_OF_SYSTEMS to listOf(
            "睡眠", "食欲", "体重", "大便", "小便", "尿", "腹泻", "便秘",
            "咳嗽", "咳痰", "胸闷", "心慌", "视力", "听力", "皮疹", "关节",
            "情绪", "精神",
        ),
    )

    fun analyze(messages: List<ChatMessage>): CoverageReport {
        val doctorText = messages
            .filter { it.role == MessageRole.DOCTOR }
            .joinToString(separator = "\n") { it.content }
        return analyzeText(doctorText)
    }

    fun analyzeText(doctorText: String): CoverageReport {
        val text = doctorText.lowercase()
        return CoverageReport(
            topics = HistoryTopic.entries.map { topic ->
                TopicCoverage(
                    topic = topic,
                    covered = keywords[topic].orEmpty().any { keyword ->
                        text.contains(keyword.lowercase())
                    },
                )
            },
        )
    }
}
