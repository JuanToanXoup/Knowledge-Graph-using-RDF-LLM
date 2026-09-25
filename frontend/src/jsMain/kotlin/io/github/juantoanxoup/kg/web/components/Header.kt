package io.github.juantoanxoup.kg.web.components

import io.github.juantoanxoup.kg.web.components.ui.Button
import io.github.juantoanxoup.kg.web.lib.Brain
import io.github.juantoanxoup.kg.web.lib.Home
import io.github.juantoanxoup.kg.web.lib.LayoutDashboard
import io.github.juantoanxoup.kg.web.lib.cn
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.nav
import react.dom.html.ReactHTML.span
import tanstack.react.router.Link
import tanstack.react.router.useLocation
import tanstack.router.core.RoutePath
import web.cssom.ClassName

external interface HeaderProps : Props {
    var showNav: Boolean?

    /** Undefined hides the status dot; true or false shows it green or red. */
    var apiConnected: Boolean?
}

/** Fixed glass header with brand, navigation, and API status (src/components/Header.tsx). */
val Header =
    FC<HeaderProps> { props ->
        val location = useLocation()
        val showNav = props.showNav ?: true

        header {
            className = ClassName("header")
            div {
                className = ClassName("header-inner")

                Link {
                    to = RoutePath("/")
                    className = ClassName("brand")
                    Brain {
                        size = 24
                        className = ClassName("text-primary")
                    }
                    span {
                        className = ClassName("gradient-text")
                        +"KnowledgeGraph.AI"
                    }
                }

                if (showNav) {
                    nav {
                        className = ClassName("nav")
                        for (item in listOf("Home", "Features", "How it Works")) {
                            val isActive = item == "Home" && location.pathname == "/"
                            a {
                                key = Key(item)
                                href = "#" + item.lowercase().replace(Regex("\\s+"), "-")
                                className = ClassName(cn("nav-link", if (isActive) "nav-link-active" else null))
                                +item
                            }
                        }
                        Link {
                            to = RoutePath("/workspace")
                            Button {
                                variant = "outline"
                                size = "sm"
                                LayoutDashboard { size = 16 }
                                +"Workspace"
                            }
                        }
                    }
                } else {
                    div {
                        className = ClassName("row")
                        Link {
                            to = RoutePath("/")
                            Button {
                                variant = "outline"
                                size = "sm"
                                Home { size = 16 }
                                +"Home"
                            }
                        }
                        Link {
                            to = RoutePath("/workspace")
                            Button {
                                variant = "outline"
                                size = "sm"
                                LayoutDashboard { size = 16 }
                                +"My Graphs"
                            }
                        }
                    }
                }

                props.apiConnected?.let { connected ->
                    div {
                        className = ClassName(cn("status-dot", if (connected) "status-on" else "status-off"))
                        title = if (connected) "API Connected" else "API Disconnected"
                    }
                }
            }
        }
    }
