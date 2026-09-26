package io.github.juantoanxoup.kg.web.components.chat

import kotlin.js.Date

// The site-wide chat panel's state rules, kept free of React so they can be unit tested. The parts and their
// transitions follow the Agentforce chat panel of help.salesforce.com, as recorded in the koog-acp reference
// (docs/reference/agentforce-chat/SPEC.md): a floating button, a floating card, a docked sidebar, a fullscreen view.

/** Where the panel is on the page. */
enum class PanelView {
    /** The round "Ask" pill at the bottom right. */
    BUTTON,

    /** The card at the bottom right with the invite and a composer; only before the first message. */
    CARD,

    /** Docked on the right below the site header, over the page. */
    SIDEBAR,

    /** Over the whole viewport. */
    FULLSCREEN,
}

object PanelViews {
    const val STORAGE_KEY = "kg.chat.view"

    fun parse(name: String?): PanelView? = PanelView.entries.firstOrNull { it.name == name }

    /** What the page shows first: the card on a desktop, the button on a phone. */
    fun initial(isMobile: Boolean): PanelView = if (isMobile) PanelView.BUTTON else PanelView.CARD

    /**
     * Minimize: an open view goes to the card before the first message and straight to the button once the
     * conversation holds messages; the card goes to the button. The button stays where it is.
     */
    fun minimized(
        view: PanelView,
        hasMessages: Boolean,
        isMobile: Boolean,
    ): PanelView =
        when (view) {
            PanelView.BUTTON, PanelView.CARD -> PanelView.BUTTON
            PanelView.SIDEBAR, PanelView.FULLSCREEN -> if (hasMessages || isMobile) PanelView.BUTTON else PanelView.CARD
        }

    /** Opening from the button or the card: fullscreen on a phone, else the remembered open view or the sidebar. */
    fun opened(
        remembered: PanelView?,
        isMobile: Boolean,
    ): PanelView =
        when {
            isMobile -> PanelView.FULLSCREEN
            remembered == PanelView.FULLSCREEN -> PanelView.FULLSCREEN
            else -> PanelView.SIDEBAR
        }

    fun isOpen(view: PanelView): Boolean = view == PanelView.SIDEBAR || view == PanelView.FULLSCREEN

    fun toggledExpand(view: PanelView): PanelView =
        if (view == PanelView.FULLSCREEN) PanelView.SIDEBAR else PanelView.FULLSCREEN
}

/** One line of the conversation as the panel shows it. */
sealed interface ChatEntry {
    val id: String
    val time: String

    /** The person's message. */
    data class User(
        override val id: String,
        val content: String,
        override val time: String,
    ) : ChatEntry

    /** The assistant's answer with the facts it rests on. */
    data class Agent(
        override val id: String,
        val content: String,
        override val time: String,
        val facts: List<String> = emptyList(),
    ) : ChatEntry

    /** A centred status line: joined, now talking about a graph, ended. */
    data class Status(
        override val id: String,
        val text: String,
        override val time: String,
    ) : ChatEntry
}

private val WORKSPACE_PATH = Regex("^/workspace/([^/?#]+)")

/** The graph the page is about, from the client route `/workspace/{id}`; null on every other page. */
fun graphIdFromPath(pathname: String): String? = WORKSPACE_PATH.find(pathname)?.groupValues?.get(1)

/** A clock time such as `7:39 PM` for status rows and message timestamps. */
fun clockTime(date: Date = Date()): String =
    date.toLocaleTimeString("en-US", js("({hour: 'numeric', minute: '2-digit'})"))

/** The conversation as a plain-text transcript, one paragraph per entry, facts indented under an answer. */
fun transcript(
    agentName: String,
    graphName: String?,
    entries: List<ChatEntry>,
): String {
    val lines = mutableListOf("$agentName chat transcript", "Graph: ${graphName ?: "none selected"}", "")
    for (e in entries) {
        when (e) {
            is ChatEntry.Status -> lines += "— ${e.text} (${e.time})"
            is ChatEntry.User -> lines += "You (${e.time}):\n${e.content}"
            is ChatEntry.Agent -> {
                lines += "$agentName (${e.time}):\n${e.content}"
                if (e.facts.isNotEmpty()) lines += e.facts.joinToString("\n") { "    · $it" }
            }
        }
        lines += ""
    }
    return lines.joinToString("\n")
}

/** A graph as a choice: its file name and the day it was built, since several graphs can come from one file. */
fun graphLabel(
    filename: String,
    createdAt: String,
): String {
    val day =
        runCatching {
            Date(createdAt).toLocaleDateString("en-US", js("({month: 'short', day: 'numeric'})"))
        }.getOrNull()
    return if (day == null || day == "Invalid Date") filename else "$filename · $day"
}

/** File name for a downloaded transcript. */
fun transcriptFileName(
    graphId: String?,
    date: Date = Date(),
): String = "chat-transcript-${graphId ?: "no-graph"}-${date.toISOString().substring(0, 10)}.txt"
