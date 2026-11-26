package com.xjb.wenzhen.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xjb.wenzhen.AppContainer
import com.xjb.wenzhen.data.api.ConnectionTestResult
import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.api.LlmException
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.cases.CaseJsonCodec
import com.xjb.wenzhen.data.db.AppDatabase
import com.xjb.wenzhen.data.db.ConsultationDao
import com.xjb.wenzhen.data.db.ConsultationDraftDao
import com.xjb.wenzhen.data.db.ConsultationDraftEntity
import com.xjb.wenzhen.data.db.ConsultationDraftRepository
import com.xjb.wenzhen.data.db.ConsultationEntity
import com.xjb.wenzhen.data.db.ConsultationRepository
import com.xjb.wenzhen.data.db.ConversationJsonCodec
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.model.PatientPersonality
import com.xjb.wenzhen.data.model.ThinkingLevel
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import kotlinx.coroutines.withContext
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
        consultationRepository: ConsultationRepository? = null,
        consultationDraftRepository: ConsultationDraftRepository? = null,
    ): AppContainer = AppContainer(
        context,
        overrideLlmClient = client,
        overrideDatabase = db,
        overrideSettingsRepository = settingsRepository,
        overrideConsultationRepository = consultationRepository,
        overrideConsultationDraftRepository = consultationDraftRepository,
    )

    private fun viewModel(
        client: LlmClient,
        settings: AppSettings = apiSettings(),
        settingsRepository: SettingsRepository = this.settingsRepository,
        consultationRepository: ConsultationRepository? = null,
        consultationDraftRepository: ConsultationDraftRepository? = null,
        container: AppContainer = appContainer(client, settingsRepository, consultationRepository, consultationDraftRepository),
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
    fun `冷启动读取设置在后台且读取完成前不暴露默认模式`() = runTest(dispatcher) {
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val mainThreadRead = AtomicBoolean(false)
        settingsPrefs.edit().putBoolean("offline_mode", true).putBoolean("privacy_accepted", true).commit()
        val blockingPrefs = object : SharedPreferences by settingsPrefs {
            override fun getString(key: String?, defValue: String?): String? {
                if (key == "deepseek_api_key") {
                    mainThreadRead.set(Looper.getMainLooper().isCurrentThread)
                    readStarted.countDown()
                    check(releaseRead.await(2, TimeUnit.SECONDS))
                }
                return settingsPrefs.getString(key, defValue)
            }
        }
        val vm = AppViewModel(appContainer(SuccessClient(), SettingsRepository(context, blockingPrefs)))
        storeViewModel("vm-startup-read", vm)

        try {
            runCurrent()
            assertTrue(readStarted.await(2, TimeUnit.SECONDS))
            assertFalse(mainThreadRead.get())
            assertEquals(SettingsReadState.Loading, vm.uiState.value.settingsRead)
        } finally {
            releaseRead.countDown()
        }
        waitUntil { vm.uiState.value.settingsRead == SettingsReadState.Ready }
        assertTrue(vm.uiState.value.settings.offlineMode)
        assertTrue(vm.uiState.value.settings.privacyAccepted)
    }

    @Test
    fun `读取设置失败仍可临时离线并能重试取回原有设置`() = runTest(dispatcher) {
        val unavailable = AtomicBoolean(true)
        settingsPrefs.edit().putString("deepseek_api_key", "synthetic-original-key").commit()
        val flakyPrefs = object : SharedPreferences by settingsPrefs {
            override fun getString(key: String?, defValue: String?): String? {
                if (unavailable.get()) error("模拟加密设置无法读取")
                return settingsPrefs.getString(key, defValue)
            }
        }
        val vm = AppViewModel(appContainer(SuccessClient(), SettingsRepository(context, flakyPrefs)))
        storeViewModel("vm-settings-retry", vm)
        waitUntil { vm.uiState.value.settingsRead is SettingsReadState.Unavailable }

        vm.continueOfflineWithoutSettings()
        vm.acceptPrivacyNotice()
        vm.setOfflineMode(false)
        assertEquals(SettingsReadState.Unavailable(temporaryOffline = true), vm.uiState.value.settingsRead)
        assertTrue(vm.uiState.value.settings.offlineMode)
        assertTrue(vm.uiState.value.settings.privacyAccepted)
        assertEquals("synthetic-original-key", settingsPrefs.getString("deepseek_api_key", null))
        assertFalse(settingsPrefs.getBoolean("privacy_accepted", false))

        unavailable.set(false)
        vm.reloadSettings()
        waitUntil { vm.uiState.value.settingsRead == SettingsReadState.Ready }
        assertEquals("synthetic-original-key", vm.uiState.value.settings.apiKey)
        assertFalse(vm.uiState.value.settings.offlineMode)
    }

    @Test
    fun `保存 HTTP Base URL 会被拒绝且不落库`() = runTest(dispatcher) {
        val vm = viewModel(SuccessClient())

        val initialSettings = vm.uiState.value.settings
        val initialPreferences = settingsPrefs.all
        vm.saveSettings(apiKey = "k", model = "deepseek-chat", baseUrl = "http://api.test.com")

        assertFalse(vm.uiState.value.settingsFormSaving)
        assertEquals(initialSettings, vm.uiState.value.settings)
        assertEquals(initialPreferences, settingsPrefs.all)

        val error = vm.uiState.value.settingsSaveError
        assertNotNull(error)
        assertTrue(error.orEmpty().contains("HTTPS"))
    }

    @Test
    fun `设置写入在后台串行执行且开启离线立即生效`() = runTest(dispatcher) {
        val firstWriteStarted = AtomicBoolean(false)
        val wroteOnMain = AtomicBoolean(false)
        val writeCount = AtomicInteger()
        val resumeFirstWrite = CountDownLatch(1)
        val delayedPrefs = object : SharedPreferences by settingsPrefs {
            override fun edit(): SharedPreferences.Editor {
                if (Looper.myLooper() == Looper.getMainLooper()) wroteOnMain.set(true)
                if (writeCount.getAndIncrement() == 0) {
                    firstWriteStarted.set(true)
                    check(resumeFirstWrite.await(5, TimeUnit.SECONDS)) { "测试未释放首个设置写入" }
                }
                return settingsPrefs.edit()
            }
        }
        val repository = SettingsRepository(context, delayedPrefs)
        val vm = viewModel(SuccessClient(), settingsRepository = repository)
        try {
            vm.saveSettings("synthetic-new-key", AppSettings.MODEL_PRO, AppSettings.DEFAULT_BASE_URL)
            waitUntil { firstWriteStarted.get() }
            assertTrue(vm.uiState.value.settingsFormSaving)

            vm.setOfflineMode(true)
            vm.setTheme(AppTheme.ROSE)
            vm.setTheme(AppTheme.JADE)
            runCurrent()

            assertTrue(vm.uiState.value.offlineMode)
            assertFalse(vm.uiState.value.settings.offlineMode)
            assertFalse(repository.load().offlineMode)
            assertEquals(1, writeCount.get())
        } finally {
            resumeFirstWrite.countDown()
        }

        waitUntil { vm.uiState.value.settings.theme == AppTheme.JADE }
        val persisted = repository.load()
        assertFalse(wroteOnMain.get())
        assertEquals(4, writeCount.get())
        assertEquals(vm.uiState.value.settings, persisted)
        assertEquals("synthetic-new-key", persisted.apiKey)
        assertEquals(AppSettings.MODEL_PRO, persisted.model)
        assertTrue(persisted.offlineMode)
        assertNull(vm.uiState.value.pendingOfflineMode)
        assertFalse(vm.uiState.value.settingsFormSaving)
    }

    @Test
    fun `保存并离开只会在持久化成功后发出导航事件`() = runTest(dispatcher) {
        val storageUnavailable = AtomicBoolean(true)
        val flakyPrefs = object : SharedPreferences by settingsPrefs {
            override fun edit(): SharedPreferences.Editor {
                check(!storageUnavailable.get()) { "测试写入失败" }
                return settingsPrefs.edit()
            }
        }
        val vm = viewModel(SuccessClient(), settingsRepository = SettingsRepository(context, flakyPrefs))
        val initial = vm.uiState.value.settings
        val exit = SettingsExit.Tab("history")

        vm.saveSettings("synthetic-new-key", AppSettings.MODEL_FLASH, AppSettings.DEFAULT_BASE_URL, exit = exit)
        waitUntil { !vm.uiState.value.settingsFormSaving }
        assertEquals(initial, vm.uiState.value.settings)
        assertNotNull(vm.uiState.value.settingsSaveError)
        assertTrue(vm.events.first() is AppEvent.ShowMessage)

        storageUnavailable.set(false)
        vm.saveSettings("synthetic-new-key", AppSettings.MODEL_FLASH, AppSettings.DEFAULT_BASE_URL, exit = exit)
        waitUntil { !vm.uiState.value.settingsFormSaving }
        assertEquals(AppEvent.SettingsSaved(exit), vm.events.first())
        assertEquals("synthetic-new-key", settingsRepository.load().apiKey)
        assertNull(vm.uiState.value.settingsSaveError)
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
        runBlocking(Dispatchers.IO) { db.consultationDraftDao().upsert(draft) }
        val vm = AppViewModel(container, AppUiState(settings = apiSettings()))
        storeViewModel("vm-draft", vm)
        waitUntil { !vm.uiState.value.draftLoading }

        vm.restoreConsultation(draft.startedAt - 1)
        assertNull(vm.uiState.value.activeCase)

        vm.restoreConsultation(draft.startedAt)

        assertEquals(medicalCase.id, vm.uiState.value.activeCase?.id)
        assertEquals(draft.startedAt, vm.uiState.value.consultationStartedAt)
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
    fun `结束问诊提交中开启新病例时旧提交失败会提示`() = runTest(dispatcher) {
        val oldCase = BuiltInCases.all.first()
        val newCase = BuiltInCases.all[1]
        val client = BlockingClient()
        val failingDao = object : ConsultationDao by db.consultationDao() {
            override suspend fun insert(entity: ConsultationEntity): Long =
                throw SQLiteException("disk I/O error")
        }
        val vm = viewModel(client, consultationRepository = ConsultationRepository(failingDao))
        val events = mutableListOf<AppEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) { vm.events.collect { events += it } }

        vm.finishConsultation(
            initialDiagnosis = oldCase.correctDiagnosis,
            diagnosisBasis = oldCase.auxiliaryExams,
            generateRecord = false,
        )
        advanceUntilIdle()
        assertTrue("评分应处于阻塞中", vm.uiState.value.isFinishing)

        vm.openCase(newCase)
        advanceUntilIdle()
        assertEquals(newCase.id, vm.uiState.value.activeCase?.id)

        client.releaseScore()
        waitUntil {
            events.any {
                it is AppEvent.ShowMessage &&
                    it.message == "上一次问诊「${oldCase.title}」保存失败，请稍后重试。"
            }
        }

        assertEquals(newCase.id, vm.uiState.value.activeCase?.id)
        assertNull(vm.uiState.value.currentResult)
        assertTrue("旧问诊不能打开结果页", events.none { it is AppEvent.OpenResult })
        val records = runBlocking(Dispatchers.IO) { db.consultationDao().observeAll().first() }
        assertTrue("写库失败不应留下记录", records.isEmpty())
    }

    @Test
    fun `恢复历史成功时只提示实际恢复条数`() = runTest(dispatcher) {
        val medicalCase = BuiltInCases.all.first()
        val record = ConsultationEntity(
            caseTitle = medicalCase.title,
            department = medicalCase.department,
            difficulty = medicalCase.difficulty.label,
            patientName = medicalCase.patient.name,
            caseJson = CaseJsonCodec.encode(medicalCase),
            conversationJson = ConversationJsonCodec.encode(emptyList()),
            medicalRecord = "",
            scoreJson = "",
            totalScore = 0,
            initialDiagnosis = medicalCase.correctDiagnosis,
            diagnosisBasis = medicalCase.auxiliaryExams,
            createdAt = 10_000L,
        )
        val vm = viewModel(SuccessClient())
        val events = mutableListOf<AppEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) { vm.events.collect { events += it } }

        vm.restoreHistory(listOf(record, record.copy(id = 0L)).asFlow())
        waitUntil { events.any { it is AppEvent.ShowMessage } }

        assertEquals(
            listOf(AppEvent.ShowMessage("已恢复 1 条")),
            events.filterIsInstance<AppEvent.ShowMessage>(),
        )
    }

    @Test
    fun `历史摘要保留显示字段且按需读取用时和完整备份`() = runTest(dispatcher) {
        val dao = db.consultationDao()
        val record = entityFor(0L, BuiltInCases.all.first()).copy(
            conversationJson = ConversationJsonCodec.encode(
                listOf(
                    ChatMessage(MessageRole.DOCTOR, "怎么不舒服？", timestamp = 1_000L),
                    ChatMessage(MessageRole.PATIENT, "练习对话", timestamp = 121_000L),
                )
            ),
            medicalRecord = "测试用病历",
        )
        val id = dao.insert(record)
        val vm = viewModel(SuccessClient())
        waitUntil { vm.uiState.value.history.isNotEmpty() }

        val summary = vm.uiState.value.history.single()
        assertEquals(id, summary.id)
        assertEquals(record.caseTitle, summary.caseTitle)
        assertEquals(record.patientName, summary.patientName)
        assertEquals(record.totalScore, summary.totalScore)
        assertEquals(2L, vm.loadHistoryDuration(id))
        assertEquals(record.copy(id = id), vm.loadHistoryForExport().single())
    }

    @Test
    fun `统计按订阅读取存档成绩且随历史变更刷新`() = runTest(dispatcher) {
        val dao = db.consultationDao()
        val case = BuiltInCases.all.first()
        val recentId = dao.insert(entityFor(0L, case).copy(
            createdAt = 2_000L,
            totalScore = 90,
            scoreJson = """{"totalScore":88,"遗漏要点":["用药史","伴随症状"]}""",
        ))
        val oldId = dao.insert(entityFor(0L, case).copy(
            createdAt = 1_000L,
            totalScore = 60,
            scoreJson = "invalid legacy score JSON",
        ))
        val vm = viewModel(SuccessClient())
        assertEquals(StatsUiState.Loading, vm.statsState.value)
        backgroundScope.launch { vm.statsState.collect {} }
        runCurrent()

        waitUntil { vm.statsState.value is StatsUiState.Ready }
        val before = (vm.statsState.value as StatsUiState.Ready).summary
        assertEquals(2, before.attempts)
        assertEquals(75, before.averageScore)
        assertEquals(listOf(60, 90), before.trend.map { it.score })
        assertEquals(setOf("用药史", "伴随症状"), before.topMissedQuestions.map { it.question }.toSet())

        dao.updateScore(recentId, """{"totalScore":40,"missedQuestions":["暴露史"]}""", 90)
        waitUntil {
            (vm.statsState.value as? StatsUiState.Ready)?.summary?.topMissedQuestions?.singleOrNull()?.question == "暴露史"
        }
        dao.deleteById(oldId)
        waitUntil { (vm.statsState.value as? StatsUiState.Ready)?.summary?.attempts == 1 }
        val after = (vm.statsState.value as StatsUiState.Ready).summary
        assertEquals(90, after.averageScore)
        assertEquals(listOf(90), after.trend.map { it.score })
    }

    @Test
    fun `删除历史记录遇到数据库异常时不崩溃并收到提示`() = runTest(dispatcher) {
        val failingDao = object : ConsultationDao by db.consultationDao() {
            override suspend fun deleteById(id: Long) {
                throw SQLiteException("disk I/O error")
            }
        }
        val vm = viewModel(SuccessClient(), consultationRepository = ConsultationRepository(failingDao))
        val events = mutableListOf<AppEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.deleteHistory(1L)
        waitUntil { events.any { it is AppEvent.ShowMessage } }

        assertTrue("events=$events", events.any { it is AppEvent.ShowMessage })
    }

    @Test
    fun `已有草稿时 AI 生成只保存新病例且病例标识不重复`() = runTest(dispatcher) {
        val original = BuiltInCases.all.first()
        val draft = ConsultationDraftEntity(
            caseJson = CaseJsonCodec.encode(original),
            conversationJson = "[]",
            patientPersonality = "COOPERATIVE",
            encounterType = "OUTPATIENT",
            startedAt = 1_000L,
            updatedAt = 2_000L,
        )
        runBlocking(Dispatchers.IO) { db.consultationDraftDao().upsert(draft) }
        val generated = original.copy(title = "用于回归测试的生成病例")
        val vm = AppViewModel(
            appContainer(SuccessClient(CaseJsonCodec.encode(generated))),
            AppUiState(settings = apiSettings()),
        )
        storeViewModel("vm-generation-with-draft", vm)
        waitUntil { !vm.uiState.value.draftLoading }

        vm.generateCase(original.department, original.difficulty)
        waitUntil {
            !vm.uiState.value.isGeneratingCase &&
                vm.uiState.value.cases.any { it.title == generated.title }
        }

        val state = vm.uiState.value
        assertNull(state.generationError)
        assertNull(state.activeCase)
        assertEquals(draft.startedAt, state.draft?.startedAt)
        assertEquals(draft.startedAt, runBlocking(Dispatchers.IO) { db.consultationDraftDao().get() }?.startedAt)
        assertEquals(1, state.cases.count { it.title == generated.title })
        assertTrue(vm.events.first() is AppEvent.ShowMessage)

        val generatedId = state.cases.single { it.title == generated.title }.id
        vm.setCaseFavorite(generatedId, true)
        waitUntil { vm.uiState.value.savedCases.any { it.id == generatedId && it.favorite } }
        assertTrue(vm.uiState.value.savedCases.single { it.id == generatedId }.caseJson.isNotBlank())
    }

    @Test
    fun `放弃草稿时在途草稿保存不会把草稿写回`() = runTest(dispatcher) {
        val draftDao = object : ConsultationDraftDao {
            val saveRelease = CompletableDeferred<Unit>()
            var saveStarted = false
            var deleteCalled = false
            var stored: ConsultationDraftEntity? = null

            override suspend fun get(): ConsultationDraftEntity? = stored

            override suspend fun upsert(draft: ConsultationDraftEntity) {
                saveStarted = true
                // 模拟 Room 正在落盘：即使调用方取消，底层写入也先完成。
                withContext(NonCancellable) {
                    saveRelease.await()
                    stored = draft
                }
            }

            override suspend fun delete() {
                stored = null
                deleteCalled = true
            }
        }
        val vm = viewModel(
            SuccessClient(),
            consultationDraftRepository = ConsultationDraftRepository(draftDao),
        )

        vm.flushDraft()
        advanceUntilIdle()
        assertTrue("草稿保存应已开始", draftDao.saveStarted)

        vm.discardDraft()
        advanceUntilIdle()
        draftDao.saveRelease.complete(Unit)
        waitUntil { draftDao.deleteCalled }

        assertNull("放弃草稿后界面不能出现旧草稿", vm.uiState.value.draft)
        assertNull("放弃草稿后仓库必须为空", draftDao.stored)
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
        val score = requireNotNull(result.score)
        val rule = RuleScoreCalculator.compute(medicalCase, conversation, diagnosis, basis, treatment)
        val records = runBlocking(Dispatchers.IO) { db.consultationDao().observeAll().first() }
        assertEquals(1, records.size)
        assertEquals(result.recordId, records.single().id)
        assertEquals(rule.total, records.single().totalScore)
        assertEquals(rule.total, score.totalScore)
        assertTrue(rule.total > 0)
        assertTrue(score.aiCommentFailed)
        assertEquals(
            AiCommentStatus.FAILED,
            ScoreParser.parse(records.single().scoreJson).aiCommentStatus,
        )
        assertTrue(result.notice.orEmpty().contains("AI 点评生成失败"))
        assertFalse(result.notice.orEmpty().contains("可返回后重试"))
        assertTrue(score.improvements.none { it.contains("可返回后重试") })
        assertNull(runBlocking(Dispatchers.IO) { db.consultationDraftDao().get() })
    }

    @Test
    fun `历史提交成功后草稿清除失败也能查看已保存的结果`() = runTest(dispatcher) {
        val draftDao = object : ConsultationDraftDao {
            override suspend fun get(): ConsultationDraftEntity? = null

            override suspend fun upsert(draft: ConsultationDraftEntity) = Unit

            override suspend fun delete() {
                error("模拟草稿清除失败")
            }
        }
        val vm = viewModel(
            ValidScoreClient(),
            consultationDraftRepository = ConsultationDraftRepository(draftDao),
        )

        vm.finishConsultation("初步诊断", "诊断依据", generateRecord = false)
        waitUntil { vm.uiState.value.currentResult != null || vm.uiState.value.finishError != null }

        val result = requireNotNull(vm.uiState.value.currentResult)
        assertNull(vm.uiState.value.finishError)
        val records = runBlocking(Dispatchers.IO) { db.consultationDao().observeAll().first() }
        assertEquals(result.recordId, records.single().id)
        assertEquals(result.recordId, (vm.events.first() as AppEvent.OpenResult).recordId)

        vm.finishConsultation("重复提交", "诊断依据", generateRecord = false)
        advanceUntilIdle()
        assertEquals(1, runBlocking(Dispatchers.IO) { db.consultationDao().observeAll().first().size })
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
        val firstScore = requireNotNull(first.score)
        assertTrue(firstScore.aiCommentFailed)
        assertTrue(first.recordId > 0L)

        val archivedScore = firstScore.copy(totalScore = 87, coverageScore = 33)
        runBlocking(Dispatchers.IO) {
            db.consultationDao().updateScore(first.recordId, ScoreParser.encode(archivedScore), archivedScore.totalScore)
        }
        client.failScore = false
        vm.regenerateAiComment(first.recordId)
        advanceUntilIdle()
        waitUntil {
            vm.uiState.value.regeneratingCommentId == null &&
                vm.uiState.value.currentResult?.score?.aiCommentFailed == false
        }

        val updated = vm.uiState.value.currentResult!!
        val updatedScore = requireNotNull(updated.score)
        assertEquals(first.recordId, updated.recordId)
        assertEquals(archivedScore.totalScore, updatedScore.totalScore)
        assertEquals(archivedScore.coverageScore, updatedScore.coverageScore)
        assertEquals(archivedScore.evidence, updatedScore.evidence)
        assertTrue(updatedScore.strengths.contains("围绕主诉展开"))
        assertNull(updated.notice)

        val records = runBlocking(Dispatchers.IO) { db.consultationDao().observeAll().first() }
        assertEquals("重新生成只能更新原记录，不能新增", 1, records.size)
        assertEquals(first.recordId, records.single().id)
        val stored = ScoreParser.parse(records.single().scoreJson)
        assertEquals(AiCommentStatus.OK, stored.aiCommentStatus)
        assertTrue(stored.strengths.contains("围绕主诉展开"))
        assertTrue(stored.improvements.contains("继续完善问诊顺序"))
        assertEquals(archivedScore.totalScore, records.single().totalScore)
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
    fun `旧记录没有评分分项仍能打开已保存总分病历和对话`() = runTest(dispatcher) {
        val medicalCase = BuiltInCases.all.first()
        val messages = listOf(ChatMessage(MessageRole.DOCTOR, "胸口疼了多久？"))
        val recordId = runBlocking(Dispatchers.IO) {
            db.consultationDao().insert(
                entityFor(0L, medicalCase).copy(
                    scoreJson = """{"totalScore":72}""",
                    totalScore = 72,
                    medicalRecord = "已存档的病历",
                    conversationJson = ConversationJsonCodec.encode(messages),
                ),
            )
        }
        val vm = viewModel(SuccessClient())

        vm.loadHistoryDetail(recordId)
        waitUntil { !vm.uiState.value.historyDetailLoading }

        val detail = requireNotNull(vm.uiState.value.historyDetail)
        assertNull(vm.uiState.value.historyDetailError)
        assertNull(detail.score)
        assertEquals(72, detail.savedTotalScore)
        assertEquals("已存档的病历", detail.medicalRecord)
        assertEquals(messages, detail.messages)
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

    @Test
    fun `测试连接取消后清除正在测试状态且可重试`() = runTest(dispatcher) {
        val vm = viewModel(CancellingClient())

        vm.testConnection("key", "deepseek-chat", "https://api.deepseek.com", ThinkingLevel.OFF)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.connectionTest.isTesting)
        assertNull(vm.uiState.value.connectionTest.message)
        assertNull(vm.uiState.value.connectionTest.isSuccess)
    }

    @Test
    fun `开启离线模式后即使已配置Key也不再调用在线AI`() = runTest(dispatcher) {
        val noNetworkClient = object : LlmClient {
            override suspend fun complete(
                config: LlmConfig,
                messages: List<LlmMessage>,
                jsonMode: Boolean,
                temperature: Double,
            ): String = throw AssertionError("离线模式不应调用在线 complete")

            override suspend fun stream(
                config: LlmConfig,
                messages: List<LlmMessage>,
                temperature: Double,
                onDelta: suspend (String) -> Unit,
            ): String = throw AssertionError("离线模式不应调用在线 stream")

            override suspend fun testConnection(config: LlmConfig): ConnectionTestResult =
                throw AssertionError("离线模式不应测试在线连接")
        }
        val vm = viewModel(
            client = noNetworkClient,
            settings = apiSettings().copy(offlineMode = true),
        )

        val medicalCase = BuiltInCases.all.first()
        vm.openCase(medicalCase, PatientPersonality.ANXIOUS)
        vm.sendMessage("您胸痛多久了？")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.messages.last().content.contains("要不要紧"))
        assertTrue(vm.uiState.value.messages.last().content.contains("3小时前"))
        assertFalse(vm.uiState.value.isPatientTyping)
        assertNull(vm.uiState.value.chatError)
        assertEquals(MessageDeliveryState.SENT, vm.uiState.value.messages[2].deliveryState)
        assertEquals(MessageRole.PATIENT, vm.uiState.value.messages.last().role)

        vm.finishConsultation("急性心肌梗死", "持续胸痛伴大汗")
        waitUntil { vm.uiState.value.currentResult != null }

        val result = vm.uiState.value.currentResult!!
        assertFalse(result.isAiGeneratedCase)
        assertFalse(requireNotNull(result.score).aiCommentFailed)

        val case = BuiltInCases.all.first()
        vm.generateCase(case.department, case.difficulty)
        vm.regenerateAiComment(result.recordId)
        vm.testConnection("sk-synthetic", AppSettings.MODEL_FLASH, AppSettings.DEFAULT_BASE_URL)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isGeneratingCase)
        assertNull(vm.uiState.value.regeneratingCommentId)
        assertEquals(AppSettings.OFFLINE_AI_MESSAGE, vm.uiState.value.connectionTest.message)
        assertEquals(AppSettings.OFFLINE_AI_MESSAGE, vm.uiState.value.currentResult?.notice)
    }

    @Test
    fun `切换离线练习会取消正在生成的病例和连接测试`() = runTest(dispatcher) {
        val generationStarted = CompletableDeferred<Unit>()
        val generationStopped = CompletableDeferred<Unit>()
        val testStarted = CompletableDeferred<Unit>()
        val testStopped = CompletableDeferred<Unit>()
        val client = object : LlmClient {
            override suspend fun complete(config: LlmConfig, messages: List<LlmMessage>, jsonMode: Boolean, temperature: Double): String {
                generationStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    generationStopped.complete(Unit)
                }
            }

            override suspend fun testConnection(config: LlmConfig): ConnectionTestResult {
                testStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    testStopped.complete(Unit)
                }
            }
        }
        val vm = viewModel(client)
        waitUntil { !vm.uiState.value.draftLoading }
        val case = BuiltInCases.all.first()
        vm.generateCase(case.department, case.difficulty)
        vm.testConnection("sk-synthetic", AppSettings.MODEL_FLASH, AppSettings.DEFAULT_BASE_URL)
        runCurrent()
        assertTrue(generationStarted.isCompleted)
        assertTrue(testStarted.isCompleted)

        vm.setOfflineMode(true)
        runCurrent()

        assertTrue(generationStopped.isCompleted)
        assertTrue(testStopped.isCompleted)
        assertTrue(vm.uiState.value.offlineMode)
        waitUntil { vm.uiState.value.pendingOfflineMode == null }
        assertTrue(vm.uiState.value.settings.offlineMode)
        assertFalse(vm.uiState.value.isGeneratingCase)
        assertFalse(vm.uiState.value.connectionTest.isTesting)
        assertTrue(vm.uiState.value.savedCases.isEmpty())
    }

    @Test
    fun `提交后开启离线会中止两路 AI 请求并保存本地评分`() = runTest(dispatcher) {
        val started = AtomicInteger()
        val stopped = AtomicInteger()
        val client = object : LlmClient {
            override suspend fun complete(
                config: LlmConfig,
                messages: List<LlmMessage>,
                jsonMode: Boolean,
                temperature: Double,
            ): String {
                started.incrementAndGet()
                try {
                    awaitCancellation()
                } finally {
                    stopped.incrementAndGet()
                }
            }

            override suspend fun testConnection(config: LlmConfig) = ConnectionTestResult.Success
        }
        val vm = viewModel(client)
        waitUntil { !vm.uiState.value.draftLoading }
        vm.finishConsultation("急性心肌梗死", "持续胸痛伴大汗", generateRecord = true)
        runCurrent()
        assertEquals(2, started.get())

        vm.setOfflineMode(true)
        waitUntil { vm.uiState.value.currentResult != null && vm.uiState.value.pendingOfflineMode == null }
        val result = requireNotNull(vm.uiState.value.currentResult)
        val saved = db.consultationDao().getById(result.recordId)

        assertEquals(2, stopped.get())
        assertFalse(vm.uiState.value.isFinishing)
        val score = requireNotNull(result.score)
        assertTrue(score.diagnosisExplanation.contains("本地规则评分"))
        assertTrue(result.medicalRecord.startsWith("【参考病历】"))
        assertTrue(result.notice.orEmpty().contains("已切换至离线练习"))
        assertEquals(score.totalScore, saved?.totalScore)
    }

    @Test
    fun `内置病例收藏持久化且不会复制或删除病例`() = runTest(dispatcher) {
        val vm = viewModel(SuccessClient())
        val case = BuiltInCases.all.first()

        vm.setCaseFavorite(case.id, true)
        vm.setCaseFavorite(case.id, true)
        waitUntil { vm.uiState.value.savedCases.any { it.id == case.id && it.favorite } }
        assertEquals(1, vm.uiState.value.cases.count { it.id == case.id })
        assertFalse(vm.uiState.value.cases.single { it.id == case.id }.isAiGenerated)

        val reopened = viewModel(SuccessClient())
        waitUntil { reopened.uiState.value.savedCases.any { it.id == case.id && it.favorite } }
        reopened.deleteSavedCase(case.id)
        assertTrue(reopened.uiState.value.savedCases.single { it.id == case.id }.favorite)

        reopened.setCaseFavorite(case.id, false)
        waitUntil { reopened.uiState.value.savedCases.any { it.id == case.id && !it.favorite } }
        assertEquals(1, reopened.uiState.value.cases.count { it.id == case.id })
    }

    @Test
    fun `快速切换历史详情时旧请求结果不会覆盖最新选择`() = runTest(dispatcher) {
        val firstCase = BuiltInCases.all.first()
        val secondCase = BuiltInCases.all[1]
        val firstGate = CompletableDeferred<Unit>()
        val dao = object : ConsultationDao by db.consultationDao() {
            override suspend fun getById(id: Long): ConsultationEntity? {
                if (id == 1L) {
                    withContext(NonCancellable) { firstGate.await() }
                    return entityFor(1L, firstCase)
                }
                return entityFor(2L, secondCase)
            }
        }
        val vm = viewModel(
            client = SuccessClient(),
            consultationRepository = ConsultationRepository(dao),
        )

        vm.loadHistoryDetail(1L)
        dispatcher.scheduler.runCurrent()
        vm.loadHistoryDetail(2L)
        try {
            waitUntil { vm.uiState.value.historyDetail?.recordId == 2L }
        } finally {
            firstGate.complete(Unit)
        }
        advanceUntilIdle()

        assertEquals(2L, vm.uiState.value.historyDetail?.recordId)
        assertEquals(secondCase.title, vm.uiState.value.historyDetail?.caseTitle)
    }

    private fun entityFor(id: Long, medicalCase: com.xjb.wenzhen.data.model.MedicalCase) = ConsultationEntity(
        id = id,
        caseTitle = medicalCase.title,
        department = medicalCase.department,
        difficulty = medicalCase.difficulty.label,
        patientName = medicalCase.patient.name,
        caseJson = CaseJsonCodec.encode(medicalCase),
        conversationJson = "[]",
        medicalRecord = "",
        scoreJson = VALID_SCORE_JSON,
        totalScore = 85,
        initialDiagnosis = medicalCase.correctDiagnosis,
        diagnosisBasis = "依据",
        createdAt = id * 1000L,
    )

    private fun waitUntil(condition: () -> Boolean) {
        repeat(200) {
            dispatcher.scheduler.advanceUntilIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("等待条件超时", condition())
    }

    private class CancellingClient : LlmClient {
        override suspend fun complete(
            config: LlmConfig,
            messages: List<LlmMessage>,
            jsonMode: Boolean,
            temperature: Double,
        ): String = throw kotlinx.coroutines.CancellationException("cancelled")

        override suspend fun testConnection(config: LlmConfig): ConnectionTestResult =
            throw kotlinx.coroutines.CancellationException("cancelled")
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
            awaitCancellation()
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
            awaitCancellation()
        }

        override suspend fun stream(
            config: LlmConfig,
            messages: List<LlmMessage>,
            temperature: Double,
            onDelta: suspend (String) -> Unit,
        ): String = awaitCancellation()
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
