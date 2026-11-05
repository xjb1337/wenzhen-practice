package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.CaseGenerationDepartments
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.PatientPersonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CaseGenerationSelectionTest {
    @Test
    fun `本组随机科室只从当前分组取值`() {
        val group = CaseGenerationDepartments.groups.first { it.label == "妇儿与生殖" }

        val resolvedDepartments = (0 until 200).map { seed ->
            CaseGenerationSelection(
                department = CaseGenerationSelection.RANDOM,
                groupLabel = group.label,
            ).resolve(Random(seed)).department
        }

        assertTrue(resolvedDepartments.all { it in group.departments })
        assertTrue(resolvedDepartments.distinct().size > 1)
    }

    @Test
    fun `全库随机科室从全部可生成科室取值`() {
        val currentGroup = CaseGenerationDepartments.groups.first { it.label == "职业与遗传" }

        val resolvedDepartments = (0 until 200).map { seed ->
            CaseGenerationSelection(
                department = CaseGenerationSelection.RANDOM_DEPARTMENT,
                groupLabel = currentGroup.label,
            ).resolve(Random(seed)).department
        }

        assertTrue(resolvedDepartments.all { it in CaseGenerationDepartments.all })
        assertTrue(resolvedDepartments.any { it !in currentGroup.departments })
    }

    @Test
    fun `随机枚举解析结果始终属于合法集合`() {
        repeat(200) { seed ->
            val resolved = CaseGenerationSelection(
                department = CaseGenerationSelection.RANDOM_DEPARTMENT,
                difficulty = null,
                personality = null,
                encounterType = null,
            ).resolve(Random(seed))

            assertTrue(resolved.difficulty in Difficulty.entries)
            assertTrue(resolved.personality in PatientPersonality.entries)
            assertTrue(resolved.encounterType in EncounterType.entries)
        }
    }

    @Test
    fun `具体筛选项解析后保持原值`() {
        val selection = CaseGenerationSelection(
            department = "心内科",
            groupLabel = "内科系统",
            difficulty = Difficulty.HARD,
            personality = PatientPersonality.ANXIOUS,
            encounterType = EncounterType.INPATIENT,
        )

        val resolved = selection.resolve(Random(7))

        assertEquals("心内科", resolved.department)
        assertEquals(Difficulty.HARD, resolved.difficulty)
        assertEquals(PatientPersonality.ANXIOUS, resolved.personality)
        assertEquals(EncounterType.INPATIENT, resolved.encounterType)
    }

    @Test
    fun `随机筛选项解析后以具体值写入提示词`() {
        val resolved = CaseGenerationSelection(
            department = CaseGenerationSelection.RANDOM_DEPARTMENT,
            difficulty = null,
            personality = null,
            encounterType = null,
        ).resolve(Random(42))

        val prompt = PromptBuilder().buildCaseGenerationPrompt(
            department = resolved.department,
            difficulty = resolved.difficulty,
            personality = resolved.personality,
            encounterType = resolved.encounterType,
            departmentHint = CaseGenerationDepartments.hintFor(resolved.department),
        )

        assertTrue(prompt.contains("科室：${resolved.department}"))
        assertTrue(prompt.contains("难度：${resolved.difficulty.label}"))
        assertTrue(prompt.contains("患者性格：${resolved.personality.label}"))
        assertTrue(prompt.contains("就诊类型：${resolved.encounterType.label}"))
        assertFalse(prompt.contains(CaseGenerationSelection.RANDOM_DEPARTMENT))
        assertFalse(prompt.contains("科室：${CaseGenerationSelection.RANDOM}"))
        assertFalse(prompt.contains("患者性格：${CaseGenerationSelection.RANDOM}"))
    }
}
