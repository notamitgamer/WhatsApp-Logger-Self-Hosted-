package com.whatsapp.logger.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapp.logger.data.repository.ResyncState
import com.whatsapp.logger.ui.theme.MessagesPage
import com.whatsapp.logger.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

enum class GroupPos { Single, Top, Middle, Bottom }

// Shapes are created once. Allocating them per row per recomposition is wasted work.
private val BIG = 20.dp
private val SMALL = 4.dp
private val ShapeSingle = RoundedCornerShape(BIG)
private val ShapeTop = RoundedCornerShape(topStart = BIG, topEnd = BIG, bottomStart = SMALL, bottomEnd = SMALL)
private val ShapeMiddle = RoundedCornerShape(SMALL)
private val ShapeBottom = RoundedCornerShape(topStart = SMALL, topEnd = SMALL, bottomStart = BIG, bottomEnd = BIG)

private fun groupShape(pos: GroupPos): Shape = when (pos) {
    GroupPos.Single -> ShapeSingle
    GroupPos.Top -> ShapeTop
    GroupPos.Middle -> ShapeMiddle
    GroupPos.Bottom -> ShapeBottom
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLogout: () -> Unit,
    onChangeBackendUrl: () -> Unit,
    onBack: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenPage: (String) -> Unit = {}
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    if (showPinDialog) {
        PinSetupDialog(
            onSave = { pin -> viewModel.enablePin(pin); showPinDialog = false },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear cache?") },
            text = { Text("This deletes all messages, cached chats and media records stored on this device. They are downloaded again from your server.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCache()
                    showClearDialog = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    MessagesPage(
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Settings",
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    ) {
        // LazyColumn composes only what is on screen, so the page opens instantly and scrolls
        // smoothly. Each row reads its own state, so a change only recomposes that row.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            item(key = "l_security", contentType = "label") { SectionLabel("App security") }
            item(key = "pin", contentType = "row") {
                PinLockRow(viewModel, onRequestPin = { showPinDialog = true })
            }
            item(key = "bio", contentType = "row") {
                BioLockRow(viewModel, onToggle = { enable -> toggleBio(context, viewModel, enable) })
            }

            item(key = "l_profile", contentType = "label") { SectionLabel("Profile") }
            item(key = "profile_name", contentType = "field") {
                ProfileField("Your name", viewModel.profileName, GroupPos.Top) { viewModel.setProfileName(it) }
            }
            item(key = "profile_phone", contentType = "field") {
                ProfileField("Phone number", viewModel.profilePhone, GroupPos.Bottom) { viewModel.setProfilePhone(it) }
            }

            item(key = "l_storage", contentType = "label") { SectionLabel("Storage and data") }
            item(key = "storage", contentType = "row") { StorageRow(viewModel) }
            item(key = "clear", contentType = "row") {
                ClearCacheRow(viewModel, onClick = { showClearDialog = true })
            }

            item(key = "l_diag", contentType = "label") { SectionLabel("System diagnostics") }
            item(key = "logs", contentType = "row") {
                SettingsItem(
                    title = "Live server logs",
                    subtitle = "Connection status and real-time sync events",
                    pos = GroupPos.Single,
                    onClick = onOpenLogs
                )
            }

            item(key = "l_help", contentType = "label") { SectionLabel("Help and support") }
            item(key = "faq", contentType = "row") {
                SettingsItem(
                    title = "View FAQ",
                    subtitle = "Frequently asked questions",
                    pos = GroupPos.Top,
                    onClick = { uriHandler.openUri("https://docs.amit.is-a.dev/whatsapp-logger/faqs") }
                )
            }
            item(key = "docs", contentType = "row") {
                SettingsItem(
                    title = "View docs",
                    subtitle = "Documentation and guides",
                    pos = GroupPos.Bottom,
                    onClick = { uriHandler.openUri("https://docs.amit.is-a.dev/whatsapp-logger") }
                )
            }

            item(key = "l_about", contentType = "label") { SectionLabel("About") }
            item(key = "server_version", contentType = "row") {
                val serverVersion by viewModel.serverVersion.collectAsStateWithLifecycle()
                SettingsItem(
                    title = "Backend version",
                    subtitle = serverVersion + " \u00b7 tap to refresh",
                    pos = GroupPos.Top,
                    onClick = { viewModel.refreshServerVersion() }
                )
            }
            item(key = "changelog", contentType = "row") {
                SettingsItem(
                    title = "Changelog",
                    subtitle = "What's new, live from GitHub",
                    pos = GroupPos.Middle,
                    onClick = { onOpenPage("changelog") }
                )
            }
            item(key = "bug", contentType = "row") {
                SettingsItem(
                    title = "Report a bug",
                    subtitle = "Open an issue on GitHub",
                    pos = GroupPos.Middle,
                    onClick = { uriHandler.openUri("https://github.com/notamitgamer/whatsapp-logger-self-hosted-/issues/new") }
                )
            }
            item(key = "contributing", contentType = "row") {
                SettingsItem(
                    title = "Contributing",
                    subtitle = "How to help improve the project",
                    pos = GroupPos.Middle,
                    onClick = { onOpenPage("contributing") }
                )
            }
            item(key = "security", contentType = "row") {
                SettingsItem(
                    title = "Security",
                    subtitle = "Security policy and reporting",
                    pos = GroupPos.Middle,
                    onClick = { onOpenPage("security") }
                )
            }
            item(key = "license", contentType = "row") {
                SettingsItem(
                    title = "License",
                    subtitle = "MIT License",
                    pos = GroupPos.Bottom,
                    onClick = { onOpenPage("license") }
                )
            }

            item(key = "l_account", contentType = "label") { SectionLabel("Account") }
            item(key = "backend", contentType = "row") {
                SettingsItem(
                    title = "Change backend URL",
                    subtitle = "Switch to a different backend server",
                    pos = GroupPos.Top,
                    onClick = onChangeBackendUrl
                )
            }
            item(key = "logout", contentType = "row") {
                SettingsItem(
                    title = "Log out",
                    subtitle = "End your session and clear credentials",
                    pos = GroupPos.Bottom,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = onLogout
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Rows. Each one collects only the state it needs.
// ---------------------------------------------------------------------------------------------

@Composable
private fun PinLockRow(viewModel: SettingsViewModel, onRequestPin: () -> Unit) {
    val enabled by viewModel.pinLockEnabled.collectAsStateWithLifecycle()
    SettingsItem(
        title = "PIN lock",
        subtitle = "Require PIN on startup",
        pos = GroupPos.Top,
        trailing = {
            Switch(
                checked = enabled,
                onCheckedChange = { if (it) onRequestPin() else viewModel.setPinLockEnabled(false) }
            )
        },
        onClick = { if (!enabled) onRequestPin() else viewModel.setPinLockEnabled(false) }
    )
}

@Composable
private fun BioLockRow(viewModel: SettingsViewModel, onToggle: (Boolean) -> Unit) {
    val enabled by viewModel.bioLockEnabled.collectAsStateWithLifecycle()
    SettingsItem(
        title = "Fingerprint lock",
        subtitle = "Use biometrics",
        pos = GroupPos.Bottom,
        trailing = { Switch(checked = enabled, onCheckedChange = { onToggle(it) }) },
        onClick = { onToggle(!enabled) }
    )
}

/**
 * Text field that edits local state and saves to encrypted storage only after you pause typing
 * (or leave the screen), instead of encrypting and writing on every keystroke.
 */
@Composable
private fun ProfileField(
    label: String,
    saved: StateFlow<String>,
    pos: GroupPos,
    onCommit: (String) -> Unit
) {
    var text by remember { mutableStateOf(saved.value) }
    val latest by rememberUpdatedState(text)
    val commit by rememberUpdatedState(onCommit)

    LaunchedEffect(text) {
        if (text != saved.value) {
            delay(500)
            commit(text)
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (latest != saved.value) commit(latest) }
    }

    SettingsCard(pos = pos) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StorageRow(viewModel: SettingsViewModel) {
    val usage by viewModel.storageUsage.collectAsStateWithLifecycle()
    SettingsItem(title = "Local storage used", subtitle = usage, pos = GroupPos.Top)
}

@Composable
private fun ClearCacheRow(viewModel: SettingsViewModel, onClick: () -> Unit) {
    val clearing by viewModel.isClearing.collectAsStateWithLifecycle()
    val resync by viewModel.resyncState.collectAsStateWithLifecycle()
    val running = resync as? ResyncState.Running
    val fraction = running?.fraction

    val subtitle = when {
        !clearing -> "Delete all messages, cached chats and media records locally"
        fraction != null -> "Re-downloading from server\u2026 ${(fraction * 100).toInt()}%"
        running != null -> "Re-downloading from server\u2026"
        else -> "Clearing\u2026"
    }

    SettingsItem(
        title = "Clear cache",
        subtitle = subtitle,
        pos = GroupPos.Bottom,
        titleColor = MaterialTheme.colorScheme.error,
        trailing = {
            if (clearing) {
                if (fraction != null) {
                    CircularProgressIndicator(progress = fraction, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
            }
        },
        onClick = if (clearing) null else onClick
    )
}

// ---------------------------------------------------------------------------------------------
// Dialogs and actions
// ---------------------------------------------------------------------------------------------

@Composable
private fun PinSetupDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val valid = pin1.length == 6 && pin1 == pin2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a 6-digit PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin1,
                    onValueChange = { v -> pin1 = v.filter { it.isDigit() }.take(6) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    )
                )
                OutlinedTextField(
                    value = pin2,
                    onValueChange = { v -> pin2 = v.filter { it.isDigit() }.take(6) },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    isError = pin2.isNotEmpty() && pin1 != pin2,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    )
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(pin1) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun toggleBio(context: android.content.Context, viewModel: SettingsViewModel, enable: Boolean) {
    if (!enable) { viewModel.setBioLockEnabled(false); return }
    if (!viewModel.pinLockEnabled.value) {
        android.widget.Toast.makeText(context, "Set a PIN lock first", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    var c: android.content.Context? = context
    while (c is android.content.ContextWrapper && c !is androidx.fragment.app.FragmentActivity) c = c.baseContext
    val activity = c as? androidx.fragment.app.FragmentActivity ?: return
    val auth = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
    if (androidx.biometric.BiometricManager.from(activity).canAuthenticate(auth) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
        android.widget.Toast.makeText(context, "No fingerprint enrolled on this phone", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val prompt = androidx.biometric.BiometricPrompt(
        activity,
        androidx.core.content.ContextCompat.getMainExecutor(activity),
        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                viewModel.setBioLockEnabled(true)
            }
        }
    )
    prompt.authenticate(
        androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm fingerprint")
            .setSubtitle("Touch the sensor to turn on fingerprint lock")
            .setAllowedAuthenticators(auth)
            .setNegativeButtonText("Cancel")
            .build()
    )
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsCard(
    pos: GroupPos,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(groupShape(pos))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        content()
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    pos: GroupPos,
    titleColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    // A plain clipped box is much cheaper than a Surface per row, and it still gets a ripple
    val base = Modifier
        .fillMaxWidth()
        .clip(groupShape(pos))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
