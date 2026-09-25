package io.github.juantoanxoup.kg

import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.system.exitProcess

/**
 * Single-document pipeline followed by the interactive query menu (main.py).
 *
 * Usage: `./gradlew :backend:runCli [-Pfile=path/to/document.pdf]`. Without a file the sample document is used.
 */
fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        val input = Path(args[0])
        if (!input.exists()) {
            println("Error: File '$input' not found!")
            exitProcess(1)
        }
        runPipeline(input)
    } else {
        println("No input file provided. Using sample document.")
        println("Usage: runCli -Pfile=<path_to_pdf_or_document>")
        println()
        runPipeline(null)
    }
}

private fun banner(title: String) {
    println("\n" + "=".repeat(70))
    println(title)
    println("=".repeat(70))
}

private fun section(title: String) {
    println("\n" + "-".repeat(70))
    println(title)
    println("-".repeat(70))
}

private fun prompt(text: String): String {
    print(text)
    return readlnOrNull()?.trim() ?: "exit"
}

private fun isBack(input: String) = input.lowercase() in setOf("back", "exit", "quit")

fun runPipeline(inputFile: Path?) =
    runBlocking {
        val state = ApiState()
        val graphId = newGraphId()
        val filesDir = state.filesDir(graphId)

        banner("KNOWLEDGE GRAPH CREATION PIPELINE\nRun ID: $graphId")

        println("\nSTEP 1: Document Loading")
        println("-".repeat(70))
        val docProcessor = DocumentProcessor()
        val text = if (inputFile != null) docProcessor.loadDocument(inputFile) else docProcessor.createSampleDocument()

        println("\nSTEP 2: Text Preprocessing")
        println("-".repeat(70))
        val preprocessed = TextPreprocessor().preprocess(text)

        println("\nSTEP 3: Entity Extraction")
        println("-".repeat(70))
        val entityExtractor = EntityExtractor(state.promptExecutor)
        val nlpEntities = entityExtractor.extractEntitiesNlp(preprocessed.cleaned)
        val llmEntities = entityExtractor.extractEntitiesLlm(preprocessed.cleaned)
        val entities = entityExtractor.mergeEntities(nlpEntities, llmEntities)
        println("\nExtracted ${entities.size} unique entities")
        if (entities.isNotEmpty()) println("Sample entities: ${entities.take(5).map { it.text }}")

        println("\nSTEP 4: Relation Extraction")
        println("-".repeat(70))
        val relationExtractor = RelationExtractor(state.promptExecutor)
        val patternRelations = relationExtractor.extractRelationsPattern(preprocessed.cleaned, entities)
        val llmRelations = relationExtractor.extractRelationsLlm(preprocessed.cleaned, entities)
        val relations = relationExtractor.mergeRelations(patternRelations, llmRelations)
        println("\nExtracted ${relations.size} unique relations")
        if (relations.isNotEmpty()) {
            println("Sample relations:")
            relations.take(3).forEach { println("  - ${it.subject} → ${it.predicate} → ${it.obj}") }
        }

        println("\nSTEP 5: Knowledge Graph Construction")
        println("-".repeat(70))
        val kgBuilder = KnowledgeGraphBuilder()
        kgBuilder.buildFromExtractions(entities, relations)
        println("\nGraph Statistics:")
        printStatistics(kgBuilder.getStatistics(), titleCase = false)

        println("\nSTEP 6: Storage, Visualization and Indexing")
        println("-".repeat(70))
        val filename = inputFile?.name ?: "sample_document.txt"
        val active = state.registerGraph(graphId, filename, kgBuilder, entities.size, relations.size)
        val turtle = filesDir.resolve("knowledge_graph.ttl")
        turtle.outputStream().use { state.store.export(graphId, it) }

        banner("KNOWLEDGE GRAPH CONSTRUCTION COMPLETED!")
        println("\n✓ Graph stored as <${state.store.graphIri(graphId)}> in '${state.dataDir}/'")
        println("✓ Files saved in '$filesDir/':")
        println("  - knowledge_graph.ttl (RDF export)")
        println("  - knowledge_graph.png (visualization)")

        banner("LAUNCHING INTERACTIVE QUERY INTERFACE")
        prompt("\nPress Enter to start querying the knowledge graph...")
        interactiveQueryInterface(active, entities, state)
        banner("SESSION COMPLETED!")
        state.close()
    }

private fun printStatistics(
    stats: GraphStatistics,
    titleCase: Boolean,
) {
    val rows =
        listOf(
            "total_triples" to stats.totalTriples,
            "unique_subjects" to stats.uniqueSubjects,
            "unique_predicates" to stats.uniquePredicates,
            "unique_objects" to stats.uniqueObjects,
        )
    for ((key, value) in rows) {
        val label = if (titleCase) key.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) } else key
        println("  $label: $value")
    }
}

suspend fun interactiveQueryInterface(
    graph: ActiveGraph,
    entities: List<Entity>,
    state: ApiState,
) {
    banner("INITIALIZING QUERY INTERFACE")
    val kgBuilder = graph.kg
    val querier = graph.querier
    val retriever = graph.retriever
    println("✓ Query interface ready!")

    while (true) {
        banner("KNOWLEDGE GRAPH QUERY MENU")
        println("\nAvailable Options:")
        println("  1. Semantic Search - Find relevant information using natural language")
        println("  2. Question Answering - Ask questions and get AI-generated answers")
        println("  3. SPARQL Query - Find all relations for a specific entity")
        println("  4. Custom SPARQL Query - Execute your own SPARQL query")
        println("  5. View Graph Statistics")
        println("  6. Exit")

        when (prompt("\nSelect an option (1-6): ")) {
            "1" -> semanticSearchInterface(retriever)
            "2" -> questionAnsweringInterface(retriever, state)
            "3" -> sparqlEntityRelationsInterface(querier, entities)
            "4" -> customSparqlInterface(querier, kgBuilder)
            "5" -> showGraphStatistics(kgBuilder)
            "6", "exit" -> {
                println("\n👋 Exiting query interface. Goodbye!")
                return
            }
            else -> println("\n❌ Invalid option. Please select 1-6.")
        }
    }
}

private suspend fun semanticSearchInterface(retriever: SemanticRetriever) {
    section("SEMANTIC SEARCH")
    println("Search the knowledge graph using natural language.")
    while (true) {
        val query = prompt("\nEnter search query (or 'back' to return): ")
        if (isBack(query)) return
        if (query.isEmpty()) {
            println("❌ Please enter a search query.")
            continue
        }
        println("\n🔍 Searching for: '$query'")
        val topK = prompt("Number of results to show (default 5): ").toIntOrNull()?.takeIf { it > 0 } ?: 5
        val results = retriever.search(query, topK)
        if (results.isEmpty()) {
            println("No results found.")
            continue
        }
        println("\n📊 Top ${results.size} Results:")
        results.forEachIndexed { i, r ->
            println("\n  ${i + 1}. ${r.subject} → ${r.predicate} → ${r.obj}")
            println("     Similarity Score: ${"%.3f".format(r.similarity)}")
        }
        if (prompt("\nSearch again? (y/n): ").lowercase() != "y") return
    }
}

private suspend fun questionAnsweringInterface(
    retriever: SemanticRetriever,
    state: ApiState,
) {
    section("QUESTION ANSWERING (AI-Powered)")
    val executor = state.promptExecutor
    if (executor == null) {
        println("❌ Question answering requires an OpenAI API key.")
        println("Please set OPENAI_API_KEY in your .env file.")
        prompt("\nPress Enter to continue...")
        return
    }
    println("Ask questions about the knowledge graph and get AI-generated answers.")
    println("Example: 'What did Einstein work on?', 'Where was Marie Curie born?'")
    while (true) {
        val question = prompt("\nEnter your question (or 'back' to return): ")
        if (isBack(question)) return
        if (question.isEmpty()) {
            println("❌ Please enter a question.")
            continue
        }
        println("\n💭 Thinking about: '$question'")
        println("⏳ Retrieving relevant facts and generating answer...")
        val answer = retriever.answerQuestionLlm(question, executor)
        println("\n✨ Answer:\n$answer")
        if (prompt("\n\nAsk another question? (y/n): ").lowercase() != "y") return
    }
}

private fun sparqlEntityRelationsInterface(
    querier: KnowledgeGraphQuerier,
    entities: List<Entity>,
) {
    section("ENTITY RELATIONS FINDER")
    println("Find all relationships for a specific entity in the graph.")
    if (entities.isEmpty()) {
        println("❌ No entities found in the knowledge graph.")
        prompt("\nPress Enter to continue...")
        return
    }
    while (true) {
        println("\n📋 Available Entities:")
        val display = entities.take(20)
        display.forEachIndexed { i, e -> println("  ${i + 1}. ${e.text} (${e.type})") }
        if (entities.size > 20) println("  ... and ${entities.size - 20} more")
        println("\nOptions:")
        println("  - Enter entity name directly")
        println("  - Enter number to select from list")
        println("  - Type 'back' to return")

        val input = prompt("\nSelect entity: ")
        if (isBack(input)) return
        if (input.isEmpty()) {
            println("❌ Please enter an entity name or number.")
            continue
        }
        val number = input.toIntOrNull()
        val entityName: String
        if (number != null) {
            val selected = display.getOrNull(number - 1)
            if (selected == null) {
                println("❌ Invalid number. Please try again.")
                continue
            }
            entityName = selected.text
        } else {
            entityName = input
        }

        println("\n🔍 Finding relations for: $entityName")
        val results = querier.findEntityRelations(entityName)
        if (results.isEmpty()) {
            println("❌ No relations found for '$entityName'")
            println("   This entity might not exist in the graph or has no connections.")
            continue
        }
        println("\n📊 Found ${results.size} relations:")
        results.forEachIndexed { i, row ->
            val predicate = readable(row["predicate"] ?: "N/A")
            val objLabel = row["objLabel"].orEmpty()
            val obj = if (objLabel.isNotEmpty()) objLabel else readable(row["object"] ?: "N/A")
            println("  ${i + 1}. $entityName → $predicate → $obj")
        }
        if (prompt("\n\nLookup another entity? (y/n): ").lowercase() != "y") return
    }
}

private fun readable(value: String): String =
    when {
        '/' in value -> value.substringAfterLast('/').replace('_', ' ')
        '#' in value -> value.substringAfterLast('#').replace('_', ' ')
        else -> value
    }

private fun customSparqlInterface(
    querier: KnowledgeGraphQuerier,
    kgBuilder: KnowledgeGraphBuilder,
) {
    section("CUSTOM SPARQL QUERY")
    println("Execute custom SPARQL queries on the knowledge graph.")
    val example =
        """
        PREFIX kg: <${kgBuilder.namespace}>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

        SELECT ?subject ?predicate ?object
        WHERE {
            ?subject ?predicate ?object .
        }
        LIMIT 10
        """.trimIndent()
    println("\nExample Query:")
    println(example)

    while (true) {
        println("\n" + "-".repeat(70))
        println("Enter your SPARQL query (type 'END' on a new line when done):")
        println("Type 'example' to use the example query above")
        println("Type 'back' to return to menu")
        val first = prompt("\n> ")
        if (isBack(first)) return
        val sparql =
            if (first.lowercase() == "example") {
                example
            } else {
                val lines = mutableListOf(first)
                while (true) {
                    val line = readlnOrNull() ?: break
                    if (line.trim().uppercase() == "END") break
                    lines += line
                }
                lines.joinToString("\n")
            }
        if (sparql.isBlank()) {
            println("❌ Empty query. Please try again.")
            continue
        }
        println("\n⏳ Executing query...")
        try {
            val results = querier.query(sparql)
            if (results.isEmpty()) {
                println("✓ Query executed successfully. No results returned.")
                continue
            }
            println("\n✓ Query returned ${results.size} results:")
            println("\n" + "-".repeat(70))
            results.forEachIndexed { i, row ->
                println("\nResult ${i + 1}:")
                row.forEach { (k, v) -> println("  $k: $v") }
            }
        } catch (e: Exception) {
            println("\n❌ Query execution failed: ${e.message}")
            println("Please check your SPARQL syntax and try again.")
        }
        if (prompt("\n\nExecute another query? (y/n): ").lowercase() != "y") return
    }
}

private fun showGraphStatistics(kgBuilder: KnowledgeGraphBuilder) {
    section("KNOWLEDGE GRAPH STATISTICS")
    println("\n📊 Graph Overview:")
    printStatistics(kgBuilder.getStatistics(), titleCase = true)
    prompt("\nPress Enter to continue...")
}
