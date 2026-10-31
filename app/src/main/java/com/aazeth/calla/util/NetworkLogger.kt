package com.aazeth.calla.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val tag: String,
    val message: String
)

object NetworkLogger {

    private const val MAX_LOGS = 500

    @Volatile
    var isEnabled: Boolean = false

    private val logList = ArrayDeque<LogEntry>()
    private var idCounter = 0L
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    @Synchronized
    fun log(tag: String, message: String) {
        if (!isEnabled) return
        val timestamp = dateFormat.format(Date())
        idCounter++
        val entry = LogEntry(id = idCounter, timestamp = timestamp, tag = tag, message = message)
        if (logList.size >= MAX_LOGS) {
            logList.removeFirst()
        }
        logList.addLast(entry)
        _logs.value = logList.toList()
    }

    @Synchronized
    fun clear() {
        logList.clear()
        _logs.value = emptyList()
    }

    @Synchronized
    fun getFormattedLogs(): String {
        return logList.joinToString("\n") { "[${it.timestamp}] [${it.tag}] ${it.message}" }
    }
}
