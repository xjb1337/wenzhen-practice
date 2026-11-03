package com.xjb.wenzhen.ui

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.db.ConsultationEntity
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.Departments
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.domain.ScoreResult

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
)

data class AppUiState(
    val cases: List<MedicalCase> = BuiltInCases.all,
    val selectedDepartment: String = Departments.ALL,
    val settings: AppSettings = AppSettings(),
    val activeCase: MedicalCase? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isPatientTyping: Boolean = false,
    val chatError: String? = null,
    val isGeneratingCase: Boolean = false,
    val generationError: String? = null,
    val finishError: String? = null,
    val isFinishing: Boolean = false,
    val currentResult: ResultViewData? = null,
    val history: List<ConsultationEntity> = emptyList(),
    val historyLoading: Boolean = true,
    val historyError: String? = null,
    val historyDetail: ResultViewData? = null,
    val historyDetailLoading: Boolean = false,
    val historyDetailError: String? = null,
    val settingsSaveError: String? = null,
) {
    val filteredCases: List<MedicalCase>
        get() = if (selectedDepartment == Departments.ALL) {
            cases
        } else {
            cases.filter { it.department == selectedDepartment }
        }

    val hasApiKey: Boolean
        get() = settings.hasApiKey

    val builtInCaseCount: Int
        get() = BuiltInCases.all.size
}

sealed interface AppEvent {
    data class ShowMessage(val message: String) : AppEvent
    data object OpenChat : AppEvent
    data object OpenResult : AppEvent
    data object OpenSettings : AppEvent
    data object SettingsSaved : AppEvent
}
