package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.EncounterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceCarePlanBuilderTest {
    private val medicalCase = BuiltInCases.byId("cardio-chest-pain")!!

    @Test
    fun `门诊参考方案完整保留病例中的诊断依据检查治疗和随访`() {
        val plan = ReferenceCarePlanBuilder.build(medicalCase, EncounterType.OUTPATIENT)

        assertEquals(medicalCase.correctDiagnosis, plan.diagnosis)
        assertEquals("门诊", plan.encounterLabel)
        assertEquals(medicalCase.differentialDiagnoses, plan.differentialDiagnoses)
        assertTrue(plan.diagnosisBasis.any { it.contains(medicalCase.chiefComplaint) })
        assertTrue(plan.diagnosisBasis.any { it.startsWith("关键病史：") })
        assertTrue(plan.diagnosisBasis.any { it.contains(medicalCase.physicalExam) })
        assertTrue(plan.necessaryExams.any { it.contains("心电图") })
        assertEquals(
            listOf("治疗原则", "药物", "处理"),
            plan.treatmentSections.map { it.label },
        )
        assertTrue(plan.treatmentSections.first { it.label == "药物" }.content.contains("阿司匹林"))
        assertTrue(plan.precautions.single().startsWith("随访："))
        assertTrue(plan.precautions.single().contains("双联抗血小板12个月"))
    }

    @Test
    fun `住院参考方案增加既往史个人史和系统回顾`() {
        val outpatient = ReferenceCarePlanBuilder.build(medicalCase, EncounterType.OUTPATIENT)
        val inpatient = ReferenceCarePlanBuilder.build(medicalCase, EncounterType.INPATIENT)

        assertEquals("住院", inpatient.encounterLabel)
        assertFalse(outpatient.diagnosisBasis.any { it.startsWith("既往史：") })
        assertTrue(inpatient.diagnosisBasis.any { it.contains(medicalCase.pastHistory) })
        assertTrue(inpatient.diagnosisBasis.any { it.contains(medicalCase.personalHistory) })
        assertTrue(inpatient.diagnosisBasis.any { it.contains(medicalCase.reviewOfSystems) })
        assertEquals(outpatient.necessaryExams, inpatient.necessaryExams)
        assertTrue(inpatient.necessaryExams.all { item -> medicalCase.auxiliaryExams.contains(item) })
    }

    @Test
    fun `病例缺少治疗方案时明确提示而不会编造内容`() {
        val plan = ReferenceCarePlanBuilder.build(
            medicalCase.copy(treatmentPlan = ""),
            EncounterType.OUTPATIENT,
        )

        assertEquals("诊疗方案", plan.treatmentSections.single().label)
        assertTrue(plan.treatmentSections.single().content.contains("未提供明确"))
        assertTrue(plan.precautions.single().contains("未提供明确"))
    }

    @Test
    fun `无结构化标签的治疗方案仍按原文保留`() {
        val plan = ReferenceCarePlanBuilder.build(
            medicalCase.copy(treatmentPlan = "补液并观察病情。\n必要时转诊。"),
            EncounterType.OUTPATIENT,
        )
        val sections = plan.treatmentSections

        assertEquals(2, sections.size)
        assertTrue(sections.all { it.label == "诊疗方案" })
        assertEquals("补液并观察病情。", sections.first().content)
    }
}
