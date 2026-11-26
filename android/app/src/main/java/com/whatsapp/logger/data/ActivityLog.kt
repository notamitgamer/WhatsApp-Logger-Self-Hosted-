package com.whatsapp.logger.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val id: Long,
    val time: Long,
    val level: LogLevel,
    val message: String
)

/** In-memory activity log (newest first) shown on the "Live server logs" screen. */
object ActivityLog {
    private const val MAX_ENTRIES = 300
    private val nextId = AtomicLong(0)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun log(level: LogLevel, message: String) {
        val entry = LogEntry(nextId.getAndIncrement(), System.currentTimeMillis(), level, message)
        _entries.update { current -> (listOf(entry) + current).take(MAX_ENTRIES) }
    }

    fun info(message: String) = log(LogLevel.INFO, message)
    fun warn(message: String) = log(LogLevel.WARN, message)
    fun error(message: String) = log(LogLevel.ERROR, message)

    fun clear() {
        _entries.value = emptyList()
    }
}
