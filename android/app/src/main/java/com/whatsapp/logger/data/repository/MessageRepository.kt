package com.whatsapp.logger.data.repository

import com.whatsapp.logger.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesForChat(chatId: String): Flow<List<MessageUiModel>>
    suspend fun getMessagesForChatSync(chatId: String): List<MessageUiModel>
    suspend fun getMessagesSince(chatId: String, since: Long): List<MessageUiModel>
    suspend fun getMessagesForChats(chatIds: List<String>): List<MessageUiModel>
    suspend fun searchMessages(query: String, onlyDeleted: Boolean = false): List<MessageUiModel>
    suspend fun getMaxTimestamp(): Long?
    suspend fun getMaxTimestampForChat(chatId: String): Long?
    suspend fun getMessageCount(chatId: String): Long
}

data class MessageUiModel(
    val id: String,
    val chatId: String,
    val fromMe: Boolean,
    val senderName: String?,
    val text: String?,
    val timestamp: Long,
    val type: String?,
    val deleted: Boolean = false,
    val edits: List<EditUi> = emptyList()
)

data class EditUi(val text: String, val ts: Long)