package io.github.juantoanxoup.kg.web.components.workspace

import io.github.juantoanxoup.kg.web.components.landing.asFixed
import io.github.juantoanxoup.kg.web.components.ui.Button
import io.github.juantoanxoup.kg.web.components.ui.Input
import io.github.juantoanxoup.kg.web.components.ui.Slider
import io.github.juantoanxoup.kg.web.lib.Api
import io.github.juantoanxoup.kg.web.lib.Search
import io.github.juantoanxoup.kg.web.lib.SearchResult
import io.github.juantoanxoup.kg.web.lib.SemanticSearchRequest
import io.github.juantoanxoup.kg.web.lib.SemanticSearchResponse
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import react.FC
import react.Key
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useState
import web.console.console
import web.cssom.ClassName

private val scope = MainScope()

/** Natural-language search over indexed triples (src/components/workspace/SearchTab.tsx). */
val SearchTab =
    FC<GraphTabProps> { props ->
        var query by useState("")
        var topK by useState(5)
        var results by useState<List<SearchResult>>(emptyList())
        var loading by useState(false)

        fun search() {
            if (query.isBlank()) return
            loading = true
            scope.launch {
                try {
                    val response =
                        Api.postJson<SemanticSearchRequest, SemanticSearchResponse>(
                            "/semantic_search",
                            SemanticSearchRequest(props.graphId, query, topK),
                        )
                    results = response.results
                } catch (e: Throwable) {
                    console.error("Search failed:", e)
                } finally {
                    loading = false
                }
            }
        }

        div {
            className = ClassName("stack-6")
            div {
                h2 {
                    className = ClassName("page-title")
                    +"Semantic Search"
                }
                p {
                    className = ClassName("text-muted")
                    +"Find relevant information using natural language queries"
                }
            }

            div {
                className = ClassName("card stack-6")
                div {
                    className = ClassName("row wrap")
                    div {
                        className = ClassName("input-wrap")
                        Search {
                            size = 20
                            className = ClassName("icon")
                        }
                        Input {
                            value = query
                            onChange = { query = it.target.value }
                            placeholder = "Enter your search query..."
                            className = ClassName("input-icon input-tall")
                            onKeyDown = { if (it.key == "Enter") search() }
                        }
                    }
                    Button {
                        onClick = { search() }
                        disabled = query.isBlank() || loading
                        className = ClassName("btn-tall glow")
                        +(if (loading) "Searching..." else "Search")
                    }
                }
                div {
                    className = ClassName("stack-2")
                    div {
                        className = ClassName("row-between")
                        label {
                            className = ClassName("text-sm font-medium")
                            +"Results: $topK"
                        }
                        span {
                            className = ClassName("text-xs text-muted")
                            +"1-20"
                        }
                    }
                    Slider {
                        value = topK
                        min = 1
                        max = 20
                        step = 1
                        onValueChange = { topK = it }
                    }
                }
            }

            if (results.isNotEmpty()) {
                div {
                    className = ClassName("stack-4")
                    h3 {
                        className = ClassName("text-xl font-semibold")
                        +"Results (${results.size})"
                    }
                    results.forEachIndexed { index, result ->
                        div {
                            key = Key(index.toString())
                            className = ClassName("card card-hover fade-in")
                            div {
                                className = ClassName("row-between")
                                p {
                                    className = ClassName("grow")
                                    +result.text
                                }
                                div {
                                    className = ClassName("score")
                                    +"${(result.similarity * 100).asFixed(1)}%"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
