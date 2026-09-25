package io.github.juantoanxoup.kg

import ai.koog.embeddings.base.Embedder
import ai.koog.prompt.executor.model.PromptExecutor
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readBytes

// ---------------------------------------------------------------------------------------------
// Request and response models (the Pydantic models in api.py)
// ---------------------------------------------------------------------------------------------

@Serializable
data class ErrorDetail(
    val detail: String,
)

@Serializable
data class GraphCreationResponse(
    @SerialName("graph_id") val graphId: String,
    val message: String,
    @SerialName("entities_count") val entitiesCount: Int,
    @SerialName("relations_count") val relationsCount: Int,
    val statistics: GraphStatistics,
    @SerialName("output_dir") val outputDir: String,
)

@Serializable
data class SemanticSearchRequest(
    @SerialName("graph_id") val graphId: String,
    val query: String,
    @SerialName("top_k") val topK: Int = 5,
)

@Serializable
data class QuestionAnswerRequest(
    @SerialName("graph_id") val graphId: String,
    val question: String,
)

@Serializable
data class EntityRelationsRequest(
    @SerialName("graph_id") val graphId: String,
    @SerialName("entity_name") val entityName: String,
)

@Serializable
data class SparqlQueryRequest(
    @SerialName("graph_id") val graphId: String,
    val query: String,
)

@Serializable
data class ChatHistoryMessage(
    val role: String,
    val content: String,
    val timestamp: String,
    val facts: List<String>? = null,
)

@Serializable
data class GraphSummary(
    @SerialName("graph_id") val graphId: String,
    val filename: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("entities_count") val entitiesCount: Int,
    @SerialName("relations_count") val relationsCount: Int,
    val statistics: GraphStatistics,
)

@Serializable
data class GraphInfo(
    @SerialName("graph_id") val graphId: String,
    val filename: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("entities_count") val entitiesCount: Int,
    @SerialName("relations_count") val relationsCount: Int,
    val statistics: GraphStatistics,
    @SerialName("sample_entities") val sampleEntities: List<String>,
)

@Serializable
data class GraphsResponse(
    val graphs: List<GraphSummary>,
    val total: Int,
)

@Serializable
data class HealthResponse(
    val status: String,
    @SerialName("active_graphs") val activeGraphs: Int,
    @SerialName("openai_configured") val openAiConfigured: Boolean,
)

@Serializable
data class MessageResponse(
    val message: String,
)

@Serializable
data class ChatHistoryResponse(
    @SerialName("graph_id") val graphId: String,
    val messages: List<ChatHistoryMessage>,
    val count: Int,
)

@Serializable
data class ChatSavedResponse(
    val message: String,
    @SerialName("total_messages") val totalMessages: Int,
)

@Serializable
data class SemanticSearchResponse(
    val results: List<SearchResult>,
    val query: String,
)

@Serializable
data class QuestionAnswerResponse(
    val question: String,
    val answer: String,
    @SerialName("relevant_facts") val relevantFacts: List<SearchResult>,
)

@Serializable
data class EntityRelationsResponse(
    val entity: String,
    val relations: List<Map<String, String>>,
    val count: Int,
)

@Serializable
data class SparqlQueryResponse(
    val results: List<Map<String, String>>,
    val count: Int,
)

@Serializable
data class EntitiesResponse(
    val entities: List<Entity>,
    val count: Int,
)

@Serializable
data class GraphMetadata(
    @SerialName("graph_id") val graphId: String,
    val filename: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("entities_count") val entitiesCount: Int,
    @SerialName("relations_count") val relationsCount: Int,
    val statistics: GraphStatistics,
)

private val ID_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

/** `YYYYMMDD_HHMMSS_` followed by 8 hex characters, the graph and run identifier used everywhere. */
fun newGraphId(): String =
    "${LocalDateTime.now().format(ID_TIMESTAMP)}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

/** Raised by handlers; rendered as `{"detail": ...}` with the given status, like FastAPI's HTTPException. */
class ApiException(
    val status: HttpStatusCode,
    val detail: String,
) : RuntimeException(detail)

// ---------------------------------------------------------------------------------------------
// In-memory state (the `active_graphs` and `chat_history` dictionaries in api.py)
// ---------------------------------------------------------------------------------------------

/** Everything the API keeps for one graph. */
class ActiveGraph(
    val kgBuilder: KnowledgeGraphBuilder,
    val retriever: SemanticRetriever,
    val querier: KnowledgeGraphQuerier,
    val entities: List<Entity>,
    val relations: List<Relation>,
    val outputDir: Path,
    val filename: String,
    val createdAt: String,
    val statistics: GraphStatistics,
)

/** A file saved from a multipart upload, with the graph identity allocated for it. */
data class UploadedFile(
    val graphId: String,
    val outputDir: Path,
    val path: Path,
    val originalName: String,
)

/** Services and registries used by the routes. Tests construct it with stubs. */
class ApiState(
    val openAiApiKey: String? = Config.openAiApiKey,
    val promptExecutor: PromptExecutor? = OpenAiHelper.promptExecutor(openAiApiKey),
    val pipeline: Pipeline = Pipeline(promptExecutor),
    val embedder: Embedder = DjlEmbedder(),
    val outputRoot: Path = Path(Config.DEFAULT_OUTPUT_DIR),
) {
    private val log = LoggerFactory.getLogger(ApiState::class.java)
    val activeGraphs = ConcurrentHashMap<String, ActiveGraph>()
    val chatHistory = ConcurrentHashMap<String, MutableList<ChatHistoryMessage>>()

    fun newGraphId(): String =
        io.github.juantoanxoup.kg
            .newGraphId()

    fun graph(graphId: String): ActiveGraph =
        activeGraphs[graphId] ?: throw ApiException(HttpStatusCode.NotFound, "Graph not found")

    /** The upload pipeline: load, preprocess, extract, build, save, render, index, register. */
    suspend fun createGraph(file: UploadedFile): GraphCreationResponse {
        val text = DocumentProcessor().loadDocument(file.path)
        log.info("[{}] 20% Document loaded, preprocessing text...", file.graphId)
        val preprocessed = TextPreprocessor().preprocess(text)

        val extraction =
            pipeline.extract(
                preprocessed.cleaned,
                onEntitiesFromNlp = {
                    log.info(
                        "[{}] 40% Extracted {} entities with CoreNLP, using LLM...",
                        file.graphId,
                        it,
                    )
                },
                onEntitiesFromLlm = { log.info("[{}] 50% Merging entities ({} from LLM)...", file.graphId, it) },
                onEntitiesMerged = {
                    log.info(
                        "[{}] 60% Extracting relations ({} unique entities)...",
                        file.graphId,
                        it,
                    )
                },
            )

        val kgBuilder = KnowledgeGraphBuilder()
        kgBuilder.buildFromExtractions(extraction.entities, extraction.relations)
        log.info("[{}] 85% Saving graph...", file.graphId)
        kgBuilder.saveRdf(file.outputDir.resolve("knowledge_graph.ttl"))
        log.info("[{}] 90% Creating visualization...", file.graphId)
        GraphVisualizer(kgBuilder).visualize(file.outputDir.resolve("knowledge_graph.png"))
        log.info("[{}] 95% Indexing for search...", file.graphId)
        val retriever = SemanticRetriever(kgBuilder, embedder)
        retriever.indexGraph()
        val querier = KnowledgeGraphQuerier(kgBuilder)
        val stats = kgBuilder.getStatistics()
        val createdAt = LocalDateTime.now().toString()

        activeGraphs[file.graphId] =
            ActiveGraph(
                kgBuilder = kgBuilder,
                retriever = retriever,
                querier = querier,
                entities = extraction.entities,
                relations = extraction.relations,
                outputDir = file.outputDir,
                filename = file.originalName,
                createdAt = createdAt,
                statistics = stats,
            )

        val metadata =
            GraphMetadata(
                graphId = file.graphId,
                filename = file.originalName,
                createdAt = createdAt,
                entitiesCount = extraction.entities.size,
                relationsCount = extraction.relations.size,
                statistics = stats,
            )
        Files.writeString(
            file.outputDir.resolve("metadata.json"),
            prettyJson.encodeToString(GraphMetadata.serializer(), metadata),
        )

        return GraphCreationResponse(
            graphId = file.graphId,
            message = "Knowledge graph created successfully",
            entitiesCount = extraction.entities.size,
            relationsCount = extraction.relations.size,
            statistics = stats,
            outputDir = file.outputDir.toString(),
        )
    }

    companion object {
        val ALLOWED_EXTENSIONS = setOf(".pdf", ".txt", ".docx", ".doc")
        private val prettyJson = Json { prettyPrint = true }
    }
}

// ---------------------------------------------------------------------------------------------
// Application
// ---------------------------------------------------------------------------------------------

private const val API_VERSION = "1.0.0"
private const val RELEVANT_FACTS = 5
private val TURTLE = ContentType("text", "turtle")
private const val STATIC_RESOURCES = "static"

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8000
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() = module(ApiState())

/** Classpath package of the production UI, or null when the jar carries none (see `backend/build.gradle.kts`). */
fun bundledUiResources(): String? =
    STATIC_RESOURCES.takeIf { Application::class.java.classLoader.getResource("$it/index.html") != null }

/**
 * Installs plugins and routes. Public so tests can supply their own [ApiState].
 * When [uiResources] names a classpath package holding the web UI, it is served at `/` with an SPA fallback.
 */
fun Application.module(
    state: ApiState,
    uiResources: String? = bundledUiResources(),
) {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }
    // Mirrors `allow_origins=["*"]` with credentials in api.py.
    install(CORS) {
        anyHost()
        allowCredentials = true
        allowNonSimpleContentTypes = true
        HttpMethod.DefaultMethods.forEach { allowMethod(it) }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
    install(StatusPages) {
        exception<ApiException> { call, cause -> call.respond(cause.status, ErrorDetail(cause.detail)) }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorDetail(cause.message ?: cause.toString()))
        }
    }
    install(CallLogging)

    routing {
        val apiIndex: suspend RoutingContext.() -> Unit = {
            call.respond(
                buildJsonObject {
                    put("message", "Knowledge Graph API")
                    put("version", API_VERSION)
                    putJsonObject("endpoints") {
                        put("api", "/api")
                        put("health", "/health")
                        put("upload", "/upload")
                        put("graphs", "/graphs")
                        put("semantic_search", "/semantic_search")
                        put("question_answer", "/question_answer")
                        put("entity_relations", "/entity_relations")
                        put("sparql_query", "/sparql_query")
                        put("visualization", "/visualization/{graph_id}")
                        put("download_graph", "/download_graph/{graph_id}")
                    }
                },
            )
        }
        get("/api", apiIndex)
        if (uiResources != null) {
            // Explicit API routes take precedence; every other GET falls back to the SPA shell.
            staticResources("/", uiResources) {
                default("index.html")
            }
        } else {
            // API-only build: keep the original service description at the root.
            get("/", apiIndex)
        }

        get("/health") {
            call.respond(HealthResponse("healthy", state.activeGraphs.size, state.promptExecutor != null))
        }

        post("/upload") {
            var uploaded: UploadedFile? = null
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file" && uploaded == null) {
                    val originalName = Path(part.originalFileName ?: "").name
                    val ext = ".${originalName.substringAfterLast('.', "").lowercase()}"
                    if (ext !in ApiState.ALLOWED_EXTENSIONS) {
                        part.dispose()
                        throw ApiException(
                            HttpStatusCode.BadRequest,
                            "Unsupported file type: $ext. Allowed: ${ApiState.ALLOWED_EXTENSIONS}",
                        )
                    }
                    val graphId = state.newGraphId()
                    val outputDir = state.outputRoot.resolve("kg_$graphId").createDirectories()
                    val target = outputDir.resolve("uploaded_$originalName")
                    part.provider().copyAndClose(target.toFile().writeChannel())
                    uploaded = UploadedFile(graphId, outputDir, target, originalName)
                }
                part.dispose()
            }
            val file =
                uploaded ?: throw ApiException(HttpStatusCode.UnprocessableEntity, "Form field 'file' is required")

            val response =
                try {
                    withContext(Dispatchers.IO) { state.createGraph(file) }
                } catch (e: ApiException) {
                    throw e
                } catch (e: Exception) {
                    file.outputDir.toFile().deleteRecursively()
                    throw ApiException(HttpStatusCode.InternalServerError, e.message ?: e.toString())
                }
            call.respond(response)
        }

        get("/graphs") {
            val graphs =
                state.activeGraphs.map { (id, g) ->
                    GraphSummary(id, g.filename, g.createdAt, g.entities.size, g.relations.size, g.statistics)
                }
            call.respond(GraphsResponse(graphs, graphs.size))
        }

        get("/chat_history/{graph_id}") {
            val id = call.parameters["graph_id"]!!
            state.graph(id)
            val messages = state.chatHistory[id].orEmpty()
            call.respond(ChatHistoryResponse(id, messages, messages.size))
        }

        post("/chat_history/{graph_id}") {
            val id = call.parameters["graph_id"]!!
            state.graph(id)
            val message = call.receive<ChatHistoryMessage>()
            val messages =
                state.chatHistory.computeIfAbsent(
                    id,
                ) { java.util.Collections.synchronizedList(mutableListOf()) }
            messages += message
            call.respond(ChatSavedResponse("Chat message saved", messages.size))
        }

        delete("/chat_history/{graph_id}") {
            val id = call.parameters["graph_id"]!!
            state.graph(id)
            state.chatHistory.remove(id)
            call.respond(MessageResponse("Chat history cleared"))
        }

        get("/graph/{graph_id}") {
            val id = call.parameters["graph_id"]!!
            val g = state.graph(id)
            call.respond(
                GraphInfo(
                    graphId = id,
                    filename = g.filename,
                    createdAt = g.createdAt,
                    entitiesCount = g.entities.size,
                    relationsCount = g.relations.size,
                    statistics = g.statistics,
                    sampleEntities = g.entities.take(10).map { it.text },
                ),
            )
        }

        delete("/graph/{graph_id}") {
            val id = call.parameters["graph_id"]!!
            state.graph(id)
            state.activeGraphs.remove(id)
            call.respond(MessageResponse("Graph $id deleted successfully"))
        }

        post("/semantic_search") {
            val request = call.receive<SemanticSearchRequest>()
            val g = state.graph(request.graphId)
            val results = withContext(Dispatchers.IO) { g.retriever.search(request.query, request.topK) }
            call.respond(SemanticSearchResponse(results, request.query))
        }

        post("/question_answer") {
            val request = call.receive<QuestionAnswerRequest>()
            val g = state.graph(request.graphId)
            val executor =
                state.promptExecutor ?: throw ApiException(HttpStatusCode.BadRequest, "OpenAI API key not configured")
            val answer = withContext(Dispatchers.IO) { g.retriever.answerQuestionLlm(request.question, executor) }
            val facts = withContext(Dispatchers.IO) { g.retriever.search(request.question, RELEVANT_FACTS) }
            call.respond(QuestionAnswerResponse(request.question, answer, facts))
        }

        post("/entity_relations") {
            val request = call.receive<EntityRelationsRequest>()
            val g = state.graph(request.graphId)
            val relations = g.querier.findEntityRelations(request.entityName)
            call.respond(EntityRelationsResponse(request.entityName, relations, relations.size))
        }

        post("/sparql_query") {
            val request = call.receive<SparqlQueryRequest>()
            val g = state.graph(request.graphId)
            val results =
                try {
                    g.querier.query(request.query)
                } catch (e: Exception) {
                    throw ApiException(HttpStatusCode.InternalServerError, "Query execution failed: ${e.message}")
                }
            call.respond(SparqlQueryResponse(results, results.size))
        }

        get("/visualization/{graph_id}") {
            val g = state.graph(call.parameters["graph_id"]!!)
            val image = g.outputDir.resolve("knowledge_graph.png")
            if (!image.exists()) throw ApiException(HttpStatusCode.NotFound, "Visualization not found")
            call.respondBytes(image.readBytes(), ContentType.Image.PNG)
        }

        get("/download_graph/{graph_id}") {
            val id = call.parameters["graph_id"]!!
            val g = state.graph(id)
            val turtle = g.outputDir.resolve("knowledge_graph.ttl")
            if (!turtle.exists()) throw ApiException(HttpStatusCode.NotFound, "Graph file not found")
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(
                        ContentDisposition.Parameters.FileName,
                        "knowledge_graph_$id.ttl",
                    ).toString(),
            )
            call.respondBytes(turtle.readBytes(), TURTLE)
        }

        get("/entities/{graph_id}") {
            val g = state.graph(call.parameters["graph_id"]!!)
            call.respond(EntitiesResponse(g.entities, g.entities.size))
        }
    }
}
