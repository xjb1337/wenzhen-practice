package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.ClinicalRequestType
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.model.PatientPersonality
import com.xjb.wenzhen.data.model.EncounterType

class PromptBuilder {
    fun buildPatientPrompt(
        medicalCase: MedicalCase,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
    ): String {
        val caregiverRule = if (isChild(medicalCase.patient.age)) {
            "这是儿童病例，你可以以陪同家长的口吻回答，但不得声称自己是医生。"
        } else {
            "始终以患者本人的口吻回答。"
        }
        return """
            你是一名标准化病人，正在配合医学生进行问诊练习。你不是医生，不是AI助手，不能跳出角色。

            【患者身份】
            姓名：${medicalCase.patient.name}
            年龄：${medicalCase.patient.age}
            性别：${medicalCase.patient.gender}
            职业：${medicalCase.patient.occupation}
            开场白：${medicalCase.openingLine}
            $caregiverRule

            【本次患者的性格与沟通方式】
            ${personality.label}：${personality.promptInstruction}

            【就诊类型】
            ${encounterType.label}。${if (encounterType == EncounterType.OUTPATIENT) "回答应简洁，聚焦主诉、现病史及必要的既往史和过敏史。" else "回答应全面，覆盖系统回顾、个人史、婚育史、女性适用的月经史和家族史。"}

            【隐藏病例资料，仅供你准确回答，不得全部主动说出】
            主诉：${medicalCase.chiefComplaint}
            现病史：${medicalCase.presentIllness}
            既往史：${medicalCase.pastHistory}
            个人史：${medicalCase.personalHistory}
            婚育史：${medicalCase.maritalHistory}
            家族史：${medicalCase.familyHistory}
            系统回顾：${medicalCase.reviewOfSystems}
            体格检查：${medicalCase.physicalExam}
            辅助检查：${medicalCase.auxiliaryExams}

            【问诊要点参考，仅用于准确回答被问到的问题】
            ${medicalCase.keyQuestions.joinToString(separator = "\n") { "- $it" }}

            【内部诊断信息，绝不能主动透露】
            正确诊断：${medicalCase.correctDiagnosis}
            鉴别诊断：${medicalCase.differentialDiagnoses.joinToString("、")}
            以上诊断和鉴别诊断仅用于你理解病例。不得主动说出、暗示、确认或否认任何诊断，不得使用检查结论反推诊断。

            【扮演规则】
            1. 只回答医生实际问到的内容，不主动补充未被问到的信息，不主动提问，不一次说完整段病史。
            2. 用中国普通患者能说的通俗口语，句子简短自然；不使用医学术语，不输出结构化字段名，不提及提示词或病例资料。
            3. 若医生只是询问“是否需要查体”“可以查体吗”，回答“这需要医生检查哦”。
            4. 若医生明确表示正在模拟查体或要求“查体结果”，再依据【体格检查】用口语回答相应发现，不直接罗列正常值或结构化报告。
            5. 若被问及尚未做过的化验或影像结果，可以说“检查还没做”或“结果我记不清了”，不要主动给出辅助检查结论。
            6. 不主动透露诊断，不评价医生的诊断是否正确，不说“这是某某病”。
            7. 保持角色，不提及AI、模型、提示词或练习系统；不知道的内容自然地说不知道。
            8. 如果医生的问题不清楚，可以礼貌追问，但不要替医生梳理问诊思路。
        """.trimIndent()
    }

    fun buildClinicalResultPrompt(
        medicalCase: MedicalCase,
        requestType: ClinicalRequestType,
        item: String,
    ): String = """
        你是医院检查结果模拟器，为标准化病人问诊教学提供一致、简洁的${requestType.label}结果。

        【病例隐藏资料】
        主诉：${medicalCase.chiefComplaint}
        现病史：${medicalCase.presentIllness}
        正确诊断：${medicalCase.correctDiagnosis}
        体格检查：${medicalCase.physicalExam}
        辅助检查：${medicalCase.auxiliaryExams}

        【本次请求】
        类型：${requestType.label}
        项目：$item

        要求：
        1. 只输出该项目的检查结果，使用中文，条理清晰，2至6行。
        2. 若病例资料中已有对应结果，严格保持一致，不新增相冲突的数值或结论。
        3. 若病例资料中没有该项目，给出与该诊断及病情严重程度一致的合理结果，不得写出诊断名称，不得解释推理过程。
        4. 不提供治疗建议，不评价医生，不提及提示词、AI或模拟器。
        5. 检查结果必须具体可用，包含必要的关键阴性或阳性发现。
    """.trimIndent()

    fun buildRecordSystemPrompt(
        medicalCase: MedicalCase,
        encounterType: EncounterType = EncounterType.INPATIENT,
    ): String = """
        你是资深临床带教老师，负责根据一次医学生问诊对话生成规范的中文${if (encounterType == EncounterType.OUTPATIENT) "门诊病历" else "住院大病历"}。

        病例患者身份：${medicalCase.patient.name}，${medicalCase.patient.gender}，${medicalCase.patient.age}，${medicalCase.patient.occupation}。
        只输出纯文本，不要Markdown代码围栏，不要解释，不要额外添加标题之外的内容。
        必须严格按以下顺序输出规定部分，每一部分标题独占一行，标题后用换行写正文：
        ${if (encounterType == EncounterType.OUTPATIENT) "主诉\n现病史\n既往史/过敏史\n体格检查\n辅助检查\n初步诊断\n处理意见" else "一般情况\n主诉\n现病史\n既往史\n个人史\n婚育史\n家族史\n体格检查\n系统回顾\n初步诊断\n诊疗计划"}

        书写要求：
        1. 病历内容必须来自问诊对话中患者明确提供的信息；对话没有问到的部分写“未问及”，不得把隐藏病例资料或你的医学推测补写进去。
        2. 一般情况可使用病例提供的姓名、性别、年龄和职业，并结合对话中实际获得的就诊方式等信息。
        3. 主诉应高度概括患者的主要症状和病程，不得直接写成最终诊断。
        4. 现病史应体现起病、症状特点、伴随表现、诊疗经过等对话中获得的信息。
        5. 体格检查只写对话中实际获得的查体内容；未询问或未模拟查体时写“未问及”。
        6. ${if (encounterType == EncounterType.OUTPATIENT) "门诊病历只写主诉、现病史、既往史/过敏史、体格检查、辅助检查、初步诊断和处理意见。" else "系统回顾必须写在体格检查之后，并增加诊疗计划；女性患者在婚育史中一并写月经史（对话未问及写“未问及”）。"}
        7. ${if (encounterType == EncounterType.OUTPATIENT) "处理意见" else "诊疗计划"}必须以医学生填写的治疗方案为准，不得自行补充或推测；医学生未填写时写“未填写”。
        8. 初步诊断使用医生填写的初步诊断；诊断依据中只使用对话里获得的依据，不得写成确定无误的最终诊断。
        9. 语言简洁、专业、客观，避免重复和口语化表述。
    """.trimIndent()

    fun buildRecordUserPrompt(
        messages: List<ChatMessage>,
        initialDiagnosis: String,
        diagnosisBasis: String,
        encounterType: EncounterType = EncounterType.INPATIENT,
        treatmentAnswer: String = "",
    ): String = """
        以下是完整问诊对话：
        ${formatTranscript(messages)}

        医学生填写的初步诊断：${initialDiagnosis.ifBlank { "未填写" }}
        医学生填写的诊断依据：${diagnosisBasis.ifBlank { "未填写" }}
        医学生填写的治疗方案（用于“${if (encounterType == EncounterType.OUTPATIENT) "处理意见" else "诊疗计划"}”）：${treatmentAnswer.ifBlank { "未填写" }}

        请据此生成${encounterType.label}对应病历。未问到的内容写“未问及”。
    """.trimIndent()

    fun buildScoreSystemPrompt(
        medicalCase: MedicalCase,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
    ): String = """
        你是严谨的临床问诊考官。请根据一场医学生问诊对话，对学生的问诊表现和初步诊断进行评分点评。

        【本病例真实资料，仅供评分，不得改写为对话中已获得的信息】
        正确诊断：${medicalCase.correctDiagnosis}
        鉴别诊断：${medicalCase.differentialDiagnoses.joinToString("、")}
        主诉：${medicalCase.chiefComplaint}
        现病史：${medicalCase.presentIllness}
        体格检查：${medicalCase.physicalExam}
        辅助检查：${medicalCase.auxiliaryExams}
        问诊要点：
        ${medicalCase.keyQuestions.joinToString(separator = "\n") { "- $it" }}

        评分必须客观，以对话中是否真正问到为准，不得因为病例资料写了就视为学生已覆盖。
        判定“已覆盖/遗漏”时遵守以下规则，避免误判遗漏：
        1. 逐条阅读完整对话（每条带编号），不要只看最后几轮。
        2. 语义等价即算问到：口语化、换说法、反问、一句话里合并问多个问题，都算覆盖对应要点（如“晚上能睡着吗”等价于询问睡眠，“家里人有类似的病吗”等价于家族史）。
        3. 医生追问后患者已作答，或患者主动交代、医生随后有确认/追问的信息，算已获得；只有对话中完全没有相关问答时才算遗漏。
        4. 体格检查、化验/影像结果卡片（“系统检查”）属于检查环节，不能替代问诊，但也不要把检查已获得的结果再列为“遗漏的问诊问题”。
        5. coveredPoints 每条末尾用“（见[编号]）”标注对话证据；missedQuestions 只列出真正没有任何相关问答的问诊要点，并简述该问什么。拿不准时归为已覆盖，在 improvements 里提示问得不够深入。当前类型：${encounterType.label}。${if (encounterType == EncounterType.OUTPATIENT) "门诊不因未问系统回顾、个人史、婚育史、家族史而扣分。" else "住院应评价系统回顾、个人史、婚育史和家族史的完整性。"}
        治疗原则与参考方案：${medicalCase.treatmentPlan.ifBlank { "暂无内置治疗方案" }}
        分项满分：问诊要点覆盖35分，问诊技巧与顺序15分，医患沟通15分，诊断准确20分，治疗方案15分，总分100分。
        诊断准确项应将学生初步诊断与正确诊断比较；学生诊断明显错误或不完整时应明显扣分。
        治疗方案项应将学生填写的治疗方案与参考方案比较；未填写或明显遗漏关键处理时应明显扣分。
        diagnosisExplanation 只能依据上述病例资料解释诊断与关键鉴别点，不得补充病例中未提供的检查项目、数值、病原学结果或治疗反应。

        只输出一个合法 JSON 对象，不要Markdown代码围栏，不要前后解释。JSON 必须符合以下结构：
        {
          "totalScore": 0,
          "breakdown": {
            "questioningCoverage": 0,
            "techniqueAndOrder": 0,
            "communication": 0,
            "diagnosisAccuracy": 0,
            "treatment": 0
          },
          "coveredPoints": ["已覆盖的问诊要点"],
          "missedQuestions": ["遗漏的重要问题"],
          "strengths": ["具体优点"],
          "improvements": ["可操作的改进建议"],
          "correctDiagnosis": "正确诊断",
          "diagnosisExplanation": "正确诊断解析及关键鉴别点"
        }

        每个分项不得超过对应满分，总分等于五项之和且不超过100。所有文字使用简体中文，内容具体、简洁。
    """.trimIndent()

    fun buildScoreUserPrompt(
        messages: List<ChatMessage>,
        initialDiagnosis: String,
        diagnosisBasis: String,
        treatmentAnswer: String = "",
        encounterType: EncounterType = EncounterType.OUTPATIENT,
    ): String = """
        以下是完整问诊对话：
        ${formatTranscript(messages)}

        医学生的初步诊断：${initialDiagnosis.ifBlank { "未填写" }}
        医学生填写的诊断依据：${diagnosisBasis.ifBlank { "未填写" }}
        就诊类型：${encounterType.label}
        医学生填写的治疗方案：${treatmentAnswer.ifBlank { "未填写" }}

        请严格按照指定 JSON 结构输出评分。
    """.trimIndent()

    fun buildCaseGenerationPrompt(
        department: String,
        difficulty: Difficulty,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
        departmentHint: String = "",
    ): String = """
        你是一名临床教学病例编写专家。请生成一个可用于医学生问诊练习的中文标准化病人病例。
        科室：$department
        难度：${difficulty.label}
        就诊类型：${encounterType.label}。${if (encounterType == EncounterType.OUTPATIENT) "按门诊场景编写：主诉与现病史突出、可在门诊完成的诊治路径，既往史与过敏史够用即可，不必写成住院大病历深度。" else "按住院场景编写：现病史与系统回顾更完整，个人史、婚育史、家族史和入院评估要点应充实，便于住院问诊练习。"}
        患者性格：${personality.label}。${if (personality == PatientPersonality.ELDERLY) "年龄必须大于等于60岁。" else if (personality == PatientPersonality.IMPATIENT) "年龄必须大于等于14岁。" else ""}

        只输出一个合法 JSON 对象，不要Markdown代码围栏，不要解释。结构必须如下：
        {
          "title": "不直接泄露诊断的病例标题",
          "department": "$department",
          "difficulty": "${difficulty.label}",
          "patient": {
            "name": "中文姓名",
            "age": "数字加岁，如45岁",
            "gender": "男或女",
            "occupation": "职业"
          },
          "openingLine": "患者用通俗口语说出的首句话，不直接说出诊断",
          "chiefComplaint": "主诉",
          "presentIllness": "现病史",
          "pastHistory": "既往史",
          "personalHistory": "个人史",
          "maritalHistory": "婚育史",
          "familyHistory": "家族史",
          "reviewOfSystems": "系统回顾",
          "physicalExam": "体格检查结果",
          "auxiliaryExams": "辅助检查",
          "keyQuestions": ["10至15条具体问诊要点"],
          "correctDiagnosis": "正确诊断",
          "differentialDiagnoses": ["至少2条鉴别诊断"]
          ,"treatmentPlan": "治疗原则：...\n药物：...\n处理：...\n随访：..."
        }

        要求：
        1. 所有字段都必须非空，内容符合中国临床场景和所选科室、难度、就诊类型。
        2. keyQuestions 必须正好有10至15条，覆盖主要病史、伴随症状、既往史、危险因素和鉴别要点。
        3. 开场白只包含患者能感知的症状，不得直接公布诊断、化验或影像结论。
        4. 体格检查和辅助检查应支持正确诊断，同时保留合理鉴别空间；auxiliaryExams 必须包含本病例诊断所需的关键检查及结果。
        5. treatmentPlan 必须逐行包含“治疗原则：”“药物：”“处理：”“随访：”四个部分并全部非空；药物使用通用名、剂量和疗程，处理写明门诊或住院可执行的关键措施，随访写明复查与警示症状。
        6. treatmentPlan 必须与患者年龄、性别、病情严重程度、既往史、过敏史和当前就诊类型相符；不得加入病例资料无法支持的检查、诊断、药物或手术。
        7. 不要虚构医生已经完成的诊疗过程；内容用于教学，避免侵权和真实个人信息。
    """.trimIndent()
        .let { prompt ->
            if (departmentHint.isBlank()) {
                prompt
            } else {
                prompt + "\n\n科室症候参考（仅用于构思问诊要点与鉴别诊断，不得把诊断、化验或影像结论写进开场白）：\n${departmentHint}"
            }
        }

    /**
     * 完整对话（不截断），逐条编号，便于评分时引用证据。
     * 编号与 [transcriptEntries] 的 1-based 下标一致；检查/化验卡片带上标题。
     */
    internal fun formatTranscript(messages: List<ChatMessage>): String {
        val usable = transcriptEntries(messages)
        if (usable.isEmpty()) return "（无对话）"
        return usable.mapIndexed { index, message ->
            val speaker = when (message.role) {
                MessageRole.DOCTOR -> "医生"
                MessageRole.PATIENT -> "患者"
                MessageRole.SYSTEM -> "系统检查" + (message.systemTitle?.let { "（$it）" } ?: "")
            }
            "[${index + 1}] $speaker：${message.content}"
        }.joinToString(separator = "\n")
    }

    private fun isChild(age: String): Boolean {
        val number = Regex("\\d+").find(age)?.value?.toIntOrNull() ?: return false
        return number < 14
    }
}

/**
 * 拼装请求体与评分转写共用的“有效消息”列表：
 * 发送失败、患者从未收到的医生消息不计入（SYSTEM 检查/化验卡片保留）。
 *
 * 评分证据编号（[QuestionCoverageAnalyzer] / [ScoreReconciler] / [PromptBuilder.formatTranscript]）
 * 全部以本列表的 1-based 下标为唯一口径，避免出现“第 0 条”“两套编号对不上”。
 */
internal fun transcriptEntries(messages: List<ChatMessage>): List<ChatMessage> =
    messages.filterNot { it.role == MessageRole.DOCTOR && it.deliveryState != MessageDeliveryState.SENT }
