package io.github.juantoanxoup.kg.web.components.workspace

import io.github.juantoanxoup.kg.web.components.ui.Button
import io.github.juantoanxoup.kg.web.components.ui.Table
import io.github.juantoanxoup.kg.web.components.ui.TableBody
import io.github.juantoanxoup.kg.web.components.ui.TableCell
import io.github.juantoanxoup.kg.web.components.ui.TableHead
import io.github.juantoanxoup.kg.web.components.ui.TableHeader
import io.github.juantoanxoup.kg.web.components.ui.TableRow
import io.github.juantoanxoup.kg.web.components.ui.Textarea
import io.github.juantoanxoup.kg.web.lib.Api
import io.github.juantoanxoup.kg.web.lib.Download
import io.github.juantoanxoup.kg.web.lib.Play
import io.github.juantoanxoup.kg.web.lib.SparqlQueryRequest
import io.github.juantoanxoup.kg.web.lib.SparqlQueryResponse
import io.github.juantoanxoup.kg.web.lib.Trash2
import io.github.juantoanxoup.kg.web.lib.downloadText
import io.github.juantoanxoup.kg.web.lib.nowMillis
import io.github.juantoanxoup.kg.web.lib.toCsv
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import react.FC
import react.Key
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.ClassName
import web.html.ButtonType
import web.html.button

private val scope = MainScope()
private val prettyJson = Json { prettyPrint = true }

private class ExampleQuery(
    val name: String,
    val query: String,
)

private val exampleQueries =
    listOf(
        ExampleQuery(
            "Get All Entities",
            "SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object } LIMIT 10",
        ),
        ExampleQuery("Entity Count", "SELECT (COUNT(DISTINCT ?entity) as ?count) WHERE { ?entity ?p ?o }"),
        ExampleQuery(
            "Find Relationships",
            "SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object . FILTER(?predicate != rdf:type) } LIMIT 20",
        ),
    )

/** SPARQL editor with results table and export (src/components/workspace/SparqlTab.tsx). */
val SparqlTab =
    FC<GraphTabProps> { props ->
        var query by useState("")
        var results by useState<List<Map<String, String>>>(emptyList())
        var loading by useState(false)
        var error by useState("")
        var executionTime by useState<Double?>(null)

        fun execute() {
            if (query.isBlank()) return
            loading = true
            error = ""
            val start = nowMillis()
            scope.launch {
                try {
                    val data =
                        Api.postJson<SparqlQueryRequest, SparqlQueryResponse>(
                            "/sparql_query",
                            SparqlQueryRequest(props.graphId, query),
                        )
                    results = data.results
                    executionTime = nowMillis() - start
                } catch (e: Throwable) {
                    error = e.message ?: "Query execution failed"
                } finally {
                    loading = false
                }
            }
        }

        fun download(format: String) {
            if (results.isEmpty()) return
            val headers = results.first().keys.toList()
            if (format == "csv") {
                downloadText(
                    "query-results.csv",
                    toCsv(
                        headers,
                        results.map { row ->
                            headers.map { row[it].orEmpty() }
                        },
                    ),
                    "text/csv",
                )
            } else {
                downloadText("query-results.json", prettyJson.encodeToString(results), "application/json")
            }
        }

        div {
            className = ClassName("stack-6")
            div {
                h2 {
                    className = ClassName("page-title")
                    +"SPARQL Query"
                }
                p {
                    className = ClassName("text-muted")
                    +"Execute SPARQL queries on your knowledge graph"
                }
            }

            div {
                className = ClassName("grid grid-lg-editor")
                div {
                    className = ClassName("stack-6")
                    div {
                        className = ClassName("card")
                        h3 {
                            className = ClassName("text-lg font-semibold")
                            +"Query Editor"
                        }
                        Textarea {
                            value = query
                            onChange = { query = it.target.value }
                            placeholder = "Enter your SPARQL query..."
                            className = ClassName("textarea-editor mono")
                        }
                        div {
                            className = ClassName("row wrap")
                            style = js.objects.unsafeJso { marginTop = "1rem".unsafeCast<web.cssom.Length>() }
                            Button {
                                onClick = { execute() }
                                disabled = query.isBlank() || loading
                                className = ClassName("glow")
                                Play { size = 16 }
                                +(if (loading) "Executing..." else "Execute Query")
                            }
                            Button {
                                variant = "outline"
                                onClick = { query = "" }
                                disabled = query.isEmpty()
                                Trash2 { size = 16 }
                                +"Clear"
                            }
                            executionTime?.let {
                                span {
                                    className = ClassName("text-sm text-muted")
                                    +"Executed in ${it.toInt()}ms"
                                }
                            }
                        }
                    }

                    if (error.isNotEmpty()) {
                        div {
                            className = ClassName("card card-destructive")
                            h3 {
                                className = ClassName("text-lg font-semibold text-destructive")
                                +"Query Error"
                            }
                            p {
                                className = ClassName("text-sm")
                                +error
                            }
                        }
                    }

                    if (results.isNotEmpty()) {
                        val headers = results.first().keys.toList()
                        div {
                            className = ClassName("card card-flush")
                            div {
                                className = ClassName("card-header row-between")
                                h3 {
                                    className = ClassName("text-lg font-semibold")
                                    +"Results (${results.size})"
                                }
                                div {
                                    className = ClassName("row")
                                    Button {
                                        variant = "outline"
                                        size = "sm"
                                        onClick = { download("csv") }
                                        Download {
                                            size =
                                                16
                                        }
                                        +"CSV"
                                    }
                                    Button {
                                        variant = "outline"
                                        size = "sm"
                                        onClick = { download("json") }
                                        Download {
                                            size =
                                                16
                                        }
                                        +"JSON"
                                    }
                                }
                            }
                            div {
                                className = ClassName("scroll-y")
                                Table {
                                    TableHeader {
                                        TableRow {
                                            for (h in headers) {
                                                TableHead {
                                                    key = Key(h)
                                                    +h
                                                }
                                            }
                                        }
                                    }
                                    TableBody {
                                        results.forEachIndexed { i, row ->
                                            TableRow {
                                                key = Key(i.toString())
                                                for (h in headers) {
                                                    TableCell {
                                                        key = Key(h)
                                                        +row[h].orEmpty()
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

                div {
                    className = ClassName("hide-mobile")
                    div {
                        className = ClassName("card")
                        h3 {
                            className = ClassName("text-lg font-semibold")
                            +"Example Queries"
                        }
                        div {
                            className = ClassName("stack-2")
                            for (example in exampleQueries) {
                                button {
                                    key = Key(example.name)
                                    type = ButtonType.button
                                    className = ClassName("example")
                                    onClick = { query = example.query }
                                    p {
                                        className = ClassName("text-sm font-medium")
                                        +example.name
                                    }
                                    p {
                                        className = ClassName("text-xs text-muted truncate")
                                        +example.query
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
