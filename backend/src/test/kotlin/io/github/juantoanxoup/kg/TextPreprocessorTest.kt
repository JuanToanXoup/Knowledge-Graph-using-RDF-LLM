package io.github.juantoanxoup.kg

import kotlin.test.Test
import kotlin.test.assertEquals

class TextPreprocessorTest {
    private val preprocessor = TextPreprocessor()

    @Test
    fun `cleaning collapses whitespace and strips characters outside the allowed set`() {
        val cleaned = preprocessor.cleanText("Hello,   world! (test) — “quoted” 日本語\n\tdone.")
        assertEquals("Hello, world! test  quoted 日本語 done.", cleaned)
    }

    @Test
    fun `sentences are split with CoreNLP`() {
        val result = preprocessor.preprocess("Einstein worked at Princeton. Curie worked in Paris!")
        assertEquals(listOf("Einstein worked at Princeton.", "Curie worked in Paris!"), result.sentences)
        assertEquals("Einstein worked at Princeton. Curie worked in Paris!", result.cleaned)
    }
}
