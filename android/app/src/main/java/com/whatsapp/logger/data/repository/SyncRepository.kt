package com.whatsapp.logger.data.repository

import kotlinx.coroutines.flow.MutableStateFlow

interface SyncRepository {
    val syncState: MutableStateFlow<SyncState>
    val resyncState: MutableStateFlow<ResyncState>
    /** Epoch millis of the last successful sync or live-connection contact (0 = never). */
    val lastSyncedAt: MutableStateFlow<Long>
    fun startGlobalSync(since: Long)
    fun stopSync()
    suspend fun performFullResync(): Result<ResyncProgress>
    suspend fun clearLocalData()
}

sealed class SyncState {
    data class Connecting(val since: Long) : SyncState()
    data class Syncing(val messagesReceived: Int, val lastTimestamp: Long) : SyncState()
    data class Connected(val lastTimestamp: Long) : SyncState()
    data class Error(val message: String, val retryIn: Long) : SyncState()
    object Idle : SyncState()
    object Stopped : SyncState()
}

data class ResyncProgress(
    val totalChats: Int,
    val completedChats: Int,
    val totalMessages: Long,
    val downloadedMessages: Long,
    val currentChat: String?
)

/** Progress of a manual / full sync, driven by the backend's /api/export/chats manifest. */
sealed class ResyncState {
    object Idle : ResyncState()
    data class Running(
        val phase: String,
        val downloaded: Long,
        val total: Long,
        val chatsDone: Int,
        val chatsTotal: Int,
        val currentChat: String?
    ) : ResyncState() {
        /** null while the total is still unknown (indeterminate bar) */
        val fraction: Float?
            get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
    }
    data class Done(val newMessages: Long, val chats: Int) : ResyncState()
    data class Failed(val message: String) : ResyncState()
}
