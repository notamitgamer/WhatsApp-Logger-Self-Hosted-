package com.whatsapp.logger.ui.screen.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsapp.logger.data.repository.MessageUiModel
import com.whatsapp.logger.ui.screen.chatlist.ChatAvatar
import com.whatsapp.logger.ui.theme.MessagesPage
import com.whatsapp.logger.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

private const val SEPARATOR_GAP_SECONDS = 60L * 60L
private const val CLUSTER_GAP_SECONDS = 2L * 60L

private fun needsSeparator(prev: MessageUiModel?, cur: MessageUiModel): Boolean {
    if (prev == null) return true
    if (cur.timestamp - prev.timestamp > SEPARATOR_GAP_SECONDS) return true
    return !isSameDay(java.util.Date(prev.timestamp * 1000), java.util.Date(cur.timestamp * 1000))
}

private fun sameCluster(a: MessageUiModel?, b: MessageUiModel?): Boolean {
    if (a == null || b == null) return false
    return a.fromMe == b.fromMe &&
        a.senderName == b.senderName &&
        b.timestamp - a.timestamp < CLUSTER_GAP_SECONDS &&
        !needsSeparator(a, b)
}

private val urlRegex = Regex("(https?://|www\\.)[^\\s<>\"']+", RegexOption.IGNORE_CASE)

/** Trailing punctuation is not part of a link ("see https://x.com/a." or "(https://x.com)"). */
private fun trimUrlEnd(url: String): String {
    var end = url.length
    while (end > 0) {
        val c = url[end - 1]
        val trim = when (c) {
            '.', ',', ';', ':', '!', '?', '\'', '"' -> true
            ')' -> url.count { it == ')' } > url.count { it == '(' }
            ']' -> url.count { it == ']' } > url.count { it == '[' }
            else -> false
        }
        if (!trim) break
        end--
    }
    return url.substring(0, end)
}

/** Message text with every link highlighted and tagged "URL" so a tap can find it. */
private fun buildLinkedText(body: String, linkColor: Color) = buildAnnotatedString {
    var last = 0
    urlRegex.findAll(body).forEach { match ->
        val link = trimUrlEnd(match.value)
        if (link.length < 4) return@forEach
        if (match.range.first > last) append(body.substring(last, match.range.first))
        pushStringAnnotation("URL", link)
        withStyle(
            SpanStyle(
                color = linkColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
                background = linkColor.copy(alpha = 0.14f)
            )
        ) { append(link) }
        pop()
        last = match.range.first + link.length
    }
    if (last < body.length) append(body.substring(last))
}

/** Consecutive messages from the same sender (no date separator between them). */
private fun sameRun(a: MessageUiModel?, b: MessageUiModel?): Boolean {
    if (a == null || b == null) return false
    return a.fromMe == b.fromMe && a.senderName == b.senderName && !needsSeparator(a, b)
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    initialJumpId: String? = null
) {
    val chatInfo by viewModel.chatInfo.collectAsStateWithLifecycle(initialValue = null)
    val messages by viewModel.filteredMessages.collectAsStateWithLifecycle(initialValue = emptyList())
    val showSearch by viewModel.showSearch.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResultCount by viewModel.searchResultCount.collectAsStateWithLifecycle(initialValue = 0)

    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var menuOpen by remember { mutableStateOf(false) }

    // Multi-select: long-press starts it, tap toggles, Back exits
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val selecting = selectedIds.isNotEmpty()
    var pendingLink by remember { mutableStateOf<String?>(null) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val screenContext = androidx.compose.ui.platform.LocalContext.current
    val selectionChatName = chatInfo?.let { it.customName ?: it.displayName } ?: ""
    androidx.activity.compose.BackHandler(enabled = selecting) { selectedIds = emptySet() }
    val showFab by remember {
        derivedStateOf {
            val last = scrollState.layoutInfo.totalItemsCount - 1
            val lastVisible = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last > 0 && lastVisible < last - 2
        }
    }

    // Open the chat at the newest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scrollState.scrollToItem(messages.lastIndex)
    }

    // Jump target chosen from the search results
    var pendingJumpId by remember { mutableStateOf(initialJumpId) }
    androidx.activity.compose.BackHandler(enabled = showSearch) { viewModel.toggleSearch() }

    Box(modifier = Modifier.fillMaxSize()) {
    MessagesPage(
        header = {
            Crossfade(targetState = selecting, label = "chatHeader") { isSelecting ->
            if (isSelecting) {
                SelectionBar(
                    count = selectedIds.size,
                    total = messages.size,
                    onClose = { selectedIds = emptySet() },
                    onSelectAll = { selectedIds = messages.map { it.id }.toSet() },
                    onCopy = {
                        val picked = messages.filter { it.id in selectedIds }
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(buildShareText(picked, selectionChatName)))
                        if (android.os.Build.VERSION.SDK_INT < 33) {
                            android.widget.Toast.makeText(
                                screenContext,
                                if (picked.size == 1) "Message copied" else "${picked.size} messages copied",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        selectedIds = emptySet()
                    },
                    onShare = {
                        val picked = messages.filter { it.id in selectedIds }
                        shareMessageText(screenContext, buildShareText(picked, selectionChatName))
                        selectedIds = emptySet()
                    }
                )
            } else Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                chatInfo?.let { info ->
                    ChatAvatar(
                        name = info.customName ?: info.displayName,
                        isGroup = info.isGroup,
                        size = 40.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = info.customName ?: info.displayName,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search in chat")
                    }
                    ChatOverflowMenu(
                        info = info,
                        messages = messages,
                        onSearch = { viewModel.toggleSearch() },
                        onJumpLatest = {
                            scope.launch { if (messages.isNotEmpty()) scrollState.animateScrollToItem(messages.lastIndex) }
                        },
                        onJumpFirst = {
                            scope.launch { if (messages.isNotEmpty()) scrollState.animateScrollToItem(0) }
                        },
                        onRename = { viewModel.renameChat(it) },
                        onJumpDate = { utcMillis ->
                            // the picker returns UTC midnight; use the same calendar day in local time
                            val day = java.time.Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                            val startSec = day.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
                            val idx = messages.indexOfFirst { it.timestamp >= startSec }
                            scope.launch { scrollState.scrollToItem(if (idx >= 0) idx else messages.lastIndex.coerceAtLeast(0)) }
                        },
                        onExport = {
                            scope.launch { exportChatAsText(appContext, info.customName ?: info.displayName, messages) }
                        }
                    )
                }
            }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showSearch) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search in conversation") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.toggleSearch() }) {
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
                    if (searchResultCount > 0) {
                        Text(
                            text = "$searchResultCount found",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = 16.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    state = scrollState
                ) {
                    itemsIndexed(messages) { index, message ->
                        val prev = messages.getOrNull(index - 1)
                        val next = messages.getOrNull(index + 1)
                        val startsCluster = !sameCluster(prev, message)
                        val endsCluster = !sameCluster(message, next)

                        if (needsSeparator(prev, message)) {
                            DateSeparator(message.timestamp)
                        }
                        MessageBubble(
                            message = message,
                            isMe = message.fromMe,
                            showSenderName = !sameRun(prev, message) && !message.fromMe && chatInfo?.isGroup == true,
                            startsCluster = startsCluster,
                            endsCluster = endsCluster,
                            isGroup = chatInfo?.isGroup == true,
                            startsRun = !sameRun(prev, message),
                            endsRun = !sameRun(message, next),
                            selected = message.id in selectedIds,
                            selectionMode = selecting,
                            onLinkClick = { pendingLink = it },
                            onClick = {
                                if (selecting) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    selectedIds = selectedIds.toggle(message.id)
                                }
                            },
                            onLongClick = {
                                if (!selecting) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                selectedIds = selectedIds.toggle(message.id)
                            }
                        )
                    }
                }
            }

            if (showFab) {
                FloatingActionButton(
                    onClick = { scope.launch { scrollState.animateScrollToItem(messages.lastIndex) } },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Scroll to bottom")
                }
            }
        }
    }
    if (showSearch) {
        ChatSearchOverlay(
            chatName = chatInfo?.let { it.customName ?: it.displayName } ?: "",
            query = searchQuery,
            results = messages,
            onQueryChange = { viewModel.setSearchQuery(it) },
            onResultClick = { msg -> pendingJumpId = msg.id; viewModel.toggleSearch() },
            onClose = { viewModel.toggleSearch() }
        )
    }
    }

    // Declared after the "open at newest" effect so the jump wins
    LaunchedEffect(messages, showSearch, pendingJumpId) {
        val id = pendingJumpId
        if (id != null && !showSearch) {
            val idx = messages.indexOfFirst { it.id == id }
            if (idx >= 0) {
                scrollState.scrollToItem(idx)
                pendingJumpId = null
            }
        }
    }

    pendingLink?.let { link ->
        LinkWarningSheet(url = link, onDismiss = { pendingLink = null })
    }
}

@Composable
private fun DateSeparator(timestamp: Long) {
    Text(
        text = formatSeparator(timestamp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageUiModel,
    isMe: Boolean,
    showSenderName: Boolean,
    startsCluster: Boolean = true,
    endsCluster: Boolean = true,
    isGroup: Boolean = false,
    startsRun: Boolean = true,
    endsRun: Boolean = true,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onLinkClick: (String) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val highlight by animateColorAsState(
        if (selected) colors.primary.copy(alpha = 0.16f) else Color.Transparent,
        label = "selectionHighlight"
    )
    val bubbleColor = if (message.deleted) colors.errorContainer.copy(alpha = 0.55f)
        else if (isMe) colors.primaryContainer else colors.surfaceContainerHigh
    val textColor = if (message.deleted) colors.onErrorContainer
        else if (isMe) colors.onPrimaryContainer else colors.onSurface

    val big = 24.dp
    val small = 6.dp
    val shape = if (isMe) {
        RoundedCornerShape(
            topStart = big,
            topEnd = if (startsCluster) big else small,
            bottomStart = big,
            bottomEnd = if (endsCluster) big else small
        )
    } else {
        RoundedCornerShape(
            topStart = if (startsCluster) big else small,
            topEnd = big,
            bottomStart = if (endsCluster) big else small,
            bottomEnd = big
        )
    }

    val linkColor = if (isMe) colors.onPrimaryContainer else colors.primary
    val body = message.text ?: message.type?.let { "[$it]" } ?: ""
    val annotated = remember(body, linkColor) { buildLinkedText(body, linkColor) }
    var textLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val isLong = body.length > 700
    var expanded by remember(message.id) { mutableStateOf(false) }
    val sender = message.senderName.orEmpty()
    val showAvatar = isGroup && !isMe
    val avatarSlot = 38.dp

    // Each sender gets a stable accent from the dynamic palette
    val accents = listOf(colors.primary, colors.tertiary, colors.secondary)
    val nameColor = accents[Math.floorMod(sender.hashCode(), accents.size)]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (startsRun) 14.dp else if (startsCluster) 6.dp else 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(highlight)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 3.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.align(if (isMe) Alignment.CenterEnd else Alignment.CenterStart),
                verticalAlignment = Alignment.Bottom
            ) {
            if (showAvatar) {
                Box(modifier = Modifier.width(avatarSlot), contentAlignment = Alignment.BottomStart) {
                    if (endsRun) {
                        ChatAvatar(name = sender, isGroup = false, size = 30.dp)
                    }
                }
            }
            Surface(
                color = bubbleColor,
                shape = shape,
                modifier = Modifier
                    .widthIn(max = if (showAvatar) 272.dp else 300.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (showSenderName && sender.isNotEmpty()) {
                    Text(
                        text = sender,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                if (message.deleted) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "  Deleted for everyone",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = colors.error
                        )
                    }
                }
                val isPlaceholder = message.type != null && message.type != "text" && body.startsWith("[---")
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (isPlaceholder) textColor.copy(alpha = 0.8f) else textColor,
                        fontStyle = if (isPlaceholder) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                    ),
                    maxLines = if (isLong && !expanded) 12 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textLayout = it },
                    modifier = Modifier.pointerInput(annotated, selectionMode) {
                        detectTapGestures(
                            onLongPress = { onLongClick() },
                            onTap = { pos ->
                                val layout = textLayout
                                var link: String? = null
                                if (!selectionMode && layout != null) {
                                    val line = layout.getLineForVerticalPosition(pos.y)
                                    // ignore taps on the empty part of a line
                                    if (pos.x >= layout.getLineLeft(line) && pos.x <= layout.getLineRight(line)) {
                                        val offset = layout.getOffsetForPosition(pos)
                                        link = annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.item
                                    }
                                }
                                if (link != null) onLinkClick(link) else onClick()
                            }
                        )
                    }
                )
                message.edits.forEach { edit ->
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        color = textColor.copy(alpha = 0.18f)
                    )
                    Text(
                        text = "Edited \u2022 " + formatMessageTime(edit.ts),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = colors.primary
                    )
                    Text(
                        text = edit.text,
                        style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (isLong) {
                    Text(
                        text = if (expanded) "Show less" else "Read more",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable { expanded = !expanded }
                    )
                }
                }
            }
            }
            // The tick lives in the empty gutter beside the bubble so nothing can clip it
            SelectionBadge(
                visible = selected,
                modifier = Modifier
                    .align(if (isMe) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(start = if (isMe) 6.dp else 0.dp, end = if (isMe) 0.dp else 6.dp)
            )
        }

        if (endsCluster) {
            Text(
                text = formatMessageTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(
                    top = 3.dp,
                    start = if (showAvatar) avatarSlot + 14.dp else 14.dp,
                    end = 14.dp
                )
            )
        }
    }
}

fun formatMessageTime(timestamp: Long): String {
    val date = java.util.Date(timestamp * 1000)
    return java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(date)
}

/** "Today • 7:02 PM", "Wednesday • 7:02 PM", or "Tuesday, Sep 15 • 7:22 PM". */
fun formatSeparator(timestamp: Long): String {
    val date = java.util.Date(timestamp * 1000)
    val locale = java.util.Locale.getDefault()
    val time = java.text.SimpleDateFormat("h:mm a", locale).format(date)
    val nowMs = System.currentTimeMillis()
    val diffDays = (nowMs - date.time) / (24L * 60 * 60 * 1000)
    return when {
        isSameDay(date, java.util.Date(nowMs)) -> "Today \u2022 $time"
        diffDays < 7 -> "${java.text.SimpleDateFormat("EEEE", locale).format(date)} \u2022 $time"
        else -> "${java.text.SimpleDateFormat("EEEE, MMM d", locale).format(date)} \u2022 $time"
    }
}

fun isSameDay(date1: java.util.Date, date2: java.util.Date): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { time = date1 }
    val cal2 = java.util.Calendar.getInstance().apply { time = date2 }
    return cal1[java.util.Calendar.YEAR] == cal2[java.util.Calendar.YEAR] &&
           cal1[java.util.Calendar.DAY_OF_YEAR] == cal2[java.util.Calendar.DAY_OF_YEAR]
}
