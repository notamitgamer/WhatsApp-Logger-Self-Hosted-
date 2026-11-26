package com.whatsapp.logger.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chat: ChatEntity)

    @Query(
        "SELECT c.id AS id, c.phoneNumber AS phoneNumber, c.displayName AS displayName, c.customName AS customName, " +
        "COALESCE((SELECT m.text FROM messages m WHERE m.chatId = c.id ORDER BY m.timestamp DESC LIMIT 1), c.preview) AS preview, " +
        "COALESCE((SELECT MAX(m.timestamp) FROM messages m WHERE m.chatId = c.id), c.lastActive) AS lastActive, " +
        "c.isGroup AS isGroup FROM chats c ORDER BY (lastActive IS NULL), lastActive DESC"
    )
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY (lastActive IS NULL), lastActive DESC")
    suspend fun getAllChatsSync(): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChat(chatId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun observeChat(chatId: String): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE phoneNumber = :phoneNumber")
    fun getChatsByPhone(phoneNumber: String): Flow<List<ChatEntity>>

    @Query("DELETE FROM chats")
    suspend fun clearAll()

    @Query("UPDATE chats SET lastActive = :lastActive, preview = :preview WHERE id = :chatId")
    suspend fun updateLastActiveAndPreview(chatId: String, lastActive: Long, preview: String?)

    @Query("UPDATE chats SET customName = :customName WHERE id = :chatId")
    suspend fun updateCustomName(chatId: String, customName: String)

    @Query("SELECT MAX(lastActive) FROM chats")
    suspend fun getMaxLastActive(): Long?

    @Query("SELECT * FROM chats WHERE lastActive > :since ORDER BY lastActive DESC")
    suspend fun getChatsSince(since: Long): List<ChatEntity>
}