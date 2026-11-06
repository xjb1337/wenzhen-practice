package com.xjb.wenzhen.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val validUrl =
        "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.3.8/wenzhen-practice.apk"

    @Test
    fun `合法的更新包地址通过`() {
        assertTrue(isTrustedApkUrl(validUrl))
    }

    @Test
    fun `路径穿越与编码绕过被拒绝`() {
        assertFalse(
            isTrustedApkUrl(
                "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.3.8/" +
                    "../../../evil/wenzhen-practice.apk"
            )
        )
        assertFalse(
            isTrustedApkUrl(
                "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.3.8/" +
                    "%2e%2e/wenzhen-practice.apk"
            )
        )
        assertFalse(
            isTrustedApkUrl(
                "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.3.8/" +
                    "%2E%2E/wenzhen-practice.apk"
            )
        )
    }

    @Test
    fun `非 https 或非 github 域名被拒绝`() {
        assertFalse(
            isTrustedApkUrl(
                "http://github.com/xjb1337/wenzhen-practice/releases/download/v1.3.8/" +
                    "wenzhen-practice.apk"
            )
        )
        assertFalse(
            isTrustedApkUrl(
                "https://github.com.evil.com/xjb1337/wenzhen-practice/releases/download/v1.3.8/" +
                    "wenzhen-practice.apk"
            )
        )
        assertFalse(
            isTrustedApkUrl(
                "https://github.com:8443/xjb1337/wenzhen-practice/releases/download/v1.3.8/" +
                    "wenzhen-practice.apk"
            )
        )
    }

    @Test
    fun `用户信息 query 和 fragment 被拒绝`() {
        assertFalse(
            isTrustedApkUrl(
                "https://user@github.com/xjb1337/wenzhen-practice/releases/download/v1.3.8/" +
                    "wenzhen-practice.apk"
            )
        )
        assertFalse(isTrustedApkUrl("$validUrl?download=1"))
        assertFalse(isTrustedApkUrl("$validUrl#fragment"))
    }

    @Test
    fun `文件名或 tag 不符合约定被拒绝`() {
        assertFalse(
            isTrustedApkUrl(
                "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.3.8/evil.apk"
            )
        )
        assertFalse(
            isTrustedApkUrl(
                "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.3/" +
                    "wenzhen-practice.apk"
            )
        )
        assertFalse(
            isTrustedApkUrl(
                "https://github.com/xjb1337/wenzhen-practice/releases/download/latest/" +
                    "wenzhen-practice.apk"
            )
        )
        assertFalse(
            isTrustedApkUrl(
                "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.3.8.0/" +
                    "wenzhen-practice.apk"
            )
        )
    }

    @Test
    fun `多余的路径段被拒绝`() {
        assertFalse(
            isTrustedApkUrl(
                "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.3.8/extra/" +
                    "wenzhen-practice.apk"
            )
        )
        assertFalse(
            isTrustedApkUrl(
                "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.3.8"
            )
        )
    }
}
