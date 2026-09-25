package io.github.juantoanxoup.kg

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector

/** Deterministic bag-of-words embedder so retrieval tests need no model download. */
class StubEmbedder : Embedder {
    private val vocabulary =
        listOf("einstein", "curie", "newton", "relativity", "radium", "physicist", "princeton", "paris")

    override suspend fun embed(text: String): Vector {
        val words = text.lowercase().split(Regex("\\W+")).toSet()
        return Vector(vocabulary.map { if (it in words) 1.0 else 0.0 })
    }

    override fun diff(
        embedding1: Vector,
        embedding2: Vector,
    ): Double = 1.0 - embedding1.cosineSimilarity(embedding2)
}

/** A small graph shared by several tests: two people, one organization, two relations. */
fun sampleGraph(): KnowledgeGraphBuilder =
    KnowledgeGraphBuilder().apply {
        buildFromExtractions(
            entities =
                listOf(
                    Entity("Albert Einstein", "PERSON"),
                    Entity("Marie Curie", "PERSON"),
                    Entity("Princeton University", "ORG"),
                    Entity("1879", "DATE"),
                ),
            relations =
                listOf(
                    Relation("Albert Einstein", "worked_at", "Princeton University", 0.9),
                    Relation("Marie Curie", "colleague_of", "Albert Einstein", 0.7),
                ),
        )
    }
