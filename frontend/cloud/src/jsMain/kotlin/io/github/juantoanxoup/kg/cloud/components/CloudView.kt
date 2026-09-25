package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.CloudRuntime
import io.github.juantoanxoup.kg.cloud.GraphData
import io.github.juantoanxoup.kg.cloud.KeyAction
import io.github.juantoanxoup.kg.cloud.buildCloud
import io.github.juantoanxoup.kg.cloud.degreeMap
import io.github.juantoanxoup.kg.cloud.keyAction
import io.github.juantoanxoup.kg.cloud.neighborGroups
import kotlinx.coroutines.awaitCancellation
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useEffect
import react.useMemo
import react.useState
import web.cssom.ClassName
import web.events.invoke
import web.html.HTMLInputElement
import web.html.HTMLTextAreaElement
import web.timers.clearTimeout
import web.timers.setTimeout
import web.window.keyDownEvent
import web.window.window

external interface CloudViewProps : Props {
    var data: GraphData

    /** URL of the label font; term-graph ships JetBrains Mono Medium. */
    var labelFont: String?
}

private const val MAX_MATCHES = 15
private const val REPACK_DELAY_MS = 250

/**
 * term-graph's App for one dataset: the scene, the search box, the hint, and the side panel of the focused
 * term. Selection and query live here; the scene reads them through the shared [CloudRuntime].
 */
val CloudView =
    FC<CloudViewProps> { props ->
        val data = props.data
        val (selectedId, setSelectedId) = useState<String?>(null)
        val (query, setQuery) = useState("")
        val (repackTick, setRepackTick) = useState(0)

        val degrees = useMemo(data) { degreeMap(data) }
        val runtime = useMemo(data) { CloudRuntime(buildCloud(data, degrees)) }
        val indexById = useMemo(data) { data.nodes.withIndex().associate { (i, n) -> n.id to i } }

        // Like the original's search: at most 15 hits; a query with zero matches leaves the graph untouched.
        val matchIdx =
            useMemo(data, query) {
                val q = query.trim().lowercase()
                if (q.isEmpty()) {
                    null
                } else {
                    data.nodes
                        .withIndex()
                        .filter { (_, n) -> n.label.lowercase().contains(q) }
                        .take(MAX_MATCHES)
                        .map { it.index }
                        .toIntArray()
                }
            }
        val searchActive = matchIdx != null && matchIdx.isNotEmpty()
        val selectedIndex = selectedId?.let { indexById[it] }

        runtime.selIdx = selectedIndex
        runtime.searchActive = searchActive
        runtime.queryActive = query.isNotBlank()

        // Visibility targets apply at once; the cluster layout is solved 250ms behind the keystrokes.
        useEffect(matchIdx, runtime) {
            if (matchIdx == null || matchIdx.isEmpty()) {
                runtime.repack.applyMatches(null)
                setRepackTick { it + 1 }
                return@useEffect
            }
            runtime.repack.applyMatches(matchIdx)
            val timer =
                setTimeout({
                    runtime.repack.solveRepack(matchIdx)
                    setRepackTick { it + 1 }
                }, REPACK_DELAY_MS)
            try {
                awaitCancellation()
            } finally {
                clearTimeout(timer)
            }
        }

        // A new dataset invalidates the selection.
        useEffect(data) {
            setSelectedId(null)
            setQuery("")
        }

        fun select(id: String?) = setSelectedId(id)

        fun step(delta: Int) {
            val current = selectedIndex ?: return
            val next = (current + delta + data.nodes.size) % data.nodes.size
            select(data.nodes[next].id)
        }

        // Keyboard: Escape clears the focus; with a term focused, the arrow keys step through the terms. The
        // subscription ends with the effect, so keys reach this view only while it is mounted.
        useEffect(data, selectedIndex) {
            window.keyDownEvent().collect { event ->
                val inTextField = event.target is HTMLInputElement || event.target is HTMLTextAreaElement
                when (keyAction(event.key, inTextField, selectedIndex != null)) {
                    KeyAction.CLEAR_SELECTION -> select(null)
                    KeyAction.STEP_PREV -> step(-1)
                    KeyAction.STEP_NEXT -> step(1)
                    null -> Unit
                }
            }
        }

        div {
            className = ClassName("cloud-view")
            div {
                className = ClassName("canvas-shift")
                asDynamic()["data-shift"] = selectedIndex != null
                Scene {
                    this.runtime = runtime
                    this.data = data
                    this.selectedId = selectedId
                    this.searchActive = searchActive
                    this.repackTick = repackTick
                    labelFont = props.labelFont
                    onSelect = { select(it) }
                }
            }
            div {
                className = ClassName("hint")
                +"Drag to rotate • Scroll to zoom"
            }
            SearchBox {
                this.query = query
                matchCount = matchIdx?.size ?: 0
                onQuery = { setQuery(it) }
                onCommit = { matchIdx?.firstOrNull()?.let { select(data.nodes[it].id) } }
            }
            if (selectedIndex != null) {
                SidePanel {
                    node = data.nodes[selectedIndex]
                    index = selectedIndex
                    total = data.nodes.size
                    groups = neighborGroups(data, data.nodes[selectedIndex].id)
                    onSelect = { select(it) }
                    onClose = { select(null) }
                    onPrev = { step(-1) }
                    onNext = { step(1) }
                }
            }
        }
    }
