package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.PatientPersonality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `评分提示词包含五个满分项和JSON约束`() {
        val prompt = promptBuilder.buildScoreSystemPrompt(medicalCase)

        assertTrue(prompt.contains("问诊要点覆盖35分"))
        assertTrue(prompt.contains("问诊技巧与顺序15分"))
        assertTrue(prompt.contains("医患沟通15分"))
        assertTrue(prompt.contains("诊断准确20分"))
        assertTrue(prompt.contains("治疗方案15分"))
        assertTrue(prompt.contains("总分等于五项之和"))
        assertTrue(prompt.contains("\"totalScore\""))
        assertTrue(prompt.contains("只输出一个合法 JSON 对象"))
    }

    @Test
    fun `门诊与住院患者提示词按就诊类型区分`() {
        val outpatient = promptBuilder.buildPatientPrompt(
            medicalCase = medicalCase,
            encounterType = EncounterType.OUTPATIENT,
        )
        val inpatient = promptBuilder.buildPatientPrompt(
            medicalCase = medicalCase,
            encounterType = EncounterType.INPATIENT,
        )

        assertNotEquals(outpatient, inpatient)
        assertTrue(outpatient.contains(EncounterType.OUTPATIENT.label))
        assertTrue(inpatient.contains(EncounterType.INPATIENT.label))
        assertTrue(inpatient.contains("系统回顾"))
    }

    @Test
    fun `门诊与住院评分提示词按就诊类型区分`() {
        val outpatient = promptBuilder.buildScoreSystemPrompt(
            medicalCase = medicalCase,
            encounterType = EncounterType.OUTPATIENT,
        )
        val inpatient = promptBuilder.buildScoreSystemPrompt(
            medicalCase = medicalCase,
            encounterType = EncounterType.INPATIENT,
        )

        assertNotEquals(outpatient, inpatient)
        assertTrue(outpatient.contains("门诊不因未问系统回顾"))
        assertTrue(inpatient.contains("住院应评价系统回顾"))
    }

    @Test
    fun `病历用户提示词按就诊类型写入学生治疗方案`() {
        val outpatient = promptBuilder.buildRecordUserPrompt(
            messages = emptyList(),
            initialDiagnosis = "急性心肌梗死",
            diagnosisBasis = "持续胸痛伴大汗",
            encounterType = EncounterType.OUTPATIENT,
            treatmentAnswer = "立即转诊并双联抗血小板",
        )
        val inpatientWithoutTreatment = promptBuilder.buildRecordUserPrompt(
            messages = emptyList(),
            initialDiagnosis = "急性心肌梗死",
            diagnosisBasis = "持续胸痛伴大汗",
            encounterType = EncounterType.INPATIENT,
        )

        assertTrue(outpatient.contains("用于“处理意见”"))
        assertTrue(outpatient.contains("立即转诊并双联抗血小板"))
        assertTrue(inpatientWithoutTreatment.contains("用于“诊疗计划”"))
        assertTrue(inpatientWithoutTreatment.contains("未填写"))
        assertFalse(outpatient.contains("用于“诊疗计划”"))
    }

    @Test
    fun `病例生成提示词包含就诊类型并按门诊住院区分`() {
        val outpatient = promptBuilder.buildCaseGenerationPrompt(
            department = "心内科",
            difficulty = Difficulty.MEDIUM,
            personality = PatientPersonality.COOPERATIVE,
            encounterType = EncounterType.OUTPATIENT,
        )
        val inpatient = promptBuilder.buildCaseGenerationPrompt(
            department = "心内科",
            difficulty = Difficulty.MEDIUM,
            personality = PatientPersonality.COOPERATIVE,
            encounterType = EncounterType.INPATIENT,
        )

        assertNotEquals(outpatient, inpatient)
        assertTrue(outpatient.contains(EncounterType.OUTPATIENT.label))
        assertTrue(inpatient.contains(EncounterType.INPATIENT.label))
        assertTrue(outpatient.contains("按门诊场景编写"))
        assertTrue(inpatient.contains("按住院场景编写"))

        val elderly = promptBuilder.buildCaseGenerationPrompt(
            department = "心内科",
            difficulty = Difficulty.EASY,
            personality = PatientPersonality.ELDERLY,
            encounterType = EncounterType.INPATIENT,
        )
        assertTrue(elderly.contains("年龄必须大于等于60岁"))
        assertTrue(elderly.contains(EncounterType.INPATIENT.label))
    }
}
