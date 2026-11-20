package com.whatsapp.logger.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.icons.filled.ArrowBack
import androidx.compose.material3.icons.filled.ArrowDownward
import androidx.compose.material3.icons.filled.CalendarMonth
import androidx.compose.material3.icons.filled.Close
import androidx.compose.material3.icons.filled.Download
import androidx.compose.material3.icons.filled.Edit
import androidx.compose.material3.icons.filled.MoreVert
import androidx.compose.material3.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsapp.logger.R
import com.whatsapp.logger.ui.theme.ChatAvatar
import com.whatsapp.logger.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val chatInfo by viewModel.chatInfo.collectAsStateWithLifecycle()
    val messages by viewModel.filteredMessages.collectAsStateWithLifecycle()
    val showSearch by viewModel.showSearch.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResultCount by viewModel.searchResultCount.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val showFab by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            TopAppBar(
                title = {
                    chatInfo?.let { info ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatAvatar(
                                name = info.displayName,
                                isGroup = info.isGroup,
                                size = 40.dp
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = info.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                info.lastActive?.let { lastActive ->
                                    Text(
                                        text = formatLastActive(lastActive),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (chatInfo != null) {
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search in chat")
                        }
                        IconButton(onClick = { /* TODO: Open menu */ }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            // In-chat Search Bar
            if (showSearch) {
                androidx.compose.material3.TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    label = { Text("Find message...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    singleLine = true
                )
                if (searchResultCount > 0) {
                    Text(
                        text = "$searchResultCount found",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, bottom = 8.dp)
                    )
                }
            }

            // Messages
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp, 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = false,
                state = scrollState
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        isMe = message.fromMe,
                        showSenderName = !message.fromMe && chatInfo?.isGroup == true
                    )
                }
            }

            // Scroll to bottom FAB
            if (showFab) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    androidx.compose.material3.FloatingActionButton(
                        onClick = { scrollState.animateScrollTo(0) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to bottom")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: com.whatsapp.logger.data.repository.MessageUiModel,
    isMe: Boolean,
    showSenderName: Boolean
) {
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val alignment = if (isMe) Alignment.End else Alignment.Start
    val shape = if (isMe) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 0.dp, max = 280.dp)
                .padding(horizontal = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = bubbleColor
            ),
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (showSenderName && message.senderName != null) {
                    Text(
                        text = message.senderName!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }

                // Message text with URL highlighting
                Text(
                    text = buildAnnotatedString {
                        message.text?.let { text ->
                            // Simple URL detection
                            val urlRegex = "https?://[^\\s]+".toRegex()
                            var lastEnd = 0
                            urlRegex.findAll(text).forEach { match ->
                                if (match.range.first > lastEnd) {
                                    append(text.substring(lastEnd, match.range.first))
                                }
                                withStyle(style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                )) {
                                    append(match.value)
                                }
                                lastEnd = match.range.last
                            }
                            if (lastEnd < text.length) {
                                append(text.substring(lastEnd))
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(color = textColor)
                )

                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

fun formatMessageTime(timestamp: Long): String {
    val date = java.util.Date(timestamp * 1000)
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
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
        isSameDay(date, today) -> "Today at ${sdfToday.format(date)}"
        isSameDay(date, yesterday) -> "Yesterday at ${sdfToday.format(date)}"
        else -> "${sdfDate.format(date)} at ${sdfToday.format(date)}"
    }
}

fun isSameDay(date1: java.util.Date, date2: java.util.Date): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { time = date1 }
    val cal2 = java.util.Calendar.getInstance().apply { time = date2 }
    return cal1[java.util.Calendar.YEAR] == cal2[java.util.Calendar.YEAR] &&
           cal1[java.util.Calendar.DAY_OF_YEAR] == cal2[java.util.Calendar.DAY_OF_YEAR]
}