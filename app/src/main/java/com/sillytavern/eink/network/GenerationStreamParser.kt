package com.sillytavern.eink.network

import com.sillytavern.eink.model.GenerationEvent
import org.json.JSONObject

internal data class ParsedGenerationChunk(
    val delta: GenerationEvent.Delta? = null,
    val terminal: Boolean = false,
)

internal fun parseOpenAiGenerationChunk(data: String): ParsedGenerationChunk {
    if (data == "[DONE]") return ParsedGenerationChunk(terminal = true)
    val json = runCatching { JSONObject(data) }.getOrNull() ?: return ParsedGenerationChunk()
    val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return ParsedGenerationChunk()
    val value = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: JSONObject()
    val text = value.optString("content")
    val reasoning = value.optString("reasoning_content", value.optString("reasoning"))
    val finishReason = choice.opt("finish_reason")
    val terminal = finishReason != null && finishReason !== JSONObject.NULL && finishReason.toString().isNotBlank()
    val delta = if (text.isNotEmpty() || reasoning.isNotEmpty()) GenerationEvent.Delta(text, reasoning) else null
    return ParsedGenerationChunk(delta, terminal)
}

internal fun requireOpenAiCompatibleStream(source: String) {
    val providerNativeSources = setOf("claude", "makersuite", "cohere")
    require(source.trim().lowercase() !in providerNativeSources) {
        "This version supports OpenAI-compatible SSE only; the selected source returns a provider-native stream."
    }
}
