package com.whatsapp.logger.data.repository

import com.whatsapp.logger.data.api.model.ChatDto
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllChats(): Flow<List<ChatUiModel>>
    suspend fun refreshChats(): Result<List<ChatUiModel>>
    suspend fun renameChat(chatId: String, customName: String): Result<Unit>
    fun getChat(chatId: String): Flow<ChatUiModel?>
    suspend fun getMaxLastActive(): Long?
}

data class ChatUiModel(
    val id: String,
    val displayName: String,
    val phoneNumber: String?,
    val customName: String?,
    val preview: String?,
    val lastActive: Long?,
    val isGroup: Boolean,
    val allIds: List<String>
)