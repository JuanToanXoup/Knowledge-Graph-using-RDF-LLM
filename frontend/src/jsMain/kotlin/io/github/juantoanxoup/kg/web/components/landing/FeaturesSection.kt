package io.github.juantoanxoup.kg.web.components.landing

import io.github.juantoanxoup.kg.web.lib.IconProps
import io.github.juantoanxoup.kg.web.lib.Link2
import io.github.juantoanxoup.kg.web.lib.MessageSquare
import io.github.juantoanxoup.kg.web.lib.Search
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

private class Feature(
    val icon: FC<IconProps>,
    val title: String,
    val description: String,
)

private val features =
    listOf(
        Feature(
            Search,
            "Smart Entity Extraction",
            "Automatically identify people, organizations, locations, and concepts from your documents",
        ),
        Feature(
            Link2,
            "Relationship Mapping",
            "Discover hidden connections and relationships between entities using advanced NLP",
        ),
        Feature(
            MessageSquare,
            "Intelligent Q&A",
            "Ask questions and get instant answers powered by your document's knowledge graph",
        ),
    )

/** Three feature cards (src/components/landing/FeaturesSection.tsx). */
val FeaturesSection =
    FC<Props> {
        section {
            id = ElementId("features")
            className = ClassName("section")
            div {
                className = ClassName("section-inner")
                h2 {
                    className = ClassName("section-title")
                    +"Powerful Features"
                }
                div {
                    className = ClassName("grid grid-sm-2 grid-md-3")
                    for (feature in features) {
                        div {
                            key = Key(feature.title)
                            className = ClassName("card feature-card")
                            div {
                                className = ClassName("icon-box icon-box-lg")
                                feature.icon { size = 28 }
                            }
                            h3 {
                                className = ClassName("text-xl font-semibold")
                                +feature.title
                            }
                            p {
                                className = ClassName("text-muted")
                                +feature.description
                            }
                        }
                    }
                }
            }
        }
    }
