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
}
