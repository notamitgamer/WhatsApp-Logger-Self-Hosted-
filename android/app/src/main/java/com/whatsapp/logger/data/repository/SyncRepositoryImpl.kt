package com.whatsapp.logger.data.repository

import com.whatsapp.logger.data.api.ApiService
import com.whatsapp.logger.data.api.model.MessageDto
import com.whatsapp.logger.data.api.model.SyncMessageChange
import com.whatsapp.logger.data.api.model.SyncSseEvent
import com.whatsapp.logger.data.local.ChatDao
import com.whatsapp.logger.data.local.MessageDao
import com.whatsapp.logger.data.local.MessageEntity
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import com.whatsapp.logger.data.repository.ResyncProgress
import com.whatsapp.logger.data.repository.SyncRepository
import com.whatsapp.logger.data.repository.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

class SyncRepositoryImpl(
    private val eventSourceFactory: okhttp3.sse.EventSource.Factory,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val authTokenRepository: AuthTokenRepository,
    private val backendConfigRepository: BackendConfigRepository,
    private val json: Json,
    private val scope: CoroutineScope,
    private val apiService: ApiService
) : SyncRepository {

    override val syncState = MutableStateFlow<SyncState>(SyncState.Idle)

    private var eventSource: EventSource? = null
    private var currentSince = 0L
    private var messagesReceived = 0
    private val syncJob = SupervisorJob()
    private val syncScope = CoroutineScope(Dispatchers.IO + syncJob)

    override fun startGlobalSync(since: Long) {
        currentSince = since
        messagesReceived = 0
        syncScope.launch {
            connectToSyncStream()
        }
    }

    override fun stopSync() {
        eventSource?.cancel()
        eventSource = null
        syncJob.cancel()
        syncState.value = SyncState.Stopped
    }

    private fun connectToSyncStream() {
        val token = authTokenRepository.getToken() ?: return
        val backendUrl = backendConfigRepository.getBackendUrl() ?: return

        syncState.value = SyncState.Connecting(currentSince)

        val request = Request.Builder()
            .url("$backendUrl/api/sync/stream?since=$currentSince&token=$token")
            .build()

        eventSource = eventSourceFactory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                syncScope.launch {
                    syncState.value = SyncState.Syncing(0, currentSince)
                }
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                syncScope.launch {
                    when (type) {
                        "initial" -> handleInitialSync(data)
                        "update" -> handleUpdateSync(data)
                        else -> {}
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                syncScope.launch {
                    syncState.value = SyncState.Error("Connection closed", 5000)
                    // Reconnect with backoff
                    kotlinx.coroutines.delay(5000)
                    connectToSyncStream()
                }
            }
        })
    }

    private suspend fun handleInitialSync(data: String) {
        val event = json.decodeFromString<SyncSseEvent.Initial>(data)
        val messages = event.data.map { dtoToEntity(it) }
        if (messages.isNotEmpty()) {
            messageDao.insertAll(messages)
            val maxTs = messages.maxByOrNull { it.timestamp }?.timestamp ?: currentSince
            currentSince = maxTs
            messagesReceived += messages.size
            syncState.value = SyncState.Syncing(messagesReceived, currentSince)
        } else {
            syncState.value = SyncState.Connected(currentSince)
        }
    }

    private suspend fun handleUpdateSync(data: String) {
        val event = json.decodeFromString<SyncSseEvent.Update>(data)
        var hasUpdates = false
        var newMaxTs = currentSince

        for (change in event.data) {
            val entity = dtoToEntity(change.doc)
            when (change.type) {
                "added", "modified" -> {
                    messageDao.insert(entity)
                    hasUpdates = true
                    newMaxTs = maxOf(newMaxTs, entity.timestamp)
                }
                "removed" -> {
                    // Handle deletion if needed
                }
            }
        }

        if (hasUpdates) {
            messagesReceived += event.data.size
            currentSince = newMaxTs
            syncState.value = SyncState.Syncing(messagesReceived, currentSince)
        }
    }

    override suspend fun performFullResync(): Result<ResyncProgress> = withContext(Dispatchers.IO) {
        val token = authTokenRepository.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        val backendUrl = backendConfigRepository.getBackendUrl() ?: return@withContext Result.failure(Exception("No backend URL"))

        // This would use the export endpoints for full resync
        // Implementation depends on the exact API structure
        try {
            // Fetch chat manifest
            val chatsResponse = apiService.getExportChats(token).execute()
            if (!chatsResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch chat manifest"))
            }

            val chatsManifest = chatsResponse.body()!!
            var downloadedMessages = 0L

            for ((index, chat) in chatsManifest.chats.withIndex()) {
                // Update progress
                val progress = ResyncProgress(
                    totalChats = chatsManifest.chats.size,
                    completedChats = index,
                    totalMessages = chatsManifest.totalMessages,
                    downloadedMessages = downloadedMessages,
                    currentChat = chat.displayName ?: chat.phoneNumber ?: chat.id
                )

                // Fetch messages for this chat
                val msgsResponse = apiService.getExportMessages(token, chat.id).execute()
                if (msgsResponse.isSuccessful) {
                    val messages = msgsResponse.body()!!.messages.map { dtoToEntity(it) }
                    if (messages.isNotEmpty()) {
                        messageDao.insertAll(messages)
                        downloadedMessages += messages.size
                    }
                }
            }

            Result.success(ResyncProgress(
                totalChats = chatsManifest.chats.size,
                completedChats = chatsManifest.chats.size,
                totalMessages = chatsManifest.totalMessages,
                downloadedMessages = downloadedMessages,
                currentChat = null
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun dtoToEntity(dto: MessageDto): MessageEntity {
        return MessageEntity(
            id = dto.id,
            chatId = dto.chatId ?: "",
            fromMe = dto.fromMe,
            senderName = dto.senderName,
            text = dto.text,
            timestamp = dto.timestamp,
            type = dto.type
        )
    }
}