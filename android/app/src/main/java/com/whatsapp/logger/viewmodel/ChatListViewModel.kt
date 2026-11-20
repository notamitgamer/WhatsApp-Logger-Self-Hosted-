package com.whatsapp.logger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsapp.logger.data.repository.ChatRepository
import com.whatsapp.logger.data.repository.ChatUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val chats = chatRepository.getAllChats()

    val filteredChats = combine(chats, searchQuery) { chats, query ->
        if (query.isBlank()) chats else chats.filter { chat ->
            chat.displayName.lowercase().contains(query.lowercase()) ||
            chat.phoneNumber.orEmpty().lowercase().contains(query.lowercase())
        }
    }.distinctUntilChanged()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshChats() {
        viewModelScope.launch {
            chatRepository.refreshChats()
        }
    }
}