package io.github.juantoanxoup.kg.cloud.components

import io.github.juantoanxoup.kg.cloud.GraphData
import io.github.juantoanxoup.kg.cloud.Palette
import io.github.juantoanxoup.kg.cloud.externals.Color
import io.github.juantoanxoup.kg.cloud.externals.Effect
import io.github.juantoanxoup.kg.cloud.externals.EffectComposer
import io.github.juantoanxoup.kg.cloud.externals.Uniform
import io.github.juantoanxoup.kg.cloud.externals.primitive
import io.github.juantoanxoup.kg.cloud.externals.useFrame
import io.github.juantoanxoup.kg.cloud.lerpColorOklab
import react.FC
import react.Props
import react.useMemo
import kotlin.js.json
import kotlin.math.exp
import kotlin.math.floor

// Ported from the original site's DuotoneEffect: the whole frame is remapped
// to a two-color ramp (ink shadow → paper highlight) by Rec.601 luma.
private const val FRAGMENT_SHADER = """
  uniform vec3 shadow;
  uniform vec3 highlight;

  void mainImage(const in vec4 inputColor, const in vec2 uv, out vec4 outputColor) {
    // Rec.601 luma — matches how the scene was authored in value
    float l = dot(inputColor.rgb, vec3(0.299, 0.587, 0.114));
    // white-point stretch so the scene's paper maps EXACTLY to the highlight.
    // This pass runs in LINEAR space, where the paper clear-color's luma is ~0.88
    // (not the ~0.94 it reads in sRGB) — dividing by that lifts paper to 1.0 so
    // the rendered background matches the defined paper color instead of landing
    // a hair toward the shadow (which read as "dimmer than defined").
    l = clamp(l / 0.86, 0.0, 1.0);
    vec3 col = mix(shadow, highlight, l);
    outputColor = vec4(col, inputColor.a);
  }
"""

// The original's per-section duotone palettes (SECTION_COLORS drive the ink,
// SECTION_PAPERS the paper); term-graph maps node groups onto them in order
// of first appearance. Toggled by the original's localStorage key.
val SECTION_COLORS =
    listOf(
        "#FFD79E",
        "#ffffff",
        "#000000",
        "#000000",
        "#ffffff",
        "#000000",
        "#ffffff",
    )
val SECTION_PAPERS =
    listOf(
        "#4500B3",
        "#EB4347",
        "#9DD395",
        "#D3C2FE",
        "#0F7A6B",
        "#FFD23F",
        "#2D3DCF",
    )
const val SECTION_COLOR_KEY = "atlas:section-color"

private const val BLEND_TAU = 0.4
private const val MULTISAMPLING = 4

/** term-graph's `DuotoneEffect`: an ink shadow / paper highlight duotone pass. */
fun createDuotoneEffect(): Effect {
    val map: dynamic = js("new Map()")
    map.set("shadow", Uniform(Color(Palette.INK)))
    map.set("highlight", Uniform(Color(Palette.PAPER)))
    return Effect("DuotoneEffect", FRAGMENT_SHADER, json("uniforms" to map))
}

fun Effect.shadow(): Color = uniforms.get("shadow").value.unsafeCast<Color>()

fun Effect.highlight(): Color = uniforms.get("highlight").value.unsafeCast<Color>()

private fun readSectionColorOn(): Boolean =
    runCatching {
        val storage: dynamic = js("localStorage")
        storage.getItem(SECTION_COLOR_KEY) == "1"
    }.getOrDefault(false)

external interface EffectsProps : Props {
    var data: GraphData
    var selectedId: String?
}

/**
 * term-graph's `Effects` + `DuotoneRig`. The original Atlas duotone loop: when section coloring is on
 * (persisted under SECTION_COLOR_KEY), the ink/paper pair eases in oklab (tau 0.4s) toward the focused
 * node's section palette — or a random overview section while nothing is focused; off, it rests at
 * neutral ink on paper.
 */
val Effects =
    FC<EffectsProps> { props ->
        val data = props.data
        val selectedId = props.selectedId
        val effect = useMemo(Unit) { createDuotoneEffect() }

        val sectionColorOn = useMemo(Unit) { readSectionColorOn() }
        val overviewSection =
            useMemo(sectionColorOn) {
                if (sectionColorOn) {
                    floor(
                        js("Math.random()").unsafeCast<Double>() * SECTION_COLORS.size,
                    ).toInt()
                } else {
                    null
                }
            }
        // Groups in order of first appearance index into the section palettes.
        val sectionByGroup =
            useMemo(data) {
                val m = LinkedHashMap<String, Int>()
                for (n in data.nodes) {
                    if (n.group.isNotEmpty() && n.group !in m) m[n.group] = m.size
                }
                m
            }
        val sectionById =
            useMemo(data, sectionByGroup) {
                data.nodes.associate { n -> n.id to (if (n.group.isNotEmpty()) sectionByGroup[n.group] else null) }
            }
        val inkColor = useMemo(Unit) { Color(Palette.INK) }
        val paperColor = useMemo(Unit) { Color(Palette.PAPER) }

        useFrame({ _, delta ->
            val section = if (!selectedId.isNullOrEmpty()) sectionById[selectedId] else overviewSection
            val useSectionColor = sectionColorOn && section != null && section >= 0
            inkColor.set(
                if (useSectionColor) {
                    SECTION_COLORS.getOrNull(
                        section!! % SECTION_COLORS.size,
                    ) ?: Palette.INK
                } else {
                    Palette.INK
                },
            )
            paperColor.set(
                if (useSectionColor) {
                    SECTION_PAPERS.getOrNull(section!! % SECTION_PAPERS.size) ?: Palette.PAPER
                } else {
                    Palette.PAPER
                },
            )
            val blend = 1 - exp(-delta / BLEND_TAU)
            lerpColorOklab(effect.shadow(), inkColor, blend)
            lerpColorOklab(effect.highlight(), paperColor, blend)
        })

        EffectComposer {
            multisampling = MULTISAMPLING
            primitive {
                `object` = effect
                dispose = null
            }
        }
    }
