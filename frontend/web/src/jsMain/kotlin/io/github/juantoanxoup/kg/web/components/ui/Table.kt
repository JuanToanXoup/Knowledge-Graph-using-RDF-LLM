package io.github.juantoanxoup.kg.web.components.ui

import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import web.cssom.ClassName

/** shadcn table primitives. */
val Table =
    FC<PropsWithChildren> { props ->
        table {
            className = ClassName("table")
            +props.children
        }
    }
val TableHeader = FC<PropsWithChildren> { props -> thead { +props.children } }
val TableBody = FC<PropsWithChildren> { props -> tbody { +props.children } }
val TableRow = FC<PropsWithChildren> { props -> tr { +props.children } }
val TableHead = FC<PropsWithChildren> { props -> th { +props.children } }
val TableCell = FC<PropsWithChildren> { props -> td { +props.children } }
