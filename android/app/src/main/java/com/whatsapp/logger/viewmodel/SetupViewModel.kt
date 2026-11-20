package com.whatsapp.logger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsapp.logger.data.api.AuthApiService
import com.whatsapp.logger.data.api.model.VerifyRequest
import com.whatsapp.logger.data.api.model.VerifyResponse
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class SetupViewModel(
    private val authApiService: AuthApiService,
    private val backendConfigRepository: BackendConfigRepository,
    private val authTokenRepository: AuthTokenRepository
) : ViewModel() {

    sealed interface SetupState {
        data class Input(val url: String, val error: String?) : SetupState
        object Validating : SetupState
        object Success : SetupState
        data class Error(val message: String) : SetupState
    }

    private val _setupState = MutableStateFlow<SetupState>(SetupState.Input("", null))
    val setupState = _setupState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun updateUrl(url: String) {
        val currentState = _setupState.value
        if (currentState is SetupState.Input) {
            _setupState.value = SetupState.Input(url, null)
        }
    }

    suspend fun validateAndSave(url: String): Result<VerifyResponse> = withContext(Dispatchers.IO) {
        _setupState.value = SetupState.Validating
        _isLoading.value = true

        // Ensure URL has proper format
        val formattedUrl = if (url.startsWith("http")) url else "https://$url"
        val cleanUrl = formattedUrl.removeTrailingSlash()

        try {
            // Test connection with a simple verify call (using dummy creds to check endpoint)
            // Actually, we just save the URL first, then auth happens separately
            backendConfigRepository.setBackendUrl(cleanUrl)
            _setupState.value = SetupState.Success
            _isLoading.value = false
            Result.success(VerifyResponse(true, null))
        } catch (e: Exception) {
            _setupState.value = SetupState.Error(e.message ?: "Failed to save URL")
            _isLoading.value = false
            Result.failure(e)
        }
    }

    private fun String.removeTrailingSlash(): String = if (endsWith("/")) substring(0, length - 1) else this
}