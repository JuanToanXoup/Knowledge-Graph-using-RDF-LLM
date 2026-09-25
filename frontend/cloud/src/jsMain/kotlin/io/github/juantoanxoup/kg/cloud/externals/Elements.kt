package io.github.juantoanxoup.kg.cloud.externals

import react.IntrinsicType
import react.PropsWithChildren
import react.PropsWithClassName
import react.PropsWithRef

// react-three-fiber turns lower-case elements into three.js objects; these are the ones the cloud uses.

/** A pointer event delivered by react-three-fiber's raycaster. */
external interface ThreeEvent {
    /** Instance index when the hit object is an `InstancedMesh`. */
    val instanceId: Int?

    /** Pointer travel in pixels between down and up, for telling clicks from drags. */
    val delta: Double

    fun stopPropagation()
}

external interface Object3DProps :
    PropsWithChildren,
    PropsWithClassName,
    PropsWithRef<Object3D> {
    var position: Array<Double>?
}

val group: IntrinsicType<Object3DProps> = IntrinsicType<Object3DProps>("group")

/** `<primitive object={...}>`: mounts a three.js object built in Kotlin. */
external interface PrimitiveProps : PropsWithClassName {
    var `object`: Any?

    /** `null` keeps fiber from disposing the object on unmount. */
    var dispose: Any?
    var onClick: ((ThreeEvent) -> Unit)?
    var onPointerMove: ((ThreeEvent) -> Unit)?
    var onPointerOut: ((ThreeEvent) -> Unit)?
}

val primitive: IntrinsicType<PrimitiveProps> = IntrinsicType<PrimitiveProps>("primitive")

/** `<color attach="background" args={[...]}>` and `<fog attach="fog" args={[...]}>`. */
external interface AttachProps : PropsWithClassName {
    var attach: String?
    var args: Array<Any>?
}

val colorElement: IntrinsicType<AttachProps> = IntrinsicType<AttachProps>("color")
val fogElement: IntrinsicType<AttachProps> = IntrinsicType<AttachProps>("fog")
