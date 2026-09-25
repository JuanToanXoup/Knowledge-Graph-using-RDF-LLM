@file:JsModule("three")
@file:JsNonModule

package io.github.juantoanxoup.kg.cloud.externals

// The subset of three.js the cloud drives directly (no kotlin-wrappers exist for it).

external class Vector3(
    x: Double = definedExternally,
    y: Double = definedExternally,
    z: Double = definedExternally,
) {
    var x: Double
    var y: Double
    var z: Double

    fun set(
        x: Double,
        y: Double,
        z: Double,
    ): Vector3

    fun setScalar(scalar: Double): Vector3

    fun copy(v: Vector3): Vector3

    fun length(): Double

    fun normalize(): Vector3

    fun multiplyScalar(scalar: Double): Vector3

    fun applyQuaternion(q: Quaternion): Vector3

    fun distanceTo(v: Vector3): Double

    /** World to normalized device coordinates through the camera. */
    fun project(camera: Object3D): Vector3
}

external class Quaternion {
    fun copy(q: Quaternion): Quaternion
}

external class Matrix4

external class Color(
    color: dynamic = definedExternally,
) {
    var r: Double
    var g: Double
    var b: Double

    fun set(color: dynamic): Color

    fun setRGB(
        r: Double,
        g: Double,
        b: Double,
    ): Color

    fun copy(color: Color): Color

    fun lerp(
        color: Color,
        alpha: Double,
    ): Color

    fun getHex(): Int
}

external class Box3(
    min: Vector3 = definedExternally,
    max: Vector3 = definedExternally,
)

external class Sphere(
    center: Vector3 = definedExternally,
    radius: Double = definedExternally,
)

open external class Object3D {
    val position: Vector3
    val quaternion: Quaternion
    val scale: Vector3
    var visible: Boolean
    var renderOrder: Int
    var frustumCulled: Boolean
    val matrix: Matrix4

    fun updateMatrix()
}

external class PerspectiveCamera : Object3D {
    var fov: Double
}

external class Clock {
    val elapsedTime: Double
}

open external class BufferAttribute(
    array: dynamic,
    itemSize: Int,
) {
    var needsUpdate: Boolean
    val array: dynamic
}

open external class BufferGeometry {
    var boundingSphere: Sphere?

    fun setAttribute(
        name: String,
        attribute: BufferAttribute,
    ): BufferGeometry

    fun dispose()
}

external class CircleGeometry(
    radius: Double = definedExternally,
    segments: Int = definedExternally,
) : BufferGeometry

external class RingGeometry(
    innerRadius: Double = definedExternally,
    outerRadius: Double = definedExternally,
    thetaSegments: Int = definedExternally,
) : BufferGeometry

open external class Material {
    var transparent: Boolean
    var opacity: Double
    var depthWrite: Boolean
    var depthTest: Boolean
    var needsUpdate: Boolean

    fun dispose()
}

external class MeshBasicMaterial(
    parameters: dynamic = definedExternally,
) : Material {
    val color: Color
}

external class LineBasicMaterial(
    parameters: dynamic = definedExternally,
) : Material {
    val color: Color
}

open external class Mesh(
    geometry: BufferGeometry = definedExternally,
    material: Material = definedExternally,
) : Object3D

external class InstancedMesh(
    geometry: BufferGeometry,
    material: Material,
    count: Int,
) : Mesh {
    val instanceMatrix: BufferAttribute
    var instanceColor: BufferAttribute?
    var count: Int

    fun setMatrixAt(
        index: Int,
        matrix: Matrix4,
    )

    fun setColorAt(
        index: Int,
        color: Color,
    )
}

external class LineSegments(
    geometry: BufferGeometry = definedExternally,
    material: Material = definedExternally,
) : Object3D

external val DoubleSide: Int

external class Vector2(
    x: Double = definedExternally,
    y: Double = definedExternally,
)

/** A uniform holder for `postprocessing` effects. */
external class Uniform(
    value: dynamic,
) {
    var value: dynamic
}

external class ShaderMaterial(
    parameters: dynamic = definedExternally,
) : Material {
    /** `{ name: { value } }`, as passed in the parameters. */
    val uniforms: dynamic
}

external class InstancedBufferAttribute(
    array: dynamic,
    itemSize: Int,
) : BufferAttribute

external class PlaneGeometry(
    width: Double = definedExternally,
    height: Double = definedExternally,
) : BufferGeometry

external class Points(
    geometry: BufferGeometry = definedExternally,
    material: Material = definedExternally,
) : Object3D

external class DataTexture(
    data: dynamic,
    width: Int,
    height: Int,
    format: Int = definedExternally,
    type: Int = definedExternally,
) {
    var minFilter: Int
    var magFilter: Int
    var needsUpdate: Boolean

    fun dispose()
}

external val RGBAFormat: Int
external val FloatType: Int
external val NearestFilter: Int
