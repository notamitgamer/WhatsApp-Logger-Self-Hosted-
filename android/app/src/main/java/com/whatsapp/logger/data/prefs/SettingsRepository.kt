package com.whatsapp.logger.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow

interface SettingsRepository {
    val theme: MutableStateFlow<AppTheme>
    val fontSize: MutableStateFlow<Float>
    val profileName: MutableStateFlow<String>
    val profilePhone: MutableStateFlow<String>
    val pinLockEnabled: MutableStateFlow<Boolean>
    val bioLockEnabled: MutableStateFlow<Boolean>

    suspend fun setTheme(theme: AppTheme)
    suspend fun setFontSize(size: Float)
    suspend fun setProfileName(name: String)
    suspend fun setProfilePhone(phone: String)
    suspend fun setPinLockEnabled(enabled: Boolean)
    suspend fun setBioLockEnabled(enabled: Boolean)
}

enum class AppTheme(val key: String, val label: String) {
    LATTE("latte", "Latte (Light)"),
    MOCHA("mocha", "Mocha (Dark)"),
    BLACK("black", "Amoled (True Black)")
}