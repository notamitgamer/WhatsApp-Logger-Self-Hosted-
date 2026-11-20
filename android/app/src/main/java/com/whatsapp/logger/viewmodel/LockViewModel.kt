package com.whatsapp.logger.viewmodel

import android.content.Context
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
    private val settingsRepository: SettingsRepository,
    private val context: Context
) : ViewModel() {

    sealed interface LockState {
        data class PinEntry(val enteredDigits: Int, val error: Boolean) : LockState
        object BiometricPrompt : LockState
        data class LockedOut(val secondsRemaining: Int) : LockState
        object PermanentLock : LockState
        object Unlocked : LockState
    }

    private val _lockState = MutableStateFlow<LockState>(LockState.PinEntry(0, false))
    val lockState = _lockState.asStateFlow()

    private val _enteredPin = mutableListOf<Int>()
    private var failAttempts = 0
    private var lockUntil = 0L

    private val biometricPrompt by lazy {
        BiometricPrompt(
            context as androidx.fragment.app.FragmentActivity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _lockState.value = LockState.Unlocked
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Biometric failed, fall back to PIN
                }
            }
        )
    }

    init {
        loadLockoutState()
        updateLockState()
    }

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

    fun startBiometricAuth() {
        _lockState.value = LockState.BiometricPrompt
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock WhatsApp Logger")
            .setSubtitle("Use biometric to unlock")
            .setNegativeButtonText("Use PIN")
            .build()
        biometricPrompt.authenticate(promptInfo)
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
            startBiometricAuth()
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
}