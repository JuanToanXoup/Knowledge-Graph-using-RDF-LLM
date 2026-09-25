@file:JsModule("postprocessing")
@file:JsNonModule

package io.github.juantoanxoup.kg.cloud.externals

/** A full-screen pass; [options] carries `uniforms` as a JS `Map<String, Uniform>`. */
open external class Effect(
    name: String,
    fragmentShader: String,
    options: dynamic = definedExternally,
) {
    /** JS `Map` of uniform name to [Uniform]. */
    val uniforms: dynamic
}
