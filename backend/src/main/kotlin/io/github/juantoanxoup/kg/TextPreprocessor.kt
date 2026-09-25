package io.github.juantoanxoup.kg

import edu.stanford.nlp.pipeline.CoreDocument
import org.slf4j.LoggerFactory

/** Output of [TextPreprocessor.preprocess]. */
data class Preprocessed(
    val original: String,
    val cleaned: String,
    val sentences: List<String>,
)

/** Text cleaning and sentence splitting (text_preprocessor.py). */
class TextPreprocessor {
    private val log = LoggerFactory.getLogger(TextPreprocessor::class.java)

    /** Collapses whitespace, removes characters outside word/space/`.,;:!?-`, trims. `(?U)` matches Python's Unicode `\w`. */
    fun cleanText(text: String): String {
        val cleaned =
            text
                .replace(WHITESPACE, " ")
                .replace(DISALLOWED, "")
                .trim()
        log.info("Text cleaned")
        return cleaned
    }

    fun sentenceTokenize(text: String): List<String> {
        val doc = CoreDocument(text)
        Nlp.sentenceSplitter.annotate(doc)
        val sentences = doc.sentences().map { it.text().trim() }
        log.info("Text split into {} sentences", sentences.size)
        return sentences
    }

    fun preprocess(text: String): Preprocessed {
        val cleaned = cleanText(text)
        return Preprocessed(original = text, cleaned = cleaned, sentences = sentenceTokenize(cleaned))
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val DISALLOWED = Regex("(?U)[^\\w\\s.,;:!?-]")
    }
}
