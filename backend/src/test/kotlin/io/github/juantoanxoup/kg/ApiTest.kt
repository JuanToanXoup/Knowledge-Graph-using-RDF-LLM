package io.github.juantoanxoup.kg

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun stateWithGraph(root: Path): Pair<ApiState, String> {
        val state = ApiState(openAiApiKey = null, promptExecutor = null, embedder = StubEmbedder(), outputRoot = root)
        val kg = sampleGraph()
        val id = "20250101_000000_abcdef01"
        val dir = root.resolve("kg_$id").createDirectories()
        kg.saveRdf(dir.resolve("knowledge_graph.ttl"))
        GraphVisualizer(kg).visualize(dir.resolve("knowledge_graph.png"))
        val retriever = SemanticRetriever(kg, StubEmbedder())
        runBlocking { retriever.indexGraph() }
        state.activeGraphs[id] =
            ActiveGraph(
                kgBuilder = kg,
                retriever = retriever,
                querier = KnowledgeGraphQuerier(kg),
                entities = listOf(Entity("Albert Einstein", "PERSON"), Entity("Marie Curie", "PERSON")),
                relations = listOf(Relation("Albert Einstein", "worked_at", "Princeton University", 0.9)),
                outputDir = dir,
                filename = "sample.txt",
                createdAt = LocalDateTime.now().toString(),
                statistics = kg.getStatistics(),
            )
        return state to id
    }

    @Test
    fun `root and health describe the service`(
        @TempDir root: Path,
    ) = testApplication {
        application {
            module(ApiState(openAiApiKey = null, promptExecutor = null, embedder = StubEmbedder(), outputRoot = root))
        }
        val rootBody = json.parseToJsonElement(client.get("/").bodyAsText()).jsonObject
        assertEquals("Knowledge Graph API", rootBody["message"]!!.jsonPrimitive.content)
        assertEquals("/sparql_query", rootBody["endpoints"]!!.jsonObject["sparql_query"]!!.jsonPrimitive.content)

        val health = json.parseToJsonElement(client.get("/health").bodyAsText()).jsonObject
        assertEquals("healthy", health["status"]!!.jsonPrimitive.content)
        assertEquals("0", health["active_graphs"]!!.jsonPrimitive.content)
        assertEquals("false", health["openai_configured"]!!.jsonPrimitive.content)
    }

    @Test
    fun `bundled UI is served at the root with an SPA fallback`(
        @TempDir root: Path,
    ) = testApplication {
        application {
            module(
                ApiState(openAiApiKey = null, promptExecutor = null, embedder = StubEmbedder(), outputRoot = root),
                uiResources = "test-ui",
            )
        }
        for (path in listOf("/", "/workspace", "/workspace/20250101_000000_abcdef01")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status, path)
            assertTrue(response.bodyAsText().contains("<div id=\"root\">"), path)
        }

        val api = json.parseToJsonElement(client.get("/api").bodyAsText()).jsonObject
        assertEquals("Knowledge Graph API", api["message"]!!.jsonPrimitive.content)
        val health = json.parseToJsonElement(client.get("/health").bodyAsText()).jsonObject
        assertEquals("healthy", health["status"]!!.jsonPrimitive.content)
        val missing = client.get("/graph/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertTrue(json.parseToJsonElement(missing.bodyAsText()).jsonObject.containsKey("detail"))
    }

    @Test
    fun `unknown graphs answer 404 with a detail`(
        @TempDir root: Path,
    ) = testApplication {
        application {
            module(ApiState(openAiApiKey = null, promptExecutor = null, embedder = StubEmbedder(), outputRoot = root))
        }
        val response = client.get("/graph/nope")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("""{"detail":"Graph not found"}""", response.bodyAsText())
    }

    @Test
    fun `graph endpoints expose info entities sparql relations search and files`(
        @TempDir root: Path,
    ) = testApplication {
        val (state, id) = stateWithGraph(root)
        application { module(state) }

        val graphs = json.parseToJsonElement(client.get("/graphs").bodyAsText()).jsonObject
        assertEquals("1", graphs["total"]!!.jsonPrimitive.content)

        val info = json.parseToJsonElement(client.get("/graph/$id").bodyAsText()).jsonObject
        assertEquals("sample.txt", info["filename"]!!.jsonPrimitive.content)
        assertEquals(2, info["sample_entities"]!!.jsonArray.size)
        assertEquals("10", info["statistics"]!!.jsonObject["total_triples"]!!.jsonPrimitive.content)

        val entities = json.parseToJsonElement(client.get("/entities/$id").bodyAsText()).jsonObject
        assertEquals("2", entities["count"]!!.jsonPrimitive.content)

        val sparql =
            client.post("/sparql_query") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"graph_id":"$id","query":"SELECT ?s WHERE { ?s <${Config.DEFAULT_NAMESPACE}worked_at> ?o }"}""",
                )
            }
        assertEquals(HttpStatusCode.OK, sparql.status)
        assertEquals("""{"results":[{"s":"Albert Einstein"}],"count":1}""", sparql.bodyAsText())

        val badSparql =
            client.post("/sparql_query") {
                contentType(ContentType.Application.Json)
                setBody("""{"graph_id":"$id","query":"SELECT ?s WHERE {"}""")
            }
        assertEquals(HttpStatusCode.InternalServerError, badSparql.status)
        assertTrue(badSparql.bodyAsText().contains("Query execution failed"))

        val relations =
            client.post("/entity_relations") {
                contentType(ContentType.Application.Json)
                setBody("""{"graph_id":"$id","entity_name":"Albert Einstein"}""")
            }
        val relationsBody = json.parseToJsonElement(relations.bodyAsText()).jsonObject
        assertEquals("Albert Einstein", relationsBody["entity"]!!.jsonPrimitive.content)
        assertEquals("3", relationsBody["count"]!!.jsonPrimitive.content)

        val search =
            client.post("/semantic_search") {
                contentType(ContentType.Application.Json)
                setBody("""{"graph_id":"$id","query":"einstein princeton","top_k":1}""")
            }
        val searchBody = json.parseToJsonElement(search.bodyAsText()).jsonObject
        assertEquals(1, searchBody["results"]!!.jsonArray.size)

        val qa =
            client.post("/question_answer") {
                contentType(ContentType.Application.Json)
                setBody("""{"graph_id":"$id","question":"who?"}""")
            }
        assertEquals(HttpStatusCode.BadRequest, qa.status)

        val png = client.get("/visualization/$id")
        assertEquals(HttpStatusCode.OK, png.status)
        assertEquals("image/png", png.headers["Content-Type"])

        val ttl = client.get("/download_graph/$id")
        assertEquals(HttpStatusCode.OK, ttl.status)
        assertTrue(ttl.headers["Content-Type"]!!.startsWith("text/turtle"))
        assertTrue(ttl.headers["Content-Disposition"]!!.contains("knowledge_graph_$id.ttl"))
        assertTrue(ttl.bodyAsText().contains("Albert_Einstein"))
    }

    @Test
    fun `chat history is stored per graph and can be cleared`(
        @TempDir root: Path,
    ) = testApplication {
        val (state, id) = stateWithGraph(root)
        application { module(state) }

        val saved =
            client.post("/chat_history/$id") {
                contentType(ContentType.Application.Json)
                setBody("""{"role":"user","content":"hi","timestamp":"2025-01-01T00:00:00Z"}""")
            }
        assertEquals("""{"message":"Chat message saved","total_messages":1}""", saved.bodyAsText())

        val history = json.parseToJsonElement(client.get("/chat_history/$id").bodyAsText()).jsonObject
        assertEquals("1", history["count"]!!.jsonPrimitive.content)

        assertEquals("""{"message":"Chat history cleared"}""", client.delete("/chat_history/$id").bodyAsText())
        val cleared = json.parseToJsonElement(client.get("/chat_history/$id").bodyAsText()).jsonObject
        assertEquals("0", cleared["count"]!!.jsonPrimitive.content)
    }

    @Test
    fun `upload rejects unsupported extensions and deleting removes only the registry entry`(
        @TempDir root: Path,
    ) = testApplication {
        val (state, id) = stateWithGraph(root)
        application { module(state) }

        val bad =
            client.post("/upload") {
                contentType(ContentType.MultiPart.FormData.withParameter("boundary", "xyz"))
                setBody(
                    "--xyz\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.csv\"\r\n\r\na,b\r\n--xyz--\r\n",
                )
            }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertTrue(bad.bodyAsText().contains("Unsupported file type: .csv"))

        assertEquals("""{"message":"Graph $id deleted successfully"}""", client.delete("/graph/$id").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/graph/$id").status)
        assertTrue(root.resolve("kg_$id/knowledge_graph.ttl").exists())
    }
}
