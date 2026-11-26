package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.ClinicalRequestType
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.model.PatientPersonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAndCoverageTest {
    private val medicalCase = BuiltInCases.all.first()

    private fun doctor(content: String, state: MessageDeliveryState = MessageDeliveryState.SENT) =
        ChatMessage(role = MessageRole.DOCTOR, content = content, deliveryState = state)

    private fun patient(content: String) = ChatMessage(role = MessageRole.PATIENT, content = content)

    @Test
    fun `失败发送的医生消息不应作为问诊覆盖证据`() {
        val failed = doctor(medicalCase.keyQuestions.first(), MessageDeliveryState.FAILED)
        val evidence = QuestionCoverageAnalyzer.analyze(medicalCase, listOf(failed))
        assertTrue(evidence.none { it.covered })
        assertTrue(evidence.all { it.messageIndexes.isEmpty() })
    }

    @Test
    fun `已发送的医生问题应被识别为覆盖并给出消息索引`() {
        val question = medicalCase.keyQuestions.first()
        val messages = listOf(
            doctor("请问您哪里不舒服？"),
            doctor(question),
            patient("我不太清楚"),
        )
        val evidence = QuestionCoverageAnalyzer.analyze(medicalCase, messages)
        val hit = evidence.first { it.question == question }
        assertTrue(hit.covered)
        // 证据编号与评分转写同一口径：1-based，且医生问题是转写里的第 2 条。
        assertEquals(listOf(2), hit.messageIndexes)
    }


    @Test
    fun `离线性格改变问答方式并保留病例原文`() {
        val olderCase = medicalCase.copy(patient = medicalCase.patient.copy(age = "68岁"))
        val firstQuestion = doctor("胸口不舒服多久了？", MessageDeliveryState.SENDING)
        val opening = listOf(doctor(DOCTOR_OPENING_LINE), patient(medicalCase.openingLine))
        val firstTurn = opening + firstQuestion
        val timing = medicalCase.presentIllness.substringBefore('。') + "。"
        fun reply(personality: PatientPersonality, messages: List<ChatMessage> = firstTurn) =
            OfflinePracticeService.patientReply(olderCase, messages, personality)

        assertEquals(timing, reply(PatientPersonality.COOPERATIVE))
        assertTrue(reply(PatientPersonality.ANXIOUS).startsWith(timing))
        assertTrue(reply(PatientPersonality.ANXIOUS).contains("要不要紧"))
        assertTrue(reply(PatientPersonality.UNCLEAR, opening + doctor("您能说说哪里不舒服吗？")).contains("具体一点"))
        assertEquals(timing, reply(PatientPersonality.UNCLEAR))
        assertTrue(reply(PatientPersonality.ELDERLY).contains("没听清"))
        assertTrue(reply(PatientPersonality.ELDERLY, opening + listOf(
            firstQuestion.copy(deliveryState = MessageDeliveryState.SENT),
            patient("医生，我刚才没听清，您能再说一遍吗？"),
            firstQuestion,
        )).contains(timing))
        assertEquals(timing, OfflinePracticeService.patientReply(medicalCase, firstTurn, PatientPersonality.ELDERLY))
        val history = opening + doctor("过去有什么慢性病？")
        val impatient = reply(PatientPersonality.IMPATIENT, history)
        assertTrue(impatient.contains(medicalCase.pastHistory.substringBefore('。')))
        assertFalse(impatient.contains("否认糖尿病"))
        assertTrue(impatient.contains("快一点"))
    }

    @Test
    fun `离线具体症状摘录保留并列否定且不推断未记录信息`() {
        val negative = "休息后症状无明显减轻，无晕厥、咯血及呼吸困难。"
        assertEquals(negative, OfflinePracticeService.patientReply(medicalCase, listOf(doctor("刚才喘不上气吗？"))))
        assertEquals(negative, OfflinePracticeService.patientReply(medicalCase, listOf(doctor("您有没有晕倒？"))))
        val unrelated = OfflinePracticeService.patientReply(medicalCase, listOf(doctor("最近有没有关节肿胀？")))
        assertTrue(unrelated.contains("其他情况请您具体问我"))
        assertFalse(unrelated.contains("无关节肿胀"))
    }

    @Test
    fun `隐瞒型只把已经送达的同一主题提问当作追问`() {
        val opening = listOf(doctor(DOCTOR_OPENING_LINE), patient(medicalCase.openingLine))
        val alcohol = doctor("平时喝酒吗？")
        val smoking = doctor("平时吸烟吗？")
        val followUp = doctor("方便说说每天抽烟多少支吗？", MessageDeliveryState.SENDING)
        fun reply(messages: List<ChatMessage>) =
            OfflinePracticeService.patientReply(medicalCase, opening + messages, PatientPersonality.EVASIVE)

        val withheld = reply(listOf(smoking))
        assertTrue(withheld.contains("不好意思"))
        assertFalse(withheld.contains("20支"))
        assertEquals(withheld, reply(listOf(alcohol, followUp)))
        assertEquals(withheld, reply(listOf(smoking.copy(deliveryState = MessageDeliveryState.FAILED), followUp)))
        assertEquals("吸烟30年，每日约20支。", reply(listOf(smoking, patient(withheld), followUp)))
        assertEquals("偶饮酒。", reply(listOf(alcohol, patient(withheld), alcohol.copy(deliveryState = MessageDeliveryState.SENDING))))
    }

    @Test
    fun `离线评分总分等于分项相加且在合法范围内`() {
        val messages = listOf(
            doctor("您这个情况多久了？"),
            patient(medicalCase.presentIllness),
        )
        val score = OfflinePracticeService.score(
            medicalCase = medicalCase,
            messages = messages,
            initialDiagnosis = medicalCase.correctDiagnosis,
            treatmentAnswer = "建议进一步检查",
            encounterType = EncounterType.OUTPATIENT,
        )
        val sum = score.coverageScore + score.techniqueScore + score.communicationScore +
            score.diagnosisScore + score.treatmentScore
        assertEquals(sum, score.totalScore)
        assertTrue(score.totalScore in 0..100)
        // 诊断名称完全匹配得 16 分；未填写诊断依据，因此不足 20 分满分。
        assertTrue(score.diagnosisScore in 14..20)
    }

    @Test
    fun `离线只返回请求的检验结果并区分未记录项目`() {
        val laboratory = ClinicalRequestType.LAB_OR_IMAGING
        val ecg = OfflinePracticeService.clinicalResult(medicalCase, laboratory, "心电图")
        assertTrue(ecg.orEmpty().startsWith("心电图示"))
        assertFalse(ecg.orEmpty().contains("肌钙蛋白"))
        assertFalse(ecg.orEmpty().contains("血常规"))
        assertEquals(ecg, OfflinePracticeService.clinicalResult(medicalCase, laboratory, "ＥＣＧ"))
        assertNull(OfflinePracticeService.clinicalResult(medicalCase, laboratory, "腹部超声"))
    }

    @Test
    fun `离线生命体征摘录保留病例数值而缺失结果不补写`() {
        val sample = medicalCase.copy(physicalExam = "T 37.2℃，P 86次/分，BP 120/70mmHg；腹软。")
        val exam = ClinicalRequestType.PHYSICAL_EXAM

        assertEquals("T 37.2℃，P 86次/分，BP 120/70mmHg", OfflinePracticeService.clinicalResult(sample, exam, "生命体征"))
        assertEquals("腹软", OfflinePracticeService.clinicalResult(sample, exam, "腹部检查"))
        assertNull(OfflinePracticeService.clinicalResult(sample, exam, "心肺听诊"))
    }

    @Test
    fun `离线病历包含患者信息与初步诊断`() {
        val record = OfflinePracticeService.record(
            medicalCase = medicalCase,
            initialDiagnosis = "待查",
            diagnosisBasis = "症状典型",
            treatmentAnswer = "随访",
            encounterType = EncounterType.OUTPATIENT,
        )
        assertTrue(record.contains(medicalCase.patient.name))
        assertTrue(record.contains("初步诊断"))
        assertTrue(record.contains("待查"))
    }

    @Test
    fun `离线病历首行标注内容来自病例标准资料`() {
        val record = OfflinePracticeService.record(
            medicalCase = medicalCase,
            initialDiagnosis = "待查",
            diagnosisBasis = "症状典型",
            treatmentAnswer = "随访",
            encounterType = EncounterType.OUTPATIENT,
        )
        assertEquals(
            "【参考病历】以下内容来自病例标准资料，不代表本次问诊采集到的信息。",
            record.lineSequence().first(),
        )
    }

    @Test
    fun `评分校正不应把已覆盖问题再次列为遗漏`() {
        val question = medicalCase.keyQuestions.first()
        val messages = listOf(doctor(question))
        val raw = ScoreResult(
            totalScore = 60,
            coverageScore = 10,
            techniqueScore = 12,
            communicationScore = 12,
            diagnosisScore = 16,
            coveredPoints = emptyList(),
            missedQuestions = listOf(question, "另一个问题"),
            strengths = emptyList(),
            improvements = emptyList(),
            correctDiagnosis = medicalCase.correctDiagnosis,
            diagnosisExplanation = "",
        )
        val reconciled = ScoreReconciler.reconcile(raw, medicalCase, messages)
        assertFalse(reconciled.missedQuestions.contains(question))
        assertTrue(reconciled.coverageScore in 0..35)
        assertEquals(
            reconciled.coverageScore + reconciled.techniqueScore + reconciled.communicationScore +
                reconciled.diagnosisScore + reconciled.treatmentScore,
            reconciled.totalScore,
        )
    }
}
