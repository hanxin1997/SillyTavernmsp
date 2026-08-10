package com.sillytavern.eink.network

import com.sillytavern.eink.model.GenerationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationStreamParserTest {
    @Test
    fun `parses OpenAI text reasoning and terminal chunks`() {
        val delta = parseOpenAiGenerationChunk(
            """{"choices":[{"delta":{"content":"Hello","reasoning_content":"Think"},"finish_reason":null}]}""",
        )
        assertEquals(GenerationEvent.Delta("Hello", "Think"), delta.delta)
        assertFalse(delta.terminal)

        val terminal = parseOpenAiGenerationChunk(
            """{"choices":[{"delta":{},"finish_reason":"stop"}]}""",
        )
        assertNull(terminal.delta)
        assertTrue(terminal.terminal)
        assertTrue(parseOpenAiGenerationChunk("[DONE]").terminal)
    }

    @Test
    fun `rejects provider-native stream sources with a clear error`() {
        listOf("claude", "makersuite", "cohere").forEach { source ->
            assertThrows(IllegalArgumentException::class.java) {
                requireOpenAiCompatibleStream(source)
            }
        }
        requireOpenAiCompatibleStream("openai")
        requireOpenAiCompatibleStream("openrouter")
    }
}
