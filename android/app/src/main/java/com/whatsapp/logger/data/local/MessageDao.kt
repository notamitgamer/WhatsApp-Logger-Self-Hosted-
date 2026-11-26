package com.whatsapp.logger.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChatSync(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND timestamp > :since ORDER BY timestamp ASC")
    suspend fun getMessagesSince(chatId: String, since: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId IN (:chatIds) ORDER BY timestamp ASC")
    suspend fun getMessagesForChats(chatIds: List<String>): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE (:onlyDeleted = 0 OR deleted = 1) AND text LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 60")
    suspend fun searchAll(query: String, onlyDeleted: Boolean): List<MessageEntity>

    @Query("SELECT MAX(timestamp) FROM messages")
    suspend fun getMaxTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM messages WHERE chatId = :chatId")
    suspend fun getMaxTimestampForChat(chatId: String): Long?

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun getMessageCount(chatId: String): Long
}