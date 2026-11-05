package com.xjb.wenzhen.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
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
    fun `内置服务配置固定 Flash 并携带设备标识`() {
        val config = AppSettings(
            apiSource = ApiSource.BUILTIN,
            apiKey = "sk-must-not-be-used",
            model = AppSettings.MODEL_PRO,
            baseUrl = "https://api.deepseek.com",
            deviceId = "device-123",
            sessionToken = "session-token",
            relayBaseUrl = "https://relay.example.com/",
        ).toLlmConfig()

        assertEquals("session-token", config.apiKey)
        assertEquals(AppSettings.MODEL_FLASH, config.model)
        assertEquals("https://relay.example.com", config.baseUrl)
        assertEquals("device-123", config.deviceId)
        assertTrue(config.useRelay)
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
