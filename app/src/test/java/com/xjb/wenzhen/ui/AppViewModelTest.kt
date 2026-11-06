package com.xjb.wenzhen.ui

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xjb.wenzhen.AppContainer
import com.xjb.wenzhen.data.api.ConnectionTestResult
import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.cases.CaseJsonCodec
import com.xjb.wenzhen.data.db.AppDatabase
import com.xjb.wenzhen.data.db.ConsultationDraftEntity
import com.xjb.wenzhen.data.db.ConsultationEntity
import com.xjb.wenzhen.data.db.ConversationJsonCodec
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.domain.ScoreParser
import com.xjb.wenzhen.domain.ScoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        // P0-6：内存数据库不在这里 close()。Room 2.6.1 的 InvalidationTracker 在 ViewModel 的
        // Flow 查询尚未完全取消时执行 close()，会与 arch_disk_io 查询线程发生锁反转死锁
        // （jstack: Found one Java-level deadlock；arch_disk_io 等 ReadLock，
        // DefaultDispatcher-worker 在 ProcessLock.lock 等 close）。测试进程结束会释放全部
        // 内存数据库连接，因此只清理 ViewModelStore 和测试调度器即可。
        store.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun viewModel(client: LlmClient, settings: AppSettings = apiSettings()): AppViewModel {
        val container = AppContainer(context, overrideLlmClient = client, overrideDatabase = db)
        val medicalCase = BuiltInCases.all.first()
        val initial = AppUiState(
            settings = settings,
            activeCase = medicalCase,
            consultationStartedAt = 1_000L,
            messages = listOf(
                ChatMessage(
                    role = MessageRole.DOCTOR,
                    content = DOCTOR_OPENING_LINE,
                ),
                ChatMessage(
                    role = MessageRole.PATIENT,
                    content = medicalCase.openingLine,
                ),
            ),
        )
        return AppViewModel(container, initial).also {
            store.put("vm-${System.nanoTime()}", it)
        }
    }

    private fun apiSettings() = AppSettings(
        apiKey = "sk-test-key",
        model = "deepseek-chat",
        baseUrl = "https://api.deepseek.com",
        offlineMode = false,
    )

    @Test
    fun `保存 HTTP Base URL 会被拒绝且不落库`() = runTest(dispatcher) {
        val vm = viewModel(SuccessClient())

        vm.saveSettings(apiKey = "k", model = "deepseek-chat", baseUrl = "http://api.test.com")

        val error = vm.uiState.value.settingsSaveError
        assertNotNull(error)
        assertTrue(error.orEmpty().contains("HTTPS"))
    }

    @Test
    fun `新问诊由医生先开口并随后展示患者主诉`() = runTest(dispatcher) {
        val vm = viewModel(SuccessClient())
        val medicalCase = BuiltInCases.all.first()

        vm.openCase(medicalCase)
        advanceUntilIdle()

        val messages = vm.uiState.value.messages
        assertEquals(2, messages.size)
        assertEquals(MessageRole.DOCTOR, messages[0].role)
        assertEquals(DOCTOR_OPENING_LINE, messages[0].content)
        assertEquals(MessageDeliveryState.SENT, messages[0].deliveryState)
        assertEquals(MessageRole.PATIENT, messages[1].role)
        assertEquals(medicalCase.openingLine, messages[1].content)
        assertTrue(messages[0].timestamp <= messages[1].timestamp)
    }

    @Test
    fun `发送成功后医生消息为已发送且患者给出回复`() = runTest(dispatcher) {
        val vm = viewModel(SuccessClient(reply = "请继续描述疼痛性质。"))

        vm.sendMessage("疼痛多久了？")
        advanceUntilIdle()

        val messages = vm.uiState.value.messages
        val doctor = messages.last { it.role == MessageRole.DOCTOR }
        assertEquals(MessageDeliveryState.SENT, doctor.deliveryState)
        assertEquals("请继续描述疼痛性质。", messages.last().content)
        assertEquals(MessageRole.PATIENT, messages.last().role)
        assertNotNull(vm.uiState.value.lastReplyMillis)
        assertNotNull(vm.uiState.value.lastReplyTokens)
    }

    @Test
    fun `发送失败后重试可将消息恢复为已发送`() = runTest(dispatcher) {
        val client = FlakyClient(failures = 1, reply = "好的，我继续说明。")
        val vm = viewModel(client)

        vm.sendMessage("请描述伴随症状")
        advanceUntilIdle()

        val failed = vm.uiState.value.messages.last { it.role == MessageRole.DOCTOR }
        assertEquals(MessageDeliveryState.FAILED, failed.deliveryState)
        assertNotNull(vm.uiState.value.chatError)

        vm.retryFailedMessage()
        advanceUntilIdle()

        val retried = vm.uiState.value.messages.last { it.role == MessageRole.DOCTOR }
        assertEquals(MessageDeliveryState.SENT, retried.deliveryState)
        assertEquals("好的，我继续说明。", vm.uiState.value.messages.last().content)
        assertEquals(2, client.streamCalls)
    }

    @Test
    fun `取消进行中的请求会把未完成消息标记为失败`() = runTest(dispatcher) {
        val vm = viewModel(BlockingClient())

        vm.sendMessage("请继续")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isPatientTyping)

        vm.cancelCurrentRequest()

        val doctor = vm.uiState.value.messages.last { it.role == MessageRole.DOCTOR }
        assertEquals(MessageDeliveryState.FAILED, doctor.deliveryState)
        assertEquals(false, vm.uiState.value.isPatientTyping)
        assertTrue(vm.uiState.value.requestNotice.orEmpty().contains("已取消"))
    }

    @Test
    fun `恢复中断草稿会丢弃半截患者回复并把医生提问标记为可重试`() = runTest(dispatcher) {
        val medicalCase = BuiltInCases.all.first()
        val draft = ConsultationDraftEntity(
            caseJson = CaseJsonCodec.encode(medicalCase),
            conversationJson = ConversationJsonCodec.encode(
                listOf(
                    ChatMessage(role = MessageRole.PATIENT, content = medicalCase.openingLine),
                    ChatMessage(
                        role = MessageRole.DOCTOR,
                        content = "疼痛多久了？",
                        deliveryState = MessageDeliveryState.SENDING,
                    ),
                    ChatMessage(
                        role = MessageRole.PATIENT,
                        content = "大概三",
                        deliveryState = MessageDeliveryState.SENDING,
                    ),
                ),
            ),
            patientPersonality = "COOPERATIVE",
            encounterType = "OUTPATIENT",
            startedAt = 1_000L,
            updatedAt = 2_000L,
        )
        val container = AppContainer(context, overrideLlmClient = SuccessClient(), overrideDatabase = db)
        val vm = AppViewModel(container, AppUiState(settings = apiSettings(), draft = draft))
        store.put("vm-draft", vm)

        vm.resumeDraft()

        val messages = vm.uiState.value.messages
        assertEquals(3, messages.size)
        assertEquals(MessageRole.DOCTOR, messages[0].role)
        assertEquals(DOCTOR_OPENING_LINE, messages[0].content)
        assertEquals(MessageRole.PATIENT, messages[1].role)
        assertEquals(MessageDeliveryState.SENT, messages[1].deliveryState)
        assertEquals(MessageRole.DOCTOR, messages[2].role)
        assertEquals(MessageDeliveryState.FAILED, messages[2].deliveryState)
        assertTrue(messages.none { it.role == MessageRole.PATIENT && it.deliveryState == MessageDeliveryState.SENDING })
        assertEquals(false, vm.uiState.value.isPatientTyping)
    }

    @Test
    fun `结束问诊后即使评分生成失败也必须清除草稿`() = runTest(dispatcher) {
        val medicalCase = BuiltInCases.all.first()
        val draft = ConsultationDraftEntity(
            caseJson = CaseJsonCodec.encode(medicalCase),
            conversationJson = ConversationJsonCodec.encode(
                listOf(
                    ChatMessage(role = MessageRole.DOCTOR, content = DOCTOR_OPENING_LINE),
                    ChatMessage(role = MessageRole.PATIENT, content = medicalCase.openingLine),
                ),
            ),
            patientPersonality = "COOPERATIVE",
            encounterType = "OUTPATIENT",
            startedAt = 1_000L,
            updatedAt = 2_000L,
        )
        runBlocking(Dispatchers.IO) { db.consultationDraftDao().upsert(draft) }
        val container = AppContainer(context, overrideLlmClient = RecordOnlyClient(), overrideDatabase = db)
        val vm = AppViewModel(
            container,
            AppUiState(
                settings = apiSettings(),
                activeCase = medicalCase,
                consultationStartedAt = 1_000L,
                messages = listOf(
                    ChatMessage(role = MessageRole.DOCTOR, content = DOCTOR_OPENING_LINE),
                    ChatMessage(role = MessageRole.PATIENT, content = medicalCase.openingLine),
                ),
                draft = draft,
            ),
        )
        store.put("vm-finish-draft", vm)

        // 病历生成成功、评分生成失败：结果页会展示病历，但本次问诊已经结束。
        vm.finishConsultation(
            initialDiagnosis = "急性心肌梗死",
            diagnosisBasis = "心电图提示 ST 段抬高",
            generateRecord = true,
        )
        advanceUntilIdle()
        waitUntil {
            vm.uiState.value.currentResult != null || vm.uiState.value.finishError != null
        }
        assertNull(vm.uiState.value.finishError)
        waitUntil { vm.uiState.value.draft == null }

        val result = vm.uiState.value.currentResult
        assertNotNull(result)
        assertNotNull(result?.referenceCarePlan)
        assertNull(vm.uiState.value.draft)
        assertNull(runBlocking(Dispatchers.IO) { db.consultationDraftDao().get() })

        // 结果页退到后台时 onStop() 会调用 flushDraft()，不能再把已结束的对话写回草稿。
        vm.flushDraft()
        advanceUntilIdle()
        waitUntil { vm.uiState.value.draft == null }
        assertNull(vm.uiState.value.draft)
        assertNull(runBlocking(Dispatchers.IO) { db.consultationDraftDao().get() })
    }

    @Test
    fun `关闭生成病历时仍保留评分与参考诊疗方案`() = runTest(dispatcher) {
        val medicalCase = BuiltInCases.all.first()
        val vm = viewModel(ValidScoreClient())

        vm.finishConsultation(
            initialDiagnosis = medicalCase.correctDiagnosis,
            diagnosisBasis = medicalCase.auxiliaryExams,
            treatmentAnswer = "阿司匹林负荷后尽快再灌注",
            generateRecord = false,
        )
        advanceUntilIdle()
        waitUntil { vm.uiState.value.currentResult != null || vm.uiState.value.finishError != null }

        val result = vm.uiState.value.currentResult
        assertNotNull(result)
        assertEquals("", result?.medicalRecord)
        assertEquals(medicalCase.correctDiagnosis, result?.score?.correctDiagnosis)
        assertEquals(medicalCase.correctDiagnosis, result?.referenceCarePlan?.diagnosis)
        assertTrue(
            result?.referenceCarePlan?.treatmentSections.orEmpty()
                .any { it.label == "药物" && it.content.contains("阿司匹林") },
        )
        assertTrue(result?.referenceCarePlan?.precautions.orEmpty().any { it.contains("随访") })
    }

    @Test
    fun `历史详情会从保存病例重建住院参考诊疗方案`() = runTest(dispatcher) {
        val medicalCase = BuiltInCases.all.first()
        val score = ScoreResult(
            totalScore = 80,
            coverageScore = 30,
            techniqueScore = 12,
            communicationScore = 12,
            diagnosisScore = 16,
            treatmentScore = 10,
            coveredPoints = emptyList(),
            missedQuestions = emptyList(),
            strengths = emptyList(),
            improvements = emptyList(),
            correctDiagnosis = medicalCase.correctDiagnosis,
            diagnosisExplanation = "依据病例资料可支持诊断。",
        )
        val recordId = runBlocking(Dispatchers.IO) {
            db.consultationDao().insert(
                ConsultationEntity(
                    caseTitle = medicalCase.title,
                    department = medicalCase.department,
                    difficulty = medicalCase.difficulty.label,
                    patientName = medicalCase.patient.name,
                    caseJson = CaseJsonCodec.encode(medicalCase),
                    conversationJson = ConversationJsonCodec.encode(emptyList()),
                    medicalRecord = "",
                    scoreJson = ScoreParser.encode(score),
                    totalScore = score.totalScore,
                    initialDiagnosis = medicalCase.correctDiagnosis,
                    diagnosisBasis = medicalCase.auxiliaryExams,
                    createdAt = 1_000L,
                    encounterType = "INPATIENT",
                ),
            )
        }
        val vm = viewModel(SuccessClient())

        vm.loadHistoryDetail(recordId)
        waitUntil { vm.uiState.value.historyDetail != null || vm.uiState.value.historyDetailError != null }

        val detail = vm.uiState.value.historyDetail
        assertNotNull(detail)
        assertNull(vm.uiState.value.historyDetailError)
        assertEquals("住院", detail?.referenceCarePlan?.encounterLabel)
        assertEquals(medicalCase.correctDiagnosis, detail?.referenceCarePlan?.diagnosis)
        assertTrue(
            detail?.referenceCarePlan?.diagnosisBasis.orEmpty()
                .any { it.startsWith("既往史：") },
        )
    }

    @Test
    fun `启动时清理已完成问诊遗留的草稿`() = runTest(dispatcher) {
        val medicalCase = BuiltInCases.all.first()
        val draft = ConsultationDraftEntity(
            caseJson = CaseJsonCodec.encode(medicalCase),
            conversationJson = ConversationJsonCodec.encode(
                listOf(
                    ChatMessage(role = MessageRole.DOCTOR, content = DOCTOR_OPENING_LINE),
                    ChatMessage(role = MessageRole.PATIENT, content = medicalCase.openingLine),
                ),
            ),
            patientPersonality = "COOPERATIVE",
            encounterType = "OUTPATIENT",
            startedAt = 1_000L,
            updatedAt = 2_000L,
        )
        runBlocking(Dispatchers.IO) {
            db.consultationDraftDao().upsert(draft)
            db.consultationDao().insert(
                ConsultationEntity(
                    caseTitle = medicalCase.title,
                    department = medicalCase.department,
                    difficulty = medicalCase.difficulty.label,
                    patientName = medicalCase.patient.name,
                    caseJson = CaseJsonCodec.encode(medicalCase),
                    conversationJson = "[]",
                    medicalRecord = "",
                    scoreJson = "",
                    totalScore = 0,
                    initialDiagnosis = "",
                    diagnosisBasis = "",
                    createdAt = 3_000L,
                ),
            )
        }
        val container = AppContainer(context, overrideLlmClient = SuccessClient(), overrideDatabase = db)
        val vm = AppViewModel(
            container,
            AppUiState(settings = apiSettings(), draft = draft),
        )
        store.put("vm-stale-draft", vm)

        advanceUntilIdle()
        waitUntil { vm.uiState.value.draft == null }

        assertNull(vm.uiState.value.draft)
        assertNull(runBlocking(Dispatchers.IO) { db.consultationDraftDao().get() })
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(200) {
            dispatcher.scheduler.advanceUntilIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("等待条件超时", condition())
    }

    private class SuccessClient(private val reply: String = "收到。") : LlmClient {
        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String = reply

        override suspend fun stream(
            config: LlmConfig,
            messages: List<LlmMessage>,
            temperature: Double,
            onDelta: suspend (String) -> Unit,
        ): String {
            onDelta(reply)
            return reply
        }

        override suspend fun testConnection(config: LlmConfig): ConnectionTestResult =
            ConnectionTestResult.Success
    }

    /** 生成合法评分 JSON，用于验证“不生成病历”时点评链路仍然完整。 */
    private class ValidScoreClient : LlmClient {
        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String {
            check(jsonMode)
            return """
                {
                  "totalScore": 70,
                  "breakdown": {
                    "questioningCoverage": 28,
                    "techniqueAndOrder": 12,
                    "communication": 12,
                    "diagnosisAccuracy": 10,
                    "treatment": 8
                  },
                  "coveredPoints": [],
                  "missedQuestions": ["仍需补充危险因素"],
                  "strengths": ["围绕主诉展开"],
                  "improvements": ["继续完善问诊顺序"],
                  "correctDiagnosis": "模型错误诊断",
                  "diagnosisExplanation": "依据病例资料解释。"
                }
            """.trimIndent()
        }

        override suspend fun stream(
            config: LlmConfig,
            messages: List<LlmMessage>,
            temperature: Double,
            onDelta: suspend (String) -> Unit,
        ): String = error("结束问诊测试不应调用流式接口")

        override suspend fun testConnection(config: LlmConfig): ConnectionTestResult =
            ConnectionTestResult.Success
    }

    private class FlakyClient(
        private var failures: Int,
        private val reply: String,
    ) : LlmClient {
        var streamCalls = 0
            private set

        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String = reply

        override suspend fun stream(
            config: LlmConfig,
            messages: List<LlmMessage>,
            temperature: Double,
            onDelta: suspend (String) -> Unit,
        ): String {
            streamCalls += 1
            if (failures > 0) {
                failures -= 1
                throw com.xjb.wenzhen.data.api.LlmException("网络错误，无法连接服务。")
            }
            onDelta(reply)
            return reply
        }
    }

    /** 病历生成（jsonMode=false）返回合法病历；评分生成（jsonMode=true）返回非法 JSON。 */
    private class RecordOnlyClient : LlmClient {
        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String = if (jsonMode) {
            "这不是评分 JSON"
        } else {
            """
            主诉
            胸痛 3 小时
            现病史
            3 小时前突发胸痛
            既往史
            高血压
            体格检查
            血压 150/90mmHg
            辅助检查
            心电图 ST 段抬高
            初步诊断
            急性心肌梗死
            处理意见
            立即住院
            """.trimIndent()
        }

        override suspend fun stream(
            config: LlmConfig,
            messages: List<LlmMessage>,
            temperature: Double,
            onDelta: suspend (String) -> Unit,
        ): String = "收到。"

        override suspend fun testConnection(config: LlmConfig): ConnectionTestResult =
            ConnectionTestResult.Success
    }

    private class BlockingClient : LlmClient {
        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String = awaitCancellation()

        override suspend fun stream(
            config: LlmConfig,
            messages: List<LlmMessage>,
            temperature: Double,
            onDelta: suspend (String) -> Unit,
        ): String = awaitCancellation()
    }
}
