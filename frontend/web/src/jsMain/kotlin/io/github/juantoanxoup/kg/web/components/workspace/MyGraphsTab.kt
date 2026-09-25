package io.github.juantoanxoup.kg.web.components.workspace

import io.github.juantoanxoup.kg.web.components.ui.Button
import io.github.juantoanxoup.kg.web.components.ui.Input
import io.github.juantoanxoup.kg.web.hooks.ToastStore
import io.github.juantoanxoup.kg.web.hooks.ToastVariant
import io.github.juantoanxoup.kg.web.lib.Api
import io.github.juantoanxoup.kg.web.lib.Download
import io.github.juantoanxoup.kg.web.lib.FolderOpen
import io.github.juantoanxoup.kg.web.lib.GraphSummary
import io.github.juantoanxoup.kg.web.lib.GraphsResponse
import io.github.juantoanxoup.kg.web.lib.Trash2
import io.github.juantoanxoup.kg.web.lib.Upload
import io.github.juantoanxoup.kg.web.lib.localDate
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useEffectOnce
import react.useState
import tanstack.react.router.useNavigate
import tanstack.router.core.RoutePath
import web.console.console
import web.cssom.ClassName
import web.window.WindowTarget
import web.window._blank
import web.window.window

private val scope = MainScope()

/** Graph cards with filter, open, download, delete (src/components/workspace/MyGraphsTab.tsx). */
val MyGraphsTab =
    FC<Props> {
        var graphs by useState<List<GraphSummary>>(emptyList())
        var search by useState("")
        val navigate = useNavigate()

        suspend fun load() {
            runCatching { Api.getJson<GraphsResponse>("/graphs") }
                .onSuccess { graphs = it.graphs }
                .onFailure { console.error(it) }
        }

        useEffectOnce { load() }

        fun deleteGraph(graphId: String) {
            scope.launch {
                try {
                    Api.delete("/graph/$graphId")
                    graphs = graphs.filter { it.graphId != graphId }
                    ToastStore.toast("Graph deleted")
                } catch (e: Throwable) {
                    ToastStore.toast("Error", "Failed to delete graph", ToastVariant.DESTRUCTIVE)
                }
            }
        }

        val filtered =
            graphs.filter {
                it.graphId.lowercase().contains(search.lowercase()) ||
                    it.filename.lowercase().contains(search.lowercase())
            }

        div {
            className = ClassName("stack-6")
            div {
                className = ClassName("row-between wrap")
                div {
                    h2 {
                        className = ClassName("page-title")
                        +"My Graphs"
                    }
                    p {
                        className = ClassName("text-muted")
                        +"Manage your knowledge graphs"
                    }
                }
                Button {
                    onClick = { navigate { to = RoutePath("/") } }
                    className = ClassName("glow")
                    Upload { size = 16 }
                    +"Upload New Document"
                }
            }

            Input {
                value = search
                onChange = { search = it.target.value }
                placeholder = "Search graphs..."
            }

            div {
                className = ClassName("grid grid-sm-2 grid-lg-3")
                for (graph in filtered) {
                    div {
                        key = Key(graph.graphId)
                        className = ClassName("card card-hover")
                        div {
                            className = ClassName("icon-box")
                            style = js.objects.unsafeJso { marginBottom = "1rem".unsafeCast<web.cssom.Length>() }
                            FolderOpen { size = 24 }
                        }
                        h3 {
                            className = ClassName("text-lg font-semibold truncate")
                            +graph.filename
                        }
                        p {
                            className = ClassName("text-xs text-muted truncate")
                            +"ID: ${graph.graphId.take(16)}..."
                        }
                        div {
                            className = ClassName("row wrap text-sm text-muted")
                            span { +"${graph.entitiesCount} entities" }
                            span { +"•" }
                            span { +"${graph.relationsCount} relations" }
                        }
                        p {
                            className = ClassName("text-xs text-muted")
                            +"Created: ${localDate(graph.createdAt)}"
                        }
                        div {
                            className = ClassName("row")
                            style = js.objects.unsafeJso { marginTop = "1rem".unsafeCast<web.cssom.Length>() }
                            Button {
                                className = ClassName("grow")
                                onClick = { navigate { to = RoutePath("/workspace/${graph.graphId}") } }
                                +"Open"
                            }
                            Button {
                                variant = "outline"
                                size = "sm"
                                onClick =
                                    { window.open(Api.url("/download_graph/${graph.graphId}"), WindowTarget._blank) }
                                Download { size = 16 }
                            }
                            Button {
                                variant = "outline"
                                size = "sm"
                                onClick = { deleteGraph(graph.graphId) }
                                Trash2 {
                                    size = 16
                                    className = ClassName("text-destructive")
                                }
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                div {
                    className = ClassName("text-center center-message")
                    p {
                        className = ClassName("text-muted")
                        +"No graphs found"
                    }
                }
            }
        }
    }
