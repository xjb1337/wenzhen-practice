package com.xjb.wenzhen.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    fun load(): AppSettings {
        val prefs = preferences ?: return AppSettings()
        return runCatching {
            AppSettings(
                apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
                model = prefs.getString(KEY_MODEL, AppSettings.MODEL_FLASH)
                    .orEmpty()
                    .ifBlank { AppSettings.MODEL_FLASH },
                baseUrl = prefs.getString(KEY_BASE_URL, AppSettings.DEFAULT_BASE_URL)
                    .orEmpty()
                    .ifBlank { AppSettings.DEFAULT_BASE_URL },
            )
        }.getOrDefault(AppSettings())
    }

    fun save(settings: AppSettings) {
        val prefs = preferences
            ?: throw IllegalStateException("本机加密存储初始化失败，请重启应用后重试。")
        val saved = prefs.edit()
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(
                KEY_MODEL,
                settings.model.trim().ifBlank { AppSettings.MODEL_FLASH },
            )
            .putString(
                KEY_BASE_URL,
                settings.baseUrl.trim().ifBlank { AppSettings.DEFAULT_BASE_URL },
            )
            .commit()
        if (!saved) {
            throw IllegalStateException("设置保存失败，请稍后重试。")
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "wenzhen_secure_settings"
        private const val KEY_API_KEY = "deepseek_api_key"
        private const val KEY_MODEL = "deepseek_model"
        private const val KEY_BASE_URL = "deepseek_base_url"
    }
}
