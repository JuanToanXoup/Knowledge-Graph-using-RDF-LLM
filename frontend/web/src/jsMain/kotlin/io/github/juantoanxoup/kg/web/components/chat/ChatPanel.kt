package io.github.juantoanxoup.kg.web.components.chat

import io.github.juantoanxoup.kg.web.hooks.MOBILE_BREAKPOINT
import io.github.juantoanxoup.kg.web.hooks.ToastStore
import io.github.juantoanxoup.kg.web.hooks.ToastVariant
import io.github.juantoanxoup.kg.web.hooks.useIsMobile
import io.github.juantoanxoup.kg.web.lib.Api
import io.github.juantoanxoup.kg.web.lib.Bot
import io.github.juantoanxoup.kg.web.lib.ChatHistoryMessage
import io.github.juantoanxoup.kg.web.lib.ChatHistoryResponse
import io.github.juantoanxoup.kg.web.lib.ChatSavedResponse
import io.github.juantoanxoup.kg.web.lib.FileText
import io.github.juantoanxoup.kg.web.lib.GraphSummary
import io.github.juantoanxoup.kg.web.lib.GraphsResponse
import io.github.juantoanxoup.kg.web.lib.Info
import io.github.juantoanxoup.kg.web.lib.Maximize2
import io.github.juantoanxoup.kg.web.lib.Minimize2
import io.github.juantoanxoup.kg.web.lib.Minus
import io.github.juantoanxoup.kg.web.lib.MoreVertical
import io.github.juantoanxoup.kg.web.lib.QuestionAnswerRequest
import io.github.juantoanxoup.kg.web.lib.QuestionAnswerResponse
import io.github.juantoanxoup.kg.web.lib.Sparkles
import io.github.juantoanxoup.kg.web.lib.X
import io.github.juantoanxoup.kg.web.lib.cn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.dom.aria.AriaHasPopup
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useMemo
import react.useState
import tanstack.react.router.useLocation
import web.blob.Blob
import web.blob.BlobPropertyBag
import web.cssom.ClassName
import web.events.invoke
import web.file.Files
import web.storage.localStorage
import web.window.keyDownEvent
import web.window.window
import kotlin.js.Date

private val scope = MainScope()

const val AGENT_NAME = "Graph Assistant"
private const val INVITE = "Hi there! Ask me anything about your knowledge graphs."
private const val DISCLAIMER =
    "This assistant uses a language model, which can produce inaccurate answers. Check the facts it cites."
private const val GRAPH_KEY = "kg.chat.graph"

private fun remembered(key: String): String? = runCatching { localStorage.getItem(key) }.getOrNull()

private fun remember(
    key: String,
    value: String?,
) {
    runCatching { if (value == null) localStorage.removeItem(key) else localStorage.setItem(key, value) }
}

private var nextId = 0

private fun id(): String = "e${nextId++}"

/**
 * The site-wide chat: one floating component on every page, after the Agentforce panel of help.salesforce.com.
 * A button or a card at the bottom right when minimized; a sidebar docked on the right or a fullscreen view
 * when open, with a toolbar (name, privacy note, menu, expand, minimize), the conversation and the composer.
 * It answers about the graph the page is on, or asks which graph to use; history is kept per graph.
 */
val ChatPanel =
    FC<Props> {
        val isMobile = useIsMobile()
        val location = useLocation()
        val routeGraphId = graphIdFromPath(location.pathname)

        val (view, setViewState) =
            useState {
                PanelViews.parse(remembered(PanelViews.STORAGE_KEY))
                    ?: PanelViews.initial(window.innerWidth < MOBILE_BREAKPOINT)
            }
        val (entries, setEntries) = useState<List<ChatEntry>>(emptyList())
        var input by useState("")
        var loading by useState(false)
        var menuOpen by useState(false)
        var privacyOpen by useState(false)
        var ended by useState(false)
        var graphs by useState<List<GraphSummary>>(emptyList())
        var graphsLoaded by useState(false)
        var chosenGraphId by useState<String?>(remembered(GRAPH_KEY))

        val graphId = routeGraphId ?: chosenGraphId
        val graph = useMemo(graphs, graphId) { graphs.firstOrNull { it.graphId == graphId } }
        val hasMessages = entries.any { it !is ChatEntry.Status }
        val open = PanelViews.isOpen(view)

        fun setView(next: PanelView) {
            setViewState(next)
            remember(PanelViews.STORAGE_KEY, next.name)
            menuOpen = false
            privacyOpen = false
        }

        fun addStatus(text: String) = setEntries { it + ChatEntry.Status(id(), text, clockTime()) }

        // The graphs the assistant can talk about, once.
        useEffect(Unit) {
            runCatching { Api.getJson<GraphsResponse>("/graphs") }
                .onSuccess { graphs = it.graphs.sortedByDescending { g -> g.createdAt } }
            graphsLoaded = true
        }

        // The page's graph wins; a graph chosen in the panel is remembered across pages.
        useEffect(routeGraphId) {
            if (routeGraphId != null && routeGraphId != chosenGraphId) {
                chosenGraphId = routeGraphId
                remember(GRAPH_KEY, routeGraphId)
            }
        }

        // The conversation of the current graph, from the server; again once the graph names are known.
        useEffect(graphId, graphsLoaded) {
            ended = false
            if (graphId == null) {
                setEntries(emptyList())
                return@useEffect
            }
            val history =
                runCatching { Api.getJson<ChatHistoryResponse>("/chat_history/$graphId") }
                    .getOrNull()
                    ?.messages
                    .orEmpty()
            val loaded =
                history.map { m ->
                    val time = clockTime(Date(m.timestamp))
                    if (m.role ==
                        "user"
                    ) {
                        ChatEntry.User(id(), m.content, time)
                    } else {
                        ChatEntry.Agent(id(), m.content, time, m.facts.orEmpty())
                    }
                }
            val name = graphs.firstOrNull { it.graphId == graphId }?.filename ?: graphId
            setEntries(listOf(ChatEntry.Status(id(), "$AGENT_NAME joined · about $name", clockTime())) + loaded)
        }

        // Escape closes the menu and the privacy note.
        useEffect(Unit) {
            window.keyDownEvent().collect { event ->
                if (event.key == "Escape") {
                    menuOpen = false
                    privacyOpen = false
                }
            }
        }

        suspend fun save(
            gid: String,
            role: String,
            content: String,
            facts: List<String>,
        ) {
            runCatching {
                Api.postJson<ChatHistoryMessage, ChatSavedResponse>(
                    "/chat_history/$gid",
                    ChatHistoryMessage(role, content, Date().toISOString(), facts),
                )
            }
        }

        fun send() {
            val question = input.trim()
            val gid = graphId
            if (question.isEmpty() || loading) return
            if (!open) setView(PanelViews.opened(view, isMobile))
            if (gid == null) {
                setEntries { it + ChatEntry.User(id(), question, clockTime()) }
                input = ""
                return
            }
            setEntries { it + ChatEntry.User(id(), question, clockTime()) }
            input = ""
            loading = true
            ended = false
            scope.launch {
                save(gid, "user", question, emptyList())
                try {
                    val answer =
                        Api.postJson<QuestionAnswerRequest, QuestionAnswerResponse>(
                            "/question_answer",
                            QuestionAnswerRequest(gid, question),
                        )
                    val facts = answer.relevantFacts.map { it.text }
                    setEntries { it + ChatEntry.Agent(id(), answer.answer, clockTime(), facts) }
                    save(gid, "assistant", answer.answer, facts)
                } catch (e: Throwable) {
                    setEntries {
                        it +
                            ChatEntry.Agent(
                                id(),
                                "I could not get an answer: ${e.message ?: "the request failed"}. Please try again.",
                                clockTime(),
                            )
                    }
                } finally {
                    loading = false
                }
            }
        }

        fun chooseGraph(g: GraphSummary) {
            chosenGraphId = g.graphId
            remember(GRAPH_KEY, g.graphId)
        }

        fun downloadTranscript() {
            menuOpen = false
            val text = transcript(AGENT_NAME, graph?.filename, entries)
            Files.downloadFile(
                Blob(arrayOf(text), BlobPropertyBag(type = "text/plain;charset=utf-8")),
                transcriptFileName(graphId),
            )
        }

        fun endConversation() {
            menuOpen = false
            val gid = graphId ?: return
            scope.launch {
                try {
                    Api.delete("/chat_history/$gid")
                    setEntries(listOf(ChatEntry.Status(id(), "You ended the conversation", clockTime())))
                    ended = true
                } catch (e: Throwable) {
                    ToastStore.toast("Could not end the conversation", e.message, ToastVariant.DESTRUCTIVE)
                }
            }
        }

        fun startNew() {
            ended = false
            val name = graph?.filename ?: graphId ?: "no graph"
            setEntries(listOf(ChatEntry.Status(id(), "$AGENT_NAME joined · about $name", clockTime())))
        }

        val graphChoices = if (graphId == null && graphs.isNotEmpty()) graphs else emptyList()
        val noGraphs = graphId == null && graphsLoaded && graphs.isEmpty()

        when (view) {
            PanelView.BUTTON ->
                button {
                    className = ClassName(cn("chat-fab", if (hasMessages) "chat-fab-active" else null))
                    ariaLabel = if (hasMessages) "Active conversation" else "Ask $AGENT_NAME"
                    onClick =
                        {
                            setView(
                                PanelViews.opened(
                                    PanelViews.parse(remembered(PanelViews.STORAGE_KEY + ".open")),
                                    isMobile,
                                ),
                            )
                        }
                    span {
                        className = ClassName("chat-avatar")
                        Bot { size = 18 }
                    }
                    span { +(if (hasMessages) "Active conversation" else "Ask $AGENT_NAME") }
                }

            PanelView.CARD ->
                div {
                    className = ClassName("chat-card")
                    role = AriaRole.region
                    ariaLabel = "Message area"
                    button {
                        className = ClassName("chat-card-minimize")
                        ariaLabel = "Minimize $AGENT_NAME"
                        onClick = { setView(PanelView.BUTTON) }
                        Minus { size = 14 }
                    }
                    div {
                        className = ClassName("chat-card-invite")
                        span {
                            className = ClassName("chat-avatar")
                            Bot { size = 18 }
                        }
                        span { +INVITE }
                    }
                    ChatComposer {
                        value = input
                        placeholder = "Ask $AGENT_NAME"
                        disabled = loading
                        leadingIcon = true
                        autoFocus = false
                        onChange = { text ->
                            input = text
                            if (text.contains('\n')) setView(PanelViews.opened(null, isMobile))
                        }
                        onSend = { send() }
                    }
                }

            PanelView.SIDEBAR, PanelView.FULLSCREEN ->
                div {
                    className =
                        ClassName(cn("chat-panel", if (view == PanelView.FULLSCREEN) "chat-panel-fullscreen" else null))
                    role = AriaRole.dialog
                    ariaModal = view == PanelView.FULLSCREEN
                    ariaLabel = AGENT_NAME
                    div {
                        className = ClassName("chat-toolbar")
                        div {
                            className = ClassName("chat-toolbar-heading")
                            +AGENT_NAME
                            button {
                                className = ClassName("chat-icon-btn chat-icon-btn-sm")
                                ariaLabel = "Privacy note"
                                ariaExpanded = privacyOpen
                                onClick = {
                                    privacyOpen = !privacyOpen
                                    menuOpen = false
                                }
                                Info { size = 16 }
                            }
                        }
                        div {
                            className = ClassName("chat-toolbar-buttons")
                            button {
                                className = ClassName("chat-icon-btn")
                                ariaLabel = "Options menu"
                                ariaHasPopup = AriaHasPopup.menu
                                ariaExpanded = menuOpen
                                onClick = {
                                    menuOpen = !menuOpen
                                    privacyOpen = false
                                }
                                MoreVertical { size = 18 }
                            }
                            if (!isMobile) {
                                button {
                                    className = ClassName("chat-icon-btn")
                                    ariaLabel =
                                        if (view ==
                                            PanelView.FULLSCREEN
                                        ) {
                                            "Contract $AGENT_NAME"
                                        } else {
                                            "Expand $AGENT_NAME"
                                        }
                                    onClick = {
                                        val next = PanelViews.toggledExpand(view)
                                        remember(PanelViews.STORAGE_KEY + ".open", next.name)
                                        setView(next)
                                    }
                                    if (view ==
                                        PanelView.FULLSCREEN
                                    ) {
                                        Minimize2 { size = 18 }
                                    } else {
                                        Maximize2 { size = 18 }
                                    }
                                }
                            }
                            button {
                                className = ClassName("chat-icon-btn")
                                ariaLabel = "Minimize $AGENT_NAME"
                                onClick = { setView(PanelViews.minimized(view, hasMessages, isMobile)) }
                                Minus { size = 18 }
                            }
                        }
                        if (menuOpen) {
                            div {
                                className = ClassName("chat-menu")
                                role = AriaRole.menu
                                div {
                                    role = AriaRole.menuitem
                                    button {
                                        className = ClassName("chat-menu-item")
                                        onClick = { downloadTranscript() }
                                        FileText { size = 16 }
                                        +"Download chat transcript"
                                    }
                                }
                                div {
                                    role = AriaRole.menuitem
                                    button {
                                        className = ClassName("chat-menu-item")
                                        disabled = graphId == null || !hasMessages
                                        onClick = { endConversation() }
                                        X { size = 16 }
                                        +"End conversation"
                                    }
                                }
                            }
                        }
                        if (privacyOpen) {
                            div {
                                className = ClassName("chat-popover")
                                role = AriaRole.dialog
                                ariaLabel = "Privacy note"
                                button {
                                    className = ClassName("chat-icon-btn chat-icon-btn-sm chat-popover-close")
                                    ariaLabel = "Close"
                                    onClick = { privacyOpen = false }
                                    X { size = 16 }
                                }
                                +DISCLAIMER
                                +" "
                                a {
                                    href = "/#how-it-works"
                                    +"Learn more"
                                }
                            }
                        }
                    }
                    div {
                        className = ClassName("chat-main")
                        ChatMessages {
                            agentName = AGENT_NAME
                            this.entries = entries
                            this.loading = loading
                            this.graphChoices = graphChoices
                            onChooseGraph = { chooseGraph(it) }
                            this.noGraphs = noGraphs
                        }
                    }
                    if (ended) {
                        div {
                            className = ClassName("chat-return")
                            button {
                                className = ClassName("btn btn-default btn-size-default chat-return-button")
                                onClick = { startNew() }
                                Sparkles { size = 16 }
                                +"Start a new conversation"
                            }
                        }
                    } else {
                        ChatComposer {
                            value = input
                            placeholder = "Type your message..."
                            disabled = loading || graphId == null
                            leadingIcon = false
                            autoFocus = !isMobile
                            onChange = { input = it }
                            onSend = { send() }
                        }
                        p {
                            className = ClassName("chat-hint")
                            +"Enter sends · Shift+Enter for a new line"
                        }
                    }
                }
        }
    }
