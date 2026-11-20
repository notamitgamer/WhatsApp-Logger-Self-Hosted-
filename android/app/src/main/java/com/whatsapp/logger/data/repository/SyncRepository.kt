package com.whatsapp.logger.data.repository

import kotlinx.coroutines.flow.MutableStateFlow

interface SyncRepository {
    val syncState: MutableStateFlow<SyncState>
    fun startGlobalSync(since: Long)
    fun stopSync()
    suspend fun performFullResync(): Result<ResyncProgress>
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