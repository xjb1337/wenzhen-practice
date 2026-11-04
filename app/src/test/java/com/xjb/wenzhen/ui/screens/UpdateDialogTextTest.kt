package com.xjb.wenzhen.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UpdateDialogTextTest {
    @Test
    fun `更新说明去除 markdown 标题粗体和列表标记`() {
        val cleaned = cleanUpdateNotes("## 本次更新\n- **修复**闪退问题\n- 优化设置页")

        assertEquals("本次更新\n• 修复闪退问题\n• 优化设置页", cleaned)
        assertFalse(cleaned.contains("#"))
        assertFalse(cleaned.contains("**"))
    }
}
