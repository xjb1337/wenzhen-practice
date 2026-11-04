package com.xjb.wenzhen.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xjb.wenzhen.AppContainer
import com.xjb.wenzhen.data.api.ConnectionTestResult
import com.xjb.wenzhen.data.cases.CaseJsonCodec
import com.xjb.wenzhen.data.db.ConsultationEntity
import com.xjb.wenzhen.data.db.ConversationJsonCodec
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.ClinicalRequestType
import com.xjb.wenzhen.data.model.Difficulty
import com.xjb.wenzhen.data.model.MedicalCase
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.model.PatientPersonality
import com.xjb.wenzhen.data.model.EncounterType
import com.xjb.wenzhen.data.model.ThinkingLevel
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.data.update.UpdateCheckResult
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

    private val _uiState = MutableStateFlow(
        AppUiState(
            settings = initialSettings,
            currentVersionName = container.currentVersionName,
            currentVersionCode = container.currentVersionCode,
        )
    )
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

    fun openCase(
        medicalCase: MedicalCase,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
    ) {
        val openingMessage = ChatMessage(
            role = MessageRole.PATIENT,
            content = medicalCase.openingLine,
        )
        _uiState.update {
            it.copy(
                activeCase = medicalCase,
                patientPersonality = personality,
                encounterType = encounterType,
                consultationStartedAt = System.currentTimeMillis(),
                messages = listOf(openingMessage),
                isPatientTyping = false,
                isQueryingClinical = false,
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
        if (state.isPatientTyping || state.isQueryingClinical) return
        val medicalCase = state.activeCase ?: return

        if (!state.hasApiKey) {
            _uiState.update { it.copy(chatError = "请先在设置中填写 DeepSeek API Key。") }
            viewModelScope.launch { _events.send(AppEvent.OpenSettings) }
            return
        }

        val doctorMessage = ChatMessage(
            role = MessageRole.DOCTOR,
            content = content,
            deliveryState = MessageDeliveryState.SENDING,
        )
        val conversation = state.messages + doctorMessage
        _uiState.update {
            it.copy(
                messages = conversation,
                isPatientTyping = true,
                chatError = null,
            )
        }
        requestPatientReply(
            medicalCase = medicalCase,
            config = state.settings.toLlmConfig(),
            personality = state.patientPersonality,
            encounterType = state.encounterType,
            conversation = conversation,
            doctorIndex = conversation.lastIndex,
        )
    }

    fun retryFailedMessage() {
        val state = _uiState.value
        if (state.isPatientTyping || state.isQueryingClinical) return
        val medicalCase = state.activeCase ?: return
        val failedIndex = state.messages.indexOfLast {
            it.role == MessageRole.DOCTOR && it.deliveryState == MessageDeliveryState.FAILED
        }
        if (failedIndex < 0) return
        if (!state.hasApiKey) {
            _uiState.update { it.copy(chatError = "请先在设置中填写 DeepSeek API Key。") }
            viewModelScope.launch { _events.send(AppEvent.OpenSettings) }
            return
        }

        val conversation = state.messages.take(failedIndex + 1).toMutableList()
        conversation[failedIndex] = conversation[failedIndex].copy(
            deliveryState = MessageDeliveryState.SENDING,
        )
        _uiState.update {
            it.copy(
                messages = conversation,
                isPatientTyping = true,
                chatError = null,
            )
        }
        requestPatientReply(
            medicalCase = medicalCase,
            config = state.settings.toLlmConfig(),
            personality = state.patientPersonality,
            encounterType = state.encounterType,
            conversation = conversation,
            doctorIndex = failedIndex,
        )
    }

    private fun requestPatientReply(
        medicalCase: MedicalCase,
        config: com.xjb.wenzhen.data.model.LlmConfig,
        personality: PatientPersonality,
        encounterType: EncounterType,
        conversation: List<ChatMessage>,
        doctorIndex: Int,
    ) {
        viewModelScope.launch {
            try {
                val reply = container.patientChatService.replyStreaming(
                    config = config,
                    medicalCase = medicalCase,
                    messages = conversation,
                    personality = personality,
                    encounterType = encounterType,
                    onDelta = { delta ->
                        if (delta.isNotEmpty()) {
                            _uiState.update { latest ->
                                applyPatientDelta(
                                    latest = latest,
                                    caseId = medicalCase.id,
                                    doctorIndex = doctorIndex,
                                    delta = delta,
                                )
                            }
                        }
                    },
                )
                if (reply.isBlank()) {
                    throw IllegalStateException("患者没有返回内容，请重新询问。")
                }
                _uiState.update { latest ->
                    finishPatientReply(
                        latest = latest,
                        caseId = medicalCase.id,
                        doctorIndex = doctorIndex,
                        reply = reply,
                    )
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { latest ->
                    failPatientReply(
                        latest = latest,
                        caseId = medicalCase.id,
                        doctorIndex = doctorIndex,
                        message = friendlyMessage(error, "发送失败，请检查网络后重试。"),
                    )
                }
            }
        }
    }

    private fun applyPatientDelta(
        latest: AppUiState,
        caseId: String,
        doctorIndex: Int,
        delta: String,
    ): AppUiState {
        if (latest.activeCase?.id != caseId) return latest.copy(isPatientTyping = false)
        if (latest.messages.getOrNull(doctorIndex)?.deliveryState != MessageDeliveryState.SENDING) {
            return latest
        }
        val updated = latest.messages.toMutableList()
        val patientIndex = doctorIndex + 1
        val existing = updated.getOrNull(patientIndex)
        if (existing?.role == MessageRole.PATIENT &&
            existing.deliveryState == MessageDeliveryState.SENDING
        ) {
            updated[patientIndex] = existing.copy(content = existing.content + delta)
        } else {
            val draft = ChatMessage(
                role = MessageRole.PATIENT,
                content = delta,
                deliveryState = MessageDeliveryState.SENDING,
            )
            if (patientIndex <= updated.size) {
                updated.add(patientIndex, draft)
            } else {
                updated.add(draft)
            }
        }
        return latest.copy(messages = updated, isPatientTyping = true)
    }

    private fun finishPatientReply(
        latest: AppUiState,
        caseId: String,
        doctorIndex: Int,
        reply: String,
    ): AppUiState {
        if (latest.activeCase?.id != caseId) return latest.copy(isPatientTyping = false)
        val updated = latest.messages.toMutableList()
        updated.getOrNull(doctorIndex)?.let { doctor ->
            updated[doctorIndex] = doctor.copy(deliveryState = MessageDeliveryState.SENT)
        }
        val patientIndex = doctorIndex + 1
        val existing = updated.getOrNull(patientIndex)
        if (existing?.role == MessageRole.PATIENT) {
            updated[patientIndex] = existing.copy(
                content = reply,
                deliveryState = MessageDeliveryState.SENT,
            )
        } else {
            val patient = ChatMessage(
                role = MessageRole.PATIENT,
                content = reply,
                deliveryState = MessageDeliveryState.SENT,
            )
            if (patientIndex <= updated.size) {
                updated.add(patientIndex, patient)
            } else {
                updated.add(patient)
            }
        }
        return latest.copy(
            messages = updated,
            isPatientTyping = false,
            chatError = null,
        )
    }

    private fun failPatientReply(
        latest: AppUiState,
        caseId: String,
        doctorIndex: Int,
        message: String,
    ): AppUiState {
        if (latest.activeCase?.id != caseId) return latest.copy(isPatientTyping = false)
        val updated = latest.messages.toMutableList()
        updated.getOrNull(doctorIndex)?.let { doctor ->
            updated[doctorIndex] = doctor.copy(deliveryState = MessageDeliveryState.FAILED)
        }
        val patientIndex = doctorIndex + 1
        val draft = updated.getOrNull(patientIndex)
        if (draft?.role == MessageRole.PATIENT &&
            draft.deliveryState == MessageDeliveryState.SENDING
        ) {
            updated.removeAt(patientIndex)
        }
        return latest.copy(
            messages = updated,
            isPatientTyping = false,
            chatError = message,
        )
    }

    fun clearChatError() {
        _uiState.update { it.copy(chatError = null) }
    }

    fun requestClinical(requestType: ClinicalRequestType, item: String) {
        val normalizedItem = item.trim()
        if (normalizedItem.isEmpty()) return
        val state = _uiState.value
        if (state.isPatientTyping || state.isQueryingClinical) return
        val medicalCase = state.activeCase ?: return
        if (!state.hasApiKey) {
            _uiState.update {
                it.copy(chatError = "请先在设置中填写 DeepSeek API Key，再索取检查结果。")
            }
            viewModelScope.launch { _events.send(AppEvent.OpenSettings) }
            return
        }

        _uiState.update { it.copy(isQueryingClinical = true, chatError = null) }
        viewModelScope.launch {
            try {
                val result = container.clinicalDataService.request(
                    config = state.settings.toLlmConfig(),
                    medicalCase = medicalCase,
                    requestType = requestType,
                    item = normalizedItem,
                )
                _uiState.update { latest ->
                    if (latest.activeCase?.id != medicalCase.id) {
                        latest.copy(isQueryingClinical = false)
                    } else {
                        latest.copy(
                            messages = latest.messages + ChatMessage(
                                role = MessageRole.SYSTEM,
                                content = result,
                                systemTitle = "${requestType.label} · $normalizedItem",
                            ),
                            isQueryingClinical = false,
                            chatError = null,
                        )
                    }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isQueryingClinical = false,
                        chatError = friendlyMessage(error, "${requestType.label}结果获取失败，请稍后重试。"),
                    )
                }
            }
        }
    }

    fun generateCase(
        department: String,
        difficulty: Difficulty,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
    ) {
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
                val (generated, finalPersonality) = container.caseGenerationService.generate(
                    config = state.settings.toLlmConfig(),
                    department = department,
                    difficulty = difficulty,
                    personality = personality,
                    encounterType = encounterType,
                )
                _uiState.update { latest ->
                    latest.copy(
                        cases = listOf(generated) + latest.cases,
                        isGeneratingCase = false,
                        generationError = null,
                    )
                }
                openCase(generated, finalPersonality, encounterType)
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

    fun finishConsultation(initialDiagnosis: String, diagnosisBasis: String, treatmentAnswer: String = "", generateRecord: Boolean = _uiState.value.settings.generateRecord) {
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
                        if (generateRecord) container.medicalRecordService.generate(
                            config = config,
                            medicalCase = medicalCase,
                            messages = conversation,
                            initialDiagnosis = diagnosis,
                            diagnosisBasis = diagnosisBasis.trim(),
                            encounterType = state.encounterType,
                            treatmentAnswer = treatmentAnswer.trim(),
                        ) else ""
                    }
                    val scoreTask = async {
                        container.scoreService.evaluate(
                            config = config,
                            medicalCase = medicalCase,
                            messages = conversation,
                            initialDiagnosis = diagnosis,
                            diagnosisBasis = diagnosisBasis.trim(),
                            encounterType = state.encounterType,
                            treatmentAnswer = treatmentAnswer.trim(),
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
                    patientPersonality = state.patientPersonality.name,
                    encounterType = state.encounterType.name,
                    treatmentAnswer = treatmentAnswer.trim(),
                    recordType = if (generateRecord) state.encounterType.name else "NONE",
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
                    treatmentAnswer = treatmentAnswer.trim(),
                    encounterType = state.encounterType,
                    messages = conversation,
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

    fun saveSettings(
        apiKey: String,
        model: String,
        baseUrl: String,
        thinkingLevel: ThinkingLevel = _uiState.value.settings.thinkingLevel,
        generateRecord: Boolean = _uiState.value.settings.generateRecord,
    ) {
        val normalizedUrl = baseUrl.trim().ifBlank { AppSettings.DEFAULT_BASE_URL }
        if (!normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://")) {
            _uiState.update { it.copy(settingsSaveError = "Base URL 必须以 http:// 或 https:// 开头。") }
            return
        }
        val newSettings = AppSettings(
            apiKey = apiKey.trim(),
            model = model.trim().ifBlank { AppSettings.MODEL_FLASH },
            baseUrl = normalizedUrl.trimEnd('/'),
            thinkingLevel = thinkingLevel,
            generateRecord = generateRecord,
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

    fun testConnection(
        apiKey: String,
        model: String,
        baseUrl: String,
        thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    ) {
        if (_uiState.value.connectionTest.isTesting) return
        val key = apiKey.trim()
        if (key.isEmpty()) {
            _uiState.update {
                it.copy(
                    connectionTest = ConnectionTestUiState(
                        message = "请先填写 DeepSeek API Key。",
                        isSuccess = false,
                    )
                )
            }
            return
        }
        val normalizedUrl = baseUrl.trim().ifBlank { AppSettings.DEFAULT_BASE_URL }
        if (!normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://")) {
            _uiState.update {
                it.copy(
                    connectionTest = ConnectionTestUiState(
                        message = "Base URL 必须以 http:// 或 https:// 开头。",
                        isSuccess = false,
                    )
                )
            }
            return
        }
        val config = AppSettings(
            apiKey = key,
            model = model.trim().ifBlank { AppSettings.MODEL_FLASH },
            baseUrl = normalizedUrl.trimEnd('/'),
            thinkingLevel = thinkingLevel,
        ).toLlmConfig()
        _uiState.update { it.copy(connectionTest = ConnectionTestUiState(isTesting = true)) }
        viewModelScope.launch {
            val result = container.llmClient.testConnection(config)
            _uiState.update {
                it.copy(
                    connectionTest = when (result) {
                        ConnectionTestResult.Success -> ConnectionTestUiState(
                            message = "连接成功，API Key 可用。",
                            isSuccess = true,
                        )
                        is ConnectionTestResult.Failure -> ConnectionTestUiState(
                            message = result.message,
                            isSuccess = false,
                        )
                    }
                )
            }
        }
    }

    fun checkForUpdate() {
        val updateState = _uiState.value.updateState
        if (updateState is UpdateUiState.Checking || updateState is UpdateUiState.Downloading) return
        _uiState.update { it.copy(updateState = UpdateUiState.Checking) }
        viewModelScope.launch {
            try {
                when (val result = container.updateChecker.checkForUpdate(
                    currentVersionCode = container.currentVersionCode,
                    currentVersionName = container.currentVersionName,
                )) {
                    UpdateCheckResult.UpToDate -> _uiState.update {
                        it.copy(updateState = UpdateUiState.UpToDate)
                    }
                    is UpdateCheckResult.Available -> {
                        if (result.update.minSdk > Build.VERSION.SDK_INT) {
                            _uiState.update {
                                it.copy(
                                    updateState = UpdateUiState.Failed(
                                        "新版本要求更高的 Android 系统版本，请通过应用商店或发布页更新。",
                                    )
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(updateState = UpdateUiState.Available(result.update))
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        updateState = UpdateUiState.Failed(
                            friendlyMessage(error, "检查更新失败，请稍后重试。"),
                        )
                    )
                }
            }
        }
    }

    fun downloadUpdate() {
        val state = _uiState.value
        if (state.updateState is UpdateUiState.Downloading) return
        val update = when (val current = state.updateState) {
            is UpdateUiState.Available -> current.update
            is UpdateUiState.Failed -> current.update
            else -> null
        } ?: return
        _uiState.update { it.copy(updateState = UpdateUiState.Downloading(update, 0)) }
        viewModelScope.launch {
            try {
                val file = container.updateChecker.downloadApk(update) { progress ->
                    _uiState.update { latest ->
                        val downloading = latest.updateState as? UpdateUiState.Downloading
                        if (downloading?.update?.versionCode == update.versionCode) {
                            latest.copy(updateState = UpdateUiState.Downloading(update, progress))
                        } else {
                            latest
                        }
                    }
                }
                _uiState.update {
                    it.copy(updateState = UpdateUiState.Downloaded(update, file))
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        updateState = UpdateUiState.Failed(
                            message = friendlyMessage(error, "更新下载失败，请稍后重试。"),
                            update = update,
                        )
                    )
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        if (_uiState.value.updateState is UpdateUiState.Downloading) return
        _uiState.update { it.copy(updateState = UpdateUiState.Idle) }
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
                    treatmentAnswer = entity.treatmentAnswer,
                    encounterType = EncounterType.from(entity.encounterType),
                    messages = ConversationJsonCodec.decode(entity.conversationJson),
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
