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
