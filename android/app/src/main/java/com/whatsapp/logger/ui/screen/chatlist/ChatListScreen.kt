package com.whatsapp.logger.ui.screen.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapp.logger.data.repository.ChatUiModel
import com.whatsapp.logger.data.repository.ResyncState
import com.whatsapp.logger.ui.theme.MessagesPage
import com.whatsapp.logger.ui.theme.SyncStatusPill
import com.whatsapp.logger.viewmodel.ChatListViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onChatClick: (String, String?) -> Unit,
    onSettingsClick: () -> Unit,
    onOpenLogs: () -> Unit = {}
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val hits by viewModel.messageHits.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val resyncState by viewModel.resyncState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }
    val onlyDeleted by viewModel.onlyDeleted.collectAsStateWithLifecycle()
    val allChats by viewModel.filteredChats.collectAsStateWithLifecycle(initialValue = emptyList())
    val chats = if (onlyDeleted) emptyList() else allChats
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    MessagesPage(
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chat Log",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SyncStatusPill(state = syncState, onClick = onOpenLogs, lastSyncedAt = lastSyncedAt)
                }
                // Profile-style button with an accent ring, like Google Messages
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onSettingsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) {
        val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        if (pullState.isRefreshing) {
            LaunchedEffect(true) { viewModel.refreshChats() }
        }
        LaunchedEffect(isRefreshing) {
            if (isRefreshing) pullState.startRefresh() else pullState.endRefresh()
        }
        Box(modifier = Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
            Column(modifier = Modifier.fillMaxSize()) {
                SyncProgressCard(resyncState)

                if (searchOpen) {
                    TextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.setSearchQuery(it)
                        },
                        placeholder = { Text("Search conversations") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.setSearchQuery("")
                                viewModel.setOnlyDeleted(false)
                                searchOpen = false
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close search")
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    androidx.compose.material3.FilterChip(
                        selected = onlyDeleted,
                        onClick = { viewModel.setOnlyDeleted(!onlyDeleted) },
                        label = { Text("Deleted messages only") },
                        leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                    )
                }

                if (chats.isEmpty() && hits.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(bottom = 16.dp)
                            )
                            Text(
                                text = if (onlyDeleted) "No deleted messages yet" else if (searchQuery.isNotBlank()) "No matching chats" else "No chats found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
                    ) {
                        items(chats, key = { it.id }) { chat ->
                            ChatListItem(
                                chat = chat,
                                onClick = { onChatClick(chat.id, null) }
                            )
                        }
                        if (hits.isNotEmpty()) {
                            item(key = "hits_header") {
                                Text(
                                    text = "Messages",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
                                )
                            }
                            items(hits, key = { "hit_" + it.message.id }) { hit ->
                                MessageHitRow(hit, searchQuery, onClick = { onChatClick(hit.chatId, hit.message.id) })
                            }
                        }
                    }
                }
            }

            androidx.compose.material3.pulltorefresh.PullToRefreshContainer(
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            if (!searchOpen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButton(
                        onClick = { searchOpen = true },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    androidx.compose.material3.ExtendedFloatingActionButton(
                        onClick = { viewModel.refreshChats() },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = {
                            val fabFraction = (resyncState as? ResyncState.Running)?.fraction
                            if (isRefreshing && fabFraction != null) {
                                CircularProgressIndicator(
                                    progress = fabFraction,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            }
                        },
                        text = {
                            Text(
                                if (isRefreshing) {
                                    (resyncState as? ResyncState.Running)?.fraction
                                        ?.let { "Syncing ${(it * 100).toInt()}%" } ?: "Syncing..."
                                } else "Sync",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
            )
        }
    }
}

@Composable
fun ChatListItem(
    chat: ChatUiModel,
    onClick: () -> Unit
) {
    val displayName = chat.customName ?: chat.displayName

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatAvatar(
            name = displayName,
            isGroup = chat.isGroup,
            size = 56.dp
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            chat.preview?.let { preview ->
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        chat.lastActive?.let { lastActive ->
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatListDate(lastActive),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Top)
            )
        }
    }
}

private val avatarPalette = listOf(
    Color(0xFFF9A03F) to Color(0xFF3E2200),
    Color(0xFFFF7BB5) to Color(0xFF45002A),
    Color(0xFFEF6C63) to Color(0xFF410002),
    Color(0xFFFFD54A) to Color(0xFF3B2F00),
    Color(0xFF7FD6A5) to Color(0xFF00391C),
    Color(0xFF8AB4F8) to Color(0xFF002F65),
    Color(0xFFC4A7FF) to Color(0xFF2B0A66)
)

@Composable
fun ChatAvatar(
    name: String,
    isGroup: Boolean,
    size: Dp = 40.dp
) {
    val (backgroundColor, contentColor) = avatarPalette[Math.floorMod(name.hashCode(), avatarPalette.size)]
    val initial = name.trim().firstOrNull()?.takeIf { it.isLetter() }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        when {
            isGroup -> Icon(
                imageVector = Icons.Filled.Group,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size * 0.55f)
            )
            initial != null -> Text(
                text = initial.uppercase(),
                fontSize = (size.value * 0.45f).sp,
                color = contentColor,
                fontWeight = FontWeight.Normal
            )
            else -> Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

/** Google Messages style: time for today, weekday for this week, "MMM d" otherwise. */
fun formatListDate(timestamp: Long): String {
    val date = java.util.Date(timestamp * 1000)
    val nowMs = System.currentTimeMillis()
    val diffDays = (nowMs - date.time) / (24L * 60 * 60 * 1000)
    val locale = java.util.Locale.getDefault()
    return when {
        isSameDay(date, java.util.Date(nowMs)) ->
            java.text.SimpleDateFormat("h:mm a", locale).format(date)
        diffDays < 7 -> java.text.SimpleDateFormat("EEE", locale).format(date)
        else -> java.text.SimpleDateFormat("MMM d", locale).format(date)
    }
}

fun isSameDay(date1: java.util.Date, date2: java.util.Date): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { time = date1 }
    val cal2 = java.util.Calendar.getInstance().apply { time = date2 }
    return cal1[java.util.Calendar.YEAR] == cal2[java.util.Calendar.YEAR] &&
           cal1[java.util.Calendar.DAY_OF_YEAR] == cal2[java.util.Calendar.DAY_OF_YEAR]
}

/** Determinate sync progress banner: percent, bar, message count and current chat. */
@Composable
fun SyncProgressCard(state: ResyncState) {
    val running = state as? ResyncState.Running
    var last by remember { mutableStateOf<ResyncState.Running?>(null) }
    if (running != null) last = running

    AnimatedVisibility(
        visible = running != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val r = running ?: last
        if (r != null) {
            val fraction = r.fraction
            val animated by animateFloatAsState(fraction ?: 0f, tween(400), label = "syncProgress")
            val clip = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = r.phase,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (fraction != null) {
                            Text(
                                text = "${(animated * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = animated,
                            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(clip)
                        )
                    } else {
                        LinearProgressIndicator(
                            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(clip)
                        )
                    }
                    if (r.total > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "%,d of %,d messages".format(r.downloaded, r.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (r.currentChat != null) {
                        Text(
                            text = "Chat ${r.chatsDone + 1} of ${r.chatsTotal} \u2022 ${r.currentChat}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun MessageHitRow(
    hit: com.whatsapp.logger.viewmodel.ChatListViewModel.MessageHit,
    query: String,
    onClick: () -> Unit
) {
    val text = hit.message.text.orEmpty()
    val bg = MaterialTheme.colorScheme.primaryContainer
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    val annotated = remember(text, query, bg) {
        androidx.compose.ui.text.buildAnnotatedString {
            val lower = text.lowercase()
            val q = query.trim().lowercase()
            var i = 0
            while (i < text.length) {
                val at = if (q.isEmpty()) -1 else lower.indexOf(q, i)
                if (at < 0) { append(text.substring(i)); break }
                append(text.substring(i, at))
                withStyle(androidx.compose.ui.text.SpanStyle(background = bg, color = fg)) { append(text.substring(at, at + q.length)) }
                i = at + q.length
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatAvatar(name = hit.chatName, isGroup = hit.isGroup, size = 44.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.chatName + " \u2022 " + formatListDate(hit.message.timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
