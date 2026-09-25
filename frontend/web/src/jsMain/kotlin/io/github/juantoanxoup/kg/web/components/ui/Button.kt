package io.github.juantoanxoup.kg.web.components.ui

import io.github.juantoanxoup.kg.web.lib.cn
import react.FC
import react.PropsWithChildren
import react.PropsWithClassName
import react.dom.events.MouseEventHandler
import react.dom.html.ReactHTML.button
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLButtonElement
import web.html.button

external interface ButtonProps :
    PropsWithChildren,
    PropsWithClassName {
    /** `default`, `outline`, or `ghost`. */
    var variant: String?

    /** `default`, `sm`, or `lg`. */
    var size: String?
    var disabled: Boolean?
    var title: String?
    var onClick: MouseEventHandler<HTMLButtonElement>?
}

/** shadcn `Button` equivalent. */
val Button =
    FC<ButtonProps> { props ->
        button {
            type = ButtonType.button
            className =
                ClassName(
                    cn(
                        "btn",
                        "btn-${props.variant ?: "default"}",
                        "btn-size-${props.size ?: "default"}",
                        props.className?.toString(),
                    ),
                )
            disabled = props.disabled ?: false
            title = props.title
            onClick = props.onClick
            +props.children
        }
    }
