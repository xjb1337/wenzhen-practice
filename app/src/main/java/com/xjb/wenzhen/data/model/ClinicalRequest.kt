package com.xjb.wenzhen.data.model

enum class ClinicalRequestType(
    val label: String,
    val actionLabel: String,
    val suggestions: List<String>,
) {
    PHYSICAL_EXAM(
        label = "查体",
        actionLabel = "进行查体",
        suggestions = listOf(
            "生命体征",
            "一般情况",
            "心肺听诊",
            "腹部检查",
            "神经系统检查",
            "专科查体",
        ),
    ),
    LAB_OR_IMAGING(
        label = "化验/检查",
        actionLabel = "开具检查",
        suggestions = listOf(
            "血常规",
            "肝肾功能",
            "电解质",
            "血糖",
            "心肌酶",
            "尿常规",
            "心电图",
            "胸部X线",
            "腹部超声",
            "CT",
        ),
    ),
}
