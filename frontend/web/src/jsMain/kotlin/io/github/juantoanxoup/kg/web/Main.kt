package io.github.juantoanxoup.kg.web

import react.create
import react.dom.client.createRoot
import web.dom.ElementId
import web.dom.document

/** Entry point (src/main.tsx). */
fun main() {
    val container = document.getElementById(ElementId("root")) ?: error("Element #root not found")
    createRoot(container).render(App.create())
}
