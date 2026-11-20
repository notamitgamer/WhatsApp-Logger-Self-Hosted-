package com.whatsapp.logger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsapp.logger.data.prefs.SettingsRepository
import com.whatsapp.logger.data.prefs.AppTheme
import com.whatsapp.logger.data.prefs.PinRepository
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import com.whatsapp.logger.data.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val pinRepository: PinRepository,
    private val backendConfigRepository: BackendConfigRepository,
    private val authTokenRepository: AuthTokenRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    val theme = settingsRepository.theme.asStateFlow()
    val fontSize = settingsRepository.fontSize.asStateFlow()
    val profileName = settingsRepository.profileName.asStateFlow()
    val profilePhone = settingsRepository.profilePhone.asStateFlow()
    val pinLockEnabled = settingsRepository.pinLockEnabled.asStateFlow()
    val bioLockEnabled = settingsRepository.bioLockEnabled.asStateFlow()

    private val _storageUsage = MutableStateFlow<String>("Calculating...")
    val storageUsage = _storageUsage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        calculateStorageUsage()
    }

    fun setTheme(newTheme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setTheme(newTheme)
        }
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch {
            settingsRepository.setFontSize(size)
        }
    }

    fun setProfileName(name: String) {
        viewModelScope.launch {
            settingsRepository.setProfileName(name)
        }
    }

    fun setProfilePhone(phone: String) {
        viewModelScope.launch {
            settingsRepository.setProfilePhone(phone)
        }
    }

    fun setPinLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // PIN setup will be handled by LockViewModel
            } else {
                settingsRepository.setPinLockEnabled(false)
                pinRepository.clearPin()
            }
        }
    }

    fun setBioLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBioLockEnabled(enabled)
            if (!enabled) {
                // Clear biometric credentials
            }
        }
    }

    fun calculateStorageUsage() {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: Calculate actual storage usage from Room database
            _storageUsage.value = "Calculating..."
            _isLoading.value = false
        }
    }

    suspend fun clearCache(): Result<Unit> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            // This would trigger a full resync via SyncRepository
            // For now, just return success
            _isLoading.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            _isLoading.value = false
            Result.failure(e)
        }
    }

    fun changeBackendUrl() {
        viewModelScope.launch {
            authTokenRepository.clearToken()
            syncRepository.stopSync()
            backendConfigRepository.clearBackendUrl()
            // Navigation to setup screen handled by UI
        }
    }

    fun logout() {
        viewModelScope.launch {
            authTokenRepository.clearToken()
            syncRepository.stopSync()
            // Navigation handled by UI
        }
    }
}