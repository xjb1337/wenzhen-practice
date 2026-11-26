package com.xjb.wenzhen.data.settings

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.ThinkingLevel
import com.xjb.wenzhen.ui.theme.AppTheme
import java.net.URI

data class AppSettings(
    val apiKey: String = "",
    val model: String = MODEL_FLASH,
    val baseUrl: String = DEFAULT_BASE_URL,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val generateRecord: Boolean = false,
    val offlineMode: Boolean = false,
    val theme: AppTheme = AppTheme.DEFAULT,
    /** 首启隐私与 AI 使用告知是否已确认。 */
    val privacyAccepted: Boolean = false,
    val legacyEndpointNeedsKey: Boolean = false,
) {
    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()

    val aiSetupMessage: String
        get() = if (legacyEndpointNeedsKey) {
            "旧版 API Key 关联自定义接口，请先在设置中填写 DeepSeek 官方 API Key。"
        } else {
            "请先在设置中填写 DeepSeek API Key。"
        }

    fun toLlmConfig(): LlmConfig = LlmConfig(
        apiKey = apiKey.trim(),
        model = model.ifBlank { MODEL_FLASH }.trim(),
        baseUrl = baseUrl.ifBlank { DEFAULT_BASE_URL }.trim(),
        thinkingLevel = thinkingLevel,
    )

    companion object {
        const val MODEL_FLASH = "deepseek-flash"
        const val MODEL_PRO = "deepseek-v4-pro"
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val OFFLINE_AI_MESSAGE = "当前为离线练习。请在设置中关闭离线练习后使用 AI 服务。"
    }
}

object BaseUrlPolicy {
    fun isOfficialEndpoint(value: String): Boolean {
        val normalized = normalizeHttps(value) ?: return false
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("api.deepseek.com", ignoreCase = true) &&
            uri.port in listOf(-1, 443) &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.orEmpty().trimEnd('/') in listOf("", "/v1")
    }

    fun normalizeHttps(value: String): String? {
        val normalized = value.trim().ifBlank { AppSettings.DEFAULT_BASE_URL }.trimEnd('/')
        return normalized.takeIf { it.startsWith("https://", ignoreCase = true) }
    }
}
