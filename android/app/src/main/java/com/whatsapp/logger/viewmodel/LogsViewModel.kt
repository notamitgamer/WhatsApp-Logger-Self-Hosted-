package com.whatsapp.logger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsapp.logger.data.ActivityLog
import com.whatsapp.logger.data.LogEntry
import com.whatsapp.logger.data.LogLevel
import com.whatsapp.logger.data.api.ApiService
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.whatsapp.logger.data.repository.ChatRepository
import com.whatsapp.logger.data.repository.SyncRepository
import com.whatsapp.logger.data.repository.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogsViewModel(
    private val syncRepository: SyncRepository,
    private val chatRepository: ChatRepository,
    private val apiService: ApiService,
    private val authTokenRepository: AuthTokenRepository
) : ViewModel() {

    /** App-side events (sync, reconnects). */
    val appEntries = ActivityLog.entries

    private val _serverEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    /** The server's own console log, newest first, refreshed every few seconds. */
    val serverEntries: StateFlow<List<LogEntry>> = _serverEntries.asStateFlow()

    private val _serverError = MutableStateFlow<String?>(null)
    val serverError: StateFlow<String?> = _serverError.asStateFlow()

    private val _showServer = MutableStateFlow(true)
    val showServer: StateFlow<Boolean> = _showServer.asStateFlow()
    fun selectServer(server: Boolean) { _showServer.value = server }

    init {
        viewModelScope.launch {
            while (isActive) {
                fetchServerLogs()
                delay(4000)
            }
        }
    }

    private suspend fun fetchServerLogs() = withContext(Dispatchers.IO) {
        val token = authTokenRepository.getToken()
        if (token == null) {
            _serverError.value = "Not signed in"
            return@withContext
        }
        try {
            val response = apiService.getServerLogs("json", "Bearer $token").execute()
            if (response.isSuccessful) {
                val list = response.body().orEmpty()
                _serverEntries.value = list.mapIndexed { i, dto ->
                    LogEntry(
                        id = i.toLong(),
                        time = dto.timestamp,
                        level = if (dto.level.equals("error", true)) LogLevel.ERROR else LogLevel.INFO,
                        message = dto.message
                    )
                }.asReversed()
                _serverError.value = null
            } else {
                _serverError.value = "Server returned ${response.code()}"
            }
        } catch (e: Exception) {
            _serverError.value = "Can't reach server (${e.message ?: "network error"})"
        }
    }

    val entries = ActivityLog.entries
    val syncState: StateFlow<SyncState> = syncRepository.syncState

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    fun reconnect() {
        ActivityLog.info("Manual reconnect requested")
        syncRepository.startGlobalSync(0)
    }

    fun checkServer() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            ActivityLog.info("Checking server...")
            val start = System.currentTimeMillis()
            val result = chatRepository.refreshChats()
            val ms = System.currentTimeMillis() - start
            result
                .onSuccess { ActivityLog.info("Server reachable: ${ms} ms, ${it.size} chats") }
                .onFailure { ActivityLog.error("Server check failed after ${ms} ms: ${friendlyError(it)}") }
            _checking.value = false
        }
    }

    fun clear() = ActivityLog.clear()

    fun exportText(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val source = if (_showServer.value) serverEntries.value else appEntries.value
        return source.reversed().joinToString("\n") {
            "${fmt.format(java.util.Date(it.time))} [${it.level}] ${it.message}"
        }
    }
}
