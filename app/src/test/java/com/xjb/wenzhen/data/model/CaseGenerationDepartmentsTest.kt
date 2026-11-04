package com.xjb.wenzhen.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseGenerationDepartmentsTest {

    @Test
    fun `清单总数与分组数量符合设计`() {
        assertEquals(54, CaseGenerationDepartments.all.size)
        assertEquals(24, CaseGenerationDepartments.aiOnly.size)
        assertEquals(30, CaseGenerationDepartments.builtIn.size)
        assertEquals(8, CaseGenerationDepartments.groups.size)
    }

    @Test
    fun `八个分组项数依次为12 16 4 6 4 6 4 2`() {
        assertEquals(
            listOf(12, 16, 4, 6, 4, 6, 4, 2),
            CaseGenerationDepartments.groups.map { it.departments.size },
        )
    }

    @Test
    fun `全量清单无重复且分组内科室无重复`() {
        val all = CaseGenerationDepartments.all
        assertEquals(all.size, all.distinct().size)
        val flat = CaseGenerationDepartments.groups.flatMap { it.departments }
        assertEquals(54, flat.size)
        assertEquals(flat.size, flat.distinct().size)
    }

    @Test
    fun `内置30科全部出现在分组里`() {
        val builtIn = Departments.all.filter { it != Departments.ALL }
        assertEquals(30, builtIn.size)
        builtIn.forEach { department ->
            assertNotNull(
                "内置科室 $department 未归入任何分组",
                CaseGenerationDepartments.groupOf(department),
            )
            assertTrue(
                "内置科室 $department 未进入全量清单",
                department in CaseGenerationDepartments.all,
            )
        }
    }

    @Test
    fun `AI专属科室不进入内置科室清单`() {
        CaseGenerationDepartments.aiOnly.forEach { department ->
            assertFalse(
                "$department 不应出现在 Departments.all",
                department in Departments.all,
            )
        }
    }

    @Test
    fun `24个AI专属科室的症候提示均为典型症候必问红旗三段式`() {
        CaseGenerationDepartments.aiOnly.forEach { department ->
            val hint = CaseGenerationDepartments.hintFor(department)
            assertTrue("$department 缺少症候提示", hint.isNotBlank())
            assertTrue("$department 提示缺少典型症候", "典型症候" in hint)
            assertTrue("$department 提示缺少必问", "必问" in hint)
            assertTrue("$department 提示缺少红旗", "红旗" in hint)
        }
    }

    @Test
    fun `全部科室都有非空症候提示且未知科室有兜底`() {
        CaseGenerationDepartments.all.forEach { department ->
            assertTrue(
                "$department 缺少症候提示",
                CaseGenerationDepartments.hintFor(department).isNotBlank(),
            )
        }
        val unknown = "不存在的占位科室"
        assertTrue(
            "未知科室兜底提示为空",
            CaseGenerationDepartments.hintFor(unknown).isNotBlank(),
        )
        assertNull(
            "未知科室不应归入任何分组",
            CaseGenerationDepartments.groupOf(unknown),
        )
    }

    @Test
    fun `colorOf与darkColorOf按分组返回语义色而非兜底默认值`() {
        assertEquals(
            8,
            CaseGenerationDepartments.groups.map { it.color }.distinct().size,
        )
        assertEquals(
            8,
            CaseGenerationDepartments.groups.map { it.darkColor }.distinct().size,
        )
        CaseGenerationDepartments.groups.forEach { group ->
            group.departments.forEach { department ->
                assertEquals(
                    "科室 $department 浅色未按分组解析",
                    group.color,
                    CaseGenerationDepartments.colorOf(department),
                )
                assertEquals(
                    "科室 $department 深色未按分组解析",
                    group.darkColor,
                    CaseGenerationDepartments.darkColorOf(department),
                )
            }
        }
        assertTrue(
            "内科系统浅色不应等于兜底默认值",
            CaseGenerationDepartments.colorOf(Departments.CARDIOLOGY) != 0xFF00796BL,
        )
        assertTrue(
            "内科系统深色不应等于兜底默认值",
            CaseGenerationDepartments.darkColorOf(Departments.CARDIOLOGY) != 0xFF80CBC4L,
        )
    }
}
