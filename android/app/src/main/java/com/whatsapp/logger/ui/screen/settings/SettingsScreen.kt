package com.whatsapp.logger.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.icons.filled.ArrowBack
import androidx.compose.material3.icons.filled.DeleteForever
import androidx.compose.material3.icons.filled.Download
import androidx.compose.material3.icons.filled.Fingerprint
import androidx.compose.material3.icons.filled.Info
import androidx.compose.material3.icons.filled.Lock
import androidx.compose.material3.icons.filled.Logout
import androidx.compose.material3.icons.filled.Palette
import androidx.compose.material3.icons.filled.Person
import androidx.compose.material3.icons.filled.Phone
import androidx.compose.material3.icons.filled.Refresh
import androidx.compose.material3.icons.filled.Security
import androidx.compose.material3.icons.filled.Settings
import androidx.compose.material3.icons.filled.Storage
import androidx.compose.material.icons.filled.Info as DefaultInfo
import androidx.compose.material.icons.filled.DeleteForever as DefaultDeleteForever
import androidx.compose.material.icons.filled.Download as DefaultDownload
import androidx.compose.material.icons.filled.Fingerprint as DefaultFingerprint
import androidx.compose.material.icons.filled.Lock as DefaultLock
import androidx.compose.material.icons.filled.Logout as DefaultLogout
import androidx.compose.material.icons.filled.Palette as DefaultPalette
import androidx.compose.material.icons.filled.Person as DefaultPerson
import androidx.compose.material.icons.filled.Phone as DefaultPhone
import androidx.compose.material.icons.filled.Refresh as DefaultRefresh
import androidx.compose.material.icons.filled.Security as DefaultSecurity
import androidx.compose.material.icons.filled.Settings as DefaultSettings
import androidx.compose.material.icons.filled.Storage as DefaultStorage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.logger.data.prefs.AppTheme
import com.whatsapp.logger.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLogout: () -> Unit,
    onChangeBackendUrl: () -> Unit
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val profileName by viewModel.profileName.collectAsStateWithLifecycle()
    val profilePhone by viewModel.profilePhone.collectAsStateWithLifecycle()
    val pinLockEnabled by viewModel.pinLockEnabled.collectAsStateWithLifecycle()
    val bioLockEnabled by viewModel.bioLockEnabled.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var nameText by remember { mutableStateOf(profileName) }
    var phoneText by remember { mutableStateOf(profilePhone) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = { /* handled by nav */ }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Install App Section (if not installed)
                // Card(
                //     modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                //     elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                // ) {
                //     Row(
                //         modifier = Modifier.padding(16.dp),
                //         horizontalArrangement = Arrangement.SpaceBetween,
                //         verticalAlignment = Alignment.CenterVertically
                //     ) {
                //         Column {
                //             Text("Install App", style = MaterialTheme.typography.titleMedium)
                //             Text("Get the app for better experience", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                //         }
                //         Button(onClick = { /* install */ }) { Text("Install") }
                //     }
                // }

                // App Security
                SettingsSection(title = "App Security", icon = Icons.Default.Security) {
                    SettingsRow(
                        title = "PIN Lock",
                        subtitle = "Require PIN on startup",
                        icon = Icons.Default.Lock,
                        trailing = {
                            Switch(
                                checked = pinLockEnabled,
                                onCheckedChange = { viewModel.setPinLockEnabled(it) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    )
                    SettingsRow(
                        title = "Fingerprint Lock",
                        subtitle = "Use biometrics",
                        icon = Icons.Default.Fingerprint,
                        trailing = {
                            Switch(
                                checked = bioLockEnabled,
                                onCheckedChange = { viewModel.setBioLockEnabled(it) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    )
                }

                // Profile
                SettingsSection(title = "Profile", icon = Icons.Default.Person) {
                    SettingsInputRow(
                        title = "Your Name",
                        value = nameText,
                        onValueChange = { nameText = it; viewModel.setProfileName(it) },
                        icon = Icons.Default.Person
                    )
                    SettingsInputRow(
                        title = "Phone Number",
                        value = phoneText,
                        onValueChange = { phoneText = it; viewModel.setProfilePhone(it) },
                        icon = Icons.Default.Phone
                    )
                }

                // Appearance
                SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                    SettingsRow(
                        title = "Theme",
                        subtitle = "Select your preferred color palette",
                        icon = Icons.Default.Palette,
                        trailing = {
                            ThemeSelector(
                                currentTheme = theme,
                                onThemeChange = { viewModel.setTheme(it) }
                            )
                        }
                    )
                    SettingsRow(
                        title = "Text Size",
                        subtitle = "Adjust message font size",
                        icon = Icons.Default.Settings,
                        trailing = {
                            FontSizeSlider(
                                fontSize = fontSize,
                                onSizeChange = { viewModel.setFontSize(it) }
                            )
                        }
                    )
                }

                // Storage and Data
                SettingsSection(title = "Storage and Data", icon = Icons.Default.Storage) {
                    SettingsRow(
                        title = "Local Storage Used",
                        subtitle = storageUsage,
                        icon = Icons.Default.Storage,
                        trailing = {}
                    )
                    SettingsRow(
                        title = "Clear Cache",
                        subtitle = "Delete all messages, cached chats, and media records locally",
                        icon = Icons.Default.DeleteForever,
                        trailing = {
                            Button(
                                onClick = { viewModel.clearCache() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError)
                                } else {
                                    Text("Clear")
                                }
                            }
                        }
                    )
                }

                // System Diagnostics
                SettingsSection(title = "System Diagnostics", icon = Icons.Default.Info) {
                    SettingsRow(
                        title = "Live Server Logs",
                        subtitle = "Real-time events from your connected instance",
                        icon = Icons.Default.Info,
                        trailing = {} // TODO: Add toggle for logs viewer
                    )
                }

                // Help & Support
                SettingsSection(title = "Help & Support", icon = Icons.Default.Info) {
                    SettingsRow(
                        title = "View FAQ",
                        subtitle = "Frequently asked questions",
                        icon = Icons.Default.Info,
                        trailing = { /* TODO: Open FAQ */ }
                    )
                    SettingsRow(
                        title = "View Docs",
                        subtitle = "Documentation and guides",
                        icon = Icons.Default.Download,
                        trailing = { /* TODO: Open docs */ }
                    )
                }

                // Danger Zone
                SettingsSection(title = "Danger Zone", icon = Icons.Default.DeleteForever) {
                    SettingsRow(
                        title = "Change Backend URL",
                        subtitle = "Switch to a different backend server",
                        icon = Icons.Default.Refresh,
                        trailing = {
                            Button(
                                onClick = onChangeBackendUrl,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) { Text("Change") }
                        }
                    )
                    SettingsRow(
                        title = "Log Out",
                        subtitle = "End your session and clear credentials",
                        icon = Icons.Default.Logout,
                        trailing = {
                            Button(
                                onClick = onLogout,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) { Text("Log Out") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: androidx.compose.material3.icons.filled.ImageVector,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).padding(end = 8.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = androidx.compose.foundation.layout.CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.material3.icons.filled.ImageVector,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun SettingsInputRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.material3.icons.filled.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).padding(end = 12.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(title) },
            modifier = Modifier
                .fillMaxWidth()
                .width(0.dp) // Allow text field to take remaining space
        )
    }
}

@Composable
fun ThemeSelector(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppTheme.values().forEach { theme ->
            val isSelected = theme == currentTheme
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .clickable { onThemeChange(theme) },
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = if (isSelected) androidx.compose.foundation.layout.CardDefaults.cardElevation(defaultElevation = 4.dp) else androidx.compose.foundation.layout.CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = theme.label, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                    if (isSelected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FontSizeSlider(
    fontSize: Float,
    onSizeChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "${fontSize.roundToInt()}sp", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp))
        androidx.compose.material3.Slider(
            value = fontSize,
            onValueChange = onSizeChange,
            valueRange = 12f..20f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )
    }
}