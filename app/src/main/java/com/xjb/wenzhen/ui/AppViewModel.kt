package com.xjb.wenzhen.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xjb.wenzhen.AppContainer
import com.xjb.wenzhen.data.cases.CaseJsonCodec
import com.xjb.wenzhen.data.db.ConsultationEntity
import com.xjb.wenzhen.data.db.ConversationJsonCodec
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.domain.ScoreParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val initialSettings = runCatching { container.settingsRepository.load() }
        .getOrDefault(AppSettings())

    private val _uiState = MutableStateFlow(AppUiState(settings = initialSettings))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _events = Channel<AppEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var historyJob: Job? = null

    init {
        observeHistory()
    }

    fun selectDepartment(department: String) {
        _uiState.update { it.copy(selectedDepartment = department) }
    }

    fun openCase(medicalCase: MedicalCase) {
        val openingMessage = ChatMessage(
            role = MessageRole.PATIENT,
            content = medicalCase.openingLine,
        )
        _uiState.update {
            it.copy(
                activeCase = medicalCase,
                messages = listOf(openingMessage),
                isPatientTyping = false,
                chatError = null,
                finishError = null,
                currentResult = null,
            )
        }
        viewModelScope.launch { _events.send(AppEvent.OpenChat) }
    }

    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        val state = _uiState.value
        if (state.isPatientTyping) return
        val medicalCase = state.activeCase ?: return

        if (!state.hasApiKey) {
            _uiState.update { it.copy(chatError = "请先在设置中填写 DeepSeek API Key。") }
            viewModelScope.launch { _events.send(AppEvent.OpenSettings) }
            return
        }

        val doctorMessage = ChatMessage(role = MessageRole.DOCTOR, content = content)
        val conversation = state.messages + doctorMessage
        _uiState.update {
            it.copy(
                messages = conversation,
                isPatientTyping = true,
                chatError = null,
            )
        }

        viewModelScope.launch {
            try {
                val reply = container.patientChatService.reply(
                    config = state.settings.toLlmConfig(),
                    medicalCase = medicalCase,
                    messages = conversation,
                )
                if (reply.isBlank()) {
                    throw IllegalStateException("患者没有返回内容，请重新询问。")
                }
                _uiState.update { latest ->
                    if (latest.activeCase?.id != medicalCase.id) {
                        latest.copy(isPatientTyping = false)
                    } else {
                        latest.copy(
                            messages = latest.messages + ChatMessage(
                                role = MessageRole.PATIENT,
                                content = reply,
                            ),
                            isPatientTyping = false,
                            chatError = null,
                        )
                    }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isPatientTyping = false,
                        chatError = friendlyMessage(error, "发送失败，请检查网络后重试。"),
                    )
                }
            }
        }
    }

    fun clearChatError() {
        _uiState.update { it.copy(chatError = null) }
    }

    fun generateCase(department: String, difficulty: Difficulty) {
        val state = _uiState.value
        if (state.isGeneratingCase) return
        if (!state.hasApiKey) {
            _uiState.update {
                it.copy(generationError = "请先在设置中保存 DeepSeek API Key，再生成病例。")
            }
            viewModelScope.launch { _events.send(AppEvent.OpenSettings) }
            return
        }

        _uiState.update {
            it.copy(isGeneratingCase = true, generationError = null)
        }
        viewModelScope.launch {
            try {
                val generated = container.caseGenerationService.generate(
                    config = state.settings.toLlmConfig(),
                    department = department,
                    difficulty = difficulty,
                )
                _uiState.update { latest ->
                    latest.copy(
                        cases = listOf(generated) + latest.cases,
                        isGeneratingCase = false,
                        generationError = null,
                    )
                }
                openCase(generated)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingCase = false,
                        generationError = friendlyMessage(error, "病例生成失败，请稍后重试。"),
                    )
                }
            }
        }
    }

    fun clearGenerationError() {
        _uiState.update { it.copy(generationError = null) }
    }

    fun finishConsultation(initialDiagnosis: String, diagnosisBasis: String) {
        val diagnosis = initialDiagnosis.trim()
        if (diagnosis.isEmpty()) {
            _uiState.update { it.copy(finishError = "请先填写你的初步诊断。") }
            return
        }
        val state = _uiState.value
        if (state.isFinishing) return
        val medicalCase = state.activeCase
        if (medicalCase == null) {
            _uiState.update { it.copy(finishError = "当前没有进行中的病例。") }
            return
        }
        if (!state.hasApiKey) {
            _uiState.update { it.copy(finishError = "请先在设置中填写 DeepSeek API Key。") }
            viewModelScope.launch { _events.send(AppEvent.OpenSettings) }
            return
        }

        val conversation = state.messages.toList()
        val config = state.settings.toLlmConfig()
        _uiState.update { it.copy(isFinishing = true, finishError = null) }
        viewModelScope.launch {
            try {
                val (record, score) = coroutineScope {
                    val recordTask = async {
                        container.medicalRecordService.generate(
                            config = config,
                            medicalCase = medicalCase,
                            messages = conversation,
                            initialDiagnosis = diagnosis,
                            diagnosisBasis = diagnosisBasis.trim(),
                        )
                    }
                    val scoreTask = async {
                        container.scoreService.evaluate(
                            config = config,
                            medicalCase = medicalCase,
                            messages = conversation,
                            initialDiagnosis = diagnosis,
                            diagnosisBasis = diagnosisBasis.trim(),
                        )
                    }
                    recordTask.await() to scoreTask.await()
                }

                val createdAt = System.currentTimeMillis()
                val entity = ConsultationEntity(
                    caseTitle = medicalCase.title,
                    department = medicalCase.department,
                    difficulty = medicalCase.difficulty.label,
                    patientName = medicalCase.patient.name,
                    caseJson = CaseJsonCodec.encode(medicalCase),
                    conversationJson = ConversationJsonCodec.encode(conversation),
                    medicalRecord = record,
                    scoreJson = ScoreParser.encode(score),
                    totalScore = score.totalScore,
                    initialDiagnosis = diagnosis,
                    diagnosisBasis = diagnosisBasis.trim(),
                    createdAt = createdAt,
                )
                val recordId = container.consultationRepository.save(entity)
                val result = ResultViewData(
                    recordId = recordId,
                    caseTitle = medicalCase.title,
                    department = medicalCase.department,
                    patientName = medicalCase.patient.name,
                    difficulty = medicalCase.difficulty.label,
                    createdAt = createdAt,
                    medicalRecord = record,
                    score = score,
                    initialDiagnosis = diagnosis,
                )
                _uiState.update {
                    it.copy(
                        isFinishing = false,
                        finishError = null,
                        currentResult = result,
                    )
                }
                _events.send(AppEvent.OpenResult)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isFinishing = false,
                        finishError = friendlyMessage(error, "病历或评分生成失败，请稍后重试。"),
                    )
                }
            }
        }
    }

    fun clearFinishError() {
        _uiState.update { it.copy(finishError = null) }
    }

    fun saveSettings(apiKey: String, model: String, baseUrl: String) {
        val normalizedUrl = baseUrl.trim().ifBlank { AppSettings.DEFAULT_BASE_URL }
        if (!normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://")) {
            _uiState.update { it.copy(settingsSaveError = "Base URL 必须以 http:// 或 https:// 开头。") }
            return
        }
        val newSettings = AppSettings(
            apiKey = apiKey.trim(),
            model = model.trim().ifBlank { AppSettings.MODEL_FLASH },
            baseUrl = normalizedUrl.trimEnd('/'),
        )
        try {
            container.settingsRepository.save(newSettings)
            _uiState.update {
                it.copy(
                    settings = newSettings,
                    settingsSaveError = null,
                    chatError = null,
                )
            }
            viewModelScope.launch { _events.send(AppEvent.SettingsSaved) }
        } catch (error: Exception) {
            _uiState.update {
                it.copy(
                    settingsSaveError = friendlyMessage(error, "设置保存失败，请稍后重试。"),
                )
            }
        }
    }

    fun clearSettingsSaveError() {
        _uiState.update { it.copy(settingsSaveError = null) }
    }

    fun loadHistoryDetail(id: Long) {
        _uiState.update {
            it.copy(
                historyDetail = null,
                historyDetailLoading = true,
                historyDetailError = null,
            )
        }
        viewModelScope.launch {
            try {
                val entity = container.consultationRepository.getById(id)
                    ?: throw IllegalStateException("这条问诊记录不存在或已被删除。")
                val score = ScoreParser.parse(entity.scoreJson)
                val result = ResultViewData(
                    recordId = entity.id,
                    caseTitle = entity.caseTitle,
                    department = entity.department,
                    patientName = entity.patientName,
                    difficulty = entity.difficulty,
                    createdAt = entity.createdAt,
                    medicalRecord = entity.medicalRecord,
                    score = score,
                    initialDiagnosis = entity.initialDiagnosis,
                )
                _uiState.update {
                    it.copy(
                        historyDetail = result,
                        historyDetailLoading = false,
                        historyDetailError = null,
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        historyDetailLoading = false,
                        historyDetailError = friendlyMessage(error, "历史详情加载失败，请稍后重试。"),
                    )
                }
            }
        }
    }

    fun retryHistory() {
        _uiState.update { it.copy(historyLoading = true, historyError = null) }
        observeHistory()
    }

    private fun observeHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            container.consultationRepository.observeAll()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            historyLoading = false,
                            historyError = friendlyMessage(error, "历史记录加载失败。"),
                        )
                    }
                }
                .collect { history ->
                    _uiState.update {
                        it.copy(
                            history = history,
                            historyLoading = false,
                            historyError = null,
                        )
                    }
                }
        }
    }

    private fun friendlyMessage(error: Throwable, fallback: String): String =
        error.message?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                        return AppViewModel(container) as T
                    }
                    throw IllegalArgumentException("未知的 ViewModel 类型：${modelClass.name}")
                }
            }
    }
}
