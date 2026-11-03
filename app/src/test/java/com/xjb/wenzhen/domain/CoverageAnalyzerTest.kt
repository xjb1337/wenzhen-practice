package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageAnalyzerTest {
    @Test
    fun `根据医生提问识别已覆盖和遗漏方面`() {
        val messages = listOf(
            ChatMessage(MessageRole.PATIENT, "我胸口疼"),
            ChatMessage(MessageRole.DOCTOR, "您胸痛多久了？有没有高血压和吸烟史？"),
            ChatMessage(MessageRole.DOCTOR, "家里父母有没有类似疾病？最近睡眠怎么样？"),
        )

        val report = CoverageAnalyzer.analyze(messages)

        assertTrue(report.topics.first { it.topic == HistoryTopic.CHIEF_COMPLAINT }.covered)
        assertTrue(report.topics.first { it.topic == HistoryTopic.PAST_HISTORY }.covered)
        assertTrue(report.topics.first { it.topic == HistoryTopic.PERSONAL_HISTORY }.covered)
        assertTrue(report.topics.first { it.topic == HistoryTopic.FAMILY_HISTORY }.covered)
        assertTrue(report.topics.first { it.topic == HistoryTopic.REVIEW_OF_SYSTEMS }.covered)
        assertFalse(report.topics.first { it.topic == HistoryTopic.MARITAL_HISTORY }.covered)
        assertTrue(HistoryTopic.MARITAL_HISTORY in report.missing)
    }

    @Test
    fun `患者回答不计入医生的问诊覆盖`() {
        val messages = listOf(
            ChatMessage(MessageRole.PATIENT, "我结婚了，有孩子，父亲有高血压，晚上睡不好。"),
        )

        val report = CoverageAnalyzer.analyze(messages)

        assertTrue(report.coveredCount == 0)
        assertEquals(HistoryTopic.entries.size, report.missing.size)
    }

    @Test
    fun `完整结构问诊覆盖全部七项`() {
        val report = CoverageAnalyzer.analyzeText(
            "您哪里不舒服，多久了，诱因和伴随症状是什么；以前有高血压、糖尿病、手术和过敏史吗；吸烟喝酒职业如何；结婚生育月经情况；家族父母遗传病；睡眠食欲大小便咳嗽视力关节情绪怎么样"
        )

        assertTrue(report.missing.isEmpty())
        assertEquals(7, report.coveredCount)
    }
}
