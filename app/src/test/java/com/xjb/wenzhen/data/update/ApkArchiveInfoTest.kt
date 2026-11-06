package com.xjb.wenzhen.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkArchiveInfoTest {
    @Test
    fun `包名与版本号一致时通过`() {
        assertNull(
            validateArchive(
                expectedPackage = "com.xjb.wenzhen",
                expectedVersion = 14,
                currentVersion = 13,
                actualPackage = "com.xjb.wenzhen",
                actualVersion = 14,
            )
        )
    }

    @Test
    fun `包名不一致被拒绝`() {
        assertEquals(
            "更新包不属于本应用，已停止安装。",
            validateArchive("com.xjb.wenzhen", 14, 13, "com.example.evil", 14),
        )
    }

    @Test
    fun `版本号与更新信息不一致被拒绝`() {
        assertEquals(
            "更新包版本与更新信息不一致，已停止安装。",
            validateArchive("com.xjb.wenzhen", 14, 13, "com.xjb.wenzhen", 12),
        )
        assertEquals(
            "更新包版本与更新信息不一致，已停止安装。",
            validateArchive("com.xjb.wenzhen", 14, 13, "com.xjb.wenzhen", 15),
        )
    }

    @Test
    fun `不高于当前版本的更新包被拒绝`() {
        assertEquals(
            "更新包版本与更新信息不一致，已停止安装。",
            validateArchive("com.xjb.wenzhen", 13, 13, "com.xjb.wenzhen", 13),
        )
        assertEquals(
            "更新包版本与更新信息不一致，已停止安装。",
            validateArchive("com.xjb.wenzhen", 14, 13, "com.xjb.wenzhen", 13),
        )
    }
}
