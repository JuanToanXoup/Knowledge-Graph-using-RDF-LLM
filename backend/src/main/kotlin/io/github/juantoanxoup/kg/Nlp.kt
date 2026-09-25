package io.github.juantoanxoup.kg

import edu.stanford.nlp.pipeline.StanfordCoreNLP
import java.util.Properties

/**
 * Shared, lazily created CoreNLP pipelines (the spaCy `en_core_web_sm` model in the original).
 *
 * The original loaded the model once per component instance; sharing it here is an implementation
 * detail that changes no behaviour.
 */
object Nlp {
    /** Tokenizer and sentence splitter only. Needs no model files. */
    val sentenceSplitter: StanfordCoreNLP by lazy {
        StanfordCoreNLP(
            Properties().apply {
                setProperty("annotators", "tokenize,ssplit")
            },
        )
    }

    /** Full pipeline: POS, lemma, NER (coarse labels), dependency parse. Needs the models jar. */
    val pipeline: StanfordCoreNLP by lazy {
        StanfordCoreNLP(
            Properties().apply {
                setProperty("annotators", Config.CORENLP_ANNOTATORS)
                setProperty("ner.applyFineGrained", "false")
                setProperty("ner.useSUTime", "false")
            },
        )
    }
}
