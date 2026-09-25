package io.github.juantoanxoup.kg.web.components.landing

import io.github.juantoanxoup.kg.web.lib.Cpu
import io.github.juantoanxoup.kg.web.lib.Eye
import io.github.juantoanxoup.kg.web.lib.IconProps
import io.github.juantoanxoup.kg.web.lib.Upload
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import web.cssom.ClassName
import web.dom.ElementId

private class Step(
    val icon: FC<IconProps>,
    val number: String,
    val title: String,
    val description: String,
)

private val steps =
    listOf(
        Step(Upload, "01", "Upload", "Drop your document"),
        Step(Cpu, "02", "Process", "AI extracts entities & relations"),
        Step(Eye, "03", "Explore", "Query and visualize insights"),
    )

/** Three numbered steps (src/components/landing/HowItWorksSection.tsx). */
val HowItWorksSection =
    FC<Props> {
        section {
            id = ElementId("how-it-works")
            className = ClassName("section")
            div {
                className = ClassName("section-inner")
                h2 {
                    className = ClassName("section-title")
                    +"How It Works"
                }
                div {
                    style = js.objects.unsafeJso { position = web.cssom.Position.relative }
                    div { className = ClassName("steps-line") }
                    div {
                        className = ClassName("grid grid-sm-2 grid-md-3")
                        for (step in steps) {
                            div {
                                key = Key(step.number)
                                className = ClassName("step stack-4")
                                div {
                                    className = ClassName("step-circle")
                                    div {
                                        className = ClassName("step-icon")
                                        step.icon { size = 56 }
                                    }
                                    div {
                                        className = ClassName("step-number")
                                        +step.number
                                    }
                                }
                                h3 {
                                    className = ClassName("text-xl font-bold")
                                    +step.title
                                }
                                p {
                                    className = ClassName("text-muted")
                                    +step.description
                                }
                            }
                        }
                    }
                }
            }
        }
    }
