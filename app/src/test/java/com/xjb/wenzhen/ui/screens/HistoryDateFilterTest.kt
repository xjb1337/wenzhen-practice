package com.xjb.wenzhen.ui.screens

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDateFilterTest {
    @Test
    fun `今天从设备时区午夜开始且排除未来记录`() {
        val now = Instant.parse("2026-11-26T16:10:00Z").toEpochMilli()
        val midnight = Instant.parse("2026-11-26T16:00:00Z").toEpochMilli()
        val range = HistoryDateFilter.TODAY.rangeAt(now, ZoneId.of("Asia/Shanghai"))

        assertFalse((midnight - 1) in range)
        assertTrue(midnight in range)
        assertTrue(now in range)
        assertFalse((now + 1) in range)
    }

    @Test
    fun `近七天和近三十天保留滚动时间窗口`() {
        val now = Instant.parse("2026-11-26T16:10:00Z").toEpochMilli()

        assertEquals((now - Duration.ofDays(7).toMillis())..now, HistoryDateFilter.LAST_WEEK.rangeAt(now))
        assertEquals((now - Duration.ofDays(30).toMillis())..now, HistoryDateFilter.LAST_MONTH.rangeAt(now))
    }
}
