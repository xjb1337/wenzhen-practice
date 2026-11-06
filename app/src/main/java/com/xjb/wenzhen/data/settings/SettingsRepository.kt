package com.xjb.wenzhen.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.xjb.wenzhen.data.model.ThinkingLevel
import com.xjb.wenzhen.ui.theme.AppTheme
import java.util.UUID

internal fun normalizeModel(name: String?): String {
    val normalized = name?.trim().orEmpty()
    return when {
        normalized.isBlank() -> AppSettings.MODEL_FLASH
        normalized.equals("deepseek-v4-flash", ignoreCase = true) -> AppSettings.MODEL_FLASH
        else -> normalized
    }
}

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    /** 设备标识不加密保存：即使加密存储损坏或重装 Keystore，也不会让中转额度“换设备”。 */
    private val deviceIdPreferences = appContext.getSharedPreferences(
        DEVICE_ID_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    /**
     * 延迟初始化：避免 Application.onCreate / AppContainer 构造时在主线程加载 Keystore。
     * security-crypto 1.1.0 仍提供 EncryptedSharedPreferences（API 已标弃用），
     * 这里集中封装，后续替换实现不影响调用方。
     */
    @Suppress("DEPRECATION")
    private val preferences: SharedPreferences? by lazy {
        runCatching {
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
    }

    fun load(): AppSettings {
        val stableDeviceId = stableDeviceId()
        val prefs = preferences ?: return AppSettings(deviceId = stableDeviceId)
        return runCatching {
            val loaded = AppSettings(
                apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
                model = normalizeModel(prefs.getString(KEY_MODEL, AppSettings.MODEL_FLASH)),
                baseUrl = prefs.getString(KEY_BASE_URL, AppSettings.DEFAULT_BASE_URL)
                    .orEmpty()
                    .ifBlank { AppSettings.DEFAULT_BASE_URL },
                thinkingLevel = ThinkingLevel.from(prefs.getString(KEY_THINKING_LEVEL, null)),
                generateRecord = prefs.getBoolean(KEY_GENERATE_RECORD, false),
                offlineMode = prefs.getBoolean(KEY_OFFLINE_MODE, false),
                theme = AppTheme.from(prefs.getString(KEY_THEME, null)),
                apiSource = ApiSource.from(prefs.getString(KEY_API_SOURCE, null)),
                deviceId = prefs.getString(KEY_DEVICE_ID, "").orEmpty().ifBlank { stableDeviceId },
                sessionToken = prefs.getString(KEY_SESSION_TOKEN, "").orEmpty(),
                relayBaseUrl = prefs.getString(KEY_RELAY_BASE_URL, AppSettings.RELAY_BASE_URL)
                    .orEmpty()
                    .ifBlank { AppSettings.RELAY_BASE_URL },
                privacyAccepted = prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false),
            )
            if (prefs.getString(KEY_DEVICE_ID, "").orEmpty().isBlank()) {
                prefs.edit().putString(KEY_DEVICE_ID, stableDeviceId).apply()
            }
            loaded
        }.getOrDefault(AppSettings(deviceId = stableDeviceId))
    }

    fun save(settings: AppSettings) {
        val prefs = preferences
            ?: throw IllegalStateException("本机加密存储初始化失败，请重启应用后重试。")
        val deviceId = settings.deviceId.trim().ifBlank { stableDeviceId() }
        deviceIdPreferences.edit().putString(KEY_STABLE_DEVICE_ID, deviceId).apply()
        val saved = prefs.edit()
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(
                KEY_MODEL,
                normalizeModel(settings.model),
            )
            .putString(KEY_THINKING_LEVEL, settings.thinkingLevel.name)
            .putBoolean(KEY_GENERATE_RECORD, settings.generateRecord)
            .putBoolean(KEY_OFFLINE_MODE, settings.offlineMode)
            .putString(KEY_THEME, settings.theme.name)
            .putString(
                KEY_BASE_URL,
                settings.baseUrl.trim().ifBlank { AppSettings.DEFAULT_BASE_URL },
            )
            .putString(KEY_API_SOURCE, settings.apiSource.name)
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_SESSION_TOKEN, settings.sessionToken.trim())
            .putString(
                KEY_RELAY_BASE_URL,
                settings.relayBaseUrl.trim().ifBlank { AppSettings.RELAY_BASE_URL },
            )
            .putBoolean(KEY_PRIVACY_ACCEPTED, settings.privacyAccepted)
            .commit()
        if (!saved) {
            throw IllegalStateException("设置保存失败，请稍后重试。")
        }
    }

    private fun stableDeviceId(): String {
        deviceIdPreferences.getString(KEY_STABLE_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val generated = UUID.randomUUID().toString()
        deviceIdPreferences.edit().putString(KEY_STABLE_DEVICE_ID, generated).commit()
        return generated
    }

    companion object {
        private const val PREFERENCES_NAME = "wenzhen_secure_settings"
        private const val DEVICE_ID_PREFERENCES_NAME = "wenzhen_plain_settings"
        private const val KEY_STABLE_DEVICE_ID = "stable_device_id"
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
        private const val KEY_RELAY_BASE_URL = "relay_base_url"
        private const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
    }
}
