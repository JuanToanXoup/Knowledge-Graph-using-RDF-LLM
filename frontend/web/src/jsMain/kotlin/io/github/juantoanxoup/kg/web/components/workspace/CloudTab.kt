package io.github.juantoanxoup.kg.web.components.workspace

import io.github.juantoanxoup.kg.cloud.GraphData
import io.github.juantoanxoup.kg.cloud.components.CloudView
import io.github.juantoanxoup.kg.web.lib.Api
import react.FC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.p
import react.useEffect
import react.useState
import web.console.console
import web.cssom.ClassName

/** term-graph's label font, served from this module's resources (see `fonts/OFL.md`). */
private const val LABEL_FONT = "/fonts/JetBrainsMono-Medium.ttf"

/** The graph as an interactive 3D cloud, rendered by the `:frontend:cloud` library from `/graph/{id}/cloud.json`. */
val CloudTab =
    FC<GraphTabProps> { props ->
        var data by useState<GraphData?>(null)
        var error by useState<String?>(null)

        useEffect(props.graphId) {
            data = null
            error = null
            runCatching { Api.getJson<GraphData>("/graph/${props.graphId}/cloud.json") }
                .onSuccess { data = it }
                .onFailure {
                    console.error(it)
                    error = "Failed to load the graph."
                }
        }

        div {
            className = ClassName("stack-6")
            div {
                h2 {
                    className = ClassName("page-title")
                    +"3D Cloud"
                }
                p {
                    className = ClassName("text-muted")
                    +"Entities as a drifting ink-on-paper cloud, sized by their connections; click one to focus it"
                }
            }
            val graph = data
            when {
                error != null ->
                    div {
                        className = ClassName("center-message text-muted")
                        +error!!
                    }
                graph == null ->
                    div {
                        className = ClassName("center-message")
                        div { className = ClassName("spinner") }
                        p {
                            className = ClassName("text-muted")
                            +"Laying out the graph..."
                        }
                    }
                graph.nodes.isEmpty() ->
                    div {
                        className = ClassName("center-message text-muted")
                        +"This graph has no entities to show."
                    }
                else ->
                    CloudView {
                        this.data = graph
                        labelFont = Api.url(LABEL_FONT)
                    }
            }
        }
    }
