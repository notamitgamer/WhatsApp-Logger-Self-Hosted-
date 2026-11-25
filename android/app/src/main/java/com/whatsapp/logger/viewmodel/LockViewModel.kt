package com.whatsapp.logger.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsapp.logger.data.prefs.PinRepository
import com.whatsapp.logger.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LockViewModel(
    private val pinRepository: PinRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    sealed interface LockState {
        data class PinEntry(val enteredDigits: Int, val error: Boolean) : LockState
        object BiometricPromptShown : LockState
        data class LockedOut(val secondsRemaining: Int) : LockState
        object PermanentLock : LockState
        object Unlocked : LockState
    }

    private val _lockState = MutableStateFlow<LockState>(LockState.PinEntry(0, false))
    val lockState = _lockState.asStateFlow()

    private val _enteredPin = mutableListOf<Int>()
    private var failAttempts = 0
    private var lockUntil = 0L

    private fun createBiometricPrompt(activity: FragmentActivity): BiometricPrompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _lockState.value = LockState.Unlocked
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Biometric failed or was cancelled, fall back to PIN
                    _lockState.value = LockState.PinEntry(0, false)
                }
            }
        )

    init {
        loadLockoutState()
        updateLockState()
    }

    val biometricEnabled: Boolean get() = settingsRepository.bioLockEnabled.value

    private fun loadLockoutState() {
        // Load from preferences
    }

    fun onDigitEntered(digit: Int) {
        if (_enteredPin.size < 6) {
            _enteredPin.add(digit)
            _lockState.value = LockState.PinEntry(_enteredPin.size, false)
            if (_enteredPin.size == 6) {
                verifyPin()
            }
        }
    }

    fun onBackspace() {
        if (_enteredPin.isNotEmpty()) {
            _enteredPin.removeAt(_enteredPin.lastIndex)
            _lockState.value = LockState.PinEntry(_enteredPin.size, false)
        }
    }

    private fun verifyPin() {
        val pin = _enteredPin.joinToString("")
        if (pinRepository.verifyPin(pin)) {
            _enteredPin.clear()
            failAttempts = 0
            lockUntil = 0
            _lockState.value = LockState.Unlocked
        } else {
            handleFailedAttempt()
        }
    }

    private fun handleFailedAttempt() {
        failAttempts++
        _enteredPin.clear()
        if (failAttempts >= 3 && failAttempts <= 4) {
            lockUntil = System.currentTimeMillis() + 30_000
            _lockState.value = LockState.LockedOut(30)
            // Start countdown timer
        } else if (failAttempts >= 5) {
            _lockState.value = LockState.PermanentLock
        } else {
            _lockState.value = LockState.PinEntry(0, true)
        }
    }

    fun startBiometricAuth(activity: FragmentActivity) {
        val can = androidx.biometric.BiometricManager.from(activity).canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (can != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            _lockState.value = LockState.PinEntry(0, false)
            return
        }
        _lockState.value = LockState.BiometricPromptShown
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock WhatsApp Logger")
            .setSubtitle("Use biometric to unlock")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText("Use PIN")
            .build()
        createBiometricPrompt(activity).authenticate(promptInfo)
    }

    fun onLockoutTimerTick() {
        val remaining = (lockUntil - System.currentTimeMillis()) / 1000
        if (remaining <= 0) {
            failAttempts = 0
            lockUntil = 0
            updateLockState()
        } else {
            _lockState.value = LockState.LockedOut(remaining.toInt())
        }
    }

    private fun updateLockState() {
        val hasPin = pinRepository.hasPin()
        val useBio = settingsRepository.bioLockEnabled.value
        if (hasPin) {
            _lockState.value = LockState.PinEntry(0, false)
        } else if (useBio) {
            // The UI starts the biometric prompt because it needs an Activity
            _lockState.value = LockState.PinEntry(0, false)
        } else {
            _lockState.value = LockState.Unlocked
        }
    }

    suspend fun setupPin(pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        pinRepository.setPin(pin)
        settingsRepository.setPinLockEnabled(true)
        Result.success(Unit)
    }

    suspend fun clearPin(): Result<Unit> = withContext(Dispatchers.IO) {
        pinRepository.clearPin()
        settingsRepository.setPinLockEnabled(false)
        Result.success(Unit)
    }

    /** Forgot PIN: wipe the local lock so the user can sign in again with server credentials. */
    suspend fun resetLock() = withContext(Dispatchers.IO) {
        pinRepository.clearPin()
        settingsRepository.setPinLockEnabled(false)
        settingsRepository.setBioLockEnabled(false)
    }
}
