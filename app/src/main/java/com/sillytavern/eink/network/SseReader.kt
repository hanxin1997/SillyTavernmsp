package com.sillytavern.eink.network

import okio.BufferedSource

data class SseEvent(val event: String?, val data: String, val id: String?)

class SseReader(private val source: BufferedSource) {
    fun next(): SseEvent? {
        var event: String? = null
        var id: String? = null
        val data = mutableListOf<String>()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                if (data.isNotEmpty()) return SseEvent(event, data.joinToString("\n"), id)
                continue
            }
            if (line.startsWith(':')) continue
            val separator = line.indexOf(':')
            val field = if (separator >= 0) line.substring(0, separator) else line
            val value = if (separator >= 0) line.substring(separator + 1).removePrefix(" ") else ""
            when (field) {
                "event" -> event = value
                "data" -> data.add(value)
                "id" -> id = value
            }
        }
        return if (data.isNotEmpty()) SseEvent(event, data.joinToString("\n"), id) else null
    }
}
