package com.whatsapp.logger.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow

interface SettingsRepository {
    val profileName: MutableStateFlow<String>
    val profilePhone: MutableStateFlow<String>
    val pinLockEnabled: MutableStateFlow<Boolean>
    val bioLockEnabled: MutableStateFlow<Boolean>

    suspend fun setProfileName(name: String)
    suspend fun setProfilePhone(phone: String)
    suspend fun setPinLockEnabled(enabled: Boolean)
    suspend fun setBioLockEnabled(enabled: Boolean)
}
