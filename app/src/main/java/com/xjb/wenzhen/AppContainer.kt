package com.xjb.wenzhen

import android.content.Context
import com.xjb.wenzhen.data.api.DeepSeekClient
import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.db.AppDatabase
import com.xjb.wenzhen.data.db.ConsultationRepository
import com.xjb.wenzhen.data.settings.SettingsRepository
import com.xjb.wenzhen.domain.CaseGenerationService
import com.xjb.wenzhen.domain.MedicalRecordService
import com.xjb.wenzhen.domain.PatientChatService
import com.xjb.wenzhen.domain.PromptBuilder
import com.xjb.wenzhen.domain.ScoreService

class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)
    val consultationRepository = ConsultationRepository(database.consultationDao())
    val settingsRepository = SettingsRepository(context)
    val llmClient: LlmClient = DeepSeekClient()

    private val promptBuilder = PromptBuilder()
    val patientChatService = PatientChatService(llmClient, promptBuilder)
    val medicalRecordService = MedicalRecordService(llmClient, promptBuilder)
    val scoreService = ScoreService(llmClient, promptBuilder)
    val caseGenerationService = CaseGenerationService(llmClient, promptBuilder)
}
