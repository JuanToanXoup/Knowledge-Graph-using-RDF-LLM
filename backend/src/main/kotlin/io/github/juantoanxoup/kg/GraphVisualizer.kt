package io.github.juantoanxoup.kg

import com.mxgraph.layout.mxFastOrganicLayout
import com.mxgraph.util.mxCellRenderer
import com.mxgraph.util.mxConstants
import org.jgrapht.Graph
import org.jgrapht.ext.JGraphXAdapter
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Directed edge carrying the predicate label. `toString` is what the renderer prints. */
class LabeledEdge : DefaultEdge() {
    var label: String = ""

    override fun toString(): String = label
}

/** Renders the graph to PNG with JGraphT and JGraphX (graph_visualizer.py). */
class GraphVisualizer(
    private val kg: KnowledgeGraphBuilder,
) {
    private val log = LoggerFactory.getLogger(GraphVisualizer::class.java)

    /** One node per distinct label, one edge per triple; parallel edges collapse and the last label wins. */
    fun createGraph(): DefaultDirectedGraph<String, LabeledEdge> =
        kg.read { model ->
            val graph = DefaultDirectedGraph<String, LabeledEdge>(LabeledEdge::class.java)
            for (statement in model.listStatements()) {
                val subject = kg.label(statement.subject)
                val obj = kg.label(statement.`object`)
                graph.addVertex(subject)
                graph.addVertex(obj)
                val edge = graph.getEdge(subject, obj) ?: graph.addEdge(subject, obj)
                edge?.label = kg.label(statement.predicate)
            }
            graph
        }

    fun visualize(
        path: Path,
        maxNodes: Int = Config.MAX_VISUALIZATION_NODES,
    ) {
        var graph: Graph<String, LabeledEdge> = createGraph()
        if (graph.vertexSet().size > maxNodes) {
            val top =
                graph
                    .vertexSet()
                    .sortedByDescending { graph.degreeOf(it) }
                    .take(maxNodes)
                    .toSet()
            graph = AsSubgraph(graph, top)
        }

        val adapter = JGraphXAdapter(graph)
        adapter.stylesheet.defaultVertexStyle.apply {
            put(mxConstants.STYLE_FILLCOLOR, "#ADD8E6")
            put(mxConstants.STYLE_STROKECOLOR, "#9DB4C0")
            put(mxConstants.STYLE_FONTCOLOR, "#000000")
            put(mxConstants.STYLE_FONTSTYLE, mxConstants.FONT_BOLD)
            put(mxConstants.STYLE_FONTSIZE, 16)
            put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE)
        }
        adapter.stylesheet.defaultEdgeStyle.apply {
            put(mxConstants.STYLE_STROKECOLOR, "#808080")
            put(mxConstants.STYLE_FONTCOLOR, "#404040")
            put(mxConstants.STYLE_FONTSIZE, 12)
            put(mxConstants.STYLE_ENDSIZE, Config.ARROW_SIZE)
        }
        for (cell in adapter.vertexToCellMap.values) {
            cell.geometry.width = Config.NODE_DIAMETER_PX.toDouble()
            cell.geometry.height = Config.NODE_DIAMETER_PX.toDouble()
        }
        mxFastOrganicLayout(adapter)
            .apply {
                forceConstant = LAYOUT_FORCE_CONSTANT
                maxIterations = LAYOUT_ITERATIONS.toDouble()
            }.execute(adapter.defaultParent)

        val image =
            mxCellRenderer.createBufferedImage(adapter, null, 1.0, Color.WHITE, true, null)
                ?: BufferedImage(Config.FIGURE_WIDTH_PX, Config.FIGURE_HEIGHT_PX, BufferedImage.TYPE_INT_ARGB)
        path.parent?.let { Files.createDirectories(it) }
        ImageIO.write(image, "png", path.toFile())
        log.info("Graph visualization saved to {}", path)
    }

    private companion object {
        const val LAYOUT_FORCE_CONSTANT = 220.0
        const val LAYOUT_ITERATIONS = 50
    }
}
