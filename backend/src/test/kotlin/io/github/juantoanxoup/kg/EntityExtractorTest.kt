package io.github.juantoanxoup.kg

import ai.koog.agents.testing.tools.getMockExecutor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityExtractorTest {
    @Test
    fun `llm entities are parsed as structured output and untyped ones become UNKNOWN`() =
        runTest {
            val executor =
                getMockExecutor {
                    mockLLMAnswer(
                        """{"entities":[{"text":"Albert Einstein","type":"PERSON"},{"text":"Ulm"}]}""",
                    ).asDefaultResponse
                }
            val entities = EntityExtractor(executor).extractEntitiesLlm("Albert Einstein was born in Ulm.")
            assertEquals(listOf(Entity("Albert Einstein", "PERSON"), Entity("Ulm", "UNKNOWN")), entities)
        }

    @Test
    fun `malformed llm output yields no entities instead of failing`() =
        runTest {
            val executor = getMockExecutor { mockLLMAnswer("not json at all").asDefaultResponse }
            assertTrue(EntityExtractor(executor).extractEntitiesLlm("text").isEmpty())
        }

    @Test
    fun `without an executor the llm pass is skipped`() =
        runTest {
            assertTrue(EntityExtractor(executor = null).extractEntitiesLlm("text").isEmpty())
        }

    @Test
    fun `merge is keyed by lower-cased text and llm entities win`() {
        val merged =
            EntityExtractor(executor = null).mergeEntities(
                nlpEntities = listOf(NlpEntity("Einstein", "PERSON", 0, 8), NlpEntity("Princeton", "ORG", 20, 29)),
                llmEntities = listOf(Entity("einstein", "SCIENTIST"), Entity("Ulm", "GPE")),
            )
        assertEquals(listOf(Entity("einstein", "SCIENTIST"), Entity("Princeton", "ORG"), Entity("Ulm", "GPE")), merged)
    }
}
