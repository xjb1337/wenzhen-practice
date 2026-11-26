package com.xjb.wenzhen.ui

import com.xjb.wenzhen.BuildConfig
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.db.ConsultationListItem
import com.xjb.wenzhen.data.db.ConsultationDraftEntity
import com.xjb.wenzhen.data.db.SavedCaseEntity
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.Departments
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.PatientPersonality
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.domain.ScoreResult
import com.xjb.wenzhen.domain.StatsSummary
import com.xjb.wenzhen.domain.ReferenceCarePlan

data class ResultViewData(
    val recordId: Long,
    val caseTitle: String,
    val department: String,
    val patientName: String,
    val difficulty: String,
    val createdAt: Long,
    val medicalRecord: String,
    val score: ScoreResult?,
    val initialDiagnosis: String,
    val diagnosisBasis: String = "",
    val treatmentAnswer: String = "",
    val encounterType: EncounterType = EncounterType.OUTPATIENT,
    val messages: List<ChatMessage> = emptyList(),
    val referenceCarePlan: ReferenceCarePlan? = null,
    val notice: String? = null,
    val isAiGeneratedCase: Boolean = false,
    val savedTotalScore: Int? = score?.totalScore,
)

data class AppUiState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val cases: List<MedicalCase> = BuiltInCases.all,
    val savedCases: List<SavedCaseEntity> = emptyList(),
    val selectedDepartment: String = Departments.ALL,
    val settings: AppSettings = AppSettings(),
    val settingsRead: SettingsReadState = SettingsReadState.Ready,
    val draft: ConsultationDraftEntity? = null,
    val draftLoading: Boolean = true,
    val activeCase: MedicalCase? = null,
    val patientPersonality: PatientPersonality = PatientPersonality.COOPERATIVE,
    val encounterType: EncounterType = EncounterType.OUTPATIENT,
    val consultationStartedAt: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isPatientTyping: Boolean = false,
    val isQueryingClinical: Boolean = false,
    val chatError: String? = null,
    val lastReplyMillis: Long? = null,
    val lastReplyTokens: Int? = null,
    val requestNotice: String? = null,
    val isGeneratingCase: Boolean = false,
    val generationError: String? = null,
    val finishError: String? = null,
    val isFinishing: Boolean = false,
    val currentResult: ResultViewData? = null,
    /** 正在重新生成 AI 点评的历史记录 id；为 null 表示没有进行中的请求。 */
    val regeneratingCommentId: Long? = null,
    val history: List<ConsultationListItem> = emptyList(),
    val historyQuery: String = "",
    val historyLoading: Boolean = true,
    val historyTransfer: HistoryTransfer? = null,
    val historyError: String? = null,
    val historyDetail: ResultViewData? = null,
    val historyDetailLoading: Boolean = false,
    val historyDetailError: String? = null,
    val settingsSaveError: String? = null,
    val settingsFormSaving: Boolean = false,
    val pendingOfflineMode: Boolean? = null,
    val acceptingPrivacyNotice: Boolean = false,
    val connectionTest: ConnectionTestUiState = ConnectionTestUiState(),
    val updateState: UpdateUiState = UpdateUiState.Idle,
) {
    val filteredCases: List<MedicalCase>
        get() = if (selectedDepartment == Departments.ALL) {
            cases
        } else {
            cases.filter { it.department == selectedDepartment }
        }

    val hasApiKey: Boolean
        get() = settings.hasApiKey

    val aiSetupMessage: String
        get() = settings.aiSetupMessage

    val offlineMode: Boolean
        get() = settings.offlineMode || pendingOfflineMode == true

    val canPractice: Boolean
        get() = hasApiKey || offlineMode

    /** 粗略的上下文 token 估算：中文约 1 字 ≈ 0.5 token，用于提前提醒。 */
    val contextTokenEstimate: Int
        get() {
            val messageCost = messages.sumOf { it.content.length } / 2
            val caseCost = (activeCase?.let { it.presentIllness.length + it.pastHistory.length } ?: 0) / 2
            return messageCost + caseCost + 400
        }

    val contextWarning: Boolean
        get() = contextTokenEstimate >= 12_000
}

sealed interface SettingsReadState {
    data object Loading : SettingsReadState
    data object Ready : SettingsReadState
    data class Unavailable(val temporaryOffline: Boolean = false) : SettingsReadState
}

enum class HistoryTransfer {
    RESTORING,
    SAVING,
}

sealed interface AppEvent {
    data class ShowMessage(val message: String) : AppEvent
    data class OpenChat(val startedAt: Long) : AppEvent
    data class OpenResult(val recordId: Long) : AppEvent
    data object OpenSettings : AppEvent
    data class SettingsSaved(val exit: SettingsExit?) : AppEvent
}

sealed interface SettingsExit {
    data object Back : SettingsExit
    data class Tab(val route: String) : SettingsExit
}

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Ready(val summary: StatsSummary) : StatsUiState
    data class Failed(val message: String) : StatsUiState
}
