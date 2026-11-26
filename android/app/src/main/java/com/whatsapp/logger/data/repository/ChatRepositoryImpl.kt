package com.whatsapp.logger.data.repository

import com.whatsapp.logger.data.api.ApiService
import com.whatsapp.logger.data.api.model.ChatChange
import com.whatsapp.logger.data.api.model.ChatDto
import com.whatsapp.logger.data.local.ChatDao
import com.whatsapp.logger.data.local.ChatEntity
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import com.whatsapp.logger.data.repository.ChatRepository
import com.whatsapp.logger.data.repository.ChatUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ChatRepositoryImpl(
    private val chatDao: ChatDao,
    private val apiService: ApiService,
    private val authTokenRepository: AuthTokenRepository,
    private val backendConfigRepository: BackendConfigRepository
) : ChatRepository {

    private val chatGroupsCache = ConcurrentHashMap<String, ChatUiModel>()

    override fun getAllChats(): Flow<List<ChatUiModel>> {
        return chatDao.getAllChats().map { entities ->
            entities.map { entityToUiModel(it) }.also { list ->
                // Update cache
                chatGroupsCache.clear()
                list.forEach { chatGroupsCache[it.id] = it }
            }
        }
    }

    override suspend fun refreshChats(): Result<List<ChatUiModel>> = withContext(Dispatchers.IO) {
        val token = authTokenRepository.getToken() ?: return@withContext Result.failure(Exception("No auth token"))

        try {
            val response = apiService.getExportChats(token).execute()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    chatDao.insertAll(body.chats.map {
                        ChatEntity(
                            id = it.id,
                            phoneNumber = it.phoneNumber,
                            displayName = it.displayName,
                            customName = it.customName,
                            preview = it.preview,
                            lastActive = it.lastActive,
                            isGroup = it.id.endsWith("@g.us")
                        )
                    })
                }
            } else {
                return@withContext Result.failure(
                    Exception(if (response.code() == 401) "session expired, sign in again" else "server returned ${response.code()}")
                )
            }
            Result.success(chatDao.getAllChatsSync().map { entityToUiModel(it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameChat(chatId: String, customName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authTokenRepository.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        try {
            val response = apiService.renameChat(
                "Bearer $token",
                com.whatsapp.logger.data.api.model.RenameRequest(chatId, customName)
            ).execute()
            if (response.isSuccessful) {
                chatDao.updateCustomName(chatId, customName)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Rename failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getChat(chatId: String): Flow<ChatUiModel?> {
        return chatDao.observeChat(chatId).map { entity ->
            entity?.let { entityToUiModel(it) }
        }
    }

    override suspend fun getMaxLastActive(): Long? = withContext(Dispatchers.IO) {
        chatDao.getMaxLastActive()
    }

    private fun entityToUiModel(entity: ChatEntity): ChatUiModel {
        val allIds = mutableListOf(entity.id)
        // In a full implementation, we'd fetch related IDs from the group mapping
        return ChatUiModel(
            id = entity.id,
            displayName = friendlyName(entity.displayName ?: entity.phoneNumber ?: entity.id, entity.isGroup),
            phoneNumber = entity.phoneNumber,
            customName = entity.customName,
            preview = entity.preview,
            lastActive = entity.lastActive,
            isGroup = entity.isGroup,
            allIds = allIds
        )
    }

    /** Raw WhatsApp ids are not names; turn them into something readable. */
    private fun friendlyName(raw: String, isGroup: Boolean): String {
        val m = Regex("^(\\d+)@(lid|g\\.us|newsletter|s\\.whatsapp\\.net|broadcast)$").find(raw) ?: return raw
        val digits = m.groupValues[1]
        return when (m.groupValues[2]) {
            "s.whatsapp.net" -> "+$digits"
            "g.us" -> "Unnamed group"
            "newsletter" -> "Channel"
            "broadcast" -> "Broadcast list"
            else -> if (isGroup) "Unnamed group" else "Unknown contact \u2022 " + digits.takeLast(4)
        }
    }
}
