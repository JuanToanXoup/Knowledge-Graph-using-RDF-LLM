package io.github.juantoanxoup.kg.web.pages

import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import react.useEffect
import tanstack.react.router.useLocation
import web.console.console
import web.cssom.ClassName

/** 404 page (src/pages/NotFound.tsx). */
val NotFound =
    FC<Props> {
        val location = useLocation()
        useEffect(location.pathname) {
            console.error("404 Error: User attempted to access non-existent route:", location.pathname)
        }
        div {
            className = ClassName("not-found")
            div {
                className = ClassName("text-center")
                h1 { +"404" }
                p { +"Oops! Page not found" }
                a {
                    href = "/"
                    +"Return to Home"
                }
            }
        }
    }
