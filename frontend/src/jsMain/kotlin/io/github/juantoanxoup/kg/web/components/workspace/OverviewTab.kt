package io.github.juantoanxoup.kg.web.components.workspace

import io.github.juantoanxoup.kg.web.components.ui.Button
import io.github.juantoanxoup.kg.web.lib.Api
import io.github.juantoanxoup.kg.web.lib.BarChart3
import io.github.juantoanxoup.kg.web.lib.Database
import io.github.juantoanxoup.kg.web.lib.EntitiesResponse
import io.github.juantoanxoup.kg.web.lib.GraphInfo
import io.github.juantoanxoup.kg.web.lib.IconProps
import io.github.juantoanxoup.kg.web.lib.Layers
import io.github.juantoanxoup.kg.web.lib.Network
import kotlinx.serialization.json.Json
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.pre
import react.useEffect
import react.useState
import web.console.console
import web.cssom.ClassName
import web.html.Hidden
import web.html.`false`
import web.html.`true`
import web.window.WindowTarget
import web.window._blank
import web.window.window

external interface GraphTabProps : Props {
    var graphId: String
}

private class Metric(
    val icon: FC<IconProps>,
    val label: String,
    val value: Int,
)

private val prettyJson = Json { prettyPrint = true }

/** Metrics, statistics JSON, and the PNG visualization (src/components/workspace/OverviewTab.tsx). */
val OverviewTab =
    FC<GraphTabProps> { props ->
        var info by useState<GraphInfo?>(null)
        var entityTypes by useState(0)
        var showStats by useState(false)
        var imageLoading by useState(true)
        var imageError by useState(false)
        val imageUrl = Api.url("/visualization/${props.graphId}")

        useEffect(props.graphId) {
            imageLoading = true
            imageError = false
            runCatching { Api.getJson<GraphInfo>("/graph/${props.graphId}") }
                .onSuccess { info = it }
                .onFailure { console.error(it) }
            runCatching { Api.getJson<EntitiesResponse>("/entities/${props.graphId}") }
                .onSuccess {
                    entityTypes =
                        it.entities
                            .map { e -> e.type }
                            .toSet()
                            .size
                }.onFailure { console.error(it) }
        }

        val metrics =
            listOf(
                Metric(Database, "Total Entities", info?.entitiesCount ?: 0),
                Metric(Network, "Total Relations", info?.relationsCount ?: 0),
                Metric(Layers, "Total Triples", info?.statistics?.totalTriples ?: 0),
                Metric(BarChart3, "Entity Types", entityTypes),
            )

        div {
            className = ClassName("stack-6")
            div {
                h2 {
                    className = ClassName("page-title")
                    +"Overview"
                }
                p {
                    className = ClassName("text-muted")
                    +"View key metrics and statistics for your knowledge graph"
                }
            }

            div {
                className = ClassName("grid grid-2 grid-keep grid-lg-4")
                for (metric in metrics) {
                    div {
                        key = Key(metric.label)
                        className = ClassName("card card-hover")
                        div {
                            className = ClassName("row-between")
                            div {
                                className = ClassName("grow")
                                p {
                                    className = ClassName("text-sm text-muted")
                                    +metric.label
                                }
                                p {
                                    className = ClassName("metric-value truncate")
                                    +metric.value.toString()
                                }
                            }
                            div {
                                className = ClassName("icon-box")
                                metric.icon { size = 24 }
                            }
                        }
                    }
                }
            }

            info?.statistics?.let { stats ->
                div {
                    className = ClassName("card")
                    div {
                        className = ClassName("row-between")
                        h3 {
                            className = ClassName("text-xl font-semibold")
                            +"Statistics"
                        }
                        Button {
                            variant = "outline"
                            size = "sm"
                            onClick = { showStats = !showStats }
                            +(if (showStats) "Hide JSON" else "Show JSON")
                        }
                    }
                    if (showStats) {
                        pre {
                            className = ClassName("pre")
                            +prettyJson.encodeToString(stats)
                        }
                    }
                }
            }

            div {
                className = ClassName("card")
                h3 {
                    className = ClassName("text-xl font-semibold")
                    +"Graph Visualization"
                }
                div {
                    className = ClassName("viz")
                    if (imageLoading && !imageError) {
                        div {
                            className = ClassName("text-center center-message")
                            div { className = ClassName("spinner") }
                            p {
                                className = ClassName("text-muted")
                                +"Loading visualization..."
                            }
                        }
                    }
                    if (imageError) {
                        div {
                            className = ClassName("text-center center-message")
                            p {
                                className = ClassName("text-muted")
                                +"Visualization not available"
                            }
                            p {
                                className = ClassName("text-xs text-muted")
                                +"The graph image may still be generating"
                            }
                        }
                    }
                    if (!imageError) {
                        img {
                            src = imageUrl
                            alt = "Knowledge Graph"
                            hidden = if (imageLoading) Hidden.`true` else Hidden.`false`
                            onLoad = { imageLoading = false }
                            onError = {
                                imageLoading = false
                                imageError = true
                                console.error("Failed to load visualization")
                            }
                        }
                    }
                    if (!imageLoading && !imageError) {
                        div {
                            className = ClassName("viz-actions")
                            Button {
                                size = "sm"
                                onClick = { window.open(imageUrl, WindowTarget._blank) }
                                +"View Full Size"
                            }
                        }
                    }
                }
            }
        }
    }
