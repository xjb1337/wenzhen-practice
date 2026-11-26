package com.xjb.wenzhen.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {
    private val hash = "a".repeat(64)

    @Test
    fun `更新清单字段完整解析`() {
        val info = UpdateInfo.parse(
            """
            {
              "versionName": "1.2.0",
              "versionCode": 12,
              "apkUrl": "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.2.0/wenzhen-practice.apk",
              "sha256": "$hash",
              "signingCertSha256": "${"B".repeat(64)}",
              "notes": "新增统计与更新功能",
              "minSdk": 26
            }
            """.trimIndent()
        )

        assertEquals("1.2.0", info.versionName)
        assertEquals(12, info.versionCode)
        assertEquals(hash, info.sha256)
        assertEquals("b".repeat(64), info.signingCertSha256)
        assertEquals("新增统计与更新功能", info.notes)
        assertEquals(26, info.minSdk)
    }

    @Test
    fun `非法哈希和下载地址被拒绝`() {
        assertThrows(UpdateParseException::class.java) {
            UpdateInfo.parse(validJson(sha = "abc"))
        }
        assertThrows(UpdateParseException::class.java) {
            UpdateInfo.parse(validJson(url = "http://example.com/app.apk"))
        }
    }

    @Test
    fun `只有更高的 versionCode 才会提示安装更新`() {
        val update = UpdateInfo.parse(validJson(name = "99.0.0", code = 12))

        assertTrue(update.isNewerThan(11))
        assertFalse(update.isNewerThan(12))
        assertFalse(update.isNewerThan(13))
    }

    private fun validJson(
        name: String = "1.2.0",
        code: Int = 12,
        url: String = "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.2.0/wenzhen-practice.apk",
        sha: String = hash,
    ): String = """
        {
          "versionName": "$name",
          "versionCode": $code,
          "apkUrl": "$url",
          "sha256": "$sha",
          "signingCertSha256": "$hash",
          "notes": "更新",
          "minSdk": 26
        }
    """.trimIndent()
}
