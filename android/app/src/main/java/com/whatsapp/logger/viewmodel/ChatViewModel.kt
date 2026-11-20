package com.whatsapp.logger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsapp.logger.data.repository.MessageRepository
import com.whatsapp.logger.data.repository.MessageUiModel
import com.whatsapp.logger.data.repository.ChatRepository
import com.whatsapp.logger.data.repository.ChatUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId = _activeChatId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _showSearch = MutableStateFlow(false)
    val showSearch = _showSearch.asStateFlow()

    val chatInfo = _activeChatId.flatMapLatest { chatId ->
        if (chatId == null) {
            emptyFlow<ChatUiModel?>()
        } else {
            chatRepository.getChat(chatId)
        }
    }.distinctUntilChanged()

    val messages = _activeChatId.flatMapLatest { chatId ->
        if (chatId == null) {
            emptyFlow<List<MessageUiModel>>()
        } else {
            messageRepository.getMessagesForChat(chatId)
        }
    }.distinctUntilChanged()

    val filteredMessages = combine(messages, searchQuery) { messages, query ->
        if (query.isBlank()) messages else messages.filter { msg ->
            msg.text?.lowercase().contains(query.lowercase()) == true ||
            msg.senderName.orEmpty().lowercase().contains(query.lowercase())
        }
    }.distinctUntilChanged()

    val searchResultCount = filteredMessages.map { it.size }

    fun openChat(chatId: String) {
        _activeChatId.value = chatId
        _searchQuery.value = ""
        _showSearch.value = false
    }

    fun closeChat() {
        _activeChatId.value = null
        _searchQuery.value = ""
        _showSearch.value = false
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch() {
        _showSearch.value = !_showSearch.value
        if (!_showSearch.value) {
            _searchQuery.value = ""
        }
    }
}