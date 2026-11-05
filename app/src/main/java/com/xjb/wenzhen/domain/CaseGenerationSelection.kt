package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.CaseGenerationDepartments
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.PatientPersonality
import kotlin.random.Random

/**
 * AI 病例生成前由 UI 收集的筛选项。
 *
 * [department] 支持两个 UI sentinel：[RANDOM_DEPARTMENT] 表示从全部生成科室中随机，
 * [RANDOM] 表示从 [groupLabel] 对应分组中随机。其余枚举为 null 时表示在该维度随机。
 * 这些未解析值只允许停留在 UI/ViewModel 边界，进入 PromptBuilder 前必须调用 [resolve]。
 */
data class CaseGenerationSelection(
    val department: String,
    val groupLabel: String? = null,
    val difficulty: Difficulty? = null,
    val personality: PatientPersonality? = null,
    val encounterType: EncounterType? = null,
) {
    fun resolve(random: Random = Random.Default): ResolvedCaseGenerationSelection {
        val resolvedDepartment = when (department) {
            RANDOM_DEPARTMENT -> CaseGenerationDepartments.all.random(random)
            RANDOM -> randomGroup().departments.random(random)
            else -> department
        }
        return ResolvedCaseGenerationSelection(
            department = resolvedDepartment,
            difficulty = difficulty ?: Difficulty.entries.random(random),
            personality = personality ?: PatientPersonality.entries.random(random),
            encounterType = encounterType ?: EncounterType.entries.random(random),
        )
    }

    private fun randomGroup() = requireNotNull(
        groupLabel?.let { label ->
            CaseGenerationDepartments.groups.firstOrNull { it.label == label }
        },
    ) { "科室分组随机需要有效的 groupLabel" }

    companion object {
        const val RANDOM = "随机"
        const val RANDOM_DEPARTMENT = "随机科室"
    }
}

data class ResolvedCaseGenerationSelection(
    val department: String,
    val difficulty: Difficulty,
    val personality: PatientPersonality,
    val encounterType: EncounterType,
)
