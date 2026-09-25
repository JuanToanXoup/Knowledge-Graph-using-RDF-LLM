package io.github.juantoanxoup.kg

import io.github.cdimascio.dotenv.dotenv
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Configuration and constants (config.py).
 *
 * Settings are read from a `.env` file in the working directory or from the process environment. The LLM settings
 * are the standard OpenAI SDK variables, so any OpenAI-compatible endpoint (a local gateway, a proxy) can be used.
 */
object Config {
    private val env =
        dotenv {
            ignoreIfMissing = true
            ignoreIfMalformed = true
        }

    // LLM endpoint: OPENAI_API_KEY (required for LLM steps), OPENAI_BASE_URL, OPENAI_MODEL
    const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
    const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
    val openAiApiKey: String? = env["OPENAI_API_KEY"]?.takeIf { it.isNotBlank() }
    val openAiBaseUrl: String =
        env["OPENAI_BASE_URL"]?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: DEFAULT_OPENAI_BASE_URL
    val openAiModel: String = env["OPENAI_MODEL"]?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_OPENAI_MODEL

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

    // Storage: the TDB2 triple store lives under `<dataDir>/tdb2`, per-graph files under `<dataDir>/graphs/<id>`.
    const val DEFAULT_DATA_DIR = "data"
    val dataDir: Path = Path(env["KG_DATA_DIR"]?.takeIf { it.isNotBlank() } ?: DEFAULT_DATA_DIR)
    const val DEFAULT_RDF_FORMAT = "TURTLE"

    // Request configuration
    const val REQUEST_TIMEOUT_MS = 30_000L
}
