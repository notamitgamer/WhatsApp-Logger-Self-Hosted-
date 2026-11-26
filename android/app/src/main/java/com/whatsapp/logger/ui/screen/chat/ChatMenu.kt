package com.whatsapp.logger.ui.screen.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.whatsapp.logger.data.repository.ChatUiModel
import com.whatsapp.logger.data.repository.MessageUiModel

/** Google Messages style overflow menu: large rounded popup with plain text rows. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatOverflowMenu(
    info: ChatUiModel,
    messages: List<MessageUiModel>,
    onSearch: () -> Unit,
    onJumpLatest: () -> Unit,
    onJumpFirst: () -> Unit,
    onExport: () -> Unit = {},
    onRename: (String) -> Unit = {},
    onJumpDate: (Long) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }

        // Material3 1.2 DropdownMenu has no shape/colour params, so restyle it through a
        // local theme: big rounded corners and the surfaceContainer colour, no tint.
        val base = MaterialTheme.colorScheme
        MaterialTheme(
            colorScheme = base.copy(surface = base.surfaceContainer, surfaceTint = Color.Transparent),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(28.dp))
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 240.dp),
                offset = DpOffset(0.dp, 4.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                MenuRow("Rename") { expanded = false; renameText = info.customName ?: info.displayName; showRename = true }
                MenuRow("Details") { expanded = false; showDetails = true }
                MenuRow("Search in chat") { expanded = false; onSearch() }
                MenuRow("Jump to latest") { expanded = false; onJumpLatest() }
                MenuRow("Jump to date") { expanded = false; showDatePicker = true }
                MenuRow("Jump to first message") { expanded = false; onJumpFirst() }
                MenuRow("Export chat as text") { expanded = false; onExport() }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        val picked = pickerState.selectedDateMillis
                        showDatePicker = false
                        if (picked != null) onJumpDate(picked)
                    }
                ) { Text("Jump") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            androidx.compose.material3.DatePicker(state = pickerState)
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename chat") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = { showRename = false; onRename(renameText) }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }

    if (showDetails) {
        val name = info.customName ?: info.displayName
        AlertDialog(
            onDismissRequest = { showDetails = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(name) },
            text = {
                Column {
                    DetailRow("Type", if (info.isGroup) "Group chat" else "Direct chat")
                    info.phoneNumber?.takeIf { it.isNotBlank() }?.let { DetailRow("Number", it) }
                    DetailRow("Messages", messages.size.toString())
                    messages.firstOrNull()?.let { DetailRow("First message", formatSeparator(it.timestamp)) }
                    messages.lastOrNull()?.let { DetailRow("Last message", formatSeparator(it.timestamp)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
