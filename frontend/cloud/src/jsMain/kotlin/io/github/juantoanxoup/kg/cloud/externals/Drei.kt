@file:JsModule("@react-three/drei")
@file:JsNonModule

package io.github.juantoanxoup.kg.cloud.externals

import react.FC
import react.Props
import react.PropsWithChildren
import react.PropsWithRef

/** drei's `CameraControls` (the camera-controls library); its instance is `useThree().controls`. */
external interface CameraControlsProps : Props {
    var makeDefault: Boolean?
    var smoothTime: Double?
    var draggingSmoothTime: Double?
    var azimuthRotateSpeed: Double?
    var polarRotateSpeed: Double?
    var minDistance: Double?
    var maxDistance: Double?
}

external val CameraControls: FC<CameraControlsProps>

/** troika SDF text; the string is the child, the ref is the troika `Text` mesh. */
external interface TextProps :
    PropsWithChildren,
    PropsWithRef<Any> {
    var font: String?
    var fontSize: Double?
    var sdfGlyphSize: Int?
    var color: String?
    var anchorX: String?
    var anchorY: String?
    var letterSpacing: Double?
    var fillOpacity: Double?
    var outlineColor: String?
    var outlineWidth: String?
    var outlineOpacity: Double?
    var renderOrder: Int?
    var raycast: dynamic
}

external val Text: FC<TextProps>
