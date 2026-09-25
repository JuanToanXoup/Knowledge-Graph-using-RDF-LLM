package io.github.juantoanxoup.kg.cloud

import io.github.juantoanxoup.kg.cloud.externals.Color
import kotlin.math.cbrt

// term-graph's oklab colour lerp, ported verbatim: duotone shadow/highlight transitions stay perceptual.

private class Oklab {
    var l = 0.0
    var a = 0.0
    var b = 0.0
}

private val scratchA = Oklab()
private val scratchB = Oklab()

private fun rgbToOklab(
    r: Double,
    g: Double,
    b: Double,
    out: Oklab,
) {
    val l = cbrt(r * 0.4122214708 + g * 0.5363325363 + b * 0.0514459929)
    val m = cbrt(r * 0.2119034982 + g * 0.6806995451 + b * 0.1073969566)
    val s = cbrt(r * 0.0883024619 + g * 0.2817188376 + b * 0.6299787005)
    out.l = l * 0.2104542553 + m * 0.793617785 - s * 0.0040720468
    out.a = l * 1.9779984951 - m * 2.428592205 + s * 0.4505937099
    out.b = l * 0.0259040371 + m * 0.7827717662 - s * 0.808675766
}

/** Lerps [from] toward [to] by [t] in oklab space, writing back into [from]. */
fun lerpColorOklab(
    from: Color,
    to: Color,
    t: Double,
) {
    rgbToOklab(from.r, from.g, from.b, scratchA)
    rgbToOklab(to.r, to.g, to.b, scratchB)
    val bigL = scratchA.l + (scratchB.l - scratchA.l) * t
    val bigA = scratchA.a + (scratchB.a - scratchA.a) * t
    val bigB = scratchA.b + (scratchB.b - scratchA.b) * t
    val l = bigL + bigA * 0.3963377774 + bigB * 0.2158037573
    val m = bigL - bigA * 0.1055613458 - bigB * 0.0638541728
    val s = bigL - bigA * 0.0894841775 - bigB * 1.291485548
    val l3 = l * l * l
    val m3 = m * m * m
    val s3 = s * s * s
    from.r = l3 * 4.0767416621 - m3 * 3.3077115913 + s3 * 0.2309699292
    from.g = l3 * -1.2684380046 + m3 * 2.6097574011 - s3 * 0.3413193965
    from.b = l3 * -0.0041960863 - m3 * 0.7034186147 + s3 * 1.707614701
}
