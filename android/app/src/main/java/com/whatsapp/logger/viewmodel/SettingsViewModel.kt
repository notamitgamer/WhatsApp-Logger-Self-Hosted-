package com.whatsapp.logger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsapp.logger.data.prefs.SettingsRepository
import com.whatsapp.logger.data.prefs.PinRepository
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import com.whatsapp.logger.data.repository.ResyncState
import com.whatsapp.logger.data.repository.SyncRepository
import kotlinx.coroutines.flow.StateFlow
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
    private val syncRepository: SyncRepository,
    private val appContext: android.content.Context,
    private val apiService: com.whatsapp.logger.data.api.ApiService
) : ViewModel() {

    val profileName = settingsRepository.profileName.asStateFlow()
    val profilePhone = settingsRepository.profilePhone.asStateFlow()
    val pinLockEnabled = settingsRepository.pinLockEnabled.asStateFlow()
    val bioLockEnabled = settingsRepository.bioLockEnabled.asStateFlow()

    private val _storageUsage = MutableStateFlow<String>("Calculating...")
    val storageUsage = _storageUsage.asStateFlow()

    // Only true while "Clear cache" is running (storage calculation no longer flashes a spinner)
    private val _isClearing = MutableStateFlow(false)
    val isClearing: StateFlow<Boolean> = _isClearing.asStateFlow()

    val resyncState: StateFlow<ResyncState> = syncRepository.resyncState

    // Version of the backend the app is talking to ("Checking...", "v4.5.5" or "Unreachable")
    private val _serverVersion = MutableStateFlow("Checking...")
    val serverVersion: StateFlow<String> = _serverVersion.asStateFlow()

    init {
        calculateStorageUsage()
        refreshServerVersion()
    }

    fun refreshServerVersion() {
        viewModelScope.launch(Dispatchers.IO) {
            val token = authTokenRepository.getToken()
            if (token == null) { _serverVersion.value = "Not signed in"; return@launch }
            _serverVersion.value = try {
                val r = apiService.getHealth("Bearer $token").execute()
                val v = r.body()?.version
                if (r.isSuccessful && v != null) "v$v" else "Unavailable (HTTP ${r.code()})"
            } catch (e: Exception) {
                "Unreachable"
            }
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
            if (!enabled) {
                settingsRepository.setBioLockEnabled(false)
                settingsRepository.setPinLockEnabled(false)
                pinRepository.clearPin()
            }
        }
    }

    fun enablePin(pin: String) {
        viewModelScope.launch {
            pinRepository.setPin(pin)
            settingsRepository.setPinLockEnabled(true)
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
            val text = withContext(Dispatchers.IO) {
                val db = appContext.getDatabasePath("whatsapp_logger.db")
                val bytes = listOf("", "-wal", "-shm").sumOf { suffix ->
                    java.io.File(db.path + suffix).takeIf { it.exists() }?.length() ?: 0L
                }
                android.text.format.Formatter.formatShortFileSize(appContext, bytes) + " on this device"
            }
            _storageUsage.value = text
        }
    }

    fun clearCache() {
        if (_isClearing.value) return
        viewModelScope.launch {
            _isClearing.value = true
            try {
                syncRepository.clearLocalData()
                calculateStorageUsage()
                // Re-download everything from the server (progress is shown on the row)
                syncRepository.performFullResync()
            } finally {
                _isClearing.value = false
                calculateStorageUsage()
            }
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