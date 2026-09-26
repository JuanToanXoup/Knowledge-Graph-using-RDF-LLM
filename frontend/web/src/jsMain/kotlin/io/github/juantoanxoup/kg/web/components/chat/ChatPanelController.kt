package io.github.juantoanxoup.kg.web.components.chat

/** Lets any page open the site-wide chat panel, the way the workspace's "Chat & Q&A" item does. */
object ChatPanelController {
    private val listeners = mutableSetOf<() -> Unit>()

    fun open() = listeners.toList().forEach { it() }

    /** Registers [listener] and returns the call that removes it. */
    fun subscribe(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}
