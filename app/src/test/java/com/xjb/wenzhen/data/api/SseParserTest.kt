package com.xjb.wenzhen.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SseParserTest {
    @Test
    fun `空行和注释被忽略`() {
        assertSame(SseEvent.Ignore, SseParser.parseLine(""))
        assertSame(SseEvent.Ignore, SseParser.parseLine("   "))
        assertSame(SseEvent.Ignore, SseParser.parseLine(": keep-alive"))
        assertSame(SseEvent.Ignore, SseParser.parseLine("event: message"))
    }

    @Test
    fun `data delta 提取内容`() {
        val line = """data: {"choices":[{"delta":{"content":"胸口"}}]}"""
        assertEquals(SseEvent.Delta("胸口"), SseParser.parseLine(line))
    }

    @Test
    fun `role 或空 delta 被忽略`() {
        assertSame(
            SseEvent.Ignore,
            SseParser.parseLine("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""),
        )
        assertSame(
            SseEvent.Ignore,
            SseParser.parseLine("""data: {"choices":[{"delta":{"content":""}}]}"""),
        )
        assertSame(
            SseEvent.Ignore,
            SseParser.parseLine("""data: {"choices":[{"delta":{"content":null}}]}"""),
        )
    }

    @Test
    fun `仅包含 reasoning_content 的 delta 不输出`() {
        assertSame(
            SseEvent.Ignore,
            SseParser.parseLine(
                """data: {"choices":[{"delta":{"reasoning_content":"先思考一下"}}]}"""
            ),
        )
    }

    @Test
    fun `content 与 reasoning_content 同时存在时只输出 content`() {
        val line = """data: {"choices":[{"delta":{"reasoning_content":"思考","content":"胸口"}}]}"""
        assertEquals(SseEvent.Delta("胸口"), SseParser.parseLine(line))
    }

    @Test
    fun `DONE 结束事件可识别`() {
        assertSame(SseEvent.Done, SseParser.parseLine("data: [DONE]"))
        assertSame(SseEvent.Done, SseParser.parseLine("data:[DONE]"))
    }

    @Test
    fun `损坏 JSON 不中断流解析`() {
        assertSame(SseEvent.Ignore, SseParser.parseLine("data: {not-json"))
    }

    @org.junit.Test
    fun errorPayloadBecomesErrorEvent() {
        val event = SseParser.parseLine("data: {\"error\":{\"message\":\"quota\"}}")
        org.junit.Assert.assertEquals(SseEvent.Error("quota"), event)
    }
}
