package io.github.juantoanxoup.kg.web.lib

/** One server-sent event: its `event` name (`message` when the server sent none) and its `data` lines joined. */
data class SseEvent(
    val event: String,
    val data: String,
)

/**
 * Parses a server-sent event stream fed in arbitrary chunks (RFC-style `event:` and `data:` lines, blank line
 * between events). Comment lines and other fields are ignored.
 */
class SseParser {
    private var buffer = ""

    /** Events completed by this chunk, in order. */
    fun feed(chunk: String): List<SseEvent> {
        buffer += chunk.replace("\r\n", "\n").replace('\r', '\n')
        val events = mutableListOf<SseEvent>()
        while (true) {
            val end = buffer.indexOf("\n\n")
            if (end < 0) break
            parse(buffer.substring(0, end))?.let(events::add)
            buffer = buffer.substring(end + 2)
        }
        return events
    }

    /** The event left unterminated when the stream closes, if any. */
    fun end(): List<SseEvent> {
        val rest = buffer
        buffer = ""
        return listOfNotNull(parse(rest))
    }

    private fun parse(block: String): SseEvent? {
        var name = "message"
        val data = mutableListOf<String>()
        for (line in block.split('\n')) {
            when {
                line.isBlank() || line.startsWith(":") -> Unit
                line.startsWith("event:") -> name = line.removePrefix("event:").trim()
                line.startsWith("data:") -> data += line.removePrefix("data:").removePrefix(" ")
            }
        }
        return if (data.isEmpty()) null else SseEvent(name, data.joinToString("\n"))
    }
}
