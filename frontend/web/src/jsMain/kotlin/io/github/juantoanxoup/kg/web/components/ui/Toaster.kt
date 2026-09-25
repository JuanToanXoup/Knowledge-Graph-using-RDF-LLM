package io.github.juantoanxoup.kg.web.components.ui

import io.github.juantoanxoup.kg.web.hooks.ToastVariant
import io.github.juantoanxoup.kg.web.hooks.useToasts
import io.github.juantoanxoup.kg.web.lib.cn
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

/** Renders the toast queue (src/components/ui/toaster.tsx). */
val Toaster =
    FC<Props> {
        val toasts = useToasts()
        div {
            className = ClassName("toaster")
            for (toast in toasts) {
                div {
                    key = Key(toast.id.toString())
                    className =
                        ClassName(
                            cn(
                                "toast",
                                if (toast.variant ==
                                    ToastVariant.DESTRUCTIVE
                                ) {
                                    "toast-destructive"
                                } else {
                                    null
                                },
                            ),
                        )
                    div {
                        className = ClassName("toast-title")
                        +toast.title
                    }
                    toast.description?.let {
                        div {
                            className = ClassName("toast-description")
                            +it
                        }
                    }
                }
            }
        }
    }
