package io.github.juantoanxoup.kg.web.components.workspace

import io.github.juantoanxoup.kg.web.components.ui.Button
import io.github.juantoanxoup.kg.web.components.ui.Textarea
import io.github.juantoanxoup.kg.web.hooks.ToastStore
import io.github.juantoanxoup.kg.web.hooks.ToastVariant
import io.github.juantoanxoup.kg.web.lib.Api
import io.github.juantoanxoup.kg.web.lib.ChatHistoryMessage
import io.github.juantoanxoup.kg.web.lib.ChatHistoryResponse
import io.github.juantoanxoup.kg.web.lib.ChatSavedResponse
import io.github.juantoanxoup.kg.web.lib.Copy
import io.github.juantoanxoup.kg.web.lib.QuestionAnswerRequest
import io.github.juantoanxoup.kg.web.lib.QuestionAnswerResponse
import io.github.juantoanxoup.kg.web.lib.Send
import io.github.juantoanxoup.kg.web.lib.ThumbsDown
import io.github.juantoanxoup.kg.web.lib.ThumbsUp
import io.github.juantoanxoup.kg.web.lib.Trash2
import io.github.juantoanxoup.kg.web.lib.cn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import react.FC
import react.Key
import react.dom.html.ReactHTML.details
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.summary
import react.dom.html.ReactHTML.ul
import react.useEffect
import react.useRef
import react.useState
import web.console.console
import web.cssom.ClassName
import web.html.HTMLDivElement
import web.navigator.navigator
import kotlin.js.Date

private val scope = MainScope()

private data class Message(
    val id: String,
    val role: String,
    val content: String,
    val facts: List<String> = emptyList(),
)

/** Question answering thread with persisted history (src/components/workspace/ChatTab.tsx). */
val ChatTab =
    FC<GraphTabProps> { props ->
        // Destructured so updates can be functional: `send()` appends from a coroutine after the closure was taken.
        val (messages, setMessages) = useState<List<Message>>(emptyList())
        var input by useState("")
        var loading by useState(false)
        val messagesEnd = useRef<HTMLDivElement>()

        fun scrollToBottom() = messagesEnd.current?.scrollIntoView()

        useEffect(props.graphId) {
            runCatching { Api.getJson<ChatHistoryResponse>("/chat_history/${props.graphId}") }
                .onSuccess { history ->
                    if (history.messages.isNotEmpty()) {
                        setMessages(
                            history.messages.mapIndexed { i, m ->
                                Message(i.toString(), m.role, m.content, m.facts.orEmpty())
                            },
                        )
                    }
                }.onFailure { console.error("Failed to load chat history:", it) }
        }

        useEffect(messages) {
            if (messages.isNotEmpty()) scrollToBottom()
        }

        suspend fun saveMessage(message: Message) {
            runCatching {
                Api.postJson<ChatHistoryMessage, ChatSavedResponse>(
                    "/chat_history/${props.graphId}",
                    ChatHistoryMessage(message.role, message.content, Date().toISOString(), message.facts),
                )
            }.onFailure { console.error("Failed to save message:", it) }
        }

        fun clearHistory() {
            scope.launch {
                try {
                    Api.delete("/chat_history/${props.graphId}")
                    setMessages(emptyList())
                    ToastStore.toast("Chat cleared", "Chat history has been cleared")
                } catch (e: Throwable) {
                    ToastStore.toast("Error", "Failed to clear chat history", ToastVariant.DESTRUCTIVE)
                }
            }
        }

        fun send() {
            val question = input.trim()
            if (question.isEmpty()) return
            val userMessage = Message(Date.now().toString(), "user", question)
            setMessages { it + userMessage }
            input = ""
            loading = true
            scope.launch {
                saveMessage(userMessage)
                try {
                    val data =
                        Api.postJson<QuestionAnswerRequest, QuestionAnswerResponse>(
                            "/question_answer",
                            QuestionAnswerRequest(props.graphId, question),
                        )
                    val assistant =
                        Message(
                            (Date.now() + 1).toString(),
                            "assistant",
                            data.answer,
                            data.relevantFacts.map { it.text },
                        )
                    setMessages { it + assistant }
                    saveMessage(assistant)
                } catch (e: Throwable) {
                    ToastStore.toast("Error", "Failed to get answer. Please try again.", ToastVariant.DESTRUCTIVE)
                } finally {
                    loading = false
                }
            }
        }

        div {
            className = ClassName("chat")

            div {
                className = ClassName("chat-history")
                div {
                    className = ClassName("card-header")
                    Button {
                        variant = "outline"
                        className = ClassName("btn-block")
                        disabled = messages.isEmpty()
                        onClick = { clearHistory() }
                        Trash2 { size = 16 }
                        +"Clear History"
                    }
                }
                div {
                    className = ClassName("grow pad-4")
                    if (messages.isNotEmpty()) {
                        p {
                            className = ClassName("text-sm font-medium")
                            +"Chat History"
                        }
                        p {
                            className = ClassName("text-xs text-muted")
                            +"${messages.size} messages in this conversation"
                        }
                    } else {
                        p {
                            className = ClassName("text-sm text-muted text-center")
                            +"No messages yet"
                        }
                    }
                }
            }

            div {
                className = ClassName("chat-main")
                div {
                    h2 {
                        className = ClassName("page-title")
                        +"Chat & Q&A"
                    }
                    p {
                        className = ClassName("text-muted")
                        +"Ask questions about your knowledge graph"
                    }
                }

                div {
                    className = ClassName("chat-messages")
                    if (messages.isEmpty()) {
                        div {
                            className = ClassName("text-center center-message stack-2")
                            p {
                                className = ClassName("text-muted")
                                +"Start a conversation by asking a question"
                            }
                            p {
                                className = ClassName("text-sm text-muted")
                                +"Try: \"What are the main entities in this document?\""
                            }
                        }
                    }
                    for (message in messages) {
                        div {
                            key = Key(message.id)
                            className =
                                ClassName(
                                    cn(
                                        "card message",
                                        if (message.role ==
                                            "user"
                                        ) {
                                            "message-user"
                                        } else {
                                            "message-assistant card-accent"
                                        },
                                    ),
                                )
                            p {
                                className = ClassName("break-words")
                                +message.content
                            }
                            if (message.facts.isNotEmpty()) {
                                details {
                                    className = ClassName("facts")
                                    summary { +"Relevant Facts (${message.facts.size})" }
                                    ul { for (fact in message.facts) li { +"• $fact" } }
                                }
                            }
                            if (message.role == "assistant") {
                                div {
                                    className = ClassName("message-actions")
                                    Button {
                                        variant = "ghost"
                                        size = "sm"
                                        onClick = {
                                            navigator.clipboard.writeTextAsync(message.content)
                                            ToastStore.toast("Copied to clipboard")
                                        }
                                        Copy { size = 16 }
                                    }
                                    Button {
                                        variant = "ghost"
                                        size = "sm"
                                        ThumbsUp { size = 16 }
                                    }
                                    Button {
                                        variant = "ghost"
                                        size = "sm"
                                        ThumbsDown { size = 16 }
                                    }
                                }
                            }
                        }
                    }
                    if (loading) {
                        div {
                            className = ClassName("card message-assistant card-accent")
                            div {
                                className = ClassName("dots")
                                repeat(3) { div { className = ClassName("dot") } }
                            }
                        }
                    }
                    div { ref = messagesEnd }
                }

                div {
                    className = ClassName("card")
                    div {
                        className = ClassName("row")
                        Textarea {
                            value = input
                            onChange = { input = it.target.value }
                            placeholder = "Ask anything about your document..."
                            onKeyDown = { event ->
                                if (event.key == "Enter" && !event.shiftKey) {
                                    event.preventDefault()
                                    send()
                                }
                            }
                        }
                        Button {
                            onClick = { send() }
                            disabled = input.isBlank() || loading
                            className = ClassName("btn-tall glow")
                            Send { size = 20 }
                        }
                    }
                    p {
                        className = ClassName("text-xs text-muted")
                        +"Press Enter to send, Shift+Enter for new line"
                    }
                }
            }
        }
    }
