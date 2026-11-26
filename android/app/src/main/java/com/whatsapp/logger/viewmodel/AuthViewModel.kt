package com.whatsapp.logger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsapp.logger.data.api.AuthApiService
import com.whatsapp.logger.data.api.model.VerifyRequest
import com.whatsapp.logger.data.api.model.VerifyResponse
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import com.whatsapp.logger.data.prefs.PinRepository
import com.whatsapp.logger.data.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class AuthViewModel(
    private val authApiService: AuthApiService,
    private val authTokenRepository: AuthTokenRepository,
    private val backendConfigRepository: BackendConfigRepository,
    private val pinRepository: PinRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    sealed interface AuthState {
        data class Checking(val hasBackendUrl: Boolean, val hasToken: Boolean) : AuthState
        data class SetupRequired(val hasBackendUrl: Boolean) : AuthState
        data class LoginRequired(val backendUrl: String) : AuthState
        data class LockRequired(val hasPin: Boolean, val hasBio: Boolean) : AuthState
        data class Authenticated(val backendUrl: String) : AuthState
        data class Error(val message: String) : AuthState
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking(false, false))
    val authState = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun initializeApp() {
        viewModelScope.launch {
            _isLoading.value = true
            val hasBackendUrl = backendConfigRepository.hasBackendUrl()
            val hasToken = authTokenRepository.hasValidToken()
            _authState.value = AuthState.Checking(hasBackendUrl, hasToken)

            if (!hasBackendUrl) {
                _authState.value = AuthState.SetupRequired(false)
            } else if (!hasToken) {
                val backendUrl = backendConfigRepository.getBackendUrl() ?: ""
                _authState.value = AuthState.LoginRequired(backendUrl)
            } else {
                if (pinRepository.hasPin()) {
                    _authState.value = AuthState.LockRequired(true, false)
                } else {
                    _authState.value = AuthState.Authenticated(backendConfigRepository.getBackendUrl() ?: "")
                    syncRepository.startGlobalSync(0)
                }
            }
            _isLoading.value = false
        }
    }

    suspend fun verifyCredentials(username: String, password: String): Result<VerifyResponse> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            val response = authApiService.verifyCredentials(VerifyRequest(username, password)).execute()
            if (response.isSuccessful && response.body()?.success == true) {
                val token = response.body()!!.token!!
                authTokenRepository.setToken(token)
                // Token expiry: 3 days if remember, 1 hour otherwise - for now use 3 days
                authTokenRepository.setAuthExpiry(System.currentTimeMillis() + (3 * 24 * 60 * 60 * 1000))
                _isLoading.value = false
                Result.success(response.body()!!)
            } else {
                _isLoading.value = false
                Result.failure(Exception("Invalid credentials"))
            }
        } catch (e: Exception) {
            _isLoading.value = false
            Result.failure(e)
        }
    }

    fun onLoginSuccess() {
        val backendUrl = backendConfigRepository.getBackendUrl() ?: ""
        val hasPin = pinRepository.hasPin()
        if (hasPin) {
            _authState.value = AuthState.LockRequired(true, false)
        } else {
            _authState.value = AuthState.Authenticated(backendUrl)
            syncRepository.startGlobalSync(0)
        }
    }

    fun onLockUnlocked() {
        val backendUrl = backendConfigRepository.getBackendUrl() ?: ""
        _authState.value = AuthState.Authenticated(backendUrl)
        syncRepository.startGlobalSync(0)
    }

    fun changeBackend() {
        viewModelScope.launch {
            authTokenRepository.clearToken()
            syncRepository.stopSync()
            _authState.value = AuthState.SetupRequired(true)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authTokenRepository.clearToken()
            syncRepository.stopSync()
            val hasBackendUrl = backendConfigRepository.hasBackendUrl()
            _authState.value = if (hasBackendUrl) {
                AuthState.LoginRequired(backendConfigRepository.getBackendUrl() ?: "")
            } else {
                AuthState.SetupRequired(false)
            }
        }
    }
}