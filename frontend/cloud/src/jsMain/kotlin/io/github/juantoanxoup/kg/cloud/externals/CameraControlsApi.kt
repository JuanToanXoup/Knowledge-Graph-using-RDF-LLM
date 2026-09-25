package io.github.juantoanxoup.kg.cloud.externals

/** The camera-controls instance behind drei's `CameraControls`, as reached through `useThree().controls`. */
external interface CameraControlsImpl {
    var smoothTime: Double
    val azimuthAngle: Double
    val polarAngle: Double
    val mouseButtons: dynamic
    val touches: dynamic

    fun setLookAt(
        positionX: Double,
        positionY: Double,
        positionZ: Double,
        targetX: Double,
        targetY: Double,
        targetZ: Double,
        enableTransition: Boolean = definedExternally,
    ): dynamic

    fun rotate(
        azimuthAngle: Double,
        polarAngle: Double,
        enableTransition: Boolean = definedExternally,
    ): dynamic

    fun rotateTo(
        azimuthAngle: Double,
        polarAngle: Double,
        enableTransition: Boolean = definedExternally,
    ): dynamic

    fun getPosition(out: Vector3): Vector3

    fun normalizeRotations()

    fun setBoundary(box: Box3)

    fun addEventListener(
        type: String,
        listener: (dynamic) -> Unit,
    )

    fun removeEventListener(
        type: String,
        listener: (dynamic) -> Unit,
    )
}
