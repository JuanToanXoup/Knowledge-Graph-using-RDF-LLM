@file:JsModule("@react-three/postprocessing")
@file:JsNonModule

package io.github.juantoanxoup.kg.cloud.externals

import react.FC
import react.PropsWithChildren

external interface EffectComposerProps : PropsWithChildren {
    var multisampling: Int?
}

/** Renders the scene through its child effects, in order. */
external val EffectComposer: FC<EffectComposerProps>
