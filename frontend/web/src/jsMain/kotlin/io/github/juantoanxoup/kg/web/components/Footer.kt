package io.github.juantoanxoup.kg.web.components

import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.footer
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

/** Site footer (src/components/Footer.tsx). */
val Footer =
    FC<Props> {
        footer {
            className = ClassName("footer")
            div {
                className = ClassName("footer-inner")
                p {
                    className = ClassName("text-sm text-muted")
                    +"© 2025 KnowledgeGraph.AI. All rights reserved."
                }
                div {
                    className = ClassName("footer-links")
                    a {
                        href = "#privacy"
                        +"Privacy"
                    }
                    span {
                        className = ClassName("text-muted")
                        +"•"
                    }
                    a {
                        href = "#terms"
                        +"Terms"
                    }
                    span {
                        className = ClassName("text-muted")
                        +"•"
                    }
                    a {
                        href = "#docs"
                        +"Documentation"
                    }
                }
            }
        }
    }
