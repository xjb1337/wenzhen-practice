package com.xjb.wenzhen.data.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateDownloadTest {
    @Test
    fun `取消已开始的更新下载会中断读取并清理临时包`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody("a".repeat(64 * 1024))
                    .throttleBody(2 * 1024, 250, TimeUnit.MILLISECONDS)
            )
            val context = RuntimeEnvironment.getApplication()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/asset.apk")).build())
            }.build()
            val checker = UpdateChecker(context, client)
            val update = UpdateInfo(
                versionName = "99.0.0",
                versionCode = 99,
                apkUrl = "https://github.com/xjb1337/wenzhen-practice/releases/download/v99.0.0/wenzhen-practice.apk",
                sha256 = "a".repeat(64),
                signingCertSha256 = "b".repeat(64),
                notes = "",
                minSdk = 26,
            )
            val target = File(context.cacheDir, "updates/wenzhen-practice-99.apk")
            val partial = File(target.parentFile, "${target.name}.part")
            val started = CompletableDeferred<Unit>()
            val download = launch {
                checker.downloadApk(update) { progress ->
                    if (progress > 0) started.complete(Unit)
                }
            }

            withTimeout(3_000) { started.await() }
            assertTrue(partial.isFile)
            withTimeout(3_000) { download.cancelAndJoin() }

            assertEquals(1, server.requestCount)
            assertFalse(partial.exists())
            assertFalse(target.exists())
        }
    }
}
