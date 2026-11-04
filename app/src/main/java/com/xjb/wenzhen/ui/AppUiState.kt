package com.xjb.wenzhen.ui

import com.xjb.wenzhen.BuildConfig
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.db.ConsultationEntity
import com.xjb.wenzhen.data.db.ConsultationDraftEntity
import com.xjb.wenzhen.data.db.SavedCaseEntity
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.Departments
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.PatientPersonality
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.domain.ScoreResult
import com.xjb.wenzhen.domain.ScoreParser
import com.xjb.wenzhen.domain.StatsCalculator
import com.xjb.wenzhen.domain.StatsRecord
import com.xjb.wenzhen.domain.StatsSummary

data class ResultViewData(
    val recordId: Long,
    val caseTitle: String,
    val department: String,
    val patientName: String,
    val difficulty: String,
    val createdAt: Long,
    val medicalRecord: String,
    val score: ScoreResult,
    val initialDiagnosis: String,
    val treatmentAnswer: String = "",
    val encounterType: EncounterType = EncounterType.OUTPATIENT,
    val messages: List<ChatMessage> = emptyList(),
    val notice: String? = null,
)

data class AppUiState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val cases: List<MedicalCase> = BuiltInCases.all,
    val savedCases: List<SavedCaseEntity> = emptyList(),
    val selectedDepartment: String = Departments.ALL,
    val settings: AppSettings = AppSettings(),
    val draft: ConsultationDraftEntity? = null,
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
    val history: List<ConsultationEntity> = emptyList(),
    val historyQuery: String = "",
    val historyLoading: Boolean = true,
    val historyError: String? = null,
    val historyDetail: ResultViewData? = null,
    val historyDetailLoading: Boolean = false,
    val historyDetailError: String? = null,
    val settingsSaveError: String? = null,
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

    val canPractice: Boolean
        get() = hasApiKey || settings.offlineMode

    val builtInCaseCount: Int
        get() = BuiltInCases.all.size

    val stats: StatsSummary
        get() = StatsCalculator.calculate(
            history.map { entity ->
                val score = runCatching { ScoreParser.parse(entity.scoreJson) }.getOrNull()
                StatsRecord(
                    id = entity.id,
                    department = entity.department,
                    totalScore = score?.totalScore ?: entity.totalScore,
                    createdAt = entity.createdAt,
                    missedQuestions = score?.missedQuestions.orEmpty(),
                )
            }
        )

    val filteredHistory: List<ConsultationEntity>
        get() = historyQuery.trim().takeIf { it.isNotEmpty() }?.let { query ->
            history.filter {
                it.caseTitle.contains(query, ignoreCase = true) ||
                    it.department.contains(query, ignoreCase = true) ||
                    it.patientName.contains(query, ignoreCase = true)
            }
        } ?: history

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

sealed interface AppEvent {
    data class ShowMessage(val message: String) : AppEvent
    data object OpenChat : AppEvent
    data object OpenResult : AppEvent
    data object OpenSettings : AppEvent
    data object SettingsSaved : AppEvent
}
