package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.cases.CaseJsonCodec
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.ClinicalRequestType
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.model.PatientPersonality

class PatientChatService(
    private val llmClient: LlmClient,
    private val promptBuilder: PromptBuilder,
) {
    suspend fun reply(
        config: LlmConfig,
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
    ): String {
        val apiMessages = buildPatientMessages(medicalCase, messages, personality)
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
        onDelta: suspend (String) -> Unit,
    ): String {
        return llmClient.stream(
            config = config,
            messages = buildPatientMessages(medicalCase, messages, personality),
            temperature = 0.8,
            onDelta = onDelta,
        ).trim()
    }

    private fun buildPatientMessages(
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
        personality: PatientPersonality,
    ): List<LlmMessage> = buildList {
        add(LlmMessage("system", promptBuilder.buildPatientPrompt(medicalCase, personality)))
        messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
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
    ): String {
        val raw = llmClient.complete(
            config = config,
            messages = listOf(
                LlmMessage("system", promptBuilder.buildRecordSystemPrompt(medicalCase)),
                LlmMessage(
                    "user",
                    promptBuilder.buildRecordUserPrompt(messages, initialDiagnosis, diagnosisBasis),
                ),
            ),
            jsonMode = false,
            temperature = 0.2,
        )
        return RecordParser.parse(raw, initialDiagnosis)
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
    ): ScoreResult {
        val raw = llmClient.complete(
            config = config,
            messages = listOf(
                LlmMessage("system", promptBuilder.buildScoreSystemPrompt(medicalCase)),
                LlmMessage(
                    "user",
                    promptBuilder.buildScoreUserPrompt(messages, initialDiagnosis, diagnosisBasis),
                ),
            ),
            jsonMode = true,
            temperature = 0.2,
        )
        val parsed = ScoreParser.parse(raw)
        return parsed.copy(
            correctDiagnosis = parsed.correctDiagnosis.ifBlank { medicalCase.correctDiagnosis },
        )
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
    ): MedicalCase {
        val raw = llmClient.complete(
            config = config,
            messages = listOf(
                LlmMessage(
                    "system",
                    promptBuilder.buildCaseGenerationPrompt(department, difficulty),
                ),
                LlmMessage("user", "请按要求生成这个$department、${difficulty.label}难度的教学病例，只输出 JSON。"),
            ),
            jsonMode = true,
            temperature = 0.9,
        )
        return CaseJsonCodec.parseGenerated(raw, department, difficulty)
    }
}
