package io.github.juantoanxoup.kg

import ai.koog.prompt.executor.model.PromptExecutor

/** Entities and relations extracted from one text. */
data class Extraction(
    val entities: List<Entity>,
    val relations: List<Relation>,
)

/**
 * The extraction steps shared by the API, the CLI and the batch processor.
 * The original repeated these calls in api.py, main.py and batch_processor.py.
 */
class Pipeline(
    executor: PromptExecutor? = OpenAiHelper.promptExecutor(),
    private val entityExtractor: EntityExtractor = EntityExtractor(executor),
    private val relationExtractor: RelationExtractor = RelationExtractor(executor),
) {
    /** NLP entities, LLM entities, merge; pattern relations, LLM relations, merge. */
    suspend fun extract(
        cleanedText: String,
        onEntitiesFromNlp: (Int) -> Unit = {},
        onEntitiesFromLlm: (Int) -> Unit = {},
        onEntitiesMerged: (Int) -> Unit = {},
    ): Extraction {
        val nlpEntities = entityExtractor.extractEntitiesNlp(cleanedText)
        onEntitiesFromNlp(nlpEntities.size)
        val llmEntities = entityExtractor.extractEntitiesLlm(cleanedText)
        onEntitiesFromLlm(llmEntities.size)
        val entities = entityExtractor.mergeEntities(nlpEntities, llmEntities)
        onEntitiesMerged(entities.size)

        val patternRelations = relationExtractor.extractRelationsPattern(cleanedText, entities)
        val llmRelations = relationExtractor.extractRelationsLlm(cleanedText, entities)
        val relations = relationExtractor.mergeRelations(patternRelations, llmRelations)
        return Extraction(entities, relations)
    }
}
