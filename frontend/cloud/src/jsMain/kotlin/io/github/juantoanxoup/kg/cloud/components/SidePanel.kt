package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.NeighborGroup
import io.github.juantoanxoup.kg.cloud.TermNode
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.aside
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.em
import react.dom.html.ReactHTML.footer
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface SidePanelProps : Props {
    var node: TermNode
    var index: Int
    var total: Int
    var groups: List<NeighborGroup>
    var onSelect: (String) -> Unit
    var onClose: () -> Unit
    var onPrev: () -> Unit
    var onNext: () -> Unit
}

/** term-graph's SidePanel: group and index, the term, its summary and facts, neighbours by relation, prev/next. */
val SidePanel =
    FC<SidePanelProps> { props ->
        aside {
            className = ClassName("side-panel")
            header {
                className = ClassName("side-panel-header")
                span {
                    className = ClassName("side-panel-group")
                    +props.node.group
                }
                span {
                    className = ClassName("side-panel-index")
                    +(props.index + 1).toString().padStart(2, '0')
                    +" "
                    em { +"/ ${props.total}" }
                }
                button {
                    className = ClassName("icon-button")
                    ariaLabel = "Close"
                    onClick = { props.onClose() }
                    +"✕"
                }
            }
            h1 {
                className = ClassName("side-panel-title")
                +props.node.label
            }
            p {
                className = ClassName("side-panel-summary")
                +props.node.summary
            }
            if (props.node.description.isNotBlank()) {
                section {
                    h2 {
                        className = ClassName("side-panel-label")
                        +"Facts"
                    }
                    p {
                        className = ClassName("side-panel-description")
                        +props.node.description
                    }
                }
            }
            for (g in props.groups) {
                if (g.nodes.isEmpty()) continue
                section {
                    key = Key(g.kind ?: "·untyped")
                    h2 {
                        className = ClassName("side-panel-label")
                        +(g.kind ?: "Connects to")
                    }
                    div {
                        className = ClassName("chip-row")
                        for (n in g.nodes) {
                            button {
                                key = Key(n.id)
                                className = ClassName("chip")
                                onClick = { props.onSelect(n.id) }
                                +n.label
                            }
                        }
                    }
                }
            }
            footer {
                className = ClassName("side-panel-footer")
                button {
                    className = ClassName("nav-button")
                    onClick = { props.onPrev() }
                    +"‹ Prev"
                }
                button {
                    className = ClassName("nav-button")
                    onClick = { props.onNext() }
                    +"Next ›"
                }
            }
        }
    }
