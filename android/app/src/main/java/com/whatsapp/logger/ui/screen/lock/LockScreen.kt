package com.whatsapp.logger.ui.screen.lock

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.logger.R
import com.whatsapp.logger.viewmodel.LockViewModel
import com.whatsapp.logger.viewmodel.LockViewModel.LockState
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.font.FontWeight
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(
    viewModel: LockViewModel,
    onUnlocked: () -> Unit,
    onForgotPin: () -> Unit = {}
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showForgot by remember { mutableStateOf(false) }
    val doReset: () -> Unit = { scope.launch { viewModel.resetLock(); onForgotPin() } }

    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val activity = rememberFragmentActivity()

    androidx.compose.runtime.LaunchedEffect(lockState) {
        if (lockState is LockState.Unlocked) {
            onUnlocked()
        }
    }

    // Offer the biometric prompt on first show when it is enabled
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (viewModel.biometricEnabled && activity != null) {
            viewModel.startBiometricAuth(activity)
        }
    }

    // Tick the lockout countdown once a second
    if (lockState is LockState.LockedOut) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                viewModel.onLockoutTimerTick()
            }
        }
    }

    if (showForgot) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showForgot = false },
            icon = { Icon(Icons.Filled.LockOpen, contentDescription = null) },
            title = { Text("Reset app lock?") },
            text = { Text("This removes your PIN and fingerprint lock and signs you out. Sign in with your server username and password to continue. Your downloaded messages stay on this phone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showForgot = false; doReset() }) { Text("Reset and sign in") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showForgot = false }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = lockState) {
                    is LockState.PinEntry -> PinEntryScreen(
                        viewModel = viewModel,
                        enteredDigits = state.enteredDigits,
                        error = state.error,
                        onForgot = { showForgot = true }
                    )
                    LockState.BiometricPromptShown -> BiometricPromptScreen(viewModel = viewModel)
                    is LockState.LockedOut -> LockedOutScreen(secondsRemaining = state.secondsRemaining)
                    LockState.PermanentLock -> PermanentLockScreen(onReset = { showForgot = true })
                    LockState.Unlocked -> {}
                }
            }
        }
    }
}

@Composable
fun PinEntryScreen(
    viewModel: LockViewModel,
    enteredDigits: Int,
    error: Boolean,
    onForgot: () -> Unit
) {
    val pinActivity = rememberFragmentActivity()
    val errorColor by animateColorAsState(
        targetValue = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(10.dp))

        // Title
        Text(
            text = if (error) "Wrong PIN" else "Enter PIN",
            style = MaterialTheme.typography.headlineMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))

        Text(
            text = "Protected by App Lock",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(20.dp))

        // PIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            (1..6).forEach { index ->
                val filled = index <= enteredDigits
                val dot by animateFloatAsState(if (filled) 1f else 0.75f, spring(Spring.DampingRatioMediumBouncy), label = "dot")
                Box(
                    modifier = Modifier.size(16.dp)
                        .graphicsLayer { scaleX = dot; scaleY = dot }
                        .clip(CircleShape)
                        .background(if (error) MaterialTheme.colorScheme.error else if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))

        // Keypad
        Keypad(
            onDigitClick = { digit -> viewModel.onDigitEntered(digit) },
            onBackspaceClick = { viewModel.onBackspace() },
            onFingerprintClick = if (viewModel.biometricEnabled && pinActivity != null) {
                { viewModel.startBiometricAuth(pinActivity) }
            } else null
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

        androidx.compose.material3.TextButton(onClick = onForgot) {
            Text("Forgot PIN?", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun Keypad(
    onDigitClick: (Int) -> Unit,
    onBackspaceClick: () -> Unit,
    onFingerprintClick: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (1..3).forEach { digit ->
                KeypadButton(text = digit.toString(), onClick = { onDigitClick(digit) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (4..6).forEach { digit ->
                KeypadButton(text = digit.toString(), onClick = { onDigitClick(digit) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (7..9).forEach { digit ->
                KeypadButton(text = digit.toString(), onClick = { onDigitClick(digit) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (onFingerprintClick != null) {
                KeypadButton(icon = Icons.Filled.Fingerprint, onClick = onFingerprintClick)
            } else {
                KeypadButton(text = "", onClick = {}, enabled = false)
            }
            KeypadButton(text = "0", onClick = { onDigitClick(0) })
            KeypadButton(icon = Icons.Filled.Backspace, onClick = onBackspaceClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeypadButton(
    text: String = "",
    icon: ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    if (!enabled) {
        Box(modifier = Modifier.size(76.dp))
        return
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(76.dp),
        shape = CircleShape,
        color = if (icon != null) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(26.dp))
            } else {
                Text(text = text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal)
            }
        }
    }
}

@Composable
fun BiometricPromptScreen(viewModel: LockViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
        )

        Text(
            text = "Verify Identity",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

        Text(
            text = "Use biometric to unlock",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))

        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun LockedOutScreen(secondsRemaining: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.LockClock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
        )

        Text(
            text = "Locked",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

        Text(
            text = "Too many attempts. Try again in ${secondsRemaining}s",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun PermanentLockScreen(onReset: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.LockClock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
        )

        Text(
            text = "Security Lockout",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

        Text(
            text = "Maximum attempts exceeded. Use server credentials to reset.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(12.dp))
        Button(onClick = onReset) { Text("Reset and sign in") }
    }
}

@Composable
private fun rememberFragmentActivity(): androidx.fragment.app.FragmentActivity? {
    var c: android.content.Context? = androidx.compose.ui.platform.LocalContext.current
    while (c is android.content.ContextWrapper && c !is androidx.fragment.app.FragmentActivity) c = c.baseContext
    return c as? androidx.fragment.app.FragmentActivity
}
