package io.github.juantoanxoup.kg.web.components.chat

import io.github.juantoanxoup.kg.web.lib.ArrowDown
import io.github.juantoanxoup.kg.web.lib.Bot
import io.github.juantoanxoup.kg.web.lib.GraphSummary
import io.github.juantoanxoup.kg.web.lib.Markdown
import io.github.juantoanxoup.kg.web.lib.remarkGfm
import react.FC
import react.Key
import react.Props
import react.dom.aria.AriaLive
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.strong
import react.useEffect
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLDivElement

external interface ChatMessagesProps : Props {
    var agentName: String
    var entries: List<ChatEntry>

    /** An answer is on its way: show the typing indicator. */
    var loading: Boolean

    /** Graphs to choose from when the page names none; empty when a graph is chosen or none exist. */
    var graphChoices: List<GraphSummary>
    var onChooseGraph: (GraphSummary) -> Unit

    /** No graph exists at all: say where to make one. */
    var noGraphs: Boolean
}

/**
 * The conversation: the hero until the first message, status rows, the assistant's answers with their sources,
 * the person's messages, the typing indicator, the graph choices, and a scroll-to-bottom control when new
 * content is below the fold.
 */
val ChatMessages =
    FC<ChatMessagesProps> { props ->
        val container = useRef<HTMLDivElement>()
        var belowFold by useState(false)
        val hasMessages = props.entries.any { it !is ChatEntry.Status }

        fun scrollToEnd() {
            val el = container.current ?: return
            el.scrollTo(0.0, el.scrollHeight.toDouble())
        }

        // New content scrolls into view unless the person has scrolled up to read; then the control appears.
        useEffect(props.entries.size, props.loading) {
            val el = container.current ?: return@useEffect
            val nearEnd = el.scrollHeight - el.scrollTop - el.clientHeight < 120
            if (nearEnd) scrollToEnd() else belowFold = true
        }

        div {
            className = ClassName("chat-messages-outer")
            div {
                ref = container
                className = ClassName("chat-messages")
                role = AriaRole.region
                ariaLabel = "messages"
                ariaLive = AriaLive.polite
                onScroll = {
                    val el = container.current
                    if (el != null) belowFold = el.scrollHeight - el.scrollTop - el.clientHeight > 120
                }
                if (!hasMessages) {
                    div {
                        className = ClassName("chat-hero")
                        +"How can "
                        span {
                            className = ClassName("gradient-text")
                            +props.agentName
                        }
                        +" help?"
                    }
                }
                for (entry in props.entries) {
                    when (entry) {
                        is ChatEntry.Status ->
                            div {
                                key = Key(entry.id)
                                className = ClassName("chat-status")
                                +"${entry.text} • ${entry.time}"
                            }
                        is ChatEntry.User ->
                            div {
                                key = Key(entry.id)
                                className = ClassName("chat-entry chat-entry-user")
                                div {
                                    className = ClassName("chat-entry-body")
                                    p {
                                        className = ClassName("break-words")
                                        +entry.content
                                    }
                                    div {
                                        className = ClassName("chat-time")
                                        +"Sent: ${entry.time}"
                                    }
                                }
                            }
                        is ChatEntry.Agent ->
                            div {
                                key = Key(entry.id)
                                className = ClassName("chat-entry chat-entry-agent")
                                AgentAvatar { name = props.agentName }
                                div {
                                    className = ClassName("chat-entry-body")
                                    div {
                                        className = ClassName("chat-md")
                                        Markdown {
                                            remarkPlugins = arrayOf(remarkGfm)
                                            +entry.content
                                        }
                                    }
                                    div {
                                        className = ClassName("chat-time")
                                        +"${props.agentName} • ${entry.time}"
                                    }
                                    if (entry.facts.isNotEmpty()) {
                                        section {
                                            className = ClassName("chat-sources")
                                            h3 {
                                                className = ClassName("chat-sources-header")
                                                +"Sources"
                                                span {
                                                    className = ClassName("chat-sources-status")
                                                    role = AriaRole.status
                                                    val n = entry.facts.size
                                                    +"$n ${if (n == 1) "fact" else "facts"} from the graph"
                                                }
                                            }
                                            div {
                                                role = AriaRole.list
                                                for ((i, fact) in entry.facts.withIndex()) {
                                                    div {
                                                        key = Key("$i")
                                                        role = AriaRole.listitem
                                                        className = ClassName("chat-source-card")
                                                        strong { +"${i + 1}. " }
                                                        +fact
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
                if (props.graphChoices.isNotEmpty()) {
                    div {
                        className = ClassName("chat-entry chat-entry-agent")
                        AgentAvatar { name = props.agentName }
                        div {
                            className = ClassName("chat-entry-body")
                            p { +"Which knowledge graph should I answer about?" }
                            div {
                                className = ClassName("chat-choices")
                                role = AriaRole.group
                                ariaLabel = "Choose a graph"
                                for (g in props.graphChoices) {
                                    button {
                                        key = Key(g.graphId)
                                        className = ClassName("chat-choice")
                                        onClick = { props.onChooseGraph(g) }
                                        +graphLabel(g.filename, g.createdAt)
                                    }
                                }
                            }
                        }
                    }
                }
                if (props.noGraphs) {
                    div {
                        className = ClassName("chat-entry chat-entry-agent")
                        AgentAvatar { name = props.agentName }
                        div {
                            className = ClassName("chat-entry-body")
                            p {
                                +"There is no knowledge graph yet. "
                                +"Upload a document on the home page and I will answer questions about it."
                            }
                        }
                    }
                }
                if (props.loading) {
                    div {
                        className = ClassName("chat-entry chat-entry-agent")
                        ariaLabel = "${props.agentName} is writing"
                        AgentAvatar { name = props.agentName }
                        div {
                            className = ClassName("chat-typing dots")
                            repeat(3) { div { className = ClassName("dot") } }
                        }
                    }
                }
            }
            if (belowFold) {
                button {
                    className = ClassName("chat-scroll-bottom")
                    ariaLabel = "Scroll to bottom of conversation"
                    onClick = {
                        scrollToEnd()
                        belowFold = false
                    }
                    ArrowDown { size = 18 }
                }
            }
        }
    }

external interface AgentAvatarProps : Props {
    var name: String
}

val AgentAvatar =
    FC<AgentAvatarProps> { props ->
        div {
            className = ClassName("chat-avatar")
            role = AriaRole.img
            ariaLabel = "${props.name} said:"
            Bot { size = 18 }
        }
    }
