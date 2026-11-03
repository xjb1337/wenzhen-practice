package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.cases.BuiltInCases
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val promptBuilder = PromptBuilder()
    private val medicalCase = BuiltInCases.byId("cardio-chest-pain")!!

    @Test
    fun `病人提示词包含隐藏资料且明确禁止泄露诊断`() {
        val prompt = promptBuilder.buildPatientPrompt(medicalCase)

        assertTrue(prompt.contains(medicalCase.chiefComplaint))
        assertTrue(prompt.contains(medicalCase.presentIllness))
        assertTrue(prompt.contains(medicalCase.pastHistory))
        assertTrue(prompt.contains(medicalCase.physicalExam))
        assertTrue(prompt.contains(medicalCase.correctDiagnosis))
        assertTrue(prompt.contains("绝不能主动透露"))
        assertTrue(prompt.contains("只回答医生实际问到的内容"))
        assertTrue(prompt.contains("这需要医生检查哦"))
        assertFalse(prompt.contains("请主动告诉医生诊断"))
        assertFalse(prompt.contains("可以直接说出诊断"))
    }

    @Test
    fun `病历提示词规定系统回顾位于体格检查之后`() {
        val prompt = promptBuilder.buildRecordSystemPrompt(medicalCase)

        val physicalExamIndex = prompt.indexOf("体格检查")
        val reviewOfSystemsIndex = prompt.indexOf("系统回顾")
        assertTrue(physicalExamIndex >= 0)
        assertTrue(reviewOfSystemsIndex > physicalExamIndex)
        assertTrue(prompt.contains("系统回顾必须写在体格检查之后"))
        assertTrue(prompt.contains("未问及"))
    }

    @Test
    fun `评分提示词包含四个满分项和JSON约束`() {
        val prompt = promptBuilder.buildScoreSystemPrompt(medicalCase)

        assertTrue(prompt.contains("问诊要点覆盖40分"))
        assertTrue(prompt.contains("问诊技巧与顺序20分"))
        assertTrue(prompt.contains("医患沟通20分"))
        assertTrue(prompt.contains("诊断准确20分"))
        assertTrue(prompt.contains("\"totalScore\""))
        assertTrue(prompt.contains("只输出一个合法 JSON 对象"))
    }
}
