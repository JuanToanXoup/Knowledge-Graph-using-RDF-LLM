package io.github.juantoanxoup.kg.web.components.ui

import io.github.juantoanxoup.kg.web.lib.cn
import react.FC
import react.PropsWithClassName
import react.dom.events.ChangeEventHandler
import react.dom.events.KeyboardEventHandler
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.textarea
import web.cssom.ClassName
import web.html.HTMLInputElement
import web.html.HTMLTextAreaElement
import web.html.InputType
import web.html.text

external interface InputProps : PropsWithClassName {
    var value: String?
    var placeholder: String?
    var type: InputType?
    var onChange: ChangeEventHandler<HTMLInputElement, HTMLInputElement>?
    var onKeyDown: KeyboardEventHandler<HTMLInputElement>?
}

/** shadcn `Input` equivalent. */
val Input =
    FC<InputProps> { props ->
        input {
            className = ClassName(cn("input", props.className?.toString()))
            type = props.type ?: InputType.text
            value = props.value ?: ""
            placeholder = props.placeholder
            onChange = props.onChange
            onKeyDown = props.onKeyDown
        }
    }

external interface TextareaProps : PropsWithClassName {
    var value: String?
    var placeholder: String?
    var onChange: ChangeEventHandler<HTMLTextAreaElement, HTMLTextAreaElement>?
    var onKeyDown: KeyboardEventHandler<HTMLTextAreaElement>?
}

/** shadcn `Textarea` equivalent. */
val Textarea =
    FC<TextareaProps> { props ->
        textarea {
            className = ClassName(cn("textarea", props.className?.toString()))
            value = props.value ?: ""
            placeholder = props.placeholder
            onChange = props.onChange
            onKeyDown = props.onKeyDown
        }
    }
