package io.github.juantoanxoup.kg

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.params.LLMParams
import edu.stanford.nlp.pipeline.CoreDocument
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/** Entity after merge: surface text and coarse type. */
@Serializable
data class Entity(
    val text: String,
    val type: String,
)

/** Entity from the NLP pass with character offsets. */
@Serializable
data class NlpEntity(
    val text: String,
    val label: String,
    val start: Int,
    val end: Int,
)

/** Structured output requested from the LLM. */
@Serializable
data class ExtractedEntities(
    val entities: List<ExtractedEntity>,
)

@Serializable
data class ExtractedEntity(
    val text: String,
    val type: String? = null,
)

/** Named entity recognition with CoreNLP and an LLM (entity_extractor.py). */
class EntityExtractor(
    private val executor: PromptExecutor? = OpenAiHelper.promptExecutor(),
) {
    private val log = LoggerFactory.getLogger(EntityExtractor::class.java)

    /** NLP pass. CoreNLP labels are mapped to the label set the graph builder understands. */
    fun extractEntitiesNlp(text: String): List<NlpEntity> {
        val doc = CoreDocument(text)
        Nlp.pipeline.annotate(doc)
        val entities =
            doc.entityMentions().map { mention ->
                val offsets = mention.charOffsets()
                NlpEntity(
                    text = mention.text(),
                    label = mapLabel(mention.entityType()),
                    start = offsets.first(),
                    end = offsets.second(),
                )
            }
        log.info("Extracted {} entities using CoreNLP", entities.size)
        return entities
    }

    /**
     * LLM pass over the whole text as a structured-output request. Returns an empty list when no
     * executor is configured or the response cannot be parsed even after Koog's fixing pass.
     */
    suspend fun extractEntitiesLlm(text: String): List<Entity> {
        val llm = executor ?: return emptyList()
        val request =
            prompt("entity-extraction", LLMParams(temperature = 0.0)) {
                system("You are an expert at named entity recognition. Return only valid JSON.")
                user(
                    """
                    Extract all named entities from the following text.
                    Give each entity a type such as PERSON, ORG, GPE, DATE, WORK_OF_ART, EVENT.

                    Text: $text
                    """.trimIndent(),
                )
            }
        val result =
            llm.executeStructured<ExtractedEntities>(
                prompt = request,
                model = OpenAiHelper.defaultModel,
                fixingParser = StructureFixingParser(OpenAiHelper.defaultModel, retries = 1),
            )
        return result
            .map { response -> response.data.entities.map { Entity(it.text, it.type ?: "UNKNOWN") } }
            .onSuccess { log.info("Extracted {} entities using LLM", it.size) }
            .onFailure { log.warn("LLM entity extraction failed: {}", it.message) }
            .getOrDefault(emptyList())
    }

    /** Keyed by lower-cased text; LLM entities overwrite NLP entities on collision. */
    fun mergeEntities(
        nlpEntities: List<NlpEntity>,
        llmEntities: List<Entity>,
    ): List<Entity> {
        val entityMap = LinkedHashMap<String, Entity>()
        for (e in nlpEntities) entityMap[e.text.lowercase()] = Entity(e.text, e.label)
        for (e in llmEntities) entityMap[e.text.lowercase()] = Entity(e.text, e.type)
        val entities = entityMap.values.toList()
        log.info("Merged into {} unique entities", entities.size)
        return entities
    }

    private companion object {
        /** CoreNLP coarse labels to the spaCy-style labels used by the graph builder. */
        fun mapLabel(label: String): String =
            when (label) {
                "ORGANIZATION" -> "ORG"
                "LOCATION", "CITY", "COUNTRY", "STATE_OR_PROVINCE" -> "GPE"
                else -> label
            }
    }
}
