package com.xjb.wenzhen.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {
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
}
