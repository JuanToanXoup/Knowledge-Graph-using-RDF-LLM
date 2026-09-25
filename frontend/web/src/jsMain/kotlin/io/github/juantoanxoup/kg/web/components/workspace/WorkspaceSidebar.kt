package io.github.juantoanxoup.kg.web.components.workspace

import io.github.juantoanxoup.kg.web.components.ui.Button
import io.github.juantoanxoup.kg.web.lib.BarChart3
import io.github.juantoanxoup.kg.web.lib.ChevronLeft
import io.github.juantoanxoup.kg.web.lib.ChevronRight
import io.github.juantoanxoup.kg.web.lib.Code
import io.github.juantoanxoup.kg.web.lib.FolderOpen
import io.github.juantoanxoup.kg.web.lib.IconProps
import io.github.juantoanxoup.kg.web.lib.MessageSquare
import io.github.juantoanxoup.kg.web.lib.Network
import io.github.juantoanxoup.kg.web.lib.Orbit
import io.github.juantoanxoup.kg.web.lib.Search
import io.github.juantoanxoup.kg.web.lib.Settings
import io.github.juantoanxoup.kg.web.lib.cn
import io.github.juantoanxoup.kg.web.pages.TabType
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.aside
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.nav
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import web.cssom.ClassName
import web.html.ButtonType
import web.html.button

external interface WorkspaceSidebarProps : Props {
    var activeTab: TabType
    var onTabChange: (TabType) -> Unit
    var isOpen: Boolean
    var onToggle: () -> Unit
    var filename: String
}

private val icons: Map<TabType, FC<IconProps>> =
    mapOf(
        TabType.OVERVIEW to BarChart3,
        TabType.CLOUD to Orbit,
        TabType.SEARCH to Search,
        TabType.CHAT to MessageSquare,
        TabType.ENTITY to Network,
        TabType.SPARQL to Code,
        TabType.GRAPHS to FolderOpen,
        TabType.SETTINGS to Settings,
    )

/** Collapsible tab sidebar (src/components/workspace/WorkspaceSidebar.tsx). */
val WorkspaceSidebar =
    FC<WorkspaceSidebarProps> { props ->
        if (props.isOpen) {
            div {
                className = ClassName("sidebar-overlay")
                onClick = { props.onToggle() }
            }
        }
        aside {
            className = ClassName(cn("sidebar", if (props.isOpen) "sidebar-open" else null))
            div {
                className = ClassName("sidebar-header")
                if (props.isOpen) {
                    div {
                        className = ClassName("grow")
                        p {
                            className = ClassName("text-xs text-muted")
                            +"Current Graph"
                        }
                        p {
                            className = ClassName("text-sm font-medium truncate")
                            +props.filename
                        }
                    }
                }
                Button {
                    variant = "ghost"
                    size = "sm"
                    onClick = { props.onToggle() }
                    if (props.isOpen) ChevronLeft { size = 16 } else ChevronRight { size = 16 }
                }
            }
            nav {
                className = ClassName("sidebar-nav")
                for (tab in TabType.entries) {
                    val icon = icons.getValue(tab)
                    button {
                        key = Key(tab.name)
                        type = ButtonType.button
                        className =
                            ClassName(cn("sidebar-item", if (props.activeTab == tab) "sidebar-item-active" else null))
                        onClick = { props.onTabChange(tab) }
                        icon { size = 20 }
                        if (props.isOpen) span { +tab.label }
                    }
                }
            }
        }
    }
