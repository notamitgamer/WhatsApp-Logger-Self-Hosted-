package com.whatsapp.logger.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import android.content.SharedPreferences
import com.whatsapp.logger.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val encryptedPrefs: SharedPreferences
) : SettingsRepository {

    private companion object {
        const val KEY_PROFILE_NAME = "profile_name"
        const val KEY_PROFILE_PHONE = "profile_phone"
        const val KEY_PIN_LOCK = "pin_lock_enabled"
        const val KEY_BIO_LOCK = "bio_lock_enabled"
    }

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