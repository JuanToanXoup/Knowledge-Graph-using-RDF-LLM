package io.github.juantoanxoup.kg.web.lib

import kotlin.test.Test
import kotlin.test.assertEquals

class SseParserTest {
    @Test
    fun events_are_assembled_across_chunk_boundaries() {
        val p = SseParser()
        assertEquals(emptyList(), p.feed("event: facts\ndata: {\"a\":"))
        assertEquals(listOf(SseEvent("facts", "{\"a\":1}")), p.feed("1}\n\nevent: delta\ndata: {\"t"))
        assertEquals(listOf(SseEvent("delta", "{\"text\":\"Hi\"}")), p.feed("ext\":\"Hi\"}\n\n"))
        assertEquals(emptyList(), p.end())
    }

    @Test
    fun multi_line_data_comments_and_missing_names_follow_the_format() {
        val p = SseParser()
        val events = p.feed(": keep-alive\n\ndata: one\ndata: two\n\nevent: x\n\n")
        assertEquals(listOf(SseEvent("message", "one\ntwo")), events)
        assertEquals(listOf(SseEvent("done", "{}")), p.feed("event: done\r\ndata: {}\r\n\r\n"))
        assertEquals(listOf(SseEvent("tail", "end")), p.feed("event: tail\ndata: end").let { p.end() })
    }
}
