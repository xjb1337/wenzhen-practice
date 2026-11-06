package com.xjb.wenzhen.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xjb.wenzhen.AppContainer
import com.xjb.wenzhen.data.api.ConnectionTestResult
import com.xjb.wenzhen.data.api.InviteRedeemer
import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.api.LlmErrorKind
import com.xjb.wenzhen.data.api.LlmException
import com.xjb.wenzhen.data.api.RedeemResult
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
import com.xjb.wenzhen.data.model.ThinkingLevel
import com.xjb.wenzhen.data.settings.ApiSource
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.data.settings.SettingsRepository
import com.xjb.wenzhen.domain.AiCommentStatus
import com.xjb.wenzhen.domain.DOCTOR_OPENING_LINE
import com.xjb.wenzhen.domain.RuleScoreCalculator
import com.xjb.wenzhen.domain.ScoreParser
import com.xjb.wenzhen.domain.ScoreResult
import com.xjb.wenzhen.ui.theme.AppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val viewModelJobs = mutableListOf<Job>()
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var roomExecutor: ExecutorService
    private lateinit var settingsPrefs: SharedPreferences
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        settingsPrefs = context.getSharedPreferences("app-viewmodel-test-settings", Context.MODE_PRIVATE)
        settingsPrefs.edit().clear().commit()
        settingsRepository = SettingsRepository(context, settingsPrefs)
        roomExecutor = Executors.newSingleThreadExecutor()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(roomExecutor)
            .setTransactionExecutor(roomExecutor)
            .build()
    }

    @After
    fun tearDown() {
        // advanceUntilIdle 只排空测试调度器，不能等待 Room 后台的查询和 Flow finally。
        // 先等整个 ViewModel 生命周期结束，再排空 Room 的独立查询/失效通知任务；
        // 此后 close 不会与观察者清理发生锁反转，resetMain 也不会与 IO 回调并发。
        runTest(dispatcher) { clearViewModels() }
        roomExecutor.shutdown()
        assertTrue("Room 后台任务未结束", roomExecutor.awaitTermination(10, TimeUnit.SECONDS))
        db.close()
        Dispatchers.resetMain()
    }

    private fun storeViewModel(key: String, vm: AppViewModel) {
        viewModelJobs += vm.viewModelScope.coroutineContext.job
        store.put(key, vm)
    }

    private suspend fun clearViewModels() {
        store.clear()
        viewModelJobs.joinAll()
    }

    private fun appContainer(
        client: LlmClient,
        settingsRepository: SettingsRepository = this.settingsRepository,
        inviteRedeemer: InviteRedeemer? = null,
    ): AppContainer = AppContainer(
        context,
        overrideLlmClient = client,
        overrideDatabase = db,
        overrideSettingsRepository = settingsRepository,
        overrideInviteRedeemer = inviteRedeemer,
    )

    private fun viewModel(
        client: LlmClient,
        settings: AppSettings = apiSettings(),
        settingsRepository: SettingsRepository = this.settingsRepository,
        inviteRedeemer: InviteRedeemer? = null,
        container: AppContainer = appContainer(client, settingsRepository, inviteRedeemer),
    ): AppViewModel {
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
            storeViewModel("vm-${System.nanoTime()}", it)
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
    fun `内置服务鉴权失败会清空会话并提示重新兑换`() = runTest(dispatcher) {
        val settings = AppSettings(
            apiSource = ApiSource.BUILTIN,
            deviceId = "device-1",
            sessionToken = "old-session",
            sessionExpiresAt = System.currentTimeMillis() + 60_000L,
        )
        val vm = viewModel(AuthFailingClient(), settings)
        val event = async { vm.events.first() }

        vm.testConnection(ApiSource.BUILTIN, "", AppSettings.MODEL_FLASH, "", ThinkingLevel.OFF)
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.settings.sessionToken)
        assertNull(vm.uiState.value.settings.sessionExpiresAt)
        assertEquals(
            AppEvent.ShowMessage("内置服务会话已失效，请在设置中重新兑换邀请码。"),
            event.await(),
        )
    }

    @Test
    fun `失效 token 与当前会话不一致时不清除`() = runTest(dispatcher) {
        val oldSettings = AppSettings(
            apiSource = ApiSource.BUILTIN,
            deviceId = "device-1",
            sessionToken = "old-session",
            sessionExpiresAt = System.currentTimeMillis() + 60_000L,
        )
        val container = appContainer(
            client = AuthFailingClient(),
            inviteRedeemer = FakeInviteRedeemer(RedeemResult.Success("new-session")),
        )
        val vm = viewModel(AuthFailingClient(), oldSettings, container = container)

        vm.redeemInvite("WZ-TEST")
        advanceUntilIdle()
        assertEquals("new-session", vm.uiState.value.settings.sessionToken)

        val staleConfig = AppSettings(
            apiSource = ApiSource.BUILTIN,
            deviceId = "device-1",
            sessionToken = "old-session",
        ).toLlmConfig()
        runCatching { container.llmClient.testConnection(staleConfig) }
        advanceUntilIdle()

        assertEquals("new-session", vm.uiState.value.settings.sessionToken)
    }

    @Test
    fun `兑换期间修改的主题不会被旧快照覆盖`() = runTest(dispatcher) {
        val redeemer = BlockingInviteRedeemer()
        val vm = viewModel(SuccessClient(), settings = apiSettings(), inviteRedeemer = redeemer)

        vm.redeemInvite("WZ-TEST")
        runCurrent()
        vm.setTheme(AppTheme.CLINICAL_BLUE)
        advanceUntilIdle()

        redeemer.result.complete(RedeemResult.Success("new-session", expiresAt = 123_000L))
        advanceUntilIdle()

        assertEquals(AppTheme.CLINICAL_BLUE, vm.uiState.value.settings.theme)
        assertEquals("new-session", vm.uiState.value.settings.sessionToken)
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
    fun `关闭时等待Room观察清理并取消挂起的草稿刷新`() = runTest(dispatcher) {
        val queryStarted = CountDownLatch(1)
        val releaseQuery = CountDownLatch(1)
        roomExecutor.execute {
            queryStarted.countDown()
            check(releaseQuery.await(10, TimeUnit.SECONDS))
        }
        assertTrue(queryStarted.await(10, TimeUnit.SECONDS))
        val vm = viewModel(SuccessClient())
        val lifecycleJob = vm.viewModelScope.coroutineContext.job
        try {
            // 启动真正的 Room Flow 和草稿写入，但暂不让后台执行取消/回调。
            vm.flushDraft()
            runCurrent()
            val clearing = async { clearViewModels() }
            runCurrent()

            assertTrue(lifecycleJob.isCancelled)
            assertFalse("Room 观察尚未清理，不能宣告关闭完成", clearing.isCompleted)
            releaseQuery.countDown()
            clearing.await()
            assertTrue(lifecycleJob.isCompleted)

            // 模拟迟到的 onStop；关闭后不能再写回草稿。
            vm.flushDraft()
            advanceUntilIdle()
            assertNull(vm.uiState.value.draft)
            assertNull(runBlocking(Dispatchers.IO) { db.consultationDraftDao().get() })
        } finally {
            releaseQuery.countDown()
        }
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
    fun `取消后旧请求的迟到片段不会写入新气泡`() = runTest(dispatcher) {
        val client = LateDeltaClient()
        val vm = viewModel(client)

        vm.sendMessage("第一次提问")
        advanceUntilIdle()
        val firstHandler = client.deltaHandler
        assertNotNull(firstHandler)

        vm.cancelCurrentRequest()
        vm.sendMessage("第二次提问")
        advanceUntilIdle()
        val secondHandler = client.deltaHandler
        assertNotNull(secondHandler)

        // 模拟旧请求在 cancel 之后才从 socket 读到片段。
        firstHandler!!.invoke("旧请求的迟到回复")
        advanceUntilIdle()

        assertFalse(
            vm.uiState.value.messages.any {
                it.role == MessageRole.PATIENT && it.content.contains("迟到")
            },
        )
        assertTrue(vm.uiState.value.isPatientTyping)

        secondHandler!!.invoke("第二次回复")
        advanceUntilIdle()
        assertEquals("第二次回复", vm.uiState.value.messages.last().content)

        vm.cancelCurrentRequest()
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
        storeViewModel("vm-draft", vm)

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
        storeViewModel("vm-finish-draft", vm)

        // 病历生成成功、AI 点评 JSON 损坏：分数按本地规则计算并写入历史，之后才清草稿。
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
        assertTrue("评分失败时也必须写入历史", (result?.recordId ?: 0L) > 0L)
        assertEquals(AiCommentStatus.FAILED, result?.score?.aiCommentStatus)
        assertTrue(result?.medicalRecord.orEmpty().isNotBlank())
        val saved = runBlocking(Dispatchers.IO) { db.consultationDao().getById(result!!.recordId) }
        assertNotNull(saved)
        assertEquals(result?.score?.totalScore, saved?.totalScore)
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
    fun `结束问诊提交中开启新病例时旧结果只写入历史`() = runTest(dispatcher) {
        val oldCase = BuiltInCases.all.first()
        val newCase = BuiltInCases.all[1]
        val client = BlockingClient()
        val vm = viewModel(client)
        val events = mutableListOf<AppEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) { vm.events.collect { events += it } }

        vm.finishConsultation(
            initialDiagnosis = oldCase.correctDiagnosis,
            diagnosisBasis = oldCase.auxiliaryExams,
            generateRecord = false,
        )
        advanceUntilIdle()
        assertTrue("评分应处于阻塞中", vm.uiState.value.isFinishing)

        // 提交尚未返回时返回并开启另一病例：旧协程不能被取消，只能写入历史。
        vm.openCase(newCase)
        advanceUntilIdle()
        assertEquals(newCase.id, vm.uiState.value.activeCase?.id)

        client.releaseScore()
        waitUntil {
            events.any {
                it is AppEvent.ShowMessage && it.message == "上一次问诊「${oldCase.title}」已保存到历史记录。"
            }
        }
        advanceUntilIdle()

        assertEquals(newCase.id, vm.uiState.value.activeCase?.id)
        assertNull(vm.uiState.value.currentResult)
        assertTrue("旧问诊不能打开结果页", events.none { it is AppEvent.OpenResult })
        assertTrue(
            "events=$events",
            events.filterIsInstance<AppEvent.ShowMessage>().any {
                it.message == "上一次问诊「${oldCase.title}」已保存到历史记录。"
            },
        )
        val records = runBlocking(Dispatchers.IO) { db.consultationDao().observeAll().first() }
        assertEquals(1, records.size)
        assertEquals(oldCase.title, records.single().caseTitle)

        // 新问诊的草稿保存仍然正常，且保存的是新病例而不是旧问诊。
        vm.sendMessage("新问诊的提问")
        advanceUntilIdle()
        waitUntil { runBlocking(Dispatchers.IO) { db.consultationDraftDao().get() } != null }
        val draft = runBlocking(Dispatchers.IO) { db.consultationDraftDao().get() }
        assertNotNull(draft)
        assertEquals(newCase.id, CaseJsonCodec.decode(draft!!.caseJson).id)
    }

    @Test
    fun `评分请求抛出LlmException时按规则分写入历史并清除草稿`() = runTest(dispatcher) {
        val medicalCase = BuiltInCases.all.first()
        val conversation = listOf(
            ChatMessage(role = MessageRole.DOCTOR, content = DOCTOR_OPENING_LINE),
            ChatMessage(role = MessageRole.PATIENT, content = medicalCase.openingLine),
            ChatMessage(role = MessageRole.DOCTOR, content = "胸口疼了多久了？"),
            ChatMessage(role = MessageRole.PATIENT, content = "三个小时了。"),
            ChatMessage(role = MessageRole.DOCTOR, content = "抽烟喝酒吗？"),
            ChatMessage(role = MessageRole.PATIENT, content = "抽了三十年烟。"),
        )
        val draft = ConsultationDraftEntity(
            caseJson = CaseJsonCodec.encode(medicalCase),
            conversationJson = ConversationJsonCodec.encode(conversation),
            patientPersonality = "COOPERATIVE",
            encounterType = "OUTPATIENT",
            startedAt = 1_000L,
            updatedAt = 2_000L,
        )
        runBlocking(Dispatchers.IO) { db.consultationDraftDao().upsert(draft) }
        val container = AppContainer(context, overrideLlmClient = SwitchableScoreClient(), overrideDatabase = db)
        val vm = AppViewModel(
            container,
            AppUiState(
                settings = apiSettings(),
                activeCase = medicalCase,
                consultationStartedAt = 1_000L,
                messages = conversation,
                draft = draft,
            ),
        )
        storeViewModel("vm-score-exception", vm)
        val diagnosis = "急性ST段抬高型心肌梗死"
        val basis = "压榨样胸痛伴大汗，心电图 ST 段抬高"
        val treatment = "阿司匹林300mg嚼服，尽快PCI"

        vm.finishConsultation(
            initialDiagnosis = diagnosis,
            diagnosisBasis = basis,
            treatmentAnswer = treatment,
            generateRecord = false,
        )
        advanceUntilIdle()
        waitUntil { vm.uiState.value.currentResult != null || vm.uiState.value.finishError != null }
        assertNull(vm.uiState.value.finishError)
        waitUntil { vm.uiState.value.draft == null }

        val result = vm.uiState.value.currentResult!!
        val rule = RuleScoreCalculator.compute(medicalCase, conversation, diagnosis, basis, treatment)
        val records = runBlocking(Dispatchers.IO) { db.consultationDao().observeAll().first() }
        assertEquals(1, records.size)
        assertEquals(result.recordId, records.single().id)
        assertEquals(rule.total, records.single().totalScore)
        assertEquals(rule.total, result.score.totalScore)
        assertTrue(rule.total > 0)
        assertTrue(result.score.aiCommentFailed)
        assertEquals(
            AiCommentStatus.FAILED,
            ScoreParser.parse(records.single().scoreJson).aiCommentStatus,
        )
        assertTrue(result.notice.orEmpty().contains("AI 点评生成失败"))
        assertFalse(result.notice.orEmpty().contains("可返回后重试"))
        assertTrue(result.score.improvements.none { it.contains("可返回后重试") })
        assertNull(runBlocking(Dispatchers.IO) { db.consultationDraftDao().get() })
    }

    @Test
    fun `重新生成AI点评成功后更新同一条历史记录`() = runTest(dispatcher) {
        val client = SwitchableScoreClient()
        val vm = viewModel(client)
        val medicalCase = BuiltInCases.all.first()

        vm.finishConsultation(
            initialDiagnosis = medicalCase.correctDiagnosis,
            diagnosisBasis = medicalCase.auxiliaryExams,
            treatmentAnswer = "阿司匹林负荷后尽快再灌注",
            generateRecord = false,
        )
        advanceUntilIdle()
        waitUntil { vm.uiState.value.currentResult != null || vm.uiState.value.finishError != null }
        val first = vm.uiState.value.currentResult!!
        assertTrue(first.score.aiCommentFailed)
        assertTrue(first.recordId > 0L)

        client.failScore = false
        vm.regenerateAiComment(first.recordId)
        advanceUntilIdle()
        waitUntil {
            vm.uiState.value.regeneratingCommentId == null &&
                vm.uiState.value.currentResult?.score?.aiCommentFailed == false
        }

        val updated = vm.uiState.value.currentResult!!
        assertEquals(first.recordId, updated.recordId)
        assertEquals(first.score.totalScore, updated.score.totalScore)
        assertTrue(updated.score.strengths.contains("围绕主诉展开"))
        assertNull(updated.notice)

        val records = runBlocking(Dispatchers.IO) { db.consultationDao().observeAll().first() }
        assertEquals("重新生成只能更新原记录，不能新增", 1, records.size)
        assertEquals(first.recordId, records.single().id)
        val stored = ScoreParser.parse(records.single().scoreJson)
        assertEquals(AiCommentStatus.OK, stored.aiCommentStatus)
        assertTrue(stored.strengths.contains("围绕主诉展开"))
        assertTrue(stored.improvements.contains("继续完善问诊顺序"))
        assertEquals(first.score.totalScore, records.single().totalScore)
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
        storeViewModel("vm-stale-draft", vm)

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
            return VALID_SCORE_JSON
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

    /** 评分请求（jsonMode=true）在 [failScore] 为 true 时抛出限流异常，否则返回合法评分 JSON。 */
    private class SwitchableScoreClient : LlmClient {
        @Volatile
        var failScore: Boolean = true

        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String {
            if (!jsonMode) return "收到。"
            if (failScore) throw LlmException("请求过于频繁，已被限流，请稍后重试。")
            return VALID_SCORE_JSON
        }

        override suspend fun stream(
            config: LlmConfig,
            messages: List<LlmMessage>,
            temperature: Double,
            onDelta: suspend (String) -> Unit,
        ): String = error("结束问诊测试不应调用流式接口")
    }

    /** 保存 onDelta 回调供测试在取消后手动触发，模拟底层阻塞读迟到的片段。 */
    private class LateDeltaClient : LlmClient {
        var deltaHandler: (suspend (String) -> Unit)? = null
            private set

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
        ): String {
            deltaHandler = onDelta
            return awaitCancellation()
        }
    }

    private class BlockingClient : LlmClient {
        private val scoreRelease = CompletableDeferred<Unit>()

        fun releaseScore() {
            scoreRelease.complete(Unit)
        }

        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String {
            if (jsonMode) {
                scoreRelease.await()
                return VALID_SCORE_JSON
            }
            return awaitCancellation()
        }

        override suspend fun stream(
            config: LlmConfig,
            messages: List<LlmMessage>,
            temperature: Double,
            onDelta: suspend (String) -> Unit,
        ): String = awaitCancellation()
    }

    private class AuthFailingClient : LlmClient {
        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String = throw LlmException("会话已失效", kind = LlmErrorKind.AUTH)

        override suspend fun testConnection(config: LlmConfig): ConnectionTestResult =
            throw LlmException("会话已失效", kind = LlmErrorKind.AUTH)
    }

    private class FakeInviteRedeemer(
        private val result: RedeemResult,
    ) : InviteRedeemer {
        override suspend fun redeem(baseUrl: String, code: String, deviceId: String): RedeemResult =
            result
    }

    private class BlockingInviteRedeemer : InviteRedeemer {
        val result = CompletableDeferred<RedeemResult>()

        override suspend fun redeem(baseUrl: String, code: String, deviceId: String): RedeemResult =
            result.await()
    }

    private companion object {
        val VALID_SCORE_JSON = """
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
}
