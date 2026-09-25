@file:JsModule("d3-force-3d")
@file:JsNonModule

package io.github.juantoanxoup.kg.cloud.externals

external interface Simulation {
    /** [force] is a d3 force object, or a plain function with an `initialize` property. */
    fun force(
        name: String,
        force: Any?,
    ): Simulation

    fun tick(iterations: Int = definedExternally): Simulation

    fun stop(): Simulation
}

/** Nodes are plain objects; the simulation seeds and then writes `x`, `y`, `z` onto them. */
external fun forceSimulation(
    nodes: Array<dynamic> = definedExternally,
    numDimensions: Int = definedExternally,
): Simulation

external interface ManyBodyForce {
    fun strength(strength: Double): ManyBodyForce
}

external fun forceManyBody(): ManyBodyForce

external interface CollideForce {
    fun iterations(iterations: Int): CollideForce
}

/** [radius] is a number or an accessor `(node) -> radius`. */
external fun forceCollide(radius: Any = definedExternally): CollideForce

external interface AxisForce {
    fun strength(strength: Double): AxisForce
}

external fun forceX(x: Double = definedExternally): AxisForce

external fun forceY(y: Double = definedExternally): AxisForce

external fun forceZ(z: Double = definedExternally): AxisForce
