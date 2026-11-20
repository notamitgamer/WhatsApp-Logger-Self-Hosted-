package com.whatsapp.logger.ui.screen.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.icons.filled.Group
import androidx.compose.material3.icons.filled.Person
import androidx.compose.material3.icons.filled.Refresh
import androidx.compose.material3.icons.filled.Search
import androidx.compose.material3.icons.filled.Settings
import androidx.compose.material.icons.filled.Person as DefaultPerson
import androidx.compose.material.icons.filled.Group as DefaultGroup
import androidx.compose.material.icons.filled.Refresh as DefaultRefresh
import androidx.compose.material.icons.filled.Search as DefaultSearch
import androidx.compose.material.icons.filled.Settings as DefaultSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.logger.R
import com.whatsapp.logger.ui.theme.ChatAvatar
import com.whatsapp.logger.viewmodel.ChatListViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onChatClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val chats by viewModel.filteredChats.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            TopAppBar(
                title = {
                    Text(
                        text = "Chat Log",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                actions = {
                    IconButton(onClick = { viewModel.refreshChats() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )

            // Search Bar
            androidx.compose.material3.TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; viewModel.setSearchQuery(it) },
                label = { Text("Search...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )

            // Chat List
            if (chats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching chats" else "No chats found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(chats) { chat ->
                        ChatListItem(
                            chat = chat,
                            onClick = { onChatClick(chat.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    chat: com.whatsapp.logger.data.repository.ChatUiModel,
    onClick: () -> Unit
) {
    val displayName = chat.customName ?: chat.displayName
    val isGroup = chat.isGroup

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = androidx.compose.foundation.layout.CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            ChatAvatar(
                name = displayName,
                isGroup = isGroup,
                size = 56.dp
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))

            // Chat Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.TextOverflow.Ellipsis
                    )
                    chat.lastActive?.let { lastActive ->
                        Text(
                            text = formatLastActive(lastActive),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                chat.preview?.let { preview ->
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ChatAvatar(
    name: String,
    isGroup: Boolean,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (isGroup) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size * 0.5f)
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

fun formatLastActive(timestamp: Long): String {
    val date = java.util.Date(timestamp * 1000)
    val now = java.util.Date()
    val calendar = java.util.Calendar.getInstance().apply { time = now }
    val today = calendar.time
    calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
    val yesterday = calendar.time

    val sdfToday = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    val sdfDate = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())

    return when {
        isSameDay(date, today) -> "Today ${sdfToday.format(date)}"
        isSameDay(date, yesterday) -> "Yesterday ${sdfToday.format(date)}"
        else -> sdfDate.format(date)
    }
}

fun isSameDay(date1: java.util.Date, date2: java.util.Date): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { time = date1 }
    val cal2 = java.util.Calendar.getInstance().apply { time = date2 }
    return cal1[java.util.Calendar.YEAR] == cal2[java.util.Calendar.YEAR] &&
           cal1[java.util.Calendar.DAY_OF_YEAR] == cal2[java.util.Calendar.DAY_OF_YEAR]
}