package com.xjb.wenzhen.ui

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xjb.wenzhen.AppContainer
import com.xjb.wenzhen.data.api.ConnectionTestResult
import com.xjb.wenzhen.data.api.LlmClient
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.db.AppDatabase
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
        store.clear()
        db.close()
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
                com.xjb.wenzhen.data.model.ChatMessage(
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
        apiKey = "test-key",
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
