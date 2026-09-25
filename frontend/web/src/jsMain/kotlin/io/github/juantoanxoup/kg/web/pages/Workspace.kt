package io.github.juantoanxoup.kg.web.pages

import io.github.juantoanxoup.kg.web.GRAPH_ID_PARAM
import io.github.juantoanxoup.kg.web.components.Header
import io.github.juantoanxoup.kg.web.components.ParticleBackground
import io.github.juantoanxoup.kg.web.components.workspace.ChatTab
import io.github.juantoanxoup.kg.web.components.workspace.CloudTab
import io.github.juantoanxoup.kg.web.components.workspace.EntityTab
import io.github.juantoanxoup.kg.web.components.workspace.MyGraphsTab
import io.github.juantoanxoup.kg.web.components.workspace.OverviewTab
import io.github.juantoanxoup.kg.web.components.workspace.SearchTab
import io.github.juantoanxoup.kg.web.components.workspace.SparqlTab
import io.github.juantoanxoup.kg.web.components.workspace.WorkspaceSidebar
import io.github.juantoanxoup.kg.web.hooks.useIsMobile
import io.github.juantoanxoup.kg.web.lib.Api
import io.github.juantoanxoup.kg.web.lib.GraphInfo
import io.github.juantoanxoup.kg.web.lib.cn
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main
import react.useEffect
import react.useEffectOnce
import react.useState
import tanstack.react.router.useParams
import web.console.console
import web.cssom.ClassName

/** Sidebar tabs, in menu order. */
enum class TabType(
    val label: String,
) {
    OVERVIEW("Overview"),
    CLOUD("3D Cloud"),
    SEARCH("Semantic Search"),
    CHAT("Chat & Q&A"),
    ENTITY("Entity Explorer"),
    SPARQL("SPARQL Query"),
    GRAPHS("My Graphs"),
    SETTINGS("Settings"),
}

/** Workspace shell with sidebar and tabs (src/pages/Workspace.tsx). */
val Workspace =
    FC<Props> {
        val graphId: String? = useParams()[GRAPH_ID_PARAM]
        var activeTab by useState(if (graphId != null) TabType.OVERVIEW else TabType.GRAPHS)
        var apiConnected by useState(false)
        var graphInfo by useState<GraphInfo?>(null)
        val isMobile = useIsMobile()
        var sidebarOpen by useState(!isMobile)

        useEffectOnce {
            apiConnected = runCatching { Api.get("/graphs") }.isSuccess
        }

        useEffect(graphId) {
            if (graphId != null) {
                runCatching { Api.getJson<GraphInfo>("/graph/$graphId") }
                    .onSuccess { graphInfo = it }
                    .onFailure { console.error(it) }
            }
        }

        useEffect(isMobile) {
            sidebarOpen = !isMobile
        }

        fun ChildrenBuilder.needsGraph(render: ChildrenBuilder.(String) -> Unit) {
            if (graphId != null) {
                render(graphId)
            } else {
                div {
                    className = ClassName("center-message")
                    +"Please select a graph from the My Graphs tab"
                }
            }
        }

        div {
            className = ClassName("page")
            ParticleBackground()
            Header {
                showNav = false
                this.apiConnected = apiConnected
            }

            div {
                className = ClassName("workspace")
                WorkspaceSidebar {
                    this.activeTab = activeTab
                    onTabChange = { tab ->
                        activeTab = tab
                        if (isMobile) sidebarOpen = false
                    }
                    isOpen = sidebarOpen
                    onToggle = { sidebarOpen = !sidebarOpen }
                    filename = graphInfo?.filename ?: graphId ?: "My Workspace"
                }

                main {
                    className =
                        ClassName(
                            cn(
                                "workspace-main",
                                if (sidebarOpen &&
                                    !isMobile
                                ) {
                                    "workspace-main-shifted"
                                } else {
                                    null
                                },
                            ),
                        )
                    div {
                        className = ClassName("container")
                        when (activeTab) {
                            TabType.OVERVIEW -> needsGraph { id -> OverviewTab { this.graphId = id } }
                            TabType.CLOUD -> needsGraph { id -> CloudTab { this.graphId = id } }
                            TabType.SEARCH -> needsGraph { id -> SearchTab { this.graphId = id } }
                            TabType.CHAT -> needsGraph { id -> ChatTab { this.graphId = id } }
                            TabType.ENTITY -> needsGraph { id -> EntityTab { this.graphId = id } }
                            TabType.SPARQL -> needsGraph { id -> SparqlTab { this.graphId = id } }
                            TabType.GRAPHS -> MyGraphsTab()
                            TabType.SETTINGS ->
                                div {
                                    className = ClassName("center-message")
                                    +"Coming soon..."
                                }
                        }
                    }
                }
            }
        }
    }
