package com.whatsapp.logger.data.repository

import com.whatsapp.logger.data.local.MessageDao
import com.whatsapp.logger.data.local.MessageEntity
import com.whatsapp.logger.data.repository.MessageRepository
import com.whatsapp.logger.data.repository.MessageUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MessageRepositoryImpl(
    private val messageDao: MessageDao
) : MessageRepository {

    override fun getMessagesForChat(chatId: String): Flow<List<MessageUiModel>> {
        return messageDao.getMessagesForChat(chatId).map { entities ->
            entities.map { entityToUiModel(it) }
        }
    }

    override suspend fun getMessagesForChatSync(chatId: String): List<MessageUiModel> = withContext(Dispatchers.IO) {
        messageDao.getMessagesForChatSync(chatId).map { entityToUiModel(it) }
    }

    override suspend fun getMessagesSince(chatId: String, since: Long): List<MessageUiModel> = withContext(Dispatchers.IO) {
        messageDao.getMessagesSince(chatId, since).map { entityToUiModel(it) }
    }

    override suspend fun getMessagesForChats(chatIds: List<String>): List<MessageUiModel> = withContext(Dispatchers.IO) {
        messageDao.getMessagesForChats(chatIds).map { entityToUiModel(it) }
    }

    override suspend fun searchMessages(query: String, onlyDeleted: Boolean): List<MessageUiModel> = withContext(Dispatchers.IO) {
        messageDao.searchAll(query, onlyDeleted).map { entityToUiModel(it) }
    }

    override suspend fun getMaxTimestamp(): Long? = withContext(Dispatchers.IO) {
        messageDao.getMaxTimestamp()
    }

    override suspend fun getMaxTimestampForChat(chatId: String): Long? = withContext(Dispatchers.IO) {
        messageDao.getMaxTimestampForChat(chatId)
    }

    override suspend fun getMessageCount(chatId: String): Long = withContext(Dispatchers.IO) {
        messageDao.getMessageCount(chatId)
    }

    private fun entityToUiModel(entity: MessageEntity): MessageUiModel {
        return MessageUiModel(
            id = entity.id,
            chatId = entity.chatId,
            fromMe = entity.fromMe,
            senderName = entity.senderName,
            text = entity.text,
            timestamp = entity.timestamp,
            type = entity.type,
            deleted = entity.deleted,
            edits = parseEdits(entity.editsJson)
        )
    }

    private val editsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun parseEdits(raw: String?): List<EditUi> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            editsJson.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(com.whatsapp.logger.data.api.model.EditDto.serializer()),
                raw
            ).map { EditUi(it.text, it.ts) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
