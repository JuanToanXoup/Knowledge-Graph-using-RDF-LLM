package io.github.juantoanxoup.kg.web.hooks

import react.useEffectOnce
import react.useState
import web.cssom.MediaQuery
import web.cssom.changeEvent
import web.cssom.matchMedia
import web.events.invoke
import web.window.window

const val MOBILE_BREAKPOINT = 768

/** True when the viewport is narrower than [MOBILE_BREAKPOINT] pixels, tracking resizes (src/hooks/use-mobile.tsx). */
fun useIsMobile(): Boolean {
    val (isMobile, setIsMobile) = useState(false)
    useEffectOnce {
        val mql = matchMedia(MediaQuery("(max-width: ${MOBILE_BREAKPOINT - 1}px)"))
        setIsMobile(window.innerWidth < MOBILE_BREAKPOINT)
        // The effect scope is cancelled on unmount, which ends this collection.
        mql.changeEvent().collect { setIsMobile(window.innerWidth < MOBILE_BREAKPOINT) }
    }
    return isMobile
}
