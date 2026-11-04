package com.xjb.wenzhen.data.settings

import com.xjb.wenzhen.data.model.LlmConfig
import com.xjb.wenzhen.data.model.ThinkingLevel

data class AppSettings(
    val apiKey: String = "",
    val model: String = MODEL_FLASH,
    val baseUrl: String = DEFAULT_BASE_URL,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val generateRecord: Boolean = false,
    val offlineMode: Boolean = false,
) {
    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()

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
    }
}

object BaseUrlPolicy {
    fun normalizeHttps(value: String): String? {
        val normalized = value.trim().ifBlank { AppSettings.DEFAULT_BASE_URL }.trimEnd('/')
        return normalized.takeIf { it.startsWith("https://", ignoreCase = true) }
    }
}
