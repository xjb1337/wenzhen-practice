package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.cases.CaseJsonCodec
import com.xjb.wenzhen.data.model.CaseGenerationDepartments
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.ClinicalRequestType
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.PatientPersonality
import com.xjb.wenzhen.data.model.EncounterType

class PatientChatService(
    private val llmClient: LlmClient,
    private val promptBuilder: PromptBuilder,
) {
    suspend fun reply(
        config: LlmConfig,
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
    ): String {
        val apiMessages = buildPatientMessages(medicalCase, messages, personality, encounterType)
        return llmClient.complete(
            config = config,
            messages = apiMessages,
            jsonMode = false,
            temperature = 0.8,
        ).trim()
    }

    suspend fun replyStreaming(
        config: LlmConfig,
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
        onDelta: suspend (String) -> Unit,
    ): String {
        return llmClient.stream(
            config = config,
            messages = buildPatientMessages(medicalCase, messages, personality, encounterType),
            temperature = 0.8,
            onDelta = onDelta,
        ).trim()
    }

    private fun buildPatientMessages(
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        personality: PatientPersonality,
        encounterType: EncounterType,
    ): List<LlmMessage> = buildList {
        add(LlmMessage("system", promptBuilder.buildPatientPrompt(medicalCase, personality, encounterType)))
        // 本轮医生提问在 AppViewModel.sendMessage 里被标记为 SENDING，回复完成后才改成 SENT。
        // 若这里只保留 SENT，API 收到的最后一条会是「上一轮患者回复」，模型就会回答上一轮问题
        // （用户反馈的「答非所问 / 滞后一轮」）。因此把「医生且 SENDING」一并纳入，
        // 保证请求体末尾始终是当前这一轮的医生提问。FAILED 不纳入，避免把未送达的问题当上下文。
        val usable = messages.filter { message ->
            message.role != MessageRole.SYSTEM &&
                (message.deliveryState == MessageDeliveryState.SENT ||
                    (message.role == MessageRole.DOCTOR && message.deliveryState == MessageDeliveryState.SENDING))
        }
        val context = if (usable.size <= 80) usable else listOf(usable.first()) + usable.takeLast(79)
        context.forEach { message ->
            add(
                LlmMessage(
                    role = if (message.role == MessageRole.DOCTOR) "user" else "assistant",
                    content = message.content,
                )
            )
        }
    }
}

class ClinicalDataService(
    private val llmClient: LlmClient,
    private val promptBuilder: PromptBuilder,
) {
    suspend fun request(
        config: LlmConfig,
        medicalCase: MedicalCase,
        requestType: ClinicalRequestType,
        item: String,
    ): String {
        val result = llmClient.complete(
            config = config,
            messages = listOf(
                LlmMessage(
                    "system",
                    promptBuilder.buildClinicalResultPrompt(medicalCase, requestType, item),
                ),
                LlmMessage("user", "请给出“${item}”的${requestType.label}结果。"),
            ),
            jsonMode = false,
            temperature = 0.2,
        ).trim()
        if (result.isEmpty()) {
            throw IllegalStateException("${requestType.label}结果为空，请稍后重试。")
        }
        return result
    }
}

class MedicalRecordService(
    private val llmClient: LlmClient,
    private val promptBuilder: PromptBuilder,
) {
    suspend fun generate(
        config: LlmConfig,
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        initialDiagnosis: String,
        diagnosisBasis: String,
        encounterType: EncounterType = EncounterType.INPATIENT,
        treatmentAnswer: String = "",
    ): String {
        val raw = llmClient.complete(
            config = config,
            messages = listOf(
                LlmMessage("system", promptBuilder.buildRecordSystemPrompt(medicalCase, encounterType)),
                LlmMessage(
                    "user",
                    promptBuilder.buildRecordUserPrompt(
                        messages = messages,
                        initialDiagnosis = initialDiagnosis,
                        diagnosisBasis = diagnosisBasis,
                        encounterType = encounterType,
                        treatmentAnswer = treatmentAnswer,
                    ),
                ),
            ),
            jsonMode = false,
            temperature = 0.2,
        )
        return RecordParser.parse(raw, initialDiagnosis, encounterType, treatmentAnswer)
    }
}

class ScoreService(
    private val llmClient: LlmClient,
    private val promptBuilder: PromptBuilder,
) {
    suspend fun evaluate(
        config: LlmConfig,
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        initialDiagnosis: String,
        diagnosisBasis: String,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
        treatmentAnswer: String = "",
    ): ScoreResult {
        val raw = llmClient.complete(
            config = config,
            messages = listOf(
                LlmMessage("system", promptBuilder.buildScoreSystemPrompt(medicalCase, encounterType)),
                LlmMessage(
                    "user",
                    promptBuilder.buildScoreUserPrompt(messages, initialDiagnosis, diagnosisBasis, treatmentAnswer, encounterType),
                ),
            ),
            jsonMode = true,
            temperature = 0.2,
        )
        val parsed = ScoreParser.parse(raw)
        val rule = RuleScoreCalculator.compute(
            medicalCase = medicalCase,
            messages = messages,
            initialDiagnosis = initialDiagnosis,
            diagnosisBasis = diagnosisBasis,
            treatmentAnswer = treatmentAnswer,
        )
        val reconciled = ScoreReconciler.reconcile(parsed, medicalCase, messages, rule)
        return reconciled.copy(
            correctDiagnosis = reconciled.correctDiagnosis.ifBlank { medicalCase.correctDiagnosis },
        )
    }
}

object ScoreReconciler {
    fun reconcile(
        score: ScoreResult,
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        rule: RuleScore? = null,
    ): ScoreResult {
        // 旧版模型 JSON 缺少 treatment 分项时 isLegacy=true，但那只是模型自报分；
        // 只要本地规则分存在就必须覆盖，否则模型自报分直接生效、满分口径还会变成 40/20/20/20。
        if (rule != null) {
            val coveredPoints = rule.evidence
                .filter { it.messageIndexes.isNotEmpty() }
                .map { item ->
                    val refs = item.messageIndexes.joinToString("") { "[$it]" }
                    "${item.question}（见$refs）"
                }
                .distinct()
            return score.copy(
                coverageScore = rule.coverage,
                techniqueScore = rule.technique,
                communicationScore = rule.communication,
                diagnosisScore = rule.diagnosis,
                treatmentScore = rule.treatment,
                totalScore = rule.total,
                coveredPoints = coveredPoints,
                missedQuestions = rule.missedQuestions.distinct(),
                evidence = rule.evidence,
                isLegacy = false,
            )
        }
        val evidence = QuestionCoverageAnalyzer.analyze(medicalCase, messages)
        if (evidence.isEmpty()) return score
        val covered = evidence.filter { it.covered }
        val coveredQuestions = covered.map { it.question }
        val sanitizedCovered = score.coveredPoints
            .mapNotNull { sanitizeReference(it, messages) }
            .distinct()
            .take(evidence.size)
        val missed = score.missedQuestions
            .distinct()
            .filterNot { missedQuestion ->
                coveredQuestions.any { question -> related(missedQuestion, question) }
            }
            .take(evidence.size)
        val coverageFloor = ((covered.size.toDouble() / evidence.size) * score.coverageMax).toInt()
        // 问诊要点覆盖改为病例级本地规则分，模型只负责解释与建议，避免同一对话评分漂移。
        val coverage = coverageFloor.coerceIn(0, score.coverageMax)
        val total = (coverage + score.techniqueScore + score.communicationScore + score.diagnosisScore + score.treatmentScore)
            .coerceIn(0, 100)
        return score.copy(
            coverageScore = coverage,
            totalScore = total,
            coveredPoints = sanitizedCovered,
            missedQuestions = missed,
            evidence = evidence.map { ScoreEvidence(it.question, it.messageIndexes) },
        )
    }

    /**
     * 校验模型给出的证据编号：编号必须指向 [PromptBuilder.formatTranscript] 里真实存在的非系统条目，
     * 全部编号无效时丢弃该条覆盖点，部分无效时仅去掉无效编号，避免“引用编号不存在”被当作有效证据。
     */
    private fun sanitizeReference(point: String, messages: List<ChatMessage>): String? {
        val refs = Regex("\\[(\\d+)]").findAll(point).toList()
        if (refs.isEmpty()) return point
        val transcript = transcriptEntries(messages)
        val valid = refs.filter { match ->
            val index = match.groupValues[1].toIntOrNull() ?: return@filter false
            index in 1..transcript.size && transcript[index - 1].role != MessageRole.SYSTEM
        }
        if (valid.isEmpty()) return null
        var cleaned = point
        refs.filterNot { it in valid }.forEach { cleaned = cleaned.replace(it.value, "") }
        return cleaned.trim()
    }

    private fun related(left: String, right: String): Boolean {
        val leftTerms = Regex("[\\u4e00-\\u9fff]{2,}").findAll(left).map { it.value }.toList()
        return leftTerms.any { term -> term.length >= 2 && right.contains(term) }
    }
}

class CaseGenerationService(
    private val llmClient: LlmClient,
    private val promptBuilder: PromptBuilder,
) {
    suspend fun generate(
        config: LlmConfig,
        department: String,
        difficulty: Difficulty,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
    ): Pair<MedicalCase, PatientPersonality> {
        val raw = llmClient.complete(
            config = config,
            messages = listOf(
                LlmMessage(
                    "system",
                    promptBuilder.buildCaseGenerationPrompt(
                        department = department,
                        difficulty = difficulty,
                        personality = personality,
                        encounterType = encounterType,
                        departmentHint = CaseGenerationDepartments.hintFor(department),
                    ),
                ),
                LlmMessage("user", "请按要求生成这个$department、${difficulty.label}难度、${encounterType.label}的教学病例，只输出 JSON。"),
            ),
            jsonMode = true,
            temperature = 0.9,
        )
        val medicalCase = CaseJsonCodec.parseGenerated(raw, department, difficulty)
        val finalPersonality = if (personality.availableFor(medicalCase.patient.age)) {
            personality
        } else {
            PatientPersonality.COOPERATIVE
        }
        return medicalCase to finalPersonality
    }
}
