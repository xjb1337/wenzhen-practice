package com.xjb.wenzhen

import android.content.Context
import com.xjb.wenzhen.BuildConfig
import com.xjb.wenzhen.data.api.DeepSeekClient
import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.db.AppDatabase
import com.xjb.wenzhen.data.db.ConsultationRepository
import com.xjb.wenzhen.data.db.ConsultationDraftRepository
import com.xjb.wenzhen.data.db.SavedCaseRepository
import com.xjb.wenzhen.data.settings.SettingsRepository
import com.xjb.wenzhen.data.update.UpdateChecker
import com.xjb.wenzhen.domain.CaseGenerationService
import com.xjb.wenzhen.domain.ClinicalDataService
import com.xjb.wenzhen.domain.MedicalRecordService
import com.xjb.wenzhen.domain.PatientChatService
import com.xjb.wenzhen.domain.PromptBuilder
import com.xjb.wenzhen.domain.ScoreService

class AppContainer(
    context: Context,
    overrideLlmClient: LlmClient? = null,
    overrideDatabase: AppDatabase? = null,
    overrideSettingsRepository: SettingsRepository? = null,
    overrideConsultationRepository: ConsultationRepository? = null,
    overrideConsultationDraftRepository: ConsultationDraftRepository? = null,
) {
    private val appContext = context.applicationContext
    private val database by lazy { overrideDatabase ?: AppDatabase.get(appContext) }
    val consultationRepository by lazy {
        overrideConsultationRepository ?: ConsultationRepository(database.consultationDao())
    }
    val consultationDraftRepository by lazy {
        overrideConsultationDraftRepository ?: ConsultationDraftRepository(database.consultationDraftDao())
    }
    val savedCaseRepository by lazy { SavedCaseRepository(database.savedCaseDao()) }
    val settingsRepository by lazy { overrideSettingsRepository ?: SettingsRepository(appContext) }
    val llmClient: LlmClient by lazy { overrideLlmClient ?: DeepSeekClient() }
    val updateChecker by lazy { UpdateChecker(appContext) }
    val currentVersionName: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Int = BuildConfig.VERSION_CODE

    private val promptBuilder by lazy { PromptBuilder() }
    val patientChatService by lazy { PatientChatService(llmClient, promptBuilder) }
    val medicalRecordService by lazy { MedicalRecordService(llmClient, promptBuilder) }
    val scoreService by lazy { ScoreService(llmClient, promptBuilder) }
    val caseGenerationService by lazy { CaseGenerationService(llmClient, promptBuilder) }
    val clinicalDataService by lazy { ClinicalDataService(llmClient, promptBuilder) }
}
