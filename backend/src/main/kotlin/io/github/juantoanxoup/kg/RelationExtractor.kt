package io.github.juantoanxoup.kg

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.params.LLMParams
import edu.stanford.nlp.pipeline.CoreDocument
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/** A (subject, predicate, object) relation between two entity surface strings. */
@Serializable
data class Relation(
    val subject: String,
    val predicate: String,
    @SerialName("object") val obj: String,
    val confidence: Double,
)

/** Structured output requested from the LLM. */
@Serializable
data class ExtractedRelations(
    val relations: List<ExtractedRelation>,
)

@Serializable
data class ExtractedRelation(
    val subject: String? = null,
    val predicate: String? = null,
    @SerialName("object") val obj: String? = null,
)

/** Relation extraction with dependency patterns and an LLM (relation_extractor.py). */
class RelationExtractor(
    private val executor: PromptExecutor? = OpenAiHelper.promptExecutor(),
) {
    private val log = LoggerFactory.getLogger(RelationExtractor::class.java)

    /**
     * Pattern pass over Universal Dependencies: `nsubj`/`nsubj:pass` gives subject and verb,
     * `obj`/`iobj`/`obl*` children of the verb give the object. Kept only when both ends are known entities.
     */
    fun extractRelationsPattern(
        text: String,
        entities: List<Entity>,
    ): List<Relation> {
        val doc = CoreDocument(text)
        Nlp.pipeline.annotate(doc)
        val entityTexts = entities.map { it.text.lowercase() }.toSet()
        val relations = mutableListOf<Relation>()

        for (sentence in doc.sentences()) {
            val graph = sentence.dependencyParse() ?: continue
            for (edge in graph.edgeIterable()) {
                val relation = edge.relation.toString()
                if (relation != "nsubj" && relation != "nsubj:pass") continue
                val subject = edge.dependent.word()
                val verb = edge.governor.word()
                for (child in graph.outgoingEdgeIterable(edge.governor)) {
                    val childRelation = child.relation.toString()
                    if (childRelation == "obj" || childRelation == "iobj" || childRelation.startsWith("obl")) {
                        val obj = child.dependent.word()
                        if (subject.lowercase() in entityTexts && obj.lowercase() in entityTexts) {
                            relations += Relation(subject, verb, obj, PATTERN_CONFIDENCE)
                        }
                    }
                }
            }
        }
        log.info("Extracted {} relations using patterns", relations.size)
        return relations
    }

    /**
     * LLM pass as a structured-output request. Transport retries come from Koog's retrying client and
     * malformed JSON is repaired by [StructureFixingParser]; long texts are chunked by sentence.
     */
    suspend fun extractRelationsLlm(
        text: String,
        entities: List<Entity>,
    ): List<Relation> {
        val llm = executor
        if (llm == null) {
            log.warn("LLM extraction skipped: No API key provided")
            return emptyList()
        }
        if (entities.isEmpty()) {
            log.warn("No entities provided for relation extraction")
            return emptyList()
        }
        if (text.length > MAX_TEXT_LENGTH) return extractRelationsChunked(text, entities)

        val entityList = entities.joinToString(", ") { it.text }
        val request =
            prompt("relation-extraction", LLMParams(temperature = LLM_TEMPERATURE)) {
                system(
                    "You are an expert at knowledge graph construction and relationship extraction. You always return valid JSON.",
                )
                user(
                    """
                    You are an expert knowledge graph builder. Extract ALL meaningful relationships between the given entities from the text.

                    ENTITIES TO FIND RELATIONSHIPS FOR:
                    $entityList

                    TEXT:
                    $text

                    INSTRUCTIONS:
                    1. Find relationships between ANY two entities from the list
                    2. Include various relationship types: worked_at, born_in, studied_at, discovered, developed, invented, founded, married_to, colleague_of, collaborated_with, published, awarded, etc.
                    3. Extract both explicit and implicit relationships
                    4. Use clear, consistent predicate names (lowercase, underscored)
                    5. If no relationships are found, return an empty list
                    """.trimIndent(),
                )
            }
        val result =
            llm.executeStructured<ExtractedRelations>(
                prompt = request,
                model = OpenAiHelper.defaultModel,
                fixingParser = StructureFixingParser(OpenAiHelper.defaultModel, retries = FIXING_RETRIES),
            )
        return result
            .map { validateRelations(it.data.relations) }
            .onSuccess { log.info("Extracted {} relations using LLM ({})", it.size, OpenAiHelper.defaultModel.id) }
            .onFailure { log.error("LLM relation extraction failed: {}", it.message) }
            .getOrDefault(emptyList())
    }

    private suspend fun extractRelationsChunked(
        text: String,
        entities: List<Entity>,
    ): List<Relation> {
        log.info("Text too long, splitting into chunks...")
        val doc = CoreDocument(text)
        Nlp.sentenceSplitter.annotate(doc)
        val chunks = chunkSentences(doc.sentences().map { it.text() }, CHUNK_LENGTH)
        log.info("Processing {} chunks...", chunks.size)

        val all = mutableListOf<Relation>()
        chunks.forEachIndexed { i, chunk ->
            log.info("Chunk {}/{}...", i + 1, chunks.size)
            all += extractRelationsLlm(chunk, entities)
        }
        log.info("Extracted {} total relations from all chunks", all.size)
        return all
    }

    /** Groups sentences into chunks of at most [maxChunkLength] characters. */
    internal fun chunkSentences(
        sentences: List<String>,
        maxChunkLength: Int,
    ): List<String> {
        val chunks = mutableListOf<String>()
        val current = mutableListOf<String>()
        var currentLength = 0
        for (sentence in sentences) {
            if (currentLength + sentence.length > maxChunkLength && current.isNotEmpty()) {
                chunks += current.joinToString(" ")
                current.clear()
                current += sentence
                currentLength = sentence.length
            } else {
                current += sentence
                currentLength += sentence.length
            }
        }
        if (current.isNotEmpty()) chunks += current.joinToString(" ")
        return chunks
    }

    /** Requires non-empty subject, predicate, object; normalizes the predicate; assigns LLM confidence. */
    internal fun validateRelations(relations: List<ExtractedRelation>): List<Relation> =
        relations.mapNotNull { rel ->
            val subject = rel.subject?.trim().orEmpty()
            val predicate = rel.predicate?.trim().orEmpty()
            val obj = rel.obj?.trim().orEmpty()
            if (subject.isEmpty() || predicate.isEmpty() || obj.isEmpty()) return@mapNotNull null
            Relation(subject, predicate.lowercase().replace(' ', '_'), obj, LLM_CONFIDENCE)
        }

    /** Keyed by lower-cased triple; LLM relations overwrite pattern relations on collision. */
    fun mergeRelations(
        patternRelations: List<Relation>,
        llmRelations: List<Relation>,
    ): List<Relation> {
        val map = LinkedHashMap<Triple<String, String, String>, Relation>()
        for (rel in patternRelations) map[rel.key()] = rel
        for (rel in llmRelations) map[rel.key()] = rel
        val merged = map.values.toList()
        log.info("Merged into {} unique relations", merged.size)
        if (merged.isNotEmpty()) log.info("Sample relations: {}", merged.take(3))
        return merged
    }

    private fun Relation.key() = Triple(subject.lowercase(), predicate.lowercase(), obj.lowercase())

    private companion object {
        const val PATTERN_CONFIDENCE = 0.7
        const val LLM_CONFIDENCE = 0.9
        const val LLM_TEMPERATURE = 0.3
        const val MAX_TEXT_LENGTH = 6000
        const val CHUNK_LENGTH = 5000
        const val FIXING_RETRIES = 2
    }
}
