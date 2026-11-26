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
    fun `旧自定义接口的凭据留在原存储且不会用于官方接口`() {
        prefs.edit()
            .putString("deepseek_api_key", "synthetic-custom-key")
            .putString("deepseek_base_url", "https://custom.example.invalid/v1")
            .putString("deepseek_model", "custom-model")
            .commit()

        val migrated = repository.load()
        assertFalse(migrated.hasApiKey)
        assertTrue(migrated.legacyEndpointNeedsKey)
        assertEquals(AppSettings.DEFAULT_BASE_URL, migrated.baseUrl)
        assertEquals(AppSettings.MODEL_FLASH, migrated.model)

        repository.save(migrated.copy(offlineMode = true, privacyAccepted = true))
        assertEquals("synthetic-custom-key", prefs.getString("deepseek_api_key", null))
        assertEquals("https://custom.example.invalid/v1", prefs.getString("deepseek_base_url", null))
        assertTrue(repository.load().offlineMode)

        repository.save(migrated.copy(apiKey = "synthetic-new-official-key"))
        val reloaded = repository.load()
        assertEquals("synthetic-new-official-key", reloaded.apiKey)
        assertFalse(reloaded.legacyEndpointNeedsKey)
        assertEquals(AppSettings.DEFAULT_BASE_URL, prefs.getString("deepseek_base_url", null))
    }

    @Test
    fun `官方接口旧别名保留已保存的凭据`() {
        prefs.edit()
            .putString("deepseek_api_key", "synthetic-official-key")
            .putString("deepseek_base_url", "https://api.deepseek.com:443/v1/")
            .putString("deepseek_model", "deepseek-v4-pro")
            .commit()

        val restored = repository.load()
        assertEquals("synthetic-official-key", restored.apiKey)
        assertEquals(AppSettings.DEFAULT_BASE_URL, restored.baseUrl)
        assertEquals(AppSettings.MODEL_PRO, restored.model)
        assertFalse(restored.legacyEndpointNeedsKey)
    }

    @Test
    fun `保存设置会清除遗留中转字段`() {
        prefs.edit()
            .putString("api_source", "BUILTIN")
            .putString("session_token", "old-session")
            .putLong("session_expires_at", 1_800_000_000_000L)
            .putString("relay_base_url", "https://old.example.com")
            .putString("device_id", "old-device")
            .commit()

        repository.save(AppSettings(apiKey = "sk-test-key"))

        assertFalse(prefs.contains("api_source"))
        assertFalse(prefs.contains("session_token"))
        assertFalse(prefs.contains("session_expires_at"))
        assertFalse(prefs.contains("relay_base_url"))
        assertFalse(prefs.contains("device_id"))
        assertTrue(repository.load().hasApiKey)
    }

    @Test
    fun `LlmConfig 正确映射个人 Key 配置`() {
        val config = AppSettings(
            apiKey = "sk-own-key",
            model = AppSettings.MODEL_PRO,
            baseUrl = "https://api.deepseek.com/",
        ).toLlmConfig()

        assertEquals("sk-own-key", config.apiKey)
        assertEquals(AppSettings.MODEL_PRO, config.model)
        assertEquals("https://api.deepseek.com/", config.baseUrl)
    }
}
