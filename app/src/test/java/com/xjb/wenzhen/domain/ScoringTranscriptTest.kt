package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.ui.screens.availableResultTabs
import com.xjb.wenzhen.ui.screens.ResultTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTranscriptTest {
    private val promptBuilder = PromptBuilder()
    private val medicalCase = BuiltInCases.byId("cardio-chest-pain")!!

    @Test
    fun `评分对话完整不截断且逐条编号`() {
        val messages = (1..60).flatMap { i ->
            listOf(
                ChatMessage(MessageRole.DOCTOR, "问题$i"),
                ChatMessage(MessageRole.PATIENT, "回答$i"),
            )
        }
        val prompt = promptBuilder.buildScoreUserPrompt(messages, "诊断", "依据")
        assertTrue(prompt.contains("[1] 医生：问题1"))
        assertTrue(prompt.contains("[120] 患者：回答60"))
    }

    @Test
    fun `检查卡片带标题保留，发送失败的医生消息不计入`() {
        val messages = listOf(
            ChatMessage(MessageRole.DOCTOR, "抽烟吗？", deliveryState = MessageDeliveryState.FAILED),
            ChatMessage(MessageRole.DOCTOR, "胸痛多久了？"),
            ChatMessage(MessageRole.SYSTEM, "ST 段抬高", systemTitle = "心电图"),
        )
        val prompt = promptBuilder.buildScoreUserPrompt(messages, "", "")
        assertFalse(prompt.contains("抽烟吗"))
        assertTrue(prompt.contains("[1] 医生：胸痛多久了？"))
        assertTrue(prompt.contains("[2] 系统检查（心电图）：ST 段抬高"))
    }

    @Test
    fun `评分规则要求语义等价计分并引用证据`() {
        val prompt = promptBuilder.buildScoreSystemPrompt(medicalCase)
        assertTrue(prompt.contains("语义等价即算问到"))
        assertTrue(prompt.contains("患者主动交代"))
        assertTrue(prompt.contains("（见[编号]）"))
        assertTrue(prompt.contains("不要只看最后几轮"))
    }

    @Test
    fun `口语化问法也计入覆盖`() {
        val report = CoverageAnalyzer.analyzeText(
            "您哪儿不舒服？几天了？最近加重了吗？平时身体怎么样？家里人有类似的吗？抽不抽烟？晚上睡得好吗？例假正常吗？爱人身体好吗？",
            EncounterType.INPATIENT,
            gender = "女",
            age = "30岁",
        )
        assertEquals(emptyList<HistoryTopic>(), report.missing)
    }

    @Test
    fun `结果页标签按内容显示对话`() {
        assertEquals(listOf(ResultTab.REVIEW), availableResultTabs(false, false))
        assertEquals(
            listOf(ResultTab.RECORD, ResultTab.REVIEW, ResultTab.TRANSCRIPT),
            availableResultTabs(true, true),
        )
    }
}
