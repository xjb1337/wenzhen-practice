package com.xjb.wenzhen.data.cases

import com.xjb.wenzhen.data.model.Departments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInCasesTest {
    @Test
    fun `病例库至少包含三十个字段完整且覆盖全部科室的病例`() {
        val cases = BuiltInCases.all

        assertTrue(cases.size >= 54)
        assertEquals(cases.size, cases.map { it.id }.distinct().size)
        Departments.all.filter { it != Departments.ALL }.forEach { department ->
            assertTrue("科室 $department 没有内置病例", cases.any { it.department == department })
        }
        assertTrue(cases.any { it.correctDiagnosis.contains("心肌梗死") })
        assertTrue(cases.any { it.correctDiagnosis.contains("阑尾炎") })
        assertTrue(cases.any { it.correctDiagnosis.contains("肺炎") })
        assertTrue(cases.any { it.correctDiagnosis.contains("蛛网膜下腔出血") })
        assertTrue(cases.any { it.correctDiagnosis.contains("2型糖尿病") })
        assertTrue(cases.any { it.correctDiagnosis.contains("上消化道出血") })
        assertTrue(cases.any { it.correctDiagnosis.contains("结石") })
        assertTrue(cases.any { it.correctDiagnosis.contains("轮状病毒肠炎") })
        assertTrue(cases.any { it.correctDiagnosis.contains("异位妊娠") })
        assertTrue(cases.any { it.correctDiagnosis.contains("哮喘") })

        cases.forEach { medicalCase ->
            val prefix = "病例 ${medicalCase.id} 的"
            assertTrue("${prefix}标题为空", medicalCase.title.isNotBlank())
            assertTrue("${prefix}科室为空", medicalCase.department.isNotBlank())
            assertTrue("${prefix}患者姓名为空", medicalCase.patient.name.isNotBlank())
            assertTrue("${prefix}患者年龄为空", medicalCase.patient.age.isNotBlank())
            assertTrue("${prefix}患者性别为空", medicalCase.patient.gender.isNotBlank())
            assertTrue("${prefix}患者职业为空", medicalCase.patient.occupation.isNotBlank())
            assertTrue("${prefix}开场白为空", medicalCase.openingLine.isNotBlank())
            assertTrue("${prefix}主诉为空", medicalCase.chiefComplaint.isNotBlank())
            assertTrue("${prefix}现病史为空", medicalCase.presentIllness.isNotBlank())
            assertTrue("${prefix}既往史为空", medicalCase.pastHistory.isNotBlank())
            assertTrue("${prefix}个人史为空", medicalCase.personalHistory.isNotBlank())
            assertTrue("${prefix}婚育史为空", medicalCase.maritalHistory.isNotBlank())
            assertTrue("${prefix}家族史为空", medicalCase.familyHistory.isNotBlank())
            assertTrue("${prefix}系统回顾为空", medicalCase.reviewOfSystems.isNotBlank())
            assertTrue("${prefix}体格检查为空", medicalCase.physicalExam.isNotBlank())
            assertTrue("${prefix}辅助检查为空", medicalCase.auxiliaryExams.isNotBlank())
            assertTrue("${prefix}正确诊断为空", medicalCase.correctDiagnosis.isNotBlank())
            assertTrue("${prefix}问诊要点应为10至15条", medicalCase.keyQuestions.size in 10..15)
            assertTrue("${prefix}问诊要点不能为空", medicalCase.keyQuestions.all { it.isNotBlank() })
            assertTrue("${prefix}鉴别诊断不能为空", medicalCase.differentialDiagnoses.isNotEmpty())
            assertTrue("${prefix}鉴别诊断不能为空字符串", medicalCase.differentialDiagnoses.all { it.isNotBlank() })
            assertTrue("${prefix}治疗方案为空", medicalCase.treatmentPlan.isNotBlank())
            assertFalse("${prefix}标题不应直接包含诊断", medicalCase.title.contains(medicalCase.correctDiagnosis))
        }
    }
}
