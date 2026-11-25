package com.whatsapp.logger.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["lastActive"]),
        Index(value = ["phoneNumber"])
    ]
)
data class ChatEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String?,
    val displayName: String?,
    val customName: String?,
    val preview: String?,
    val lastActive: Long?,
    val isGroup: Boolean
)