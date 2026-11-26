package com.whatsapp.logger.data.api.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerLogDto(
    val timestamp: Long,
    val level: String,
    val message: String
)

@Serializable
data class VerifyRequest(
    val username: String,
    val password: String
)

@Serializable
data class VerifyResponse(
    val success: Boolean,
    val token: String? = null
)

@Serializable
data class ChatDto(
    val id: String,
    val phoneNumber: String? = null,
    val displayName: String? = null,
    val customName: String? = null,
    val preview: String? = null,
    val lastActive: Long? = null,
    val ids: List<String>? = null,
    val subIds: List<String>? = null
)

@Serializable
data class MessageDto(
    val id: String,
    val chatId: String? = null,
    val fromMe: Boolean,
    val senderName: String? = null,
    val text: String? = null,
    val timestamp: Long,
    val type: String? = null,
    val deleted: Boolean = false,
    val viewOnce: Boolean = false,
    val edits: List<EditDto>? = null
)

@Serializable
data class EditDto(
    val text: String = "",
    val ts: Long = 0L
)

@Serializable
data class ExportChatsResponse(
    val chats: List<ExportChatDto>,
    val totalMessages: Long
)

@Serializable
data class ExportChatDto(
    val id: String,
    val phoneNumber: String? = null,
    val displayName: String? = null,
    val customName: String? = null,
    val preview: String? = null,
    val lastActive: Long? = null,
    val messageCount: Long
)

@Serializable
data class ExportMessagesResponse(
    val chatId: String,
    val messages: List<MessageDto>
)

@Serializable
data class FullExportResponse(
    val chats: Map<String, ChatDto>,
    val messages: Map<String, List<MessageDto>>
)

@Serializable
data class RenameRequest(
    val id: String,
    val customName: String
)

@Serializable
data class RenameResponse(
    val success: Boolean
)

@Serializable
data class ChatChange(
    val type: String, // "added", "modified", "removed"
    val doc: ChatDto
)

@Serializable
data class HealthDto(val version: String? = null, val whatsappConnected: Boolean? = null)

@Serializable
data class SyncMessageChange(
    val type: String, // "added", "modified", "removed"
    val doc: MessageDto
)