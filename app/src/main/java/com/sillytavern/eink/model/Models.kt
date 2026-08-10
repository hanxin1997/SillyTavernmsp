package com.sillytavern.eink.model

import org.json.JSONArray

data class ServerProfile(
    val baseUrl: String,
    val handle: String = "",
    val source: String = "openai",
    val model: String = "",
    val allowPrivateLanHttp: Boolean = false,
    val contextTokens: Int = 4095,
    val responseTokens: Int = 300,
)

data class CharacterSummary(
    val avatarUrl: String,
    val name: String,
    val worldName: String? = null,
)

data class ChatSummary(
    val fileId: String,
    val fileName: String,
    val revision: String?,
    val modifiedAt: Long,
    val messageCount: Int,
)

data class ChatSnapshot(
    val chat: JSONArray,
    val revision: String?,
    val fromCache: Boolean = false,
)

data class MessageItem(
    val rawIndex: Int,
    val name: String,
    val text: String,
    val isUser: Boolean,
    val isSystem: Boolean,
)

sealed interface GenerationEvent {
    data class Delta(val text: String, val reasoning: String = "") : GenerationEvent
    data object Done : GenerationEvent
}
