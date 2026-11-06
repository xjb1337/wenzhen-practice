package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C-2 验收：通用词不能覆盖多个要点；每个内置要点都有可用的非通用关键词。 */
class QuestionCoverageAnalyzerTest {
    private val cardio = BuiltInCases.byId("cardio-chest-pain")!!

    private fun doctor(content: String) = ChatMessage(role = MessageRole.DOCTOR, content = content)

    private fun coveredCount(case: MedicalCase, vararg lines: String): Int =
        QuestionCoverageAnalyzer.analyze(case, lines.map { doctor(it) }).count { it.covered }

    @Test
    fun `一句这个多长时间了在每个内置病例中最多覆盖一个要点`() {
        BuiltInCases.all.forEach { case ->
            val covered = coveredCount(case, "这个多长时间了")
            assertTrue("病例 ${case.id} 被“这个多长时间了”覆盖了 $covered 个要点", covered <= 1)
        }
        assertEquals(1, coveredCount(cardio, "这个多长时间了"))
    }

    @Test
    fun `寒暄和空话不覆盖任何要点`() {
        val fillers = listOf(
            "您好，我是接诊医生。请问您哪里不舒服？",
            "还有其他不舒服吗？",
            "情况怎么样？",
            "嗯？",
            "好的？",
            "还有吗？",
            "然后呢？",
        )
        BuiltInCases.all.forEach { case ->
            fillers.forEach { line ->
                assertEquals("病例 ${case.id} 被“$line”覆盖", 0, coveredCount(case, line))
            }
        }
    }

    @Test
    fun `内置病例每个问诊要点至少有一个非通用关键词`() {
        BuiltInCases.all.forEach { case ->
            QuestionCoverageAnalyzer.matchTerms(case).forEach { terms ->
                val label = "病例 ${case.id} 的要点“${terms.spec.text}”"
                assertTrue("${label}没有可用关键词", terms.keywords.isNotEmpty())
                terms.keywords.forEach { keyword ->
                    assertTrue("${label}含通用词或单字“$keyword”", KeyQuestionCatalog.isUsableTerm(keyword))
                }
            }
        }
    }

    @Test
    fun `关键词不再包含任意两字切片和单字别名`() {
        val terms = QuestionCoverageAnalyzer.matchTerms(cardio)
            .first { it.spec.text == "胸痛开始的时间、诱因和持续时间" }
            .keywords
        listOf("时间", "开始", "持续", "痛开", "始的").forEach { generic ->
            assertFalse("关键词里不应出现“$generic”", generic in terms)
        }
        val allTerms = QuestionCoverageAnalyzer.matchTerms(cardio).flatMap { it.keywords }
        assertFalse("烟" in allTerms)
        assertFalse("酒" in allTerms)
    }

    @Test
    fun `单字烟酒不再算问到吸烟饮酒`() {
        val question = "吸烟、饮酒等危险因素"
        fun covered(line: String) = QuestionCoverageAnalyzer.analyze(cardio, listOf(doctor(line)))
            .first { it.question == question }
            .covered

        assertFalse(covered("烟呢？"))
        assertFalse(covered("酒呢？"))
        assertTrue(covered("抽烟吗？"))
        assertTrue(covered("平时喝酒吗？"))
        assertTrue(covered("抽不抽烟？"))
    }

    @Test
    fun `被多个要点共用的症状词不能单独证明覆盖`() {
        // “胸痛”同时出现在起病时间、部位性质、既往类似发作三个要点里，单说“胸痛”不能证明问到其中任何一个。
        assertEquals(0, coveredCount(cardio, "胸痛厉害吗？"))
        assertEquals(1, coveredCount(cardio, "胸痛多久了？"))
    }

    @Test
    fun `口语化的具体问法仍能覆盖对应要点`() {
        val evidence = QuestionCoverageAnalyzer.analyze(
            cardio,
            listOf(
                doctor("胸口疼了多久了？"),
                doctor("会往肩膀或者后背串吗？"),
                doctor("家里人有心脏病吗？"),
                doctor("最近睡得怎么样？"),
            ),
        )
        val covered = evidence.filter { it.covered }.map { it.question }
        assertTrue(covered.contains("胸痛开始的时间、诱因和持续时间"))
        assertTrue(covered.contains("疼痛是否向肩部、背部或下颌放射"))
        assertTrue(covered.contains("冠心病及早发心血管病家族史"))
        assertTrue(covered.contains("发病后的一般状态和饮食睡眠"))
    }
}
