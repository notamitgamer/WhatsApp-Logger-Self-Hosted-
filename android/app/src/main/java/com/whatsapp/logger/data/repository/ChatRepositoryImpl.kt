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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
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
            entities.map { it -> entityToUiModel(it) }.also { list ->
                // Update cache
                chatGroupsCache.clear()
                list.forEach { chatGroupsCache[it.id] = it }
            }
        }
    }

    override suspend fun refreshChats(): Result<List<ChatUiModel>> = withContext(Dispatchers.IO) {
        val token = authTokenRepository.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        val backendUrl = backendConfigRepository.getBackendUrl() ?: return@withContext Result.failure(Exception("No backend URL"))

        // Note: For SSE, we'd typically use EventSource. Here we do a one-time fetch via the export endpoint
        // In a real implementation, you'd connect to the SSE endpoint
        try {
            // For now, we'll rely on the SSE sync repository to populate the database
            // This method can trigger a manual sync if needed
            val chats = chatDao.getAllChatsSync()
            Result.success(chats.map { it -> entityToUiModel(it) })
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
        return chatDao.getChatSync(chatId)?.let { entityToUiModel(it) }?.let { flowOf(it) } ?: emptyFlow()
    }

    override fun getChatSync(chatId: String): ChatUiModel? {
        return chatDao.getChatSync(chatId)?.let { entityToUiModel(it) }
    }

    override suspend fun getMaxLastActive(): Long? = withContext(Dispatchers.IO) {
        chatDao.getMaxLastActive()
    }

    private fun entityToUiModel(entity: ChatEntity): ChatUiModel {
        val allIds = mutableListOf(entity.id)
        // In a full implementation, we'd fetch related IDs from the group mapping
        return ChatUiModel(
            id = entity.id,
            displayName = entity.displayName ?: entity.phoneNumber ?: entity.id,
            phoneNumber = entity.phoneNumber,
            customName = entity.customName,
            preview = entity.preview,
            lastActive = entity.lastActive,
            isGroup = entity.isGroup,
            allIds = allIds
        )
    }
}