@file:JsModule("@react-three/fiber")
@file:JsNonModule

package io.github.juantoanxoup.kg.cloud.externals

import react.FC
import react.PropsWithChildren
import react.PropsWithClassName

external interface CanvasProps :
    PropsWithChildren,
    PropsWithClassName {
    /** `{ position: [x, y, z], fov, near, far }`, read once when the canvas mounts. */
    var camera: dynamic

    /** Device pixel ratio or `[min, max]` range. */
    var dpr: dynamic
    var onPointerMissed: ((dynamic) -> Unit)?
}

external val Canvas: FC<CanvasProps>

external interface Size {
    val width: Double
    val height: Double
}

external interface RootState {
    val camera: PerspectiveCamera
    val clock: Clock
    val size: Size

    /** The controls registered with `makeDefault` (camera-controls here), or null before they mount. */
    val controls: dynamic
    val gl: dynamic
}

/** Per-frame callback; a negative priority runs before the default layers without disabling auto-render. */
external fun useFrame(
    callback: (state: RootState, delta: Double) -> Unit,
    renderPriority: Int = definedExternally,
)

external fun useThree(): RootState
