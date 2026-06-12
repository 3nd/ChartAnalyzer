package com.chartanalyzer.app.utils

import com.chartanalyzer.app.models.LogEntry
import com.chartanalyzer.app.models.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

object AppLog {

    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    var networkLogEnabled: Boolean = false
    var stateLogEnabled:   Boolean = false

    fun network(tag: String, message: String) {
        if (!networkLogEnabled) return
        add(LogLevel.NETWORK, tag, message)
    }

    fun state(tag: String, message: String) {
        if (!stateLogEnabled) return
        add(LogLevel.STATE, tag, message)
    }

    fun error(tag: String, message: String) {
        add(LogLevel.ERROR, tag, message)
    }

    fun info(tag: String, message: String) {
        add(LogLevel.INFO, tag, message)
    }

    private fun add(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(level = level, tag = tag, message = message)
        val current = _entries.value.toMutableList()
        current.add(0, entry)
        if (current.size > MAX_ENTRIES) current.removeAt(current.size - 1)
        _entries.value = current
    }

    fun clear() { _entries.value = emptyList() }

    fun toText(): String {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return _entries.value.joinToString("\n") { e ->
            "[${fmt.format(Date(e.timestamp))}][${e.level.name}][${e.tag}] ${e.message}"
        }
    }
}
