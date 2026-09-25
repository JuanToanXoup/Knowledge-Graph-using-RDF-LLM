package io.github.juantoanxoup.kg.web.components.ui

import io.github.juantoanxoup.kg.web.lib.cn
import react.FC
import react.PropsWithChildren
import react.PropsWithClassName
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

/** shadcn `Card` equivalent. */
val Card =
    FC<PropsWithChildren> { props ->
        div {
            className = ClassName(cn("card", (props as? PropsWithClassName)?.className?.toString()))
            +props.children
        }
    }

external interface CardProps :
    PropsWithChildren,
    PropsWithClassName

/** Card with an extra class. */
val StyledCard =
    FC<CardProps> { props ->
        div {
            className = ClassName(cn("card", props.className?.toString()))
            +props.children
        }
    }
