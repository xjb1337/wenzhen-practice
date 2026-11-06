package com.xjb.wenzhen.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xjb.wenzhen.AppContainer
import com.xjb.wenzhen.data.api.ConnectionTestResult
import com.xjb.wenzhen.data.api.RedeemResult
import com.xjb.wenzhen.data.cases.CaseJsonCodec
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.db.ConsultationEntity
import com.xjb.wenzhen.data.db.ConsultationDraftEntity
import com.xjb.wenzhen.data.db.ConversationJsonCodec
import com.xjb.wenzhen.data.db.SavedCaseEntity
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
import com.xjb.wenzhen.data.settings.ApiSource
import com.xjb.wenzhen.data.settings.BaseUrlPolicy
import com.xjb.wenzhen.data.update.UpdateCheckResult
import com.xjb.wenzhen.domain.CaseGenerationSelection
import com.xjb.wenzhen.domain.OfflinePracticeService
import com.xjb.wenzhen.domain.ReferenceCarePlanBuilder
import com.xjb.wenzhen.domain.ScoreParser
import com.xjb.wenzhen.domain.ScoreResult
import com.xjb.wenzhen.ui.theme.AppTheme
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
import kotlinx.coroutines.delay
import java.util.UUID

/** 新问诊固定由医生先开口，随后患者再描述主诉；旧草稿恢复时也会补上这句。 */
internal const val DOCTOR_OPENING_LINE = "您好，我是接诊医生。请问您哪里不舒服？"

class AppViewModel(
    private val container: AppContainer,
    initialUiState: AppUiState? = null,
) : ViewModel() {
    private val initialSettings = runCatching { container.settingsRepository.load() }
        .getOrDefault(AppSettings())

    private val _uiState = MutableStateFlow(
        initialUiState ?: AppUiState(
            settings = initialSettings,
            currentVersionName = container.currentVersionName,
            currentVersionCode = container.currentVersionCode,
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _events = Channel<AppEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var historyJob: Job? = null
    private var draftSaveJob: Job? = null
    /**
     * 只有「进行中的问诊」才允许自动保存草稿。问诊结束并生成结果后置为 false，
     * 否则结果页退到后台时 onStop() 的 flushDraft() 会把刚结束的对话重新写回草稿，
     * 首页就会再次出现「发现未完成的问诊」。
     */
    private var draftPersistenceEnabled = true
    private var savedCaseJob: Job? = null
    private var patientReplyJob: Job? = null
    private var clinicalJob: Job? = null

    init {
        observeHistory()
        observeDraft()
        observeSavedCases()
    }

    override fun onCleared() {
        // viewModelScope 会取消状态观察和请求；关闭后也不再接受迟到的草稿刷新。
        draftPersistenceEnabled = false
        draftSaveJob?.cancel()
        draftSaveJob = null
        super.onCleared()
    }

    fun selectDepartment(department: String) {
        _uiState.update { it.copy(selectedDepartment = department) }
    }

    fun openCase(
        medicalCase: MedicalCase,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
    ) {
        draftPersistenceEnabled = true
        val startedAt = System.currentTimeMillis()
        val doctorOpeningMessage = ChatMessage(
            role = MessageRole.DOCTOR,
            content = DOCTOR_OPENING_LINE,
            timestamp = startedAt,
        )
        val openingMessage = ChatMessage(
            role = MessageRole.PATIENT,
            content = medicalCase.openingLine,
            timestamp = startedAt + 1,
        )
        _uiState.update {
            it.copy(
                activeCase = medicalCase,
                patientPersonality = personality,
                encounterType = encounterType,
                consultationStartedAt = startedAt,
                messages = listOf(doctorOpeningMessage, openingMessage),
                isPatientTyping = false,
                isQueryingClinical = false,
                chatError = null,
                finishError = null,
                currentResult = null,
            )
        }
        scheduleDraftSave()
        viewModelScope.launch { _events.send(AppEvent.OpenChat) }
    }

    fun setOfflineMode(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(offlineMode = enabled)
        runCatching { container.settingsRepository.save(updated) }
            .onSuccess { _uiState.update { it.copy(settings = updated) } }
    }

    /** 主题即点即存；MainActivity 通过 uiState.settings.theme 驱动 WenzhenTheme。 */
    fun setTheme(theme: AppTheme) {
        val updated = _uiState.value.settings.copy(theme = theme)
        runCatching { container.settingsRepository.save(updated) }
            .onSuccess { _uiState.update { it.copy(settings = updated) } }
    }

    fun resumeDraft() {
        val draft = _uiState.value.draft ?: return
        draftPersistenceEnabled = true
        val medicalCase = runCatching { CaseJsonCodec.decode(draft.caseJson) }.getOrNull() ?: return
        val messages = ensureDoctorOpening(
            reconcileRecoveredConversation(ConversationJsonCodec.decode(draft.conversationJson)),
            draft.startedAt,
        )
        _uiState.update {
            it.copy(
                activeCase = medicalCase,
                patientPersonality = PatientPersonality.valueOfOrDefault(draft.patientPersonality),
                encounterType = EncounterType.from(draft.encounterType),
                consultationStartedAt = draft.startedAt,
                messages = messages,
                isPatientTyping = false,
                isQueryingClinical = false,
                chatError = null,
                finishError = null,
                currentResult = null,
            )
        }
        viewModelScope.launch { _events.send(AppEvent.OpenChat) }
    }

    /**
     * 草稿可能是在“医生提问已发出、患者回复还在生成”时被保存的。恢复时必须把上次中断的请求
     * 归一化成可操作状态：半截患者消息丢弃，未收到回复的医生提问标记为 FAILED（界面会显示
     * “发送失败，重试”），否则会留下永远停在 SENDING 的消息、重试按钮也不出现。
     */
    private fun reconcileRecoveredConversation(messages: List<ChatMessage>): List<ChatMessage> =
        messages.mapNotNull { message ->
            when {
                message.role == MessageRole.PATIENT && message.deliveryState == MessageDeliveryState.SENDING -> null
                message.role == MessageRole.DOCTOR && message.deliveryState != MessageDeliveryState.SENT ->
                    message.copy(deliveryState = MessageDeliveryState.FAILED)
                else -> message
            }
        }

    /** v1.3.1 及更早的草稿以患者开场；恢复时补医生开场白，保证问诊始终由医生先发问。 */
    private fun ensureDoctorOpening(messages: List<ChatMessage>, startedAt: Long): List<ChatMessage> {
        if (messages.firstOrNull()?.role == MessageRole.DOCTOR) return messages
        val doctorOpening = ChatMessage(
            role = MessageRole.DOCTOR,
            content = DOCTOR_OPENING_LINE,
            timestamp = startedAt,
        )
        return listOf(doctorOpening) + messages
    }

    fun discardDraft() {
        draftPersistenceEnabled = false
        draftSaveJob?.cancel()
        draftSaveJob = null
        viewModelScope.launch { container.consultationDraftRepository.clear() }
        _uiState.update { it.copy(draft = null) }
    }

    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        val state = _uiState.value
        if (state.isPatientTyping || state.isQueryingClinical) return
        val medicalCase = state.activeCase ?: return

        if (!state.canPractice) {
            _uiState.update { it.copy(chatError = state.settings.aiSetupMessage) }
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
        scheduleDraftSave()
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
        if (!state.canPractice) {
            _uiState.update { it.copy(chatError = state.settings.aiSetupMessage) }
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
        scheduleDraftSave()
        requestPatientReply(
            medicalCase = medicalCase,
            config = state.settings.toLlmConfig(),
            personality = state.patientPersonality,
            encounterType = state.encounterType,
            conversation = conversation,
            doctorIndex = failedIndex,
        )
    }

    fun cancelCurrentRequest() {
        val state = _uiState.value
        if (!state.isPatientTyping && !state.isQueryingClinical) return
        patientReplyJob?.cancel()
        patientReplyJob = null
        clinicalJob?.cancel()
        clinicalJob = null
        _uiState.update { latest ->
            val messages = latest.messages.toMutableList()
            if (latest.isPatientTyping) {
                val doctorIndex = messages.indexOfLast {
                    it.role == MessageRole.DOCTOR && it.deliveryState == MessageDeliveryState.SENDING
                }
                if (doctorIndex >= 0) {
                    messages[doctorIndex] = messages[doctorIndex].copy(deliveryState = MessageDeliveryState.FAILED)
                    val patientIndex = doctorIndex + 1
                    if (messages.getOrNull(patientIndex)?.role == MessageRole.PATIENT &&
                        messages[patientIndex].deliveryState == MessageDeliveryState.SENDING
                    ) {
                        messages.removeAt(patientIndex)
                    }
                }
            }
            latest.copy(
                messages = messages,
                isPatientTyping = false,
                isQueryingClinical = false,
                chatError = null,
                requestNotice = "已取消本次请求，可重新发送。",
            )
        }
        scheduleDraftSave()
    }

    fun clearRequestNotice() {
        _uiState.update { it.copy(requestNotice = null) }
    }

    private fun requestPatientReply(
        medicalCase: MedicalCase,
        config: com.xjb.wenzhen.data.model.LlmConfig,
        personality: PatientPersonality,
        encounterType: EncounterType,
        conversation: List<ChatMessage>,
        doctorIndex: Int,
    ) {
        patientReplyJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                if (config.apiKey.isBlank() && _uiState.value.settings.offlineMode) {
                    val reply = OfflinePracticeService.patientReply(medicalCase, conversation)
                    _uiState.update { latest ->
                        finishPatientReply(
                            latest = latest,
                            caseId = medicalCase.id,
                            doctorIndex = doctorIndex,
                            reply = reply,
                        ).copy(
                            lastReplyMillis = System.currentTimeMillis() - startedAt,
                            lastReplyTokens = (conversation.sumOf { it.content.length } + reply.length) / 2,
                            requestNotice = null,
                        )
                    }
                    scheduleDraftSave()
                    return@launch
                }
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
                            scheduleDraftSave()
                        }
                    },
                )
                if (reply.isBlank()) {
                    throw IllegalStateException("患者没有返回内容，请重新询问。")
                }
                _uiState.update { latest ->
                    val updated = finishPatientReply(
                        latest = latest,
                        caseId = medicalCase.id,
                        doctorIndex = doctorIndex,
                        reply = reply,
                    )
                    updated.copy(
                        lastReplyMillis = System.currentTimeMillis() - startedAt,
                        lastReplyTokens = (conversation.sumOf { it.content.length } + reply.length) / 2,
                        requestNotice = null,
                    )
                }
                scheduleDraftSave()
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
                scheduleDraftSave()
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
        if (!state.canPractice) {
            _uiState.update {
                it.copy(chatError = "${state.settings.aiSetupMessage}再索取检查结果。")
            }
            viewModelScope.launch { _events.send(AppEvent.OpenSettings) }
            return
        }

        _uiState.update { it.copy(isQueryingClinical = true, chatError = null) }
        clinicalJob = viewModelScope.launch {
            try {
                val result = if (state.settings.offlineMode && !state.hasApiKey) {
                    OfflinePracticeService.clinicalResult(medicalCase, requestType)
                } else {
                    container.clinicalDataService.request(
                        config = state.settings.toLlmConfig(),
                        medicalCase = medicalCase,
                        requestType = requestType,
                        item = normalizedItem,
                    )
                }
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
                scheduleDraftSave()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isQueryingClinical = false,
                        chatError = friendlyMessage(error, "${requestType.label}结果获取失败，请稍后重试。"),
                    )
                }
                scheduleDraftSave()
            }
        }
    }

    fun generateCase(selection: CaseGenerationSelection) {
        val state = _uiState.value
        if (state.isGeneratingCase) return
        if (!state.hasApiKey) {
            _uiState.update {
                it.copy(generationError = "${state.settings.aiSetupMessage}再生成病例。")
            }
            viewModelScope.launch { _events.send(AppEvent.OpenSettings) }
            return
        }

        _uiState.update {
            it.copy(isGeneratingCase = true, generationError = null)
        }
        viewModelScope.launch {
            try {
                val resolved = selection.resolve()
                val (generated, finalPersonality) = container.caseGenerationService.generate(
                    config = state.settings.toLlmConfig(),
                    department = resolved.department,
                    difficulty = resolved.difficulty,
                    personality = resolved.personality,
                    encounterType = resolved.encounterType,
                )
                _uiState.update { latest ->
                    latest.copy(
                        cases = listOf(generated) + latest.cases,
                        isGeneratingCase = false,
                        generationError = null,
                    )
                }
                container.savedCaseRepository.save(
                    SavedCaseEntity(
                        id = generated.id,
                        caseJson = CaseJsonCodec.encode(generated),
                        createdAt = System.currentTimeMillis(),
                    )
                )
                openCase(generated, finalPersonality, resolved.encounterType)
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

    fun generateCase(
        department: String,
        difficulty: Difficulty,
        personality: PatientPersonality = PatientPersonality.COOPERATIVE,
        encounterType: EncounterType = EncounterType.OUTPATIENT,
    ) {
        generateCase(
            CaseGenerationSelection(
                department = department,
                difficulty = difficulty,
                personality = personality,
                encounterType = encounterType,
            )
        )
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
        if (!state.canPractice) {
            _uiState.update {
                it.copy(finishError = "${state.settings.aiSetupMessage}或在设置中开启离线基础训练。")
            }
            viewModelScope.launch { _events.send(AppEvent.OpenSettings) }
            return
        }

        val conversation = state.messages.toList()
        val config = state.settings.toLlmConfig()
        val referenceCarePlan = ReferenceCarePlanBuilder.build(medicalCase, state.encounterType)
        _uiState.update { it.copy(isFinishing = true, finishError = null) }
        viewModelScope.launch {
            try {
                val recordResult: Result<String>
                val scoreResult: Result<ScoreResult>
                if (state.settings.offlineMode && !state.hasApiKey) {
                    recordResult = if (generateRecord) {
                        runCatching {
                            OfflinePracticeService.record(
                                medicalCase,
                                diagnosis,
                                diagnosisBasis.trim(),
                                treatmentAnswer.trim(),
                                state.encounterType,
                            )
                        }
                    } else {
                        Result.success("")
                    }
                    scoreResult = runCatching {
                        OfflinePracticeService.score(
                            medicalCase,
                            conversation,
                            diagnosis,
                            treatmentAnswer.trim(),
                            state.encounterType,
                            diagnosisBasis.trim(),
                        )
                    }
                } else {
                    coroutineScope {
                        val recordTask = async {
                            runCatching {
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
                        }
                        val scoreTask = async {
                            runCatching {
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
                        }
                        recordResult = recordTask.await()
                        scoreResult = scoreTask.await()
                    }
                }

                val recordFailure = recordResult.exceptionOrNull()
                val scoreFailure = scoreResult.exceptionOrNull()
                val record = recordResult.getOrDefault("")
                val score = scoreResult.getOrNull()
                if (score == null && record.isBlank()) {
                    throw scoreFailure ?: IllegalStateException("评分生成失败")
                }
                val createdAt = System.currentTimeMillis()
                val resultId: Long
                val effectiveScore: ScoreResult
                val notice: String?
                if (score != null) {
                    effectiveScore = score
                    notice = if (recordFailure != null && generateRecord) {
                        "病历生成失败，已保留评分结果：${friendlyMessage(recordFailure, "请稍后重试")}"
                    } else {
                        null
                    }
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
                        recordType = if (record.isNotBlank()) state.encounterType.name else "NONE",
                    )
                    resultId = container.consultationRepository.save(entity)
                } else {
                    effectiveScore = ScoreResult(
                        totalScore = 0,
                        coverageScore = 0,
                        techniqueScore = 0,
                        communicationScore = 0,
                        diagnosisScore = 0,
                        treatmentScore = 0,
                        coveredPoints = emptyList(),
                        missedQuestions = emptyList(),
                        strengths = emptyList(),
                        improvements = listOf("评分未生成，可返回后重试评分。"),
                        correctDiagnosis = medicalCase.correctDiagnosis,
                        diagnosisExplanation = "评分生成失败，暂不写入历史记录。",
                    )
                    resultId = 0L
                    notice = "评分生成失败，以下仅展示已生成的病历：${friendlyMessage(scoreFailure ?: IllegalStateException("评分生成失败"), "请稍后重试")}"
                }
                val result = ResultViewData(
                    recordId = resultId,
                    caseTitle = medicalCase.title,
                    department = medicalCase.department,
                    patientName = medicalCase.patient.name,
                    difficulty = medicalCase.difficulty.label,
                    createdAt = createdAt,
                    medicalRecord = record,
                    score = effectiveScore,
                    initialDiagnosis = diagnosis,
                    treatmentAnswer = treatmentAnswer.trim(),
                    encounterType = state.encounterType,
                    messages = conversation,
                    referenceCarePlan = referenceCarePlan,
                    notice = notice,
                )
                _uiState.update {
                    it.copy(
                        isFinishing = false,
                        finishError = null,
                        currentResult = result,
                    )
                }
                // 只要走到了结果页，本次问诊就已经结束：即使评分生成失败、只展示了病历，
                // 也必须清掉草稿，并停止后续 onStop/flushDraft 再把它写回来。
                draftPersistenceEnabled = false
                draftSaveJob?.cancel()
                draftSaveJob = null
                container.consultationDraftRepository.clear()
                _uiState.update { it.copy(draft = null) }
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
        apiSource: ApiSource = _uiState.value.settings.apiSource,
    ) {
        val currentSettings = _uiState.value.settings
        val normalizedUrl = if (apiSource == ApiSource.OWN_KEY) {
            BaseUrlPolicy.normalizeHttps(baseUrl)
        } else {
            currentSettings.baseUrl.ifBlank { AppSettings.DEFAULT_BASE_URL }
        }
        if (apiSource == ApiSource.OWN_KEY && normalizedUrl == null) {
            _uiState.update {
                it.copy(settingsSaveError = "Base URL 必须使用 HTTPS（以 https:// 开头）。")
            }
            return
        }
        val newSettings = currentSettings.copy(
            apiKey = apiKey.trim(),
            model = if (apiSource == ApiSource.BUILTIN) {
                AppSettings.MODEL_FLASH
            } else {
                model.trim().ifBlank { AppSettings.MODEL_FLASH }
            },
            baseUrl = normalizedUrl ?: currentSettings.baseUrl,
            thinkingLevel = thinkingLevel,
            generateRecord = generateRecord,
            apiSource = apiSource,
            deviceId = currentSettings.deviceId.trim().ifBlank { UUID.randomUUID().toString() },
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

    fun redeemInvite(code: String) {
        val inviteCode = code.trim()
        if (inviteCode.isEmpty()) {
            _uiState.update { it.copy(redeemError = "请输入邀请码。") }
            return
        }
        val state = _uiState.value
        if (state.isRedeemingInvite) return
        val currentSettings = state.settings
        val deviceId = currentSettings.deviceId.trim().ifBlank { UUID.randomUUID().toString() }
        _uiState.update { it.copy(isRedeemingInvite = true, redeemError = null) }
        viewModelScope.launch {
            when (val result = container.relayClient.redeem(
                baseUrl = currentSettings.relayBaseUrl,
                code = inviteCode,
                deviceId = deviceId,
            )) {
                is RedeemResult.Success -> {
                    val updated = currentSettings.copy(
                        apiSource = ApiSource.BUILTIN,
                        deviceId = deviceId,
                        sessionToken = result.sessionToken,
                        relayBaseUrl = currentSettings.relayBaseUrl.trim()
                            .ifBlank { AppSettings.RELAY_BASE_URL },
                    )
                    try {
                        container.settingsRepository.save(updated)
                        _uiState.update {
                            it.copy(
                                settings = updated,
                                isRedeemingInvite = false,
                                redeemError = null,
                                settingsSaveError = null,
                                connectionTest = ConnectionTestUiState(),
                            )
                        }
                        _events.send(AppEvent.ShowMessage("邀请码兑换成功，已启用内置服务。"))
                    } catch (error: Exception) {
                        _uiState.update {
                            it.copy(
                                isRedeemingInvite = false,
                                redeemError = friendlyMessage(error, "兑换成功，但本机保存失败，请重试。"),
                            )
                        }
                    }
                }
                is RedeemResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isRedeemingInvite = false,
                            redeemError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun clearInviteRedemption() {
        val currentSettings = _uiState.value.settings
        val updated = currentSettings.copy(sessionToken = "")
        try {
            container.settingsRepository.save(updated)
            _uiState.update {
                it.copy(
                    settings = updated,
                    redeemError = null,
                    connectionTest = ConnectionTestUiState(),
                )
            }
            viewModelScope.launch { _events.send(AppEvent.ShowMessage("已清除内置服务兑换信息。")) }
        } catch (error: Exception) {
            _uiState.update {
                it.copy(redeemError = friendlyMessage(error, "清除兑换信息失败，请重试。"))
            }
        }
    }

    fun clearRedeemError() {
        _uiState.update { it.copy(redeemError = null) }
    }

    fun testConnection(
        apiSource: ApiSource,
        apiKey: String,
        model: String,
        baseUrl: String,
        thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    ) {
        if (_uiState.value.connectionTest.isTesting) return
        val currentSettings = _uiState.value.settings
        val config = if (apiSource == ApiSource.BUILTIN) {
            if (currentSettings.sessionToken.isBlank()) {
                _uiState.update {
                    it.copy(
                        connectionTest = ConnectionTestUiState(
                            message = "请先兑换邀请码。",
                            isSuccess = false,
                        )
                    )
                }
                return
            }
            val relayUrl = BaseUrlPolicy.normalizeHttps(
                currentSettings.relayBaseUrl.ifBlank { AppSettings.RELAY_BASE_URL },
            )
            if (relayUrl == null) {
                _uiState.update {
                    it.copy(
                        connectionTest = ConnectionTestUiState(
                            message = "内置服务地址必须使用 HTTPS。",
                            isSuccess = false,
                        )
                    )
                }
                return
            }
            AppSettings(
                apiSource = ApiSource.BUILTIN,
                sessionToken = currentSettings.sessionToken,
                deviceId = currentSettings.deviceId,
                relayBaseUrl = relayUrl,
                thinkingLevel = thinkingLevel,
            ).toLlmConfig()
        } else {
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
            val normalizedUrl = BaseUrlPolicy.normalizeHttps(baseUrl)
            if (normalizedUrl == null) {
                _uiState.update {
                    it.copy(
                        connectionTest = ConnectionTestUiState(
                            message = "Base URL 必须使用 HTTPS（以 https:// 开头）。",
                            isSuccess = false,
                        )
                    )
                }
                return
            }
            AppSettings(
                apiKey = key,
                model = model.trim().ifBlank { AppSettings.MODEL_FLASH },
                baseUrl = normalizedUrl,
                thinkingLevel = thinkingLevel,
            ).toLlmConfig()
        }
        _uiState.update { it.copy(connectionTest = ConnectionTestUiState(isTesting = true)) }
        viewModelScope.launch {
            try {
                val result = container.llmClient.testConnection(config)
                _uiState.update {
                    it.copy(
                        connectionTest = when (result) {
                            ConnectionTestResult.Success -> ConnectionTestUiState(
                                message = if (apiSource == ApiSource.BUILTIN) {
                                    "连接成功，内置服务可用。"
                                } else {
                                    "连接成功，API Key 可用。"
                                },
                                isSuccess = true,
                            )
                            is ConnectionTestResult.Failure -> ConnectionTestUiState(
                                message = result.message,
                                isSuccess = false,
                            )
                        }
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        connectionTest = ConnectionTestUiState(
                            message = friendlyMessage(error, "连接测试失败，请稍后重试。"),
                            isSuccess = false,
                        )
                    )
                }
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
                val encounterType = EncounterType.from(entity.encounterType)
                val medicalCase = runCatching { CaseJsonCodec.decode(entity.caseJson) }.getOrNull()
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
                    encounterType = encounterType,
                    messages = ConversationJsonCodec.decode(entity.conversationJson),
                    referenceCarePlan = medicalCase?.let {
                        ReferenceCarePlanBuilder.build(it, encounterType)
                    },
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

    fun setHistoryQuery(query: String) {
        _uiState.update { it.copy(historyQuery = query) }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch { container.consultationRepository.delete(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.consultationRepository.clear() }
    }

    fun restoreHistory(records: List<ConsultationEntity>) {
        if (records.isEmpty()) return
        viewModelScope.launch {
            records.forEach { record ->
                container.consultationRepository.save(record.copy(id = 0L))
            }
        }
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

    private fun observeDraft() {
        viewModelScope.launch {
            val draft = container.consultationDraftRepository.get()
            if (draft != null &&
                container.consultationRepository.findCompletedAfter(draft.caseJson, draft.startedAt) != null
            ) {
                // v1.3.2 及更早版本在结果页退后台时可能把已完成的问诊写回草稿；
                // 同一病例在草稿开始之后已有完成记录，说明这是完成后的残留，启动时直接清掉。
                container.consultationDraftRepository.clear()
                _uiState.update { it.copy(draft = null) }
            } else {
                // finishConsultation 写入结果并由这里并发读取草稿时，不能在结束问诊后
                // 再把读取到的旧草稿快照写回状态，否则首页会误报存在未完成问诊。
                if (!draftPersistenceEnabled) return@launch
                _uiState.update { it.copy(draft = draft) }
            }
        }
    }

    private fun observeSavedCases() {
        savedCaseJob?.cancel()
        savedCaseJob = viewModelScope.launch {
            container.savedCaseRepository.observeAll().collect { saved ->
                val generated = saved.mapNotNull { entity ->
                    runCatching { CaseJsonCodec.decode(entity.caseJson) }.getOrNull()
                }
                _uiState.update { it.copy(cases = BuiltInCases.all + generated, savedCases = saved) }
            }
        }
    }

    fun toggleSavedCaseFavorite(caseId: String) {
        viewModelScope.launch {
            val current = _uiState.value.savedCases.firstOrNull { it.id == caseId } ?: return@launch
            container.savedCaseRepository.setFavorite(caseId, !current.favorite)
        }
    }

    fun deleteSavedCase(caseId: String) {
        viewModelScope.launch {
            container.savedCaseRepository.delete(caseId)
        }
    }

    private fun scheduleDraftSave() {
        if (!draftPersistenceEnabled) return
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(350)
            persistDraftNow()
        }
    }

    /** App 退到后台/可能被系统回收前调用：跳过 350ms 防抖，立即把当前对话写入草稿。 */
    fun flushDraft() {
        if (!draftPersistenceEnabled) return
        if (_uiState.value.activeCase == null || _uiState.value.consultationStartedAt == null) return
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch { persistDraftNow() }
    }

    private suspend fun persistDraftNow() {
        if (!draftPersistenceEnabled) return
        val state = _uiState.value
        val medicalCase = state.activeCase ?: return
        val startedAt = state.consultationStartedAt ?: return
        val draft = ConsultationDraftEntity(
            caseJson = CaseJsonCodec.encode(medicalCase),
            conversationJson = ConversationJsonCodec.encode(state.messages),
            patientPersonality = state.patientPersonality.name,
            encounterType = state.encounterType.name,
            startedAt = startedAt,
            updatedAt = System.currentTimeMillis(),
        )
        container.consultationDraftRepository.save(draft)
        _uiState.update { it.copy(draft = draft) }
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
