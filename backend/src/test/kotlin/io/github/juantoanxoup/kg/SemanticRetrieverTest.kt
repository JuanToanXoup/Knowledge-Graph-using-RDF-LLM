package io.github.juantoanxoup.kg

import ai.koog.agents.testing.tools.getMockExecutor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemanticRetrieverTest {
    private val retriever = SemanticRetriever(sampleGraph(), StubEmbedder())

    @Test
    fun `every triple is indexed with labels`() =
        runTest {
            retriever.indexGraph()
            assertEquals(10, retriever.triples.size)
            assertTrue(retriever.triples.any { it.text == "Albert Einstein worked at Princeton University" })
        }

    @Test
    fun `search ranks by dot product and respects top k`() =
        runTest {
            val results = retriever.search("einstein princeton", topK = 2)
            assertEquals(2, results.size)
            assertEquals("Albert Einstein worked at Princeton University", results.first().text)
            assertTrue(results.first().similarity >= results.last().similarity)
        }

    @Test
    fun `answers come from the llm with retrieved facts as context`() =
        runTest {
            val executor =
                getMockExecutor {
                    mockLLMAnswer("Einstein worked at Princeton University.") onRequestContains
                        "Albert Einstein worked at Princeton University"
                    mockLLMAnswer("no context").asDefaultResponse
                }
            assertEquals(
                "Einstein worked at Princeton University.",
                retriever.answerQuestionLlm("Where did Einstein work?", executor),
            )
        }
}
