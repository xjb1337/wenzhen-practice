package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.cases.CaseJsonCodec
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageRole

class PatientChatService(
    private val llmClient: LlmClient,
    private val promptBuilder: PromptBuilder,
) {
    suspend fun reply(
        config: LlmConfig,
        medicalCase: MedicalCase,
        messages: List<ChatMessage>,
    ): String {
        val apiMessages = buildList {
            add(LlmMessage("system", promptBuilder.buildPatientPrompt(medicalCase)))
            messages.forEach { message ->
                add(
                    LlmMessage(
                        role = if (message.role == MessageRole.DOCTOR) "user" else "assistant",
                        content = message.content,
                    )
                )
            }
        }
        return llmClient.complete(
            config = config,
            messages = apiMessages,
            jsonMode = false,
            temperature = 0.8,
        ).trim()
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
