package com.whatsapp.logger.data.prefs

import android.content.SharedPreferences
import com.whatsapp.logger.data.prefs.AppTheme
import com.whatsapp.logger.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val encryptedPrefs: SharedPreferences
) : SettingsRepository {

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_PROFILE_NAME = "profile_name"
        const val KEY_PROFILE_PHONE = "profile_phone"
        const val KEY_PIN_LOCK = "pin_lock_enabled"
        const val KEY_BIO_LOCK = "bio_lock_enabled"
        const val DEFAULT_FONT_SIZE = 15f
    }

    override val theme = MutableStateFlow(
        AppTheme.values().firstOrNull { it.key == encryptedPrefs.getString(KEY_THEME, null) } ?: AppTheme.LATTE
    )

    override val fontSize = MutableStateFlow(
        encryptedPrefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
    )

    override val profileName = MutableStateFlow(
        encryptedPrefs.getString(KEY_PROFILE_NAME, "") ?: ""
    )

    override val profilePhone = MutableStateFlow(
        encryptedPrefs.getString(KEY_PROFILE_PHONE, "") ?: ""
    )

    override val pinLockEnabled = MutableStateFlow(
        encryptedPrefs.getBoolean(KEY_PIN_LOCK, false)
    )

    override val bioLockEnabled = MutableStateFlow(
        encryptedPrefs.getBoolean(KEY_BIO_LOCK, false)
    )

    override suspend fun setTheme(newTheme: AppTheme) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putString(KEY_THEME, newTheme.key).apply()
        theme.value = newTheme
    }

    override suspend fun setFontSize(size: Float) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putFloat(KEY_FONT_SIZE, size).apply()
        fontSize.value = size
    }

    override suspend fun setProfileName(name: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putString(KEY_PROFILE_NAME, name).apply()
        profileName.value = name
    }

    override suspend fun setProfilePhone(phone: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putString(KEY_PROFILE_PHONE, phone).apply()
        profilePhone.value = phone
    }

    override suspend fun setPinLockEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putBoolean(KEY_PIN_LOCK, enabled).apply()
        pinLockEnabled.value = enabled
    }

    override suspend fun setBioLockEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putBoolean(KEY_BIO_LOCK, enabled).apply()
        bioLockEnabled.value = enabled
    }
}