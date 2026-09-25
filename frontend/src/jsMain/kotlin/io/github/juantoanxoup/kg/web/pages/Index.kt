package io.github.juantoanxoup.kg.web.pages

import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import web.cssom.ClassName

/** Placeholder page kept from the generated project (src/pages/Index.tsx). Not routed. */
val Index =
    FC<Props> {
        div {
            className = ClassName("not-found")
            div {
                className = ClassName("text-center")
                h1 { +"Welcome to Your Blank App" }
                p { +"Start building your amazing project here!" }
            }
        }
    }
