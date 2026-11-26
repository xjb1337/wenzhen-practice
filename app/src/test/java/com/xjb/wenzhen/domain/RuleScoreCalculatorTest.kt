package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C-2 验收：单字诊断、空话技巧分、治疗分等对抗场景，以及合理作答不被低估。 */
class RuleScoreCalculatorTest {
    private val medicalCase = BuiltInCases.byId("cardio-chest-pain")!!
    private val openingLine = DOCTOR_OPENING_LINE

    private fun doctor(content: String) = ChatMessage(role = MessageRole.DOCTOR, content = content)

    private fun patient(content: String) = ChatMessage(role = MessageRole.PATIENT, content = content)

    /** 每条医生提问后跟一条患者回答，模拟真实对话的交替顺序。 */
    private fun dialogue(vararg doctorLines: String): List<ChatMessage> =
        doctorLines.flatMap { listOf(doctor(it), patient("嗯，是这样的。")) }

    private fun opened(case: MedicalCase, vararg doctorLines: String): List<ChatMessage> =
        listOf(doctor(openingLine), patient(case.openingLine)) + dialogue(*doctorLines)

    private fun nameScore(diagnosis: String, case: MedicalCase = medicalCase): Int =
        RuleScoreCalculator.diagnosisNameScore(case, diagnosis)

    @Test
    fun `诊断只填一个字或笼统词时诊断分不超过8分`() {
        listOf("急", "心", "死", "急性", "待查").forEach { diagnosis ->
            val score = RuleScoreCalculator.compute(
                medicalCase = medicalCase,
                messages = emptyList(),
                initialDiagnosis = diagnosis,
                diagnosisBasis = "胸痛伴大汗，心电图 ST 段抬高",
            )
            assertTrue("诊断“$diagnosis”得了 ${score.diagnosis} 分", score.diagnosis <= 8)
        }
        val single = RuleScoreCalculator.compute(
            medicalCase = medicalCase,
            messages = emptyList(),
            initialDiagnosis = "急",
            diagnosisBasis = "胸痛伴大汗，心电图 ST 段抬高",
        )
        assertEquals(0, single.diagnosis)
        assertTrue(single.notes.contains(RuleScoreCalculator.NOTE_DIAGNOSIS_TOO_SHORT))
    }

    @Test
    fun `标准诊断和病例别名都得名称满分16分`() {
        assertEquals(16, nameScore("急性ST段抬高型心肌梗死（前壁）"))
        assertEquals(16, nameScore("急性ST段抬高型心肌梗死"))
        assertEquals(16, nameScore("急性 ST 段抬高型心肌梗死(前壁)"))
        assertEquals(16, nameScore("初步诊断：考虑急性ST段抬高型心肌梗死"))

        val withAliases = medicalCase.copy(diagnosisAliases = listOf("STEMI", "急性前壁心肌梗死"))
        assertEquals(16, nameScore("STEMI", withAliases))
        assertEquals(16, nameScore("stemi", withAliases))
        assertEquals(16, nameScore("急性前壁心肌梗死", withAliases))
        assertEquals(
            16,
            RuleScoreCalculator.compute(withAliases, emptyList(), initialDiagnosis = "STEMI").diagnosis,
        )
        // 没有登记别名时，缩写不能凭空命中。
        assertEquals(6, nameScore("STEMI"))
    }

    @Test
    fun `部分重叠或否定词不一致的诊断只拿部分分`() {
        assertEquals(8, nameScore("急性心肌梗死"))
        assertEquals(8, nameScore("梗死"))
        assertEquals(12, nameScore("急性前壁ST段抬高型心肌梗死"))
        assertEquals(6, nameScore("非ST段抬高型心肌梗死"))
        assertEquals(6, nameScore("主动脉夹层"))

        val appendicitis = BuiltInCases.byId("gastro-appendicitis")!!
        assertEquals(16, nameScore("急性阑尾炎", appendicitis))
        assertEquals(12, nameScore("阑尾炎", appendicitis))
        assertEquals(6, nameScore("急性胃肠炎", appendicitis))
    }

    @Test
    fun `诊断依据只在诊断名称有效时计分`() {
        val withBasis = RuleScoreCalculator.compute(
            medicalCase = medicalCase,
            messages = listOf(patient("胸口疼，伴大汗。")),
            initialDiagnosis = "急性ST段抬高型心肌梗死",
            diagnosisBasis = "胸痛伴大汗",
        )
        assertEquals(20, withBasis.diagnosis)
    }

    @Test
    fun `只发4条带问号的空话时技巧分加沟通分不超过18分`() {
        val fillers = arrayOf("嗯？", "好的？", "还有吗？", "然后呢？")
        BuiltInCases.all.forEach { case ->
            val conversations = listOf(
                opened(case, *fillers),
                dialogue(*fillers),
                dialogue("还有吗？", "还有吗？", "还有吗？", "还有吗？"),
            )
            conversations.forEach { messages ->
                val score = RuleScoreCalculator.compute(case, messages)
                assertTrue(
                    "病例 ${case.id}：技巧 ${score.technique} + 沟通 ${score.communication}",
                    score.technique + score.communication <= 18,
                )
                assertEquals("病例 ${case.id} 的空话不应覆盖任何要点", 0, score.coverage)
            }
        }
    }

    @Test
    fun `只有系统预置开场白和患者开场时技巧沟通覆盖都为0`() {
        val score = RuleScoreCalculator.compute(
            medicalCase = medicalCase,
            messages = listOf(doctor(openingLine), patient(medicalCase.openingLine)),
        )
        assertEquals(0, score.technique)
        assertEquals(0, score.communication)
        assertEquals(0, score.coverage)
        assertTrue(score.evidence.all { it.messageIndexes.isEmpty() })
    }

    @Test
    fun `非字否定词只在另一诊断未否定同一部位时判冲突`() {
        val stCore = medicalCase.copy(correctDiagnosis = "急性ST段抬高型心肌梗死")
        assertEquals(6, nameScore("急性非ST段抬高型心肌梗死", stCore))
        val nonStCore = medicalCase.copy(correctDiagnosis = "非ST段抬高型心肌梗死")
        assertEquals(6, nameScore("ST段抬高型心肌梗死", nonStCore))
        assertEquals(16, nameScore("非ST段抬高型心肌梗死", nonStCore))
        val pneumonia = BuiltInCases.byId("resp-pneumonia")!!.copy(correctDiagnosis = "社区获得性肺炎")
        assertEquals(14, nameScore("社区获得性肺炎非重症型", pneumonia))
    }

    @Test
    fun `需要和建议这类医嘱用词不再算共情加分`() {
        val plain = RuleScoreCalculator.compute(medicalCase, dialogue("胸口疼了多久了？", "有没有出汗或者恶心？"))
        val orders = RuleScoreCalculator.compute(
            medicalCase,
            dialogue("胸口疼了多久了？需要注意", "有没有出汗或者恶心？建议配合"),
        )
        val empathy = RuleScoreCalculator.compute(
            medicalCase,
            dialogue("胸口疼了多久了？您别担心", "有没有出汗或者恶心？慢慢说"),
        )
        assertEquals(plain.communication, orders.communication)
        assertTrue(empathy.communication > plain.communication)
    }

    @Test
    fun `完整问诊仍能拿到较高的覆盖技巧和沟通分`() {
        val messages = opened(
            medicalCase,
            "胸口疼了多久了？",
            "是怎么个痛法，压着疼还是刺痛？",
            "疼的时候会往肩膀或者后背串吗？",
            "有没有出汗、恶心或者喘不上气？",
            "休息以后能好一点吗？",
            "以前有过这样的胸痛吗？",
            "有高血压、糖尿病吗？",
            "抽烟喝酒吗？",
            "家里人有心脏病吗？",
            "平时吃什么药？对什么药过敏吗？",
            "最近睡得怎么样？",
            "有没有咳嗽咯血？",
        )
        val score = RuleScoreCalculator.compute(medicalCase, messages)
        assertTrue("覆盖分 ${score.coverage}", score.coverage >= 30)
        assertTrue("技巧分 ${score.technique}", score.technique >= 12)
        assertTrue("沟通分 ${score.communication}", score.communication >= 10)
        assertTrue("遗漏 ${score.missedQuestions}", score.missedQuestions.size <= 1)
    }

    @Test
    fun `治疗方案按药名和处理措施命中计分`() {
        val good = RuleScoreCalculator.compute(
            medicalCase = medicalCase,
            messages = emptyList(),
            treatmentAnswer = "阿司匹林300mg嚼服，替格瑞洛180mg负荷，阿托伐他汀40mg，依诺肝素抗凝，" +
                "心电监护、吸氧，尽快直接PCI再灌注",
        )
        assertTrue("合理方案只得了 ${good.treatment} 分", good.treatment >= 12)

        val vague = RuleScoreCalculator.compute(
            medicalCase = medicalCase,
            messages = emptyList(),
            treatmentAnswer = "建议好好治疗，注意休息，定期复查，必要时住院观察",
        )
        assertTrue("空泛方案得了 ${vague.treatment} 分", vague.treatment <= 4)

        assertEquals(0, RuleScoreCalculator.compute(medicalCase, emptyList(), treatmentAnswer = "").treatment)
    }

    @Test
    fun `病例没有参考治疗方案时治疗分为0并说明原因`() {
        val noPlan = medicalCase.copy(treatmentPlan = "")
        val score = RuleScoreCalculator.compute(
            medicalCase = noPlan,
            messages = emptyList(),
            treatmentAnswer = "这是一段超过十二个字的治疗方案描述，但病例没有参考方案",
        )
        assertEquals(0, score.treatment)
        assertTrue(score.notes.contains(RuleScoreCalculator.NOTE_NO_TREATMENT_PLAN))
    }

    @Test
    fun `从参考方案的药物一节抽取药名关键字`() {
        val keys = TreatmentRubric.medicationNames(medicalCase.treatmentPlan)
        assertTrue(keys.toString(), keys.containsAll(listOf("阿司匹林", "替格瑞洛", "氯吡格雷", "阿托伐他汀", "依诺肝素", "美托洛尔")))
        assertTrue(keys.toString(), keys.none { it.contains("小时") || it.contains("每日") })
    }

    @Test
    fun `总分等于各分项之和`() {
        val score = RuleScoreCalculator.compute(
            medicalCase = medicalCase,
            messages = opened(medicalCase, "胸口疼了多久了？", "抽烟喝酒吗？"),
            initialDiagnosis = "急性心肌梗死",
            diagnosisBasis = "胸痛伴大汗",
            treatmentAnswer = "阿司匹林嚼服，尽快PCI",
        )
        assertEquals(
            score.coverage + score.technique + score.communication + score.diagnosis + score.treatment,
            score.total,
        )
    }

    @Test
    fun `排除和待鉴别内容不作为初步诊断`() {
        val standard = "急性ST段抬高型心肌梗死"
        for (onlyExcluded in listOf(
            "排除$standard",
            "${standard}已排除",
            "$standard（待排）",
            "鉴别诊断：$standard",
            "不能排除$standard",
            "初步诊断：不考虑$standard",
        )) {
            assertEquals(onlyExcluded, 0, nameScore(onlyExcluded))
        }
        assertEquals(6, nameScore("考虑主动脉夹层，排除$standard"))
        assertEquals(6, nameScore("初步诊断：主动脉夹层\n鉴别诊断：\n- $standard"))
        assertEquals(16, nameScore("考虑$standard；仍需排除主动脉夹层"))
        assertEquals(16, nameScore("拟诊：$standard"))
        assertEquals(16, nameScore("考虑$standard（需排除主动脉夹层）"))
    }
    @Test
    fun `否定用药和处理不计为实施而剂量限制仍保留`() {
        val case = medicalCase.copy(treatmentPlan = "药物：测试药甲10mg。\n处理：心电监护、经皮介入。")
        fun treatment(answer: String) = RuleScoreCalculator.compute(case, emptyList(), treatmentAnswer = answer)

        val baseline = treatment("进行心电监护")
        assertTrue(baseline.treatment > 0)
        assertEquals(0, treatment("不用测试药甲；不做经皮介入，心电监护暂缓。").treatment)
        assertEquals(0, treatment("未使用测试药甲；经皮介入（暂缓）。").treatment)
        assertEquals(treatment("继续测试药甲").treatment, treatment("不必停用测试药甲").treatment)
        assertEquals(baseline.treatment,
            treatment("暂缓测试药甲、经皮介入；但继续心电监护。").treatment)
        assertEquals(baseline.treatment,
            treatment("继续心电监护；使用测试药甲，又明确停用测试药甲。").treatment)
        assertEquals(treatment("用测试药甲，继续心电监护。").treatment,
            treatment("用测试药甲，剂量不超过参考限量，继续心电监护。").treatment)
    }

    @Test
    fun `参考方案要求避免的动作按肯否方向匹配`() {
        val case = medicalCase.copy(treatmentPlan = "处理：暂缓经皮介入。")
        fun points(answer: String) =
            RuleScoreCalculator.compute(case, emptyList(), treatmentAnswer = answer).treatment

        assertEquals(5, points("暂缓经皮介入"))
        assertEquals(5, points("经皮介入暂缓"))
        assertEquals(0, points("进行经皮介入"))
    }

    @Test
    fun `药名尾部相同不能替代完整药名`() {
        val case = medicalCase.copy(treatmentPlan = "药物：甲类测试药10mg。")
        fun points(answer: String) =
            RuleScoreCalculator.compute(case, emptyList(), treatmentAnswer = answer).treatment

        assertEquals(10, points("给予甲类测试药"))
        assertEquals(0, points("给予乙类测试药"))
    }

    @Test
    fun `同一处理的文本和缩写只计一个参考要点`() {
        val case = medicalCase.copy(treatmentPlan = "治疗原则：再灌注。\n处理：PCI再灌注。")
        fun points(answer: String) =
            RuleScoreCalculator.compute(case, emptyList(), treatmentAnswer = answer).treatment

        assertEquals(5, points("PCI再灌注，继续再灌注"))
        assertEquals(5, points("PCI"))
        assertEquals(0, points("使用abcpcix"))

        val withAbbreviation = medicalCase.copy(treatmentPlan = "处理：经皮冠状动脉介入（PCI）。")
        val usingBothNames = RuleScoreCalculator.compute(
            withAbbreviation, emptyList(), treatmentAnswer = "行经皮冠状动脉介入（PCI）",
        )
        assertEquals(5, usingBothNames.treatment)
    }

    @Test
    fun `药名末尾是字母时允许紧接合法剂量且仍区分其他药名`() {
        val case = medicalCase.copy(treatmentPlan = "药物：测试药A0.1g。")
        assertEquals(10, RuleScoreCalculator.compute(case, emptyList(), treatmentAnswer = "使用测试药A0.1g").treatment)
        assertEquals(0, RuleScoreCalculator.compute(case, emptyList(), treatmentAnswer = "使用测试药AB").treatment)
    }

    @Test
    fun `全部内置病例的参考治疗可计分且重复作答不累积分数`() {
        BuiltInCases.all.filter { it.treatmentPlan.isNotBlank() }.forEach { case ->
            val plan = case.treatmentPlan
            val score = RuleScoreCalculator.compute(case, emptyList(), treatmentAnswer = plan).treatment
            val repeated = RuleScoreCalculator.compute(case, emptyList(), treatmentAnswer = "$plan\n$plan").treatment
            assertTrue(case.id, score > 0)
            assertEquals(case.id, score, repeated)
        }
    }

    @Test
    fun `诊断依据按本次出现的证据计分而非按字数`() {
        val case = medicalCase.copy(
            chiefComplaint = "胸痛3小时",
            presentIllness = "胸痛伴大汗、恶心，否认发热、咳嗽。",
            auxiliaryExams = "心电图示ST段抬高",
        )
        val dialogue = listOf(
            doctor(openingLine),
            patient("胸口疼了3小时，伴大汗，没有发热、咳嗽。"),
            doctor("心电图是否ST段抬高？"),
        )
        val diagnosis = "急性ST段抬高型心肌梗死"
        fun points(basis: String, messages: List<ChatMessage> = dialogue) =
            RuleScoreCalculator.compute(case, messages, initialDiagnosis = diagnosis, diagnosisBasis = basis).diagnosis

        assertEquals(16, points("根据各项检查和病史综合分析认为符合该诊断"))
        assertEquals(16, points("ST段抬高"))
        assertEquals(18, points("大汗"))
        assertEquals(20, points("胸痛3小时伴大汗"))
        assertEquals(18, points("胸痛、胸口疼"))
        assertEquals(16, points("胸痛30小时"))
        assertEquals(16, points("发热、咳嗽"))
        assertEquals(18, points("没有发热"))
        assertEquals(16, points("大汗", dialogue + patient("刚才不确定是否有大汗。")))
        assertEquals(16, points("大汗", dialogue + patient("其实不是大汗。")))
        assertEquals(16, points("大汗", dialogue + patient("刚才说错了，其实没有大汗。")))
        val exam = ChatMessage(MessageRole.SYSTEM, "ST段抬高。", systemTitle = "化验/检查 · 心电图")
        assertEquals(16, points("ST段抬高", dialogue + exam.copy(deliveryState = com.xjb.wenzhen.data.model.MessageDeliveryState.FAILED)))
        assertEquals(16, points("ST段抬高", dialogue + exam.copy(systemTitle = "系统通知")))
        assertEquals(16, points("ST段抬高", dialogue + patient("ST段抬高")))
        assertEquals(18, points("心电图：ST段抬高", dialogue + exam))
        val scored = OfflinePracticeService.score(case, dialogue + exam, diagnosis, "", com.xjb.wenzhen.data.model.EncounterType.OUTPATIENT, "大汗，ST段抬高")
        val restored = requireNotNull(ScoreParser.parseStored(ScoreParser.encode(scored), scored.totalScore))
        assertEquals(listOf(DiagnosisClue("大汗", 2), DiagnosisClue("ST段抬高", 4)), restored.diagnosisClues)
        assertEquals(20, restored.diagnosisScore)
        assertTrue(ScoreParser.parse(ScoreParser.encode(scored)).diagnosisClues.isEmpty())
    }

}
