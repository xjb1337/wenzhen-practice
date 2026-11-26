package com.xjb.wenzhen.domain

import kotlin.math.roundToInt

data class StatsRecord(
    val id: Long,
    val department: String,
    val totalScore: Int,
    val createdAt: Long,
    val missedQuestions: List<String> = emptyList(),
)

data class ScoreTrendPoint(
    val label: String,
    val score: Int,
    val createdAt: Long,
)

data class ScoreTrendSegment(
    val firstIndex: Int,
    val lastIndex: Int,
    val minScore: Int,
    val maxScore: Int,
    val averageScore: Float,
)

data class DepartmentAverage(
    val department: String,
    val averageScore: Double,
    val attempts: Int,
)

data class MissedQuestionCount(
    val question: String,
    val count: Int,
)

data class StatsSummary(
    val attempts: Int,
    val averageScore: Int,
    val trend: List<ScoreTrendPoint>,
    val departmentAverages: List<DepartmentAverage>,
    val topMissedQuestions: List<MissedQuestionCount>,
    val trendSegments: List<ScoreTrendSegment> = emptyList(),
)

object StatsCalculator {
    private const val MAX_CHART_SEGMENTS = 60

    fun calculate(records: List<StatsRecord>): StatsSummary {
        if (records.isEmpty()) {
            return StatsSummary(
                attempts = 0,
                averageScore = 0,
                trend = emptyList(),
                departmentAverages = emptyList(),
                topMissedQuestions = emptyList(),
            )
        }

        val chronological = records.sortedBy { it.createdAt }
        val trend = chronological.mapIndexed { index, record ->
            ScoreTrendPoint(
                label = "#${index + 1}",
                score = record.totalScore.coerceIn(0, 100),
                createdAt = record.createdAt,
            )
        }
        val departmentAverages = chronological
            .groupBy { it.department }
            .map { (department, items) ->
                DepartmentAverage(
                    department = department,
                    averageScore = roundOneDecimal(items.map { it.totalScore }.average()),
                    attempts = items.size,
                )
            }
            .sortedWith(compareByDescending<DepartmentAverage> { it.averageScore }.thenBy { it.department })

        val missedQuestions = chronological
            .flatMap { it.missedQuestions }
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(10)
            .map { MissedQuestionCount(question = it.key, count = it.value) }

        return StatsSummary(
            attempts = records.size,
            averageScore = chronological.map { it.totalScore }.average().roundToInt(),
            trend = trend,
            departmentAverages = departmentAverages,
            topMissedQuestions = missedQuestions,
            trendSegments = chartSegments(trend),
        )
    }

    private fun chartSegments(points: List<ScoreTrendPoint>): List<ScoreTrendSegment> {
        if (points.size <= MAX_CHART_SEGMENTS) return emptyList()
        val segmentSize = points.lastIndex / MAX_CHART_SEGMENTS + 1
        return points.chunked(segmentSize).mapIndexed { segmentIndex, items ->
            val first = segmentIndex * segmentSize
            ScoreTrendSegment(
                firstIndex = first,
                lastIndex = first + items.lastIndex,
                minScore = items.minOf { it.score },
                maxScore = items.maxOf { it.score },
                averageScore = items.sumOf { it.score }.toFloat() / items.size,
            )
        }
    }

    private fun roundOneDecimal(value: Double): Double =
        (value * 10.0).roundToInt() / 10.0
}
