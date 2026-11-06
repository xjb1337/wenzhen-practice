package com.xjb.wenzhen.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.xjb.wenzhen.data.model.ThinkingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("settings-repository-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = SettingsRepository(context, prefs)
    }

    @Test
    fun `API 来源名称和中文标签都能解析`() {
        assertEquals(ApiSource.BUILTIN, ApiSource.from("BUILTIN"))
        assertEquals(ApiSource.BUILTIN, ApiSource.from("内置服务"))
        assertEquals(ApiSource.OWN_KEY, ApiSource.from("OWN_KEY"))
        assertEquals(ApiSource.OWN_KEY, ApiSource.from("unknown"))
    }

    @Test
    fun `旧模型名迁移为新名称`() {
        assertEquals(AppSettings.MODEL_FLASH, normalizeModel("deepseek-v4-flash"))
    }

    @Test
    fun `空模型名回退到默认模型`() {
        assertEquals(AppSettings.MODEL_FLASH, normalizeModel(""))
        assertEquals(AppSettings.MODEL_FLASH, normalizeModel(null))
    }

    @Test
    fun `专业模型名称保持不变`() {
        assertEquals("deepseek-v4-pro", normalizeModel("deepseek-v4-pro"))
    }

    @Test
    fun `HTTP Base URL 会被拒绝`() {
        assertEquals(null, BaseUrlPolicy.normalizeHttps("http://api.test.com"))
    }

    @Test
    fun `HTTPS Base URL 会去掉首尾空白和尾部斜杠`() {
        assertEquals("https://api.test.com", BaseUrlPolicy.normalizeHttps("  https://api.test.com/  "))
    }

    @Test
    fun `空 Base URL 使用默认地址`() {
        assertEquals(AppSettings.DEFAULT_BASE_URL, BaseUrlPolicy.normalizeHttps("   "))
    }

    @Test
    fun `内置服务兑换后有会话令牌才算可用`() {
        assertFalse(AppSettings(apiSource = ApiSource.BUILTIN).hasApiKey)
        assertTrue(
            AppSettings(
                apiSource = ApiSource.BUILTIN,
                sessionToken = "session-token",
            ).hasApiKey,
        )
        assertFalse(AppSettings(apiKey = "sk-own-key", sessionToken = "session-token").let {
            it.copy(apiSource = ApiSource.OWN_KEY, apiKey = "")
        }.hasApiKey)
    }

    @Test
    fun `内置服务会话按过期时间判断可用性`() {
        val now = System.currentTimeMillis()
        val active = AppSettings(
            apiSource = ApiSource.BUILTIN,
            sessionToken = "session-token",
            sessionExpiresAt = now + 60_000L,
        )
        assertTrue(active.hasApiKey)
        assertTrue(active.inviteRedeemed)
        assertFalse(active.isSessionExpired(now))

        val expired = active.copy(sessionExpiresAt = now - 1L)
        assertFalse(expired.hasApiKey)
        assertFalse(expired.inviteRedeemed)
        assertTrue(expired.isSessionExpired(now))

        val noExpiry = active.copy(sessionExpiresAt = null)
        assertTrue(noExpiry.hasApiKey)
        assertTrue(noExpiry.inviteRedeemed)
        assertFalse(noExpiry.isSessionExpired(now))
    }

    @Test
    fun `sessionExpiresAt 往返保存且为空时移除`() {
        val expiresAt = 1_800_000_000_000L
        repository.save(
            AppSettings(
                sessionToken = "session-token",
                sessionExpiresAt = expiresAt,
            ),
        )
        assertEquals(expiresAt, repository.load().sessionExpiresAt)

        repository.save(AppSettings(sessionToken = "session-token", sessionExpiresAt = null))
        assertEquals(null, repository.load().sessionExpiresAt)
        assertFalse(prefs.contains("session_expires_at"))
    }

    @Test
    fun `内置服务配置固定 Flash 并携带设备标识`() {
        val config = AppSettings(
            apiSource = ApiSource.BUILTIN,
            apiKey = "sk-must-not-be-used",
            model = AppSettings.MODEL_PRO,
            baseUrl = "https://api.deepseek.com",
            deviceId = "device-123",
            sessionToken = "session-token",
            relayBaseUrl = "https://relay.example.com/",
            thinkingLevel = ThinkingLevel.MAX,
        ).toLlmConfig()

        assertEquals("session-token", config.apiKey)
        assertEquals(AppSettings.MODEL_FLASH, config.model)
        assertEquals("https://relay.example.com", config.baseUrl)
        assertEquals("device-123", config.deviceId)
        assertEquals(ThinkingLevel.OFF, config.thinkingLevel)
        assertTrue(config.useRelay)
    }

    @Test
    fun `relayBaseUrl 不读取旧持久化值且保存时清除`() {
        prefs.edit().putString("relay_base_url", "https://old.example.com").commit()

        assertEquals(AppSettings.RELAY_BASE_URL, repository.load().relayBaseUrl)

        repository.save(AppSettings(apiKey = "sk-test-key", sessionToken = "session-token"))
        assertFalse(prefs.contains("relay_base_url"))
    }

    @Test
    fun `个人 Key 配置不启用中转`() {
        val config = AppSettings(
            apiKey = "sk-own-key",
            model = AppSettings.MODEL_PRO,
            baseUrl = "https://api.deepseek.com/",
        ).toLlmConfig()

        assertEquals("sk-own-key", config.apiKey)
        assertEquals(AppSettings.MODEL_PRO, config.model)
        assertFalse(config.useRelay)
        assertEquals("", config.deviceId)
    }
}
