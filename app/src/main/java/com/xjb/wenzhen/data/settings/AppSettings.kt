package com.xjb.wenzhen.data.settings

import com.xjb.wenzhen.data.model.LlmConfig

data class AppSettings(
    val apiKey: String = "",
    val model: String = MODEL_FLASH,
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()

    fun toLlmConfig(): LlmConfig = LlmConfig(
        apiKey = apiKey.trim(),
        model = model.ifBlank { MODEL_FLASH }.trim(),
        baseUrl = baseUrl.ifBlank { DEFAULT_BASE_URL }.trim(),
    )

    companion object {
        const val MODEL_FLASH = "deepseek-v4-flash"
        const val MODEL_PRO = "deepseek-v4-pro"
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    }
}
