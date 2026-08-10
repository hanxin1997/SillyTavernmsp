package com.sillytavern.eink.network

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseReaderTest {
    @Test
    fun `reads multiple data lines and CRLF frames`() {
        val reader = SseReader(Buffer().writeUtf8("event: text\r\nid: 7\r\ndata: hello\r\ndata: world\r\n\r\n"))
        assertEquals(SseEvent("text", "hello\nworld", "7"), reader.next())
        assertNull(reader.next())
    }

    @Test
    fun `ignores comments and reads done sentinel`() {
        val reader = SseReader(Buffer().writeUtf8(": keepalive\n\ndata: [DONE]\n\n"))
        assertEquals("[DONE]", reader.next()?.data)
    }
}
