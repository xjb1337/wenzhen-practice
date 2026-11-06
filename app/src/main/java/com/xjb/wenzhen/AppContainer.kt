package com.xjb.wenzhen

import android.content.Context
import com.xjb.wenzhen.BuildConfig
import com.xjb.wenzhen.data.api.DeepSeekClient
import com.xjb.wenzhen.data.api.InviteRedeemer
import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.api.RelayAuthWatchingClient
import com.xjb.wenzhen.data.api.RelayClient
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
    overrideInviteRedeemer: InviteRedeemer? = null,
    overrideConsultationRepository: ConsultationRepository? = null,
) {
    private val appContext = context.applicationContext
    private val database = overrideDatabase ?: AppDatabase.get(context)
    val consultationRepository = overrideConsultationRepository ?: ConsultationRepository(database.consultationDao())
    val consultationDraftRepository = ConsultationDraftRepository(database.consultationDraftDao())
    val savedCaseRepository = SavedCaseRepository(database.savedCaseDao())
    val settingsRepository = overrideSettingsRepository ?: SettingsRepository(context)
    private val baseLlmClient: LlmClient = overrideLlmClient ?: DeepSeekClient()
    val relayAuthWatchingClient = RelayAuthWatchingClient(baseLlmClient)
    val llmClient: LlmClient = relayAuthWatchingClient
    val relayClient: InviteRedeemer = overrideInviteRedeemer ?: RelayClient()
    val updateChecker = UpdateChecker(appContext)
    val currentVersionName: String
    val currentVersionCode: Int

    init {
        currentVersionName = BuildConfig.VERSION_NAME
        currentVersionCode = BuildConfig.VERSION_CODE
    }

    private val promptBuilder = PromptBuilder()
    val patientChatService = PatientChatService(llmClient, promptBuilder)
    val medicalRecordService = MedicalRecordService(llmClient, promptBuilder)
    val scoreService = ScoreService(llmClient, promptBuilder)
    val caseGenerationService = CaseGenerationService(llmClient, promptBuilder)
    val clinicalDataService = ClinicalDataService(llmClient, promptBuilder)
}
