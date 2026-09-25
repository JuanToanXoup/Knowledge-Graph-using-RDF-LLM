package io.github.juantoanxoup.kg

import ai.koog.agents.testing.tools.getMockExecutor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelationExtractorTest {
    private val entities = listOf(Entity("Albert Einstein", "PERSON"), Entity("Princeton University", "ORG"))

    @Test
    fun `llm relations are validated and predicates normalized`() =
        runTest {
            val executor =
                getMockExecutor {
                    mockLLMAnswer(
                        """{"relations":[
                           {"subject":"Albert Einstein","predicate":"Worked At","object":"Princeton University"},
                           {"subject":"","predicate":"x","object":"y"},
                           {"subject":"a","predicate":"b"}
                        ]}""",
                    ).asDefaultResponse
                }
            val relations = RelationExtractor(executor).extractRelationsLlm("Einstein worked at Princeton.", entities)
            assertEquals(listOf(Relation("Albert Einstein", "worked_at", "Princeton University", 0.9)), relations)
        }

    @Test
    fun `no entities or no executor means no llm relations`() =
        runTest {
            val executor = getMockExecutor { mockLLMAnswer("""{"relations":[]}""").asDefaultResponse }
            assertTrue(RelationExtractor(executor).extractRelationsLlm("text", emptyList()).isEmpty())
            assertTrue(RelationExtractor(executor = null).extractRelationsLlm("text", entities).isEmpty())
        }

    @Test
    fun `merge is keyed by lower-cased triple and llm relations override pattern ones`() {
        val extractor = RelationExtractor(executor = null)
        val merged =
            extractor.mergeRelations(
                patternRelations =
                    listOf(
                        Relation("Einstein", "worked_at", "Princeton", 0.7),
                        Relation("Curie", "studied", "Paris", 0.7),
                    ),
                llmRelations = listOf(Relation("einstein", "worked_at", "princeton", 0.9)),
            )
        assertEquals(2, merged.size)
        assertEquals(Relation("einstein", "worked_at", "princeton", 0.9), merged.first())
    }

    @Test
    fun `sentences are chunked without exceeding the limit`() {
        val extractor = RelationExtractor(executor = null)
        val sentences = listOf("a".repeat(40), "b".repeat(40), "c".repeat(40), "d".repeat(10))
        val chunks = extractor.chunkSentences(sentences, maxChunkLength = 85)
        assertEquals(listOf("${"a".repeat(40)} ${"b".repeat(40)}", "${"c".repeat(40)} ${"d".repeat(10)}"), chunks)
    }
}
