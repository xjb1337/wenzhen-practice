package com.xjb.wenzhen.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {
    @Test
    fun `趋势按时间排序并计算总平均`() {
        val summary = StatsCalculator.calculate(
            listOf(
                record(1, "心内科", 90, 300, emptyList()),
                record(2, "心内科", 70, 100, emptyList()),
                record(3, "呼吸内科", 80, 200, emptyList()),
            )
        )

        assertEquals(3, summary.attempts)
        assertEquals(80, summary.averageScore)
        assertEquals(listOf(70, 80, 90), summary.trend.map { it.score })
        assertEquals(listOf(100L, 200L, 300L), summary.trend.map { it.createdAt })
    }

    @Test
    fun `科室平均分和次数正确`() {
        val summary = StatsCalculator.calculate(
            listOf(
                record(1, "心内科", 90, 100, emptyList()),
                record(2, "心内科", 81, 200, emptyList()),
                record(3, "儿科", 70, 300, emptyList()),
            )
        )

        val cardiology = summary.departmentAverages.first { it.department == "心内科" }
        assertEquals(2, cardiology.attempts)
        assertEquals(85.5, cardiology.averageScore, 0.001)
        assertEquals("心内科", summary.departmentAverages.first().department)
    }

    @Test
    fun `遗漏要点按频次排序且最多十项`() {
        val records = (1..12).map { index ->
            record(
                id = index.toLong(),
                department = "心内科",
                score = 60 + index,
                createdAt = index.toLong(),
                missed = listOf("未问吸烟史", "遗漏要点$index"),
            )
        }
        val summary = StatsCalculator.calculate(records)

        assertEquals(10, summary.topMissedQuestions.size)
        assertEquals("未问吸烟史", summary.topMissedQuestions.first().question)
        assertEquals(12, summary.topMissedQuestions.first().count)
        assertTrue(summary.topMissedQuestions.all { it.count > 0 })
    }

    @Test
    fun `空记录返回空统计`() {
        val summary = StatsCalculator.calculate(emptyList())
        assertEquals(0, summary.attempts)
        assertEquals(0, summary.averageScore)
        assertTrue(summary.trend.isEmpty())
        assertTrue(summary.departmentAverages.isEmpty())
        assertTrue(summary.topMissedQuestions.isEmpty())
    }

    private fun record(
        id: Long,
        department: String,
        score: Int,
        createdAt: Long,
        missed: List<String>,
    ) = StatsRecord(
        id = id,
        department = department,
        totalScore = score,
        createdAt = createdAt,
        missedQuestions = missed,
    )
}
