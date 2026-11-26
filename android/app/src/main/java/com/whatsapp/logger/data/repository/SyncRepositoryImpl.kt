package com.whatsapp.logger.data.repository

import com.whatsapp.logger.data.api.ApiService
import com.whatsapp.logger.data.api.model.MessageDto
import com.whatsapp.logger.data.api.model.SyncMessageChange
import com.whatsapp.logger.data.local.ChatDao
import com.whatsapp.logger.data.local.ChatEntity
import com.whatsapp.logger.data.local.MessageDao
import com.whatsapp.logger.data.local.MessageEntity
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import com.whatsapp.logger.data.repository.ResyncProgress
import com.whatsapp.logger.data.repository.SyncRepository
import com.whatsapp.logger.data.repository.SyncState
import com.whatsapp.logger.data.ActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.Response

class SyncRepositoryImpl(
    private val eventSourceFactory: EventSource.Factory,
    private val apiService: ApiService,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val authTokenRepository: AuthTokenRepository,
    private val backendConfigRepository: BackendConfigRepository,
    private val json: Json,
    context: android.content.Context
) : SyncRepository {

    // Last server messageCount we fully downloaded per chat (local count can differ, e.g. merged chats)
    private val syncedPrefs = context.getSharedPreferences("sync_counts", android.content.Context.MODE_PRIVATE)


    override val syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val resyncState = MutableStateFlow<ResyncState>(ResyncState.Idle)
    override val lastSyncedAt = MutableStateFlow(syncedPrefs.getLong("last_synced_at", 0L))
    private fun markSynced() {
        val now = System.currentTimeMillis()
        lastSyncedAt.value = now
        syncedPrefs.edit().putLong("last_synced_at", now).apply()
    }
    private val resyncMutex = kotlinx.coroutines.sync.Mutex()

    private var eventSource: EventSource? = null
    private var currentSince = 0L
    private var messagesReceived = 0
    private var syncJob = SupervisorJob()
    private var syncScope = CoroutineScope(Dispatchers.IO + syncJob)

    override fun startGlobalSync(since: Long) {
        eventSource?.cancel()
        syncJob.cancel()
        syncJob = SupervisorJob()
        syncScope = CoroutineScope(Dispatchers.IO + syncJob)
        currentSince = since
        messagesReceived = 0
        syncScope.launch {
            // Resume from the newest stored message instead of re-downloading everything
            if (currentSince == 0L) {
                currentSince = messageDao.getMaxTimestamp() ?: 0L
                if (currentSince > 0L) ActivityLog.info("Resuming live stream from the last stored message")
            }
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
            override fun onOpen(eventSource: EventSource, response: Response) {
                syncScope.launch {
                    syncState.value = SyncState.Syncing(0, currentSince)
                    markSynced()
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

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                syncScope.launch {
                    val retryIn = when (response?.code) {
                        401 -> 0L // Don't retry on auth failure
                        else -> 5000L
                    }
                    syncState.value = SyncState.Error(t?.message ?: "Unknown error", retryIn)
                    if (retryIn > 0) {
                        kotlinx.coroutines.delay(retryIn)
                        connectToSyncStream()
                    }
                }
            }
        })
    }

    private suspend fun handleInitialSync(data: String) {
        val dtos = json.decodeFromString(ListSerializer(MessageDto.serializer()), data)
        val messages = dtos.map { dtoToEntity(it) }
        if (messages.isNotEmpty()) {
            messageDao.insertAll(messages)
            val maxTs = messages.maxByOrNull { it.timestamp }?.timestamp ?: currentSince
            currentSince = maxTs
            messagesReceived += messages.size
            syncState.value = SyncState.Syncing(messagesReceived, currentSince)
            markSynced()
        } else {
            syncState.value = SyncState.Connected(currentSince)
            markSynced()
        }
    }

    private suspend fun handleUpdateSync(data: String) {
        val changes = json.decodeFromString(ListSerializer(SyncMessageChange.serializer()), data)
        var hasUpdates = false
        var newMaxTs = currentSince

        for (change in changes) {
            val entity = dtoToEntity(change.doc)
            when (change.type) {
                "added", "modified" -> {
                    messageDao.insert(entity)
                    chatDao.updateLastActiveAndPreview(entity.chatId, entity.timestamp, entity.text)
                    hasUpdates = true
                    newMaxTs = maxOf(newMaxTs, entity.timestamp)
                }
                "removed" -> {
                    // Handle deletion if needed
                }
            }
        }

        if (hasUpdates) {
            messagesReceived += changes.size
            currentSince = newMaxTs
            syncState.value = SyncState.Syncing(messagesReceived, currentSince)
            markSynced()
        }
    }

    /**
     * Incremental sync with real progress. The backend manifest (/api/export/chats) lists every chat
     * with its message count and the overall total. Only chats where the server has more messages than
     * this device are downloaded, one chat at a time, and progress is downloaded / total.
     */
    override suspend fun performFullResync(): Result<ResyncProgress> = withContext(Dispatchers.IO) {
        val token = authTokenRepository.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        if (!resyncMutex.tryLock()) return@withContext Result.failure(Exception("A sync is already running"))

        try {
            resyncState.value = ResyncState.Running("Fetching chat list", 0, 0, 0, 0, null)
            ActivityLog.info("Sync started")

            val response = apiService.getExportChats(token).execute()
            if (!response.isSuccessful) {
                val message = when (response.code()) {
                    401 -> "session expired, sign in again"
                    503 -> "server is warming up, try again shortly"
                    else -> "server returned ${response.code()}"
                }
                throw Exception(message)
            }
            val manifest = response.body() ?: throw Exception("empty response from server")

            chatDao.insertAll(manifest.chats.map {
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

            // Newest conversations first so the useful data lands early
            val pending = manifest.chats
                .filter {
                    it.messageCount > 0 &&
                        (syncedPrefs.getLong(it.id, -1L) != it.messageCount || messageDao.getMessageCount(it.id) == 0L)
                }
                .sortedByDescending { it.lastActive ?: 0L }
            val total = pending.sumOf { it.messageCount }

            if (pending.isEmpty()) {
                ActivityLog.info("Sync finished: already up to date (${manifest.totalMessages} messages on server)")
                resyncState.value = ResyncState.Done(0, 0)
                markSynced()
                return@withContext Result.success(
                    ResyncProgress(manifest.chats.size, manifest.chats.size, manifest.totalMessages, 0, null)
                )
            }

            ActivityLog.info("Downloading ${pending.size} chats ($total messages)")
            val startedAt = System.currentTimeMillis()
            var downloaded = 0L
            var failed = 0

            pending.forEachIndexed { index, chat ->
                val name = chat.customName ?: chat.displayName ?: chat.phoneNumber ?: chat.id
                resyncState.value = ResyncState.Running("Downloading messages", downloaded, total, index, pending.size, name)
                try {
                    val msgs = apiService.getExportMessages(token, chat.id).execute()
                    if (msgs.isSuccessful) {
                        val entities = (msgs.body()?.messages ?: emptyList()).map { dtoToEntity(it.copy(chatId = it.chatId ?: chat.id)) }
                        if (entities.isNotEmpty()) messageDao.insertAll(entities)
                        syncedPrefs.edit().putLong(chat.id, chat.messageCount).apply()
                    } else {
                        failed++
                        ActivityLog.warn("Could not download \"$name\" (server returned ${msgs.code()})")
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    ActivityLog.warn("Could not download \"$name\": ${e.message ?: "unknown error"}")
                }
                downloaded += chat.messageCount
            }

            val seconds = (System.currentTimeMillis() - startedAt) / 1000.0
            ActivityLog.info(
                "Sync finished: $downloaded messages from ${pending.size} chats in ${"%.1f".format(seconds)}s" +
                    if (failed > 0) ", $failed chats failed" else ""
            )
            resyncState.value = ResyncState.Done(downloaded, pending.size)
            markSynced()
            Result.success(
                ResyncProgress(manifest.chats.size, pending.size, total, downloaded, null)
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            resyncState.value = ResyncState.Idle
            throw e
        } catch (e: Exception) {
            val message = e.message ?: "unknown error"
            ActivityLog.error("Sync failed: $message")
            resyncState.value = ResyncState.Failed(message)
            Result.failure(e)
        } finally {
            resyncMutex.unlock()
        }
    }

    override suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        messageDao.clearAll()
        chatDao.clearAll()
        syncedPrefs.edit().clear().apply()
    }

    private fun dtoToEntity(dto: MessageDto): MessageEntity {
        return MessageEntity(
            id = dto.id,
            chatId = dto.chatId ?: "",
            fromMe = dto.fromMe,
            senderName = dto.senderName,
            text = dto.text,
            timestamp = dto.timestamp,
            type = dto.type,
            deleted = dto.deleted,
            editsJson = dto.edits?.takeIf { it.isNotEmpty() }?.let {
                json.encodeToString(ListSerializer(com.whatsapp.logger.data.api.model.EditDto.serializer()), it)
            }
        )
    }
}