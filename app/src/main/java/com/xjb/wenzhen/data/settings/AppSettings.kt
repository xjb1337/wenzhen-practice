package com.xjb.wenzhen.data.settings

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.ThinkingLevel
import com.xjb.wenzhen.ui.theme.AppTheme

enum class ApiSource(val label: String) {
    OWN_KEY("自己的 Key"),
    BUILTIN("内置服务");

    companion object {
        fun from(value: String?): ApiSource = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true) || it.label == value?.trim()
        } ?: OWN_KEY
    }
}

data class AppSettings(
    val apiKey: String = "",
    val model: String = MODEL_FLASH,
    val baseUrl: String = DEFAULT_BASE_URL,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val generateRecord: Boolean = false,
    val offlineMode: Boolean = false,
    val theme: AppTheme = AppTheme.DEFAULT,
    val apiSource: ApiSource = ApiSource.OWN_KEY,
    val deviceId: String = "",
    val sessionToken: String = "",
    val relayBaseUrl: String = RELAY_BASE_URL,
) {
    /** 历史上用于 AI 能力门禁；两种服务模式任一可用的凭据都会返回 true。 */
    val hasApiKey: Boolean
        get() = when (apiSource) {
            ApiSource.OWN_KEY -> apiKey.isNotBlank()
            ApiSource.BUILTIN -> sessionToken.isNotBlank()
        }

    val inviteRedeemed: Boolean
        get() = sessionToken.isNotBlank()

    val aiSetupMessage: String
        get() = when (apiSource) {
            ApiSource.OWN_KEY -> "请先在设置中填写 DeepSeek API Key。"
            ApiSource.BUILTIN -> "请先在设置中兑换邀请码。"
        }

    fun toLlmConfig(): LlmConfig = when (apiSource) {
        ApiSource.OWN_KEY -> LlmConfig(
            apiKey = apiKey.trim(),
            model = model.ifBlank { MODEL_FLASH }.trim(),
            baseUrl = baseUrl.ifBlank { DEFAULT_BASE_URL }.trim(),
            thinkingLevel = thinkingLevel,
        )
        ApiSource.BUILTIN -> LlmConfig(
            apiKey = sessionToken.trim(),
            model = MODEL_FLASH,
            baseUrl = relayBaseUrl.trim().ifBlank { RELAY_BASE_URL }.trimEnd('/'),
            thinkingLevel = thinkingLevel,
            deviceId = deviceId.trim(),
            useRelay = true,
        )
    }

    companion object {
        const val MODEL_FLASH = "deepseek-flash"
        const val MODEL_PRO = "deepseek-v4-pro"
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        // Worker 部署后替换此处（或后续迁到 BuildConfig）。说明见 docs/RELAY.md。
        const val RELAY_BASE_URL = "https://wenzhen-relay.wenzhen-xjb1337.workers.dev"
    }
}

object BaseUrlPolicy {
    fun normalizeHttps(value: String): String? {
        val normalized = value.trim().ifBlank { AppSettings.DEFAULT_BASE_URL }.trimEnd('/')
        return normalized.takeIf { it.startsWith("https://", ignoreCase = true) }
    }
}
