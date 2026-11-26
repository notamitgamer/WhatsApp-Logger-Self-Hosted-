package com.whatsapp.logger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsapp.logger.data.ActivityLog
import com.whatsapp.logger.data.repository.ChatRepository
import com.whatsapp.logger.data.repository.ResyncState
import com.whatsapp.logger.data.repository.SyncRepository
import com.whatsapp.logger.data.repository.SyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val syncRepository: SyncRepository,
    private val messageRepository: com.whatsapp.logger.data.repository.MessageRepository
) : ViewModel() {

    data class MessageHit(
        val chatId: String,
        val chatName: String,
        val isGroup: Boolean,
        val message: com.whatsapp.logger.data.repository.MessageUiModel
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // One-off messages shown as a snackbar ("Chats up to date", "Couldn't sync: ...")
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val syncState: StateFlow<SyncState> = syncRepository.syncState
    val resyncState: StateFlow<ResyncState> = syncRepository.resyncState
    val lastSyncedAt: StateFlow<Long> = syncRepository.lastSyncedAt

    val chats = chatRepository.getAllChats()

    val filteredChats = combine(chats, searchQuery) { chats, query ->
        if (query.isBlank()) chats else chats.filter { chat ->
            chat.displayName.lowercase().contains(query.lowercase()) ||
            chat.phoneNumber?.lowercase()?.contains(query.lowercase()) == true
        }
    }.distinctUntilChanged()

    private val _onlyDeleted = MutableStateFlow(false)
    val onlyDeleted: StateFlow<Boolean> = _onlyDeleted
    fun setOnlyDeleted(value: Boolean) { _onlyDeleted.value = value }

    /** Offline search across the text of every stored message. */
    @kotlin.OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messageHits: StateFlow<List<MessageHit>> = combine(
        searchQuery.debounce(250).distinctUntilChanged(),
        chats,
        _onlyDeleted
    ) { q, chatList, onlyDeleted -> Triple(q.trim(), chatList, onlyDeleted) }
        .mapLatest { (q, chatList, onlyDeleted) ->
            if (q.length < 2 && !onlyDeleted) emptyList() else
                messageRepository.searchMessages(q, onlyDeleted).mapNotNull { m ->
                    val chat = chatList.firstOrNull { it.id == m.chatId || m.chatId in it.allIds } ?: return@mapNotNull null
                    MessageHit(chat.id, chat.customName ?: chat.displayName, chat.isGroup, m)
                }
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Gmail-style: open instantly from local data. Only the very first run does a bulk
        // download; after that the live stream (since = newest local message) brings new ones.
        viewModelScope.launch {
            if (messageRepository.getMaxTimestamp() == null) refreshChats(userInitiated = false)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshChats(userInitiated: Boolean = true) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val start = System.currentTimeMillis()

            // If the live stream dropped, a manual sync also reconnects it
            val state = syncRepository.syncState.value
            if (userInitiated && (state is SyncState.Error || state is SyncState.Stopped || state is SyncState.Idle)) {
                ActivityLog.info("Manual sync: reconnecting live stream")
                syncRepository.startGlobalSync(0)
            }

            val result = syncRepository.performFullResync()

            // Keep the progress visible long enough to be noticed
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < 700) delay(700 - elapsed)

            result
                .onSuccess { progress ->
                    if (userInitiated) {
                        _events.tryEmit(
                            if (progress.downloadedMessages == 0L) "Everything is up to date"
                            else "Synced ${"%,d".format(progress.downloadedMessages)} new messages"
                        )
                    }
                }
                .onFailure { e ->
                    _events.tryEmit("Couldn't sync: ${friendlyError(e)}")
                }
            _isRefreshing.value = false
        }
    }
}

internal fun friendlyError(e: Throwable): String = when (e) {
    is java.net.UnknownHostException, is java.net.ConnectException -> "no connection to the server"
    is java.net.SocketTimeoutException -> "server timed out (it may be waking up)"
    else -> e.message ?: "unknown error"
}
