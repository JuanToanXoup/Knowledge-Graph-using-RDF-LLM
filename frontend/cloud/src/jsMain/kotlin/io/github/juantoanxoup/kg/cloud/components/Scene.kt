package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.CloudRuntime
import io.github.juantoanxoup.kg.cloud.GraphData
import io.github.juantoanxoup.kg.cloud.Palette
import io.github.juantoanxoup.kg.cloud.externals.CameraControls
import io.github.juantoanxoup.kg.cloud.externals.Canvas
import io.github.juantoanxoup.kg.cloud.externals.colorElement
import io.github.juantoanxoup.kg.cloud.externals.fogElement
import react.FC
import react.Props
import kotlin.js.json

external interface SceneProps : Props {
    var runtime: CloudRuntime
    var data: GraphData
    var selectedId: String?
    var searchActive: Boolean
    var repackTick: Int
    var labelFont: String?
    var onSelect: (String?) -> Unit
}

private const val FOG_NEAR = 700
private const val FOG_FAR = 2400

/** term-graph's Scene: paper background and fog, the layers, camera-controls, the rig, and the duotone pass. */
val Scene =
    FC<SceneProps> { props ->
        Canvas {
            camera = json("position" to arrayOf(126, 168, 420), "fov" to 50, "near" to 1, "far" to 4000)
            dpr = arrayOf(1, 2)
            colorElement {
                attach = "background"
                args = arrayOf(Palette.PAPER)
            }
            fogElement {
                attach = "fog"
                args = arrayOf(Palette.PAPER, FOG_NEAR, FOG_FAR)
            }
            MotionDriver { runtime = props.runtime }
            NodesLayer {
                runtime = props.runtime
                onSelect = props.onSelect
            }
            FocusRing { runtime = props.runtime }
            EdgesLayer { runtime = props.runtime }
            EdgeParticles { runtime = props.runtime }
            LabelsLayer {
                runtime = props.runtime
                labelFont = props.labelFont
            }
            CameraControls {
                makeDefault = true
                smoothTime = 0.28
                draggingSmoothTime = 0.1
                azimuthRotateSpeed = 0.6
                polarRotateSpeed = 0.6
                minDistance = 70.0
                maxDistance = 1200.0
            }
            CameraRig {
                runtime = props.runtime
                selectedId = props.selectedId
                searchActive = props.searchActive
                repackTick = props.repackTick
            }
            Effects {
                data = props.data
                selectedId = props.selectedId
            }
        }
    }
