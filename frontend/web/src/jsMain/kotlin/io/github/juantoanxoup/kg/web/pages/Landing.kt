package io.github.juantoanxoup.kg.web.pages

import io.github.juantoanxoup.kg.web.components.Footer
import io.github.juantoanxoup.kg.web.components.Header
import io.github.juantoanxoup.kg.web.components.ParticleBackground
import io.github.juantoanxoup.kg.web.components.landing.ContactSection
import io.github.juantoanxoup.kg.web.components.landing.FeaturesSection
import io.github.juantoanxoup.kg.web.components.landing.HeroSection
import io.github.juantoanxoup.kg.web.components.landing.HowItWorksSection
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

/** Landing page (src/pages/Landing.tsx). */
val Landing =
    FC<Props> {
        div {
            className = ClassName("page")
            ParticleBackground()
            Header { showNav = true }
            HeroSection()
            FeaturesSection()
            HowItWorksSection()
            ContactSection()
            Footer()
        }
    }
