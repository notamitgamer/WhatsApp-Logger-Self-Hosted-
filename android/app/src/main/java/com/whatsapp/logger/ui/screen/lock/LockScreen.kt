package com.whatsapp.logger.ui.screen.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateFloatAsState
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
import androidx.compose.material3.icons.filled.Backspace
import androidx.compose.material3.icons.filled.Fingerprint
import androidx.compose.material3.icons.filled.Lock
import androidx.compose.material3.icons.filled.LockClock
import androidx.compose.material3.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.logger.R
import com.whatsapp.logger.viewmodel.LockViewModel
import com.whatsapp.logger.viewmodel.LockViewModel.LockState
import androidx.compose.material3.icons.filled.Lock
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(
    viewModel: LockViewModel,
    onUnlocked: () -> Unit
) {
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(lockState) {
        if (lockState is LockState.Unlocked) {
            onUnlocked()
        }
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
                when (lockState) {
                    is LockState.PinEntry -> PinEntryScreen(
                        viewModel = viewModel,
                        enteredDigits = lockState.enteredDigits,
                        error = lockState.error
                    )
                    is LockState.BiometricPrompt -> BiometricPromptScreen(viewModel = viewModel)
                    is LockState.LockedOut -> LockedOutScreen(secondsRemaining = lockState.secondsRemaining)
                    is LockState.PermanentLock -> PermanentLockScreen()
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
    error: Boolean
) {
    val errorColor by animateColorAsState(
        targetValue = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp).padding(bottom = 16.dp)
        )

        // Title
        Text(
            text = "Enter PIN",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

        Text(
            text = "Protected by App Lock",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(32.dp))

        // PIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            (1..6).forEach { index ->
                val filled = index <= enteredDigits
                Box(
                    modifier = Modifier.size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) MaterialTheme.colorScheme.primary else errorColor)
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(32.dp))

        // Keypad
        Keypad(
            onDigitClick = { digit -> viewModel.onDigitEntered(digit) },
            onBackspaceClick = { viewModel.onBackspace() }
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))

        // Forgot PIN
        Text(
            text = "Forgot PIN? Use server credentials to reset",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .wrapContentWidth(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun Keypad(
    onDigitClick: (Int) -> Unit,
    onBackspaceClick: () -> Unit
) {
    Column {
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
            KeypadButton(text = "", onClick = {}, enabled = false)
            KeypadButton(text = "0", onClick = { onDigitClick(0) })
            KeypadButton(icon = Icons.Default.Backspace, onClick = onBackspaceClick)
        }
    }
}

@Composable
fun KeypadButton(
    text: String = "",
    icon: ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val colors = androidx.compose.material3.ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    Button(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        enabled = enabled,
        colors = colors,
        shape = CircleShape
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        } else {
            Text(text = text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun BiometricPromptScreen(viewModel: LockViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
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
            imageVector = Icons.Default.LockClock,
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
            textAlign = androidx.compose.ui.text.TextAlign.Center
        )
    }
}

@Composable
fun PermanentLockScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.LockClock,
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
            textAlign = androidx.compose.ui.text.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}