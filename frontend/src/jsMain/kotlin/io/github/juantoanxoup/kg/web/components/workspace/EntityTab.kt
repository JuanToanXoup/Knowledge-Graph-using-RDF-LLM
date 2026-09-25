package io.github.juantoanxoup.kg.web.components.workspace

import io.github.juantoanxoup.kg.web.components.ui.Button
import io.github.juantoanxoup.kg.web.components.ui.Input
import io.github.juantoanxoup.kg.web.components.ui.Table
import io.github.juantoanxoup.kg.web.components.ui.TableBody
import io.github.juantoanxoup.kg.web.components.ui.TableCell
import io.github.juantoanxoup.kg.web.components.ui.TableHead
import io.github.juantoanxoup.kg.web.components.ui.TableHeader
import io.github.juantoanxoup.kg.web.components.ui.TableRow
import io.github.juantoanxoup.kg.web.lib.Api
import io.github.juantoanxoup.kg.web.lib.Download
import io.github.juantoanxoup.kg.web.lib.EntityRelationsRequest
import io.github.juantoanxoup.kg.web.lib.EntityRelationsResponse
import io.github.juantoanxoup.kg.web.lib.Search
import io.github.juantoanxoup.kg.web.lib.downloadText
import io.github.juantoanxoup.kg.web.lib.toCsv
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import react.FC
import react.Key
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.useState
import web.console.console
import web.cssom.ClassName

private val scope = MainScope()

/** Outgoing relations of one entity (src/components/workspace/EntityTab.tsx). */
val EntityTab =
    FC<GraphTabProps> { props ->
        var entity by useState("")
        var relations by useState<List<Map<String, String>>>(emptyList())
        var loading by useState(false)
        var selectedEntity by useState("")

        fun search() {
            val name = entity.trim()
            if (name.isEmpty()) return
            loading = true
            selectedEntity = name
            scope.launch {
                try {
                    val data =
                        Api.postJson<EntityRelationsRequest, EntityRelationsResponse>(
                            "/entity_relations",
                            EntityRelationsRequest(props.graphId, name),
                        )
                    relations = data.relations
                } catch (e: Throwable) {
                    console.error("Failed to fetch relations:", e)
                } finally {
                    loading = false
                }
            }
        }

        fun exportCsv() {
            val rows =
                relations.map {
                    listOf(
                        selectedEntity,
                        it["predicate"].orEmpty(),
                        it["objLabel"].orEmpty().ifEmpty { it["object"].orEmpty() },
                    )
                }
            downloadText(
                "$selectedEntity-relations.csv",
                toCsv(listOf("Subject", "Predicate", "Object"), rows),
                "text/csv",
            )
        }

        div {
            className = ClassName("stack-6")
            div {
                h2 {
                    className = ClassName("page-title")
                    +"Entity Explorer"
                }
                p {
                    className = ClassName("text-muted")
                    +"Explore entities and their relationships"
                }
            }

            div {
                className = ClassName("card")
                div {
                    className = ClassName("row wrap")
                    div {
                        className = ClassName("input-wrap")
                        Search {
                            size = 20
                            className = ClassName("icon")
                        }
                        Input {
                            value = entity
                            onChange = { entity = it.target.value }
                            placeholder = "Enter entity name..."
                            className = ClassName("input-icon input-tall")
                            onKeyDown = { if (it.key == "Enter") search() }
                        }
                    }
                    Button {
                        onClick = { search() }
                        disabled = entity.isBlank() || loading
                        className = ClassName("btn-tall glow")
                        +(if (loading) "Loading..." else "Explore")
                    }
                }
            }

            if (selectedEntity.isNotEmpty()) {
                div {
                    className = ClassName("stack-6")
                    div {
                        className = ClassName("card card-accent-4")
                        div {
                            className = ClassName("row-between")
                            div {
                                h3 {
                                    className = ClassName("text-xl font-bold")
                                    +selectedEntity
                                }
                                p {
                                    className = ClassName("text-sm text-muted")
                                    +"${relations.size} relations found"
                                }
                            }
                            Button {
                                variant = "outline"
                                onClick = { exportCsv() }
                                disabled = relations.isEmpty()
                                Download { size = 16 }
                                +"Export CSV"
                            }
                        }
                    }

                    if (relations.isNotEmpty()) {
                        div {
                            className = ClassName("card card-flush")
                            Table {
                                TableHeader {
                                    TableRow {
                                        TableHead { +"Subject" }
                                        TableHead { +"Predicate" }
                                        TableHead { +"Object" }
                                    }
                                }
                                TableBody {
                                    relations.forEachIndexed { index, relation ->
                                        TableRow {
                                            key = Key(index.toString())
                                            TableCell { +selectedEntity }
                                            TableCell {
                                                div {
                                                    className = ClassName("text-primary")
                                                    +relation["predicate"].orEmpty()
                                                }
                                            }
                                            TableCell {
                                                +(
                                                    relation["objLabel"].orEmpty().ifEmpty {
                                                        relation["object"]
                                                            .orEmpty()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
