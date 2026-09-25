# backend — Kotlin/JVM port of the Python pipeline and API

Implements every backend requirement in `docs/FUNCTIONAL_REQUIREMENTS.md` (FR-CFG, FR-ING, FR-EXT, FR-REL, FR-GRA, FR-VIS, FR-IDX, FR-QA, FR-ENT, FR-SPQ, FR-LLM, FR-API, FR-CLI, FR-BAT).

## Module map

The original Python code was removed once this port was complete; it is preserved in git history at commit `683ef48` and earlier.

| Original file | Kotlin file | Library replacing the Python dependency |
|---|---|---|
| `config.py` | `Config.kt` | dotenv-kotlin |
| `document_processor.py` | `DocumentProcessor.kt` | Apache PDFBox, Apache POI |
| `text_preprocessor.py` | `TextPreprocessor.kt` | Stanford CoreNLP (tokenize, ssplit) |
| `entity_extractor.py` | `EntityExtractor.kt` | CoreNLP NER; Koog structured output |
| `relation_extractor.py` | `RelationExtractor.kt` | CoreNLP dependency parse; Koog structured output, retrying client, fixing parser |
| `openai_helper.py` | `OpenAiHelper.kt` | Koog `OpenAILLMClient`, `MultiLLMPromptExecutor` |
| `graph_builder.py` | `GraphBuilder.kt` | Apache Jena |
| `graph_querier.py` | `GraphQuerier.kt` | Jena ARQ, `ParameterizedSparqlString` |
| `graph_visualizer.py` | `GraphVisualizer.kt` | JGraphT, JGraphX |
| `semantic_retriever.py` | `SemanticRetriever.kt` | DJL (all-MiniLM-L6-v2) behind Koog `Embedder`; Koog prompt DSL |
| `api.py` | `Api.kt` | Ktor server, kotlinx.serialization |
| `main.py` | `Main.kt` | — |
| `batch_processor.py` | `BatchProcessor.kt` | — |
| — | `Nlp.kt` | shared CoreNLP pipelines (loaded once instead of once per component) |
| — | `Pipeline.kt` | extraction steps shared by API, CLI and batch (the original repeated them) |
| `app.py` (Streamlit) | — | replaced by the `frontend` module |
| `start.sh` | Gradle tasks below | — |

## Run

| Task | Equivalent | Command |
|---|---|---|
| API on port 8000 | `uvicorn api:app` | `./gradlew :backend:run` |
| API + web UI on port 8000 | `start.sh` | `./gradlew :application:runFullStack` (see below) |
| Single-document pipeline + query menu | `python main.py [file]` | `./gradlew :backend:runCli [-Pfile=doc.pdf]` |
| Batch | `python batch_processor.py <dir\|files>` | `./gradlew :backend:runBatch -Pargs="./documents/ *.pdf"` |
| Tests | — | `./gradlew :backend:test` |
| Lint | — | `./gradlew :backend:ktlintCheck` |

This module is API-only. The `:application` module puts it on a classpath together with the production bundle of `:frontend` under `static/`; when that is present, Ktor serves it at `/` and answers every other unmatched GET with `index.html` so client routes such as `/workspace/{id}` load. API routes keep precedence, and the service description moves to `/api`.

Configuration: `PORT` (default 8000). `OPENAI_API_KEY` from `backend/.env` or the environment. Without it every LLM step is skipped and `/question_answer` answers 400, as in the original.

First run downloads the CoreNLP models jar (about 500 MB) through Gradle and the MiniLM weights plus PyTorch natives through DJL.

## Decisions taken against the spec's §9 list

| Item | Decision |
|---|---|
| D-01 undefined `upload_progress` | Progress is logged at the same percentages; no endpoint, none existed |
| D-11 unused entity check in LLM relation validation | Kept as the original behaves: only non-empty fields are required |
| SPARQL injection in entity lookup | Entity name bound as a literal with `ParameterizedSparqlString` |
| Unbound SPARQL variables | Rendered as an empty string (the original read an arbitrary label) |
| Non-SELECT queries | Rejected with a clear error (the original crashed) |
| Upload file name | Path segments stripped before writing `uploaded_<name>` |
| Hand-rolled OpenAI client, JSON fence parsing, retry loop | Replaced by Koog: typed structured output, `RetryConfig.PRODUCTION`, `StructureFixingParser` |
| Everything else in §8 (in-memory state, synchronous ingestion, open CORS) | Mirrored unchanged |

## Not ported

- `past_scripts/` (deprecated in the original)
- Streamlit UI (`app.py`); the React UI in `frontend` is the single client
