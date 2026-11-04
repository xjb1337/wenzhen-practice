package com.xjb.wenzhen

import android.content.Context
import com.xjb.wenzhen.data.api.DeepSeekClient
import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.db.AppDatabase
import com.xjb.wenzhen.data.db.ConsultationRepository
import com.xjb.wenzhen.data.settings.SettingsRepository
import com.xjb.wenzhen.data.update.UpdateChecker
import com.xjb.wenzhen.domain.CaseGenerationService
import com.xjb.wenzhen.domain.ClinicalDataService
import com.xjb.wenzhen.domain.MedicalRecordService
import com.xjb.wenzhen.domain.PatientChatService
import com.xjb.wenzhen.domain.PromptBuilder
import com.xjb.wenzhen.domain.ScoreService

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.get(context)
    val consultationRepository = ConsultationRepository(database.consultationDao())
    val settingsRepository = SettingsRepository(context)
    val llmClient: LlmClient = DeepSeekClient()
    val updateChecker = UpdateChecker(appContext)
    val currentVersionName: String
    val currentVersionCode: Int

    init {
        @Suppress("DEPRECATION")
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        currentVersionName = info.versionName.orEmpty().ifBlank { "1.2.1" }
        currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
    }

    private val promptBuilder = PromptBuilder()
    val patientChatService = PatientChatService(llmClient, promptBuilder)
    val medicalRecordService = MedicalRecordService(llmClient, promptBuilder)
    val scoreService = ScoreService(llmClient, promptBuilder)
    val caseGenerationService = CaseGenerationService(llmClient, promptBuilder)
    val clinicalDataService = ClinicalDataService(llmClient, promptBuilder)
}
