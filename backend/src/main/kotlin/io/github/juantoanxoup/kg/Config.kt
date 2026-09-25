package io.github.juantoanxoup.kg

import io.github.cdimascio.dotenv.dotenv

/**
 * Configuration and constants (config.py).
 *
 * `OPENAI_API_KEY` is read from a `.env` file in the working directory or from the process environment.
 */
object Config {
    private val env =
        dotenv {
            ignoreIfMissing = true
            ignoreIfMalformed = true
        }

    // API configuration
    val openAiApiKey: String? = env["OPENAI_API_KEY"]?.takeIf { it.isNotBlank() }
    const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
    const val OPENAI_MODEL = "gpt-3.5-turbo"

    // Knowledge graph configuration
    const val DEFAULT_NAMESPACE = "http://example.org/kg/"

    // Model configuration
    const val CORENLP_ANNOTATORS = "tokenize,ssplit,pos,lemma,ner,depparse"
    const val SENTENCE_TRANSFORMER_MODEL = "sentence-transformers/all-MiniLM-L6-v2"

    // Visualization configuration (16 x 12 inches at 300 dpi in the original)
    const val FIGURE_WIDTH_PX = 4800
    const val FIGURE_HEIGHT_PX = 3600
    const val MAX_VISUALIZATION_NODES = 50
    const val NODE_DIAMETER_PX = 110
    const val ARROW_SIZE = 20

    // File paths
    const val DEFAULT_OUTPUT_DIR = "output"
    const val DEFAULT_RDF_FORMAT = "TURTLE"

    // Request configuration
    const val REQUEST_TIMEOUT_MS = 30_000L
}
