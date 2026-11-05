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
import com.xjb.wenzhen.data.db.ConversationJsonCodec
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.LlmMessage
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
