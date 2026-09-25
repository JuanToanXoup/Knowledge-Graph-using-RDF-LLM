package io.github.juantoanxoup.kg

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.streams.asSequence

/** Result of processing one document in a batch. */
data class ProcessedDocument(
    val path: Path,
    val entities: List<Entity>,
    val relations: List<Relation>,
    val textLength: Int,
)

/** Processing summary printed at the end of a batch run. */
data class BatchSummary(
    val totalFiles: Int,
    val totalEntities: Int,
    val totalRelations: Int,
    val files: List<String>,
)

/** Processes many documents into one unified knowledge graph (batch_processor.py), stored like any upload. */
class BatchProcessor(
    private val state: ApiState = ApiState(),
    val graphId: String = newGraphId(),
) {
    private val log = LoggerFactory.getLogger(BatchProcessor::class.java)
    private val docProcessor = DocumentProcessor()
    private val textPreprocessor = TextPreprocessor()
    private val pipeline = state.pipeline
    val kgBuilder = KnowledgeGraphBuilder()

    val allEntities = mutableListOf<Entity>()
    val allRelations = mutableListOf<Relation>()
    val processedFiles = mutableListOf<Path>()

    suspend fun processSingleDocument(path: Path): ProcessedDocument? {
        println("\n" + "=".repeat(70))
        println("Processing: ${path.name}")
        println("=".repeat(70))
        val text =
            try {
                docProcessor.loadDocument(path)
            } catch (e: Exception) {
                log.error("Error loading document: {}", e.message)
                return null
            }
        val preprocessed = textPreprocessor.preprocess(text)
        val extraction = pipeline.extract(preprocessed.cleaned)
        return ProcessedDocument(path, extraction.entities, extraction.relations, text.length)
    }

    /** Processes every file in [directory] matching the glob [pattern] (for example `*.pdf`). */
    suspend fun processDirectory(
        directory: Path,
        pattern: String = "*.pdf",
    ): List<ProcessedDocument> {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        val files =
            Files.list(directory).use {
                it
                    .asSequence()
                    .filter { p ->
                        p.isRegularFile() &&
                            matcher.matches(p.fileName)
                    }.sorted()
                    .toList()
            }
        if (files.isEmpty()) {
            println("No files matching '$pattern' found in $directory")
            return emptyList()
        }
        println("\nFound ${files.size} file(s) to process")
        return processFileList(files)
    }

    suspend fun processFileList(paths: List<Path>): List<ProcessedDocument> =
        paths.mapNotNull { path ->
            processSingleDocument(path)?.also { result ->
                allEntities += result.entities
                allRelations += result.relations
                processedFiles.add(path)
            }
        }

    /** Deduplicates across documents (first occurrence wins), builds, stores, and exports Turtle and PNG. */
    suspend fun buildUnifiedGraph(): ActiveGraph {
        println("\n" + "=".repeat(70))
        println("Building Unified Knowledge Graph")
        println("=".repeat(70))

        val uniqueEntities = allEntities.distinctBy { it.text.lowercase() }
        val uniqueRelations =
            allRelations.distinctBy {
                Triple(it.subject.lowercase(), it.predicate.lowercase(), it.obj.lowercase())
            }
        println("✓ Deduplicated to ${uniqueEntities.size} unique entities")
        println("✓ Deduplicated to ${uniqueRelations.size} unique relations")

        kgBuilder.buildFromExtractions(uniqueEntities, uniqueRelations)
        val stats = kgBuilder.getStatistics()
        println("\nUnified Graph Statistics:")
        println("  total_triples: ${stats.totalTriples}")
        println("  unique_subjects: ${stats.uniqueSubjects}")
        println("  unique_predicates: ${stats.uniquePredicates}")
        println("  unique_objects: ${stats.uniqueObjects}")

        val filename = processedFiles.singleOrNull()?.name ?: "${processedFiles.size} documents"
        val active = state.registerGraph(graphId, filename, kgBuilder, uniqueEntities.size, uniqueRelations.size)
        state
            .filesDir(graphId)
            .resolve("knowledge_graph.ttl")
            .outputStream()
            .use { state.store.export(graphId, it) }
        return active
    }

    /** Directory holding the exported Turtle and PNG of the unified graph. */
    val filesDir: Path get() = state.filesDir(graphId)

    fun getSummary() =
        BatchSummary(
            processedFiles.size,
            allEntities.size,
            allRelations.size,
            processedFiles.map {
                it.name
            },
        )
}

/** Usage: `./gradlew :backend:runBatch -Pargs="<directory> [pattern]"` or `-Pargs="<file1> <file2> ..."`. */
fun main(args: Array<String>) =
    runBlocking {
        if (args.isEmpty()) {
            println("Usage:")
            println("  Process directory: runBatch -Pargs=\"<directory> [pattern]\"")
            println("  Process files: runBatch -Pargs=\"<file1.pdf> <file2.pdf> ...\"")
            println("\nExamples:")
            println("  runBatch -Pargs=\"./documents/\"")
            println("  runBatch -Pargs=\"./documents/ *.pdf\"")
            println("  runBatch -Pargs=\"doc1.pdf doc2.pdf doc3.pdf\"")
            return@runBlocking
        }

        val processor = BatchProcessor()
        val first = Path(args[0])
        val results =
            when {
                first.isDirectory() -> processor.processDirectory(first, args.getOrNull(1) ?: "*.pdf")
                first.isRegularFile() -> processor.processFileList(args.map { Path(it) })
                else -> {
                    println("Error: '$first' is not a valid file or directory")
                    return@runBlocking
                }
            }
        if (results.isEmpty()) {
            println("\nNo documents were successfully processed.")
            return@runBlocking
        }

        val graph = processor.buildUnifiedGraph()
        val summary = processor.getSummary()
        println("\n" + "=".repeat(70))
        println("PROCESSING SUMMARY")
        println("=".repeat(70))
        println("Total files processed: ${summary.totalFiles}")
        println("Total entities extracted: ${summary.totalEntities}")
        println("Total relations extracted: ${summary.totalRelations}")
        println("\nProcessed files:")
        summary.files.forEach { println("  - $it") }

        println("\n✓ Knowledge graph stored as <${graph.kg.namespace}graph/${graph.graphId}> and indexed for search")
        println("\nFiles saved in '${processor.filesDir}/':")
        println("  - knowledge_graph.ttl (RDF export)")
        println("  - knowledge_graph.png (visualization)")
    }
