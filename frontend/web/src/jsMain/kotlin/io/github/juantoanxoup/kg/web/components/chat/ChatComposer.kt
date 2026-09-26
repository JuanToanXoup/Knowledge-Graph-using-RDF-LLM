package io.github.juantoanxoup.kg.web.components.chat

import io.github.juantoanxoup.kg.web.lib.Send
import io.github.juantoanxoup.kg.web.lib.Sparkles
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.textarea
import react.useEffect
import react.useRef
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLTextAreaElement
import web.html.submit

external interface ChatComposerProps : Props {
    var value: String
    var placeholder: String
    var disabled: Boolean
    var onChange: (String) -> Unit
    var onSend: () -> Unit

    /** The sparkle before the field, as the floating card shows it. */
    var leadingIcon: Boolean

    /** Take the keyboard focus when shown. */
    var autoFocus: Boolean
}

/**
 * The message field: grows with its lines up to a limit, Enter sends, Shift+Enter keeps a new line, the send
 * button is disabled while the field is empty or an answer is on its way.
 */
val ChatComposer =
    FC<ChatComposerProps> { props ->
        val field = useRef<HTMLTextAreaElement>()

        // Grow with the content; `field-sizing: content` does the same where the browser supports it.
        useEffect(props.value) {
            val el = field.current ?: return@useEffect
            el.style.height = "auto"
            el.style.height = "${el.scrollHeight}px"
        }
        useEffect(props.autoFocus) {
            if (props.autoFocus) field.current?.focus()
        }

        val canSend = props.value.isNotBlank() && !props.disabled
        form {
            className = ClassName("chat-composer")
            ariaLabel = "Message area"
            onSubmit = {
                it.preventDefault()
                if (canSend) props.onSend()
            }
            div {
                className = ClassName("chat-field")
                if (props.leadingIcon) {
                    Sparkles {
                        size = 18
                        className = ClassName("chat-field-icon")
                    }
                }
                textarea {
                    ref = field
                    rows = 1
                    value = props.value
                    placeholder = props.placeholder
                    ariaLabel = props.placeholder
                    name = "userMessage"
                    onChange = { props.onChange(it.target.value) }
                    onKeyDown = { event ->
                        if (event.key == "Enter" && !event.shiftKey) {
                            event.preventDefault()
                            if (canSend) props.onSend()
                        }
                    }
                }
                button {
                    className = ClassName("chat-send")
                    type = ButtonType.submit
                    ariaLabel = "Send"
                    disabled = !canSend
                    Send { size = 18 }
                }
            }
        }
    }
