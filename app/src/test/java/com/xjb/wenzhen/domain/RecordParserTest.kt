package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.EncounterType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordParserTest {
    @Test
    fun `门诊病历只输出门诊规定小节`() {
        val raw = """
            主诉
            胸痛3小时。
            现病史
            活动后出现压榨性胸痛。
            既往史/过敏史
            高血压5年，青霉素过敏。
            体格检查
            心率96次/分。
            辅助检查
            心电图示ST段抬高。
            初步诊断
            急性心肌梗死。
            处理意见
            尽快再灌注治疗。
        """.trimIndent()

        val record = RecordParser.parse(
            raw = raw,
            encounterType = EncounterType.OUTPATIENT,
            treatmentAnswer = "尽快再灌注治疗。",
        )

        assertTrue(record.contains("主诉\n胸痛3小时。"))
        assertTrue(record.contains("既往史/过敏史\n高血压5年，青霉素过敏。"))
        assertTrue(record.contains("处理意见\n尽快再灌注治疗。"))
        assertFalse(record.contains("一般情况\n"))
        assertFalse(record.contains("系统回顾\n"))
        assertFalse(record.contains("诊疗计划\n"))
    }

    @Test
    fun `住院病历输出住院规定小节并保留诊疗计划`() {
        val raw = """
            一般情况
            患者，男，58岁。
            主诉
            胸痛3小时。
            现病史
            活动后出现压榨性胸痛。
            既往史
            高血压5年。
            个人史
            吸烟30年。
            婚育史
            已婚，育有一子。
            家族史
            父亲有冠心病。
            体格检查
            心率96次/分。
            系统回顾
            无发热、咳嗽及腹痛。
            初步诊断
            急性心肌梗死。
            诊疗计划
            尽快再灌注治疗。
        """.trimIndent()

        val record = RecordParser.parse(
            raw = raw,
            encounterType = EncounterType.INPATIENT,
            treatmentAnswer = "尽快再灌注治疗。",
        )

        val physicalIndex = record.indexOf("体格检查\n")
        val reviewIndex = record.indexOf("系统回顾\n")
        assertTrue(record.contains("一般情况\n患者，男，58岁。"))
        assertTrue(record.contains("个人史\n吸烟30年。"))
        assertTrue(record.contains("婚育史\n已婚，育有一子。"))
        assertTrue(reviewIndex > physicalIndex)
        assertTrue(record.contains("诊疗计划\n尽快再灌注治疗。"))
        assertFalse(record.contains("处理意见\n"))
    }

    @Test
    fun `学生未填写治疗方案时病历写未填写`() {
        val raw = """
            主诉
            胸痛3小时。
            现病史
            活动后胸痛。
            既往史/过敏史
            无特殊。
            体格检查
            心率96次/分。
            辅助检查
            心电图异常。
            初步诊断
            急性心肌梗死。
            处理意见
            AI 自行补充的治疗内容。
        """.trimIndent()

        val record = RecordParser.parse(raw, encounterType = EncounterType.OUTPATIENT)

        assertTrue(record.contains("处理意见\n未填写"))
        assertFalse(record.contains("AI 自行补充的治疗内容"))
    }
}
