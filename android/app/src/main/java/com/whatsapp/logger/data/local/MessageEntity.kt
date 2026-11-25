package com.whatsapp.logger.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["timestamp"]),
        Index(value = ["chatId", "timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val fromMe: Boolean,
    val senderName: String?,
    val text: String?,
    val timestamp: Long,
    val type: String?,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    val editsJson: String? = null
)