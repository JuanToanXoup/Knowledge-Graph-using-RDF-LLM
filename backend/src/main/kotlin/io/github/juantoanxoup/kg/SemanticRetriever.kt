package io.github.juantoanxoup.kg

import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/** One indexed triple, verbalized with labels. */
@Serializable
data class IndexedTriple(
    val subject: String,
    val predicate: String,
    @SerialName("object") val obj: String,
    val text: String,
)

/** A search hit: the triple plus its dot-product score. */
@Serializable
data class SearchResult(
    val subject: String,
    val predicate: String,
    @SerialName("object") val obj: String,
    val text: String,
    val similarity: Double,
)

/** Embeds every triple and answers questions with retrieved context (semantic_retriever.py). */
class SemanticRetriever(
    private val kg: KnowledgeGraphBuilder,
    private val embedder: Embedder = DjlEmbedder(),
) {
    private val log = LoggerFactory.getLogger(SemanticRetriever::class.java)

    var triples: List<IndexedTriple> = emptyList()
        private set
    private var embeddings: List<Vector>? = null

    suspend fun indexGraph() {
        // Labels are resolved inside one read transaction; embedding happens outside it because it suspends.
        val indexed =
            kg.read { model ->
                model.listStatements().toList().map { statement ->
                    val subject = kg.label(statement.subject)
                    val predicate = kg.label(statement.predicate)
                    val obj = kg.label(statement.`object`)
                    IndexedTriple(subject, predicate, obj, "$subject $predicate $obj")
                }
            }
        triples = indexed
        embeddings = indexed.map { embedder.embed(it.text) }
        log.info("Indexed {} triples for semantic search", indexed.size)
    }

    /** Top-k triples by dot product with the query embedding, descending. */
    suspend fun search(
        query: String,
        topK: Int = 5,
    ): List<SearchResult> {
        val vectors =
            embeddings ?: run {
                indexGraph()
                embeddings.orEmpty()
            }
        val queryVector = embedder.embed(query)
        return vectors
            .mapIndexed { i, v -> i to (v dotProduct queryVector) }
            .sortedByDescending { it.second }
            .take(topK)
            .map { (i, score) ->
                val t = triples[i]
                SearchResult(t.subject, t.predicate, t.obj, t.text, score)
            }
    }

    /** Retrieval-augmented answer: top-10 triples as context, then the LLM. */
    suspend fun answerQuestionLlm(
        question: String,
        executor: PromptExecutor,
    ): String =
        runCatching { executor.execute(questionPrompt(question), OpenAiHelper.defaultModel).textContent() }
            .onFailure { log.error("Question answering failed: {}", it.message) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: NO_ANSWER

    /**
     * The answer as the model writes it: a flow of text pieces in order. Failures surface to the collector, which
     * decides what to show; an empty answer is the collector's to replace with [NO_ANSWER].
     */
    suspend fun answerQuestionLlmStreaming(
        question: String,
        executor: PromptExecutor,
    ): Flow<String> = executor.executeStreaming(questionPrompt(question), OpenAiHelper.defaultModel).filterTextOnly()

    /** The question with the graph facts nearest to it as context, the prompt both answer paths send. */
    private suspend fun questionPrompt(question: String): Prompt {
        val relevant = search(question, topK = QA_CONTEXT_SIZE)
        val context = relevant.joinToString("\n") { "- ${it.subject} ${it.predicate} ${it.obj}" }
        return prompt("question-answering", LLMParams(temperature = QA_TEMPERATURE)) {
            system("You are a helpful assistant that answers questions based on knowledge graph facts.")
            user(
                """
                Based on the following knowledge graph facts, answer the question.

                Facts:
                $context

                Question: $question

                Answer:
                """.trimIndent(),
            )
        }
    }

    companion object {
        const val NO_ANSWER = "Unable to generate answer"
        private const val QA_CONTEXT_SIZE = 10
        private const val QA_TEMPERATURE = 0.7
    }
}

/** Koog [Embedder] backed by `all-MiniLM-L6-v2` through DJL's Hugging Face PyTorch model zoo. */
class DjlEmbedder(
    modelId: String = Config.SENTENCE_TRANSFORMER_MODEL,
) : Embedder,
    AutoCloseable {
    private val model: ZooModel<String, FloatArray> by lazy {
        Criteria
            .builder()
            .setTypes(String::class.java, FloatArray::class.java)
            .optModelUrls("djl://ai.djl.huggingface.pytorch/$modelId")
            .optEngine("PyTorch")
            .optTranslatorFactory(TextEmbeddingTranslatorFactory())
            .build()
            .loadModel()
    }

    override suspend fun embed(text: String): Vector =
        Vector(
            model
                .newPredictor()
                .use {
                    it.predict(text)
                }.map(Float::toDouble),
        )

    /** Cosine distance: lower means more similar, as the interface requires. */
    override fun diff(
        embedding1: Vector,
        embedding2: Vector,
    ): Double = 1.0 - embedding1.cosineSimilarity(embedding2)

    override fun close() = model.close()
}
