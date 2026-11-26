package com.xjb.wenzhen.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.xjb.wenzhen.data.model.ThinkingLevel
import com.xjb.wenzhen.ui.theme.AppTheme

internal fun normalizeModel(name: String?): String {
    val normalized = name?.trim().orEmpty()
    return if (normalized.equals(AppSettings.MODEL_PRO, ignoreCase = true)) {
        AppSettings.MODEL_PRO
    } else {
        AppSettings.MODEL_FLASH
    }
}

class SettingsRepository(
    context: Context,
    private val preferencesOverride: SharedPreferences? = null,
) {
    private val appContext = context.applicationContext

    private var openedPreferences: SharedPreferences? = null

    /** 只缓存成功打开的存储，以便设备解锁后可以重试。 */
    @Synchronized
    @Suppress("DEPRECATION")
    private fun preferences(): SharedPreferences {
        preferencesOverride?.let { return it }
        openedPreferences?.let { return it }
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { openedPreferences = it }
    }

    fun load(): AppSettings {
        val prefs = preferences()
        val storedKey = prefs.getString(KEY_API_KEY, "").orEmpty().trim()
        val storedEndpoint = prefs.getString(KEY_BASE_URL, AppSettings.DEFAULT_BASE_URL).orEmpty()
        val legacyEndpointNeedsKey = storedKey.isNotBlank() && !BaseUrlPolicy.isOfficialEndpoint(storedEndpoint)
        return AppSettings(
            apiKey = storedKey.takeUnless { legacyEndpointNeedsKey }.orEmpty(),
            model = normalizeModel(prefs.getString(KEY_MODEL, AppSettings.MODEL_FLASH)),
            thinkingLevel = ThinkingLevel.from(prefs.getString(KEY_THINKING_LEVEL, null)),
            generateRecord = prefs.getBoolean(KEY_GENERATE_RECORD, false),
            offlineMode = prefs.getBoolean(KEY_OFFLINE_MODE, false),
            theme = AppTheme.from(prefs.getString(KEY_THEME, null)),
            privacyAccepted = prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false),
            legacyEndpointNeedsKey = legacyEndpointNeedsKey,
        )
    }

    fun save(settings: AppSettings) {
        val prefs = preferences()
        val editor = prefs.edit()
        // 修改主题等普通设置时保留旧接口的凭据。用户保存新的官方 Key 后再替换。
        if (!settings.legacyEndpointNeedsKey || settings.apiKey.isNotBlank()) {
            editor.putString(KEY_API_KEY, settings.apiKey.trim())
                .putString(KEY_BASE_URL, AppSettings.DEFAULT_BASE_URL)
        }
        val saved = editor
            .putString(KEY_MODEL, normalizeModel(settings.model))
            .putString(KEY_THINKING_LEVEL, settings.thinkingLevel.name)
            .putBoolean(KEY_GENERATE_RECORD, settings.generateRecord)
            .putBoolean(KEY_OFFLINE_MODE, settings.offlineMode)
            .putString(KEY_THEME, settings.theme.name)
            .remove(KEY_API_SOURCE)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_SESSION_EXPIRES_AT)
            .remove(KEY_RELAY_BASE_URL)
            .putBoolean(KEY_PRIVACY_ACCEPTED, settings.privacyAccepted)
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
        private const val KEY_THINKING_LEVEL = "thinking_level"
        private const val KEY_GENERATE_RECORD = "generate_record"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        private const val KEY_THEME = "app_theme"
        private const val KEY_API_SOURCE = "api_source"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SESSION_EXPIRES_AT = "session_expires_at"
        private const val KEY_RELAY_BASE_URL = "relay_base_url"
        private const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
    }
}
