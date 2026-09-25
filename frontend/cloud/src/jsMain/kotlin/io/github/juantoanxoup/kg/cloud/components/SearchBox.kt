package io.github.juantoanxoup.kg.cloud.components

import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.ClassName

external interface SearchBoxProps : Props {
    var query: String
    var matchCount: Int
    var onQuery: (String) -> Unit

    /** Enter with matches: focus the first one. */
    var onCommit: () -> Unit
}

/** term-graph's SearchBox: a magnifier toggle that opens a pill field; Enter commits, Escape closes. */
val SearchBox =
    FC<SearchBoxProps> { props ->
        var open by useState(false)

        fun close() {
            props.onQuery("")
            open = false
        }

        if (!open) {
            button {
                className = ClassName("search-toggle")
                ariaLabel = "Search the graph"
                onClick = { open = true }
                +"⌕"
            }
        } else {
            div {
                className = ClassName("search-box")
                div {
                    className = ClassName("search-field")
                    span {
                        className = ClassName("search-glyph")
                        +"⌕"
                    }
                    input {
                        autoFocus = true
                        value = props.query
                        placeholder = "Search the graph"
                        onChange = { props.onQuery(it.target.value) }
                        onKeyDown = {
                            if (it.key == "Enter" && props.matchCount > 0) {
                                props.onCommit()
                                close()
                            }
                            if (it.key == "Escape") close()
                        }
                    }
                    button {
                        className = ClassName("icon-button")
                        ariaLabel = "Clear search"
                        onClick = { close() }
                        +"✕"
                    }
                }
                if (props.query.isNotEmpty()) {
                    div {
                        className = ClassName("search-count")
                        +"${props.matchCount} ${if (props.matchCount == 1) "TERM" else "TERMS"}"
                    }
                }
            }
        }
    }
