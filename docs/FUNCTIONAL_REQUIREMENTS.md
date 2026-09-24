# Knowledge Graph System — Functional Requirements Specification

| Field | Value |
|---|---|
| Derived from | Repository state at commit `dc6892e` ("multiple changes-- however working") |
| Method | Static reading of every module and component; nothing inferred from README alone |
| Status | Baseline. Describes what the original design does, not what it should do |
| Audience | Engineers porting the system to a new stack |

Rule used throughout: a requirement appears here only if the original code implements it. Behaviours that are present but defective are listed separately in §9 so the port can decide on them rather than replicate them blindly.

---

## 1. Scope

- **In scope**: document ingestion, entity and relation extraction, RDF graph construction, static visualization, semantic indexing, question answering, entity lookup, SPARQL execution, graph management, chat history, the HTTP API, the React web UI, the Streamlit web UI, the single-file CLI, and the batch CLI.
- **Out of scope**: deprecated scripts under `past_scripts/`, the Lovable-generated unused UI primitives under `src/components/ui/`, and the placeholder `src/pages/Index.tsx`.

## 2. Source of truth

| Area | Files |
|---|---|
| Configuration | `config.py`, `.env` |
| Ingestion | `document_processor.py`, `text_preprocessor.py` |
| Extraction | `entity_extractor.py`, `relation_extractor.py`, `openai_helper.py` |
| Graph | `graph_builder.py`, `graph_querier.py`, `graph_visualizer.py`, `semantic_retriever.py` |
| API | `api.py` |
| React UI | `src/App.tsx`, `src/pages/*`, `src/components/{Header,Footer,ParticleBackground}.tsx`, `src/components/landing/*`, `src/components/workspace/*`, `src/hooks/*` |
| Streamlit UI | `app.py` |
| CLI | `main.py`, `batch_processor.py` |
| Process | `start.sh` |

## 3. Definitions

| Term | Meaning in this system |
|---|---|
| Document | One uploaded or CLI-supplied file: PDF, TXT, DOCX, or DOC |
| Entity | `{text, type}` after merge; `{text, label, start, end}` from the NLP pass; `{text, type}` from the LLM pass |
| Relation | `{subject, predicate, object, confidence}` where subject and object are entity surface strings |
| Graph | One RDF graph built from one document (API, CLI) or from many documents (batch) |
| Graph ID | `YYYYMMDD_HHMMSS_` + 8 lowercase hex characters |
| Output directory | `output/kg_<graph id>/` |
| Triple text | `"<subject label> <predicate label> <object label>"` used for embeddings |
| Label | `rdfs:label` of a node when present, else the last segment of the IRI after `/` or `#` with `_` replaced by space; literals use their lexical form |

## 4. Actors

| Actor | Interface |
|---|---|
| End user | React UI (primary), Streamlit UI (secondary) |
| Operator | `start.sh`, `.env`, `config.py` |
| Analyst | `main.py` CLI, `batch_processor.py` CLI |
| External service | OpenAI Chat Completions HTTP API |

## 5. System context

- Components
  - HTTP API on port 8000 (FastAPI), holds all graph state in process memory
  - React SPA (Vite dev server), calls the API at `http://localhost:8000`
  - Streamlit app on port 8501, calls the same API
  - Single-file CLI and batch CLI, run the pipeline in-process without the API
- External dependencies
  - OpenAI Chat Completions endpoint, optional; every LLM step is skipped or returns a fallback when the key is absent
  - spaCy `en_core_web_sm` for NER, sentence splitting, dependency parsing
  - Sentence-transformer `all-MiniLM-L6-v2` for embeddings
- Persistence
  - Turtle, PNG, uploaded file, and `metadata.json` written to the output directory
  - Graph registry and chat history exist only in memory and are lost on restart

---

## 6. Functional requirements

Notation: **FR-AREA-nn**. "Source" names the implementing file.

### 6.1 Configuration (FR-CFG)

| ID | Requirement | Source |
|---|---|---|
| FR-CFG-01 | Load `OPENAI_API_KEY` from a `.env` file or the process environment. | `config.py` |
| FR-CFG-02 | Constants: OpenAI URL `https://api.openai.com/v1/chat/completions`; default model `gpt-3.5-turbo`; request timeout 30 s. | `config.py` |
| FR-CFG-03 | Constants: default namespace `http://example.org/kg/`; RDF format `turtle`; output directory `output`. | `config.py` |
| FR-CFG-04 | Constants: spaCy model `en_core_web_sm`; embedding model `all-MiniLM-L6-v2`. | `config.py` |
| FR-CFG-05 | Constants: visualization figure size 16×12, maximum 50 nodes, node size 3000, arrow size 20. | `config.py` |

### 6.2 Document ingestion (FR-ING)

| ID | Requirement | Source |
|---|---|---|
| FR-ING-01 | Accept files with extension `.pdf`, `.txt`, `.docx`, `.doc` (case-insensitive). Reject others. | `document_processor.py`, `api.py` |
| FR-ING-02 | TXT: read the whole file as UTF-8. | `document_processor.py` |
| FR-ING-03 | DOCX and DOC: read paragraph texts and join with newline. (`.doc` is routed to the DOCX reader.) | `document_processor.py` |
| FR-ING-04 | PDF: extract text page by page and concatenate without a separator. | `document_processor.py` |
| FR-ING-05 | Raise a not-found error when the path does not exist and an unsupported-format error for any other extension. | `document_processor.py` |
| FR-ING-06 | Provide a built-in sample document (Einstein, Curie, Newton, relativity paragraphs) for runs with no input file. | `document_processor.py` |
| FR-ING-07 | Preprocess: collapse all whitespace runs to one space; delete every character not in the set word-character, whitespace, `. , ; : ! ? -`; trim. | `text_preprocessor.py` |
| FR-ING-08 | Sentence-split the cleaned text with spaCy. | `text_preprocessor.py` |
| FR-ING-09 | Preprocess output is `{original, cleaned, sentences}`. All downstream steps consume `cleaned`. | `text_preprocessor.py` |

### 6.3 Entity extraction (FR-EXT)

| ID | Requirement | Source |
|---|---|---|
| FR-EXT-01 | NLP pass: run spaCy NER over the cleaned text; emit `{text, label, start, end}` per entity span with character offsets. | `entity_extractor.py` |
| FR-EXT-02 | LLM pass: send the entire cleaned text in one prompt asking for a JSON list of `{text, type}` with types such as PERSON, ORG, GPE, DATE, WORK_OF_ART. System message: expert NER, return only valid JSON. Temperature 0. Default model. | `entity_extractor.py` |
| FR-EXT-03 | LLM pass is skipped (returns empty list) when no API key is configured. | `entity_extractor.py` |
| FR-EXT-04 | LLM response handling: strip a surrounding Markdown code fence (with or without `json` tag); parse JSON; on any failure return an empty list. | `entity_extractor.py` |
| FR-EXT-05 | Merge: key by lower-cased `text`. Insert NLP entities as `{text, type: label}`; then LLM entities as `{text, type: type or "UNKNOWN"}`, overwriting on key collision. Output order is insertion order. | `entity_extractor.py` |

### 6.4 Relation extraction (FR-REL)

| ID | Requirement | Source |
|---|---|---|
| FR-REL-01 | Pattern pass: for every sentence and every token whose dependency is `nsubj` or `nsubjpass`, take the token text as subject and its head text as predicate; for each child of the head with dependency `dobj`, `pobj`, or `attr`, take the child text as object. | `relation_extractor.py` |
| FR-REL-02 | Pattern pass keeps a relation only when both subject and object (lower-cased) are in the lower-cased entity text set. Confidence 0.7. | `relation_extractor.py` |
| FR-REL-03 | LLM pass is skipped when no API key is configured or when the entity list is empty. | `relation_extractor.py` |
| FR-REL-04 | LLM pass, short text (≤ 6000 characters): one prompt containing the comma-joined entity names and the text, instructing to find relationships between any two listed entities, to use lower-case underscored predicate names, and to return only a JSON array of `{subject, predicate, object}` (or `[]`). Model `gpt-4o-mini`, temperature 0.3. | `relation_extractor.py` |
| FR-REL-05 | LLM pass retries up to 3 attempts on empty response or unparseable JSON, sleeping 2^attempt seconds between attempts (1 s, 2 s). | `relation_extractor.py` |
| FR-REL-06 | LLM pass, long text (> 6000 characters): sentence-split with spaCy, group sentences into chunks of at most 5000 characters, run the short-text pass per chunk with a single attempt and the full entity list, concatenate all results. | `relation_extractor.py` |
| FR-REL-07 | Response parsing: strip a ```` ```json ```` or ```` ``` ```` fence; parse JSON; reject anything that is not a list. | `relation_extractor.py` |
| FR-REL-08 | Validation: keep only items with `subject`, `predicate`, `object` keys whose stripped string values are non-empty; lower-case the predicate and replace spaces with `_`; assign confidence 0.9. | `relation_extractor.py` |
| FR-REL-09 | Merge: key by lower-cased `(subject, predicate, object)`. Insert pattern relations first, then LLM relations overwriting on collision. | `relation_extractor.py` |

### 6.5 Graph construction and RDF model (FR-GRA)

| ID | Requirement | Source |
|---|---|---|
| FR-GRA-01 | One in-memory RDF graph per build. Bind prefix `kg` to the configured namespace and `foaf` to FOAF. | `graph_builder.py` |
| FR-GRA-02 | Local name rule: remove every character not in word-character, whitespace, `-`; then replace each run of whitespace or `-` with one `_`. IRI = namespace + local name. | `graph_builder.py` |
| FR-GRA-03 | For every entity add `(entity IRI, rdf:type, class)` and `(entity IRI, rdfs:label, plain literal of text)`. | `graph_builder.py` |
| FR-GRA-04 | Class mapping: PERSON→`foaf:Person`; ORG→`foaf:Organization`; GPE→`kg:Place`; DATE→`kg:Date`; WORK_OF_ART→`kg:CreativeWork`; EVENT→`kg:Event`; any other type→`kg:Entity`. | `graph_builder.py` |
| FR-GRA-05 | For every relation add `(IRI(subject), IRI(predicate), IRI(object))` using the same local-name rule for all three. Objects are always IRIs, never literals. Confidence is not stored. | `graph_builder.py` |
| FR-GRA-06 | Serialize to Turtle at a given path; parse Turtle from a given path. | `graph_builder.py` |
| FR-GRA-07 | Statistics: `total_triples`, `unique_subjects`, `unique_predicates`, `unique_objects`. | `graph_builder.py` |

### 6.6 Visualization (FR-VIS)

| ID | Requirement | Source |
|---|---|---|
| FR-VIS-01 | Build a directed graph with one node per distinct label and one edge per triple, edge label = predicate label. All triples participate, including `rdf:type` and `rdfs:label` (so class names and label literals become nodes). Parallel edges collapse; the last predicate label wins. | `graph_visualizer.py` |
| FR-VIS-02 | If the node count exceeds 50, keep the 50 highest-degree nodes and their induced edges. | `graph_visualizer.py` |
| FR-VIS-03 | Render with a spring layout (k = 2, 50 iterations), light-blue nodes, gray edges with arrows, bold 8-pt node labels, 6-pt edge labels, no axes, 300 dpi, tight bounding box, to PNG. | `graph_visualizer.py` |

### 6.7 Semantic index and search (FR-IDX)

| ID | Requirement | Source |
|---|---|---|
| FR-IDX-01 | Index every triple in the graph as its triple text (labels for subject, predicate, object) together with `{subject, predicate, object, text}`. | `semantic_retriever.py` |
| FR-IDX-02 | Embed all triple texts with `all-MiniLM-L6-v2`. | `semantic_retriever.py` |
| FR-IDX-03 | Search: embed the query, score every triple by dot product, return the top-k in descending score, each result carrying `subject, predicate, object, text, similarity`. | `semantic_retriever.py` |
| FR-IDX-04 | Build the index lazily on first search if it has not been built. | `semantic_retriever.py` |

### 6.8 Question answering (FR-QA)

| ID | Requirement | Source |
|---|---|---|
| FR-QA-01 | Retrieve the top-10 triples for the question and format them as `- subject predicate object` lines. | `semantic_retriever.py` |
| FR-QA-02 | Prompt: "Based on the following knowledge graph facts, answer the question." with the facts, the question, and an `Answer:` cue; system message: helpful assistant answering from knowledge graph facts. Temperature 0.7, default model. | `semantic_retriever.py` |
| FR-QA-03 | Return the model text, or the string `Unable to generate answer` when the call fails. | `semantic_retriever.py` |
| FR-QA-04 | API layer additionally returns the top-5 search results as `relevant_facts`. | `api.py` |

### 6.9 Entity lookup (FR-ENT)

| ID | Requirement | Source |
|---|---|---|
| FR-ENT-01 | Given an entity name, run: select `?predicate ?object ?objLabel` where a subject has `rdfs:label` equal to the exact name, followed by every `(subject, ?predicate, ?object)`, with optional `?object rdfs:label ?objLabel`. | `graph_querier.py` |
| FR-ENT-02 | Results pass through the generic SPARQL row mapping (FR-SPQ-02). | `graph_querier.py` |

### 6.10 SPARQL execution (FR-SPQ)

| ID | Requirement | Source |
|---|---|---|
| FR-SPQ-01 | Execute an arbitrary SPARQL string against the graph. | `graph_querier.py` |
| FR-SPQ-02 | Row mapping: for each projected variable, literals become their string; IRIs become their `rdfs:label` if one exists, else the IRI string. Output is a list of `{variable: string}` dictionaries. | `graph_querier.py` |
| FR-SPQ-03 | Query errors surface as an error message "Query execution failed: …". | `api.py`, `main.py` |

### 6.11 OpenAI client (FR-LLM)

| ID | Requirement | Source |
|---|---|---|
| FR-LLM-01 | POST to the configured Chat Completions URL with bearer auth, JSON `{model, messages, temperature}`, 30 s timeout. | `openai_helper.py` |
| FR-LLM-02 | Return `choices[0].message.content`; on any exception or missing key return null and log the error. | `openai_helper.py` |

### 6.12 HTTP API (FR-API)

Base URL `http://localhost:8000`. JSON bodies unless stated. CORS: all origins, methods, headers, with credentials. All graph-scoped endpoints return **404 `Graph not found`** for an unknown graph ID.

| ID | Method and path | Request | Response | Source |
|---|---|---|---|---|
| FR-API-01 | `GET /` | — | `{message, version: "1.0.0", endpoints: {name: path}}` | `api.py` |
| FR-API-02 | `GET /health` | — | `{status: "healthy", active_graphs: n, openai_configured: bool}` | `api.py` |
| FR-API-03 | `POST /upload` | multipart `file` | On success `{graph_id, message, entities_count, relations_count, statistics, output_dir}`. 400 for a disallowed extension. 500 with the exception text on any pipeline error, after deleting the output directory. | `api.py` |
| FR-API-04 | `GET /graphs` | — | `{graphs: [{graph_id, filename, created_at, entities_count, relations_count, statistics}], total}` | `api.py` |
| FR-API-05 | `GET /graph/{id}` | — | `{graph_id, filename, created_at, entities_count, relations_count, statistics, sample_entities: first 10 entity texts}` | `api.py` |
| FR-API-06 | `DELETE /graph/{id}` | — | `{message}`; removes from the in-memory registry only, files remain | `api.py` |
| FR-API-07 | `GET /entities/{id}` | — | `{entities: [{text, type}], count}` | `api.py` |
| FR-API-08 | `POST /semantic_search` | `{graph_id, query, top_k = 5}` | `{results: [{subject, predicate, object, text, similarity}], query}` | `api.py` |
| FR-API-09 | `POST /question_answer` | `{graph_id, question}` | `{question, answer, relevant_facts: top-5 results}`; 400 when no API key | `api.py` |
| FR-API-10 | `POST /entity_relations` | `{graph_id, entity_name}` | `{entity, relations: [{predicate, object, objLabel}], count}` | `api.py` |
| FR-API-11 | `POST /sparql_query` | `{graph_id, query}` | `{results: [row], count}`; 500 `Query execution failed: …` on error | `api.py` |
| FR-API-12 | `GET /visualization/{id}` | — | PNG bytes; 404 `Visualization not found` when the file is missing | `api.py` |
| FR-API-13 | `GET /download_graph/{id}` | — | Turtle file, media type `text/turtle`, filename `knowledge_graph_<id>.ttl`; 404 when missing | `api.py` |
| FR-API-14 | `GET /chat_history/{id}` | — | `{graph_id, messages: [ChatMessage], count}` | `api.py` |
| FR-API-15 | `POST /chat_history/{id}` | `ChatMessage = {role, content, timestamp, facts?: [string]}` | `{message, total_messages}` | `api.py` |
| FR-API-16 | `DELETE /chat_history/{id}` | — | `{message}` | `api.py` |

Upload pipeline (FR-API-03) in order: create graph ID and output directory → save upload as `uploaded_<original filename>` → load → preprocess → NLP entities → LLM entities → merge → pattern relations → LLM relations → merge → build graph → save `knowledge_graph.ttl` → render `knowledge_graph.png` → build semantic index → create querier → compute statistics → register in memory `{kg_builder, retriever, querier, entities, relations, output_dir, filename, created_at, statistics}` → write `metadata.json` `{graph_id, filename, created_at, entities_count, relations_count, statistics}` → respond.

### 6.13 React web UI (FR-UI)

#### Routing and shell

| ID | Requirement | Source |
|---|---|---|
| FR-UI-01 | Routes: `/` Landing; `/workspace` Workspace without a graph; `/workspace/:graphId` Workspace for a graph; anything else NotFound. | `App.tsx` |
| FR-UI-02 | NotFound: heading `404`, text `Oops! Page not found`, link `Return to Home` to `/`; log the attempted path. | `NotFound.tsx` |
| FR-UI-03 | Header: fixed, glass-styled bar with brand `KnowledgeGraph.AI` linking to `/`. With navigation: anchor links `Home`, `Features`, `How it Works` (Home highlighted on `/`) and a `Workspace` button to `/workspace`, hidden below the medium breakpoint. Without navigation: `Home` and `My Graphs` buttons. | `Header.tsx` |
| FR-UI-04 | Header API status dot when a connection state is supplied: green pulsing `API Connected` or red `API Disconnected` (tooltip text). | `Header.tsx` |
| FR-UI-05 | Animated particle canvas background on every page. | `ParticleBackground.tsx` |
| FR-UI-06 | Footer: `© 2025 KnowledgeGraph.AI. All rights reserved.` and anchor links `Privacy`, `Terms`, `Documentation`. | `Footer.tsx` |
| FR-UI-07 | Mobile detection: viewport narrower than 768 px, updated live on resize. | `use-mobile.tsx` |
| FR-UI-08 | Toast notifications with title, description, and a destructive variant. | `use-toast.ts`, `toaster.tsx` |

#### Landing page

| ID | Requirement | Source |
|---|---|---|
| FR-UI-10 | Hero left column: badge `Powered by Advanced AI`; heading `From Data Points to Knowledge Graphs`; subtitle `Build, visualize, and reason through your own intelligent knowledge graph`; chips `Lightning Fast` and `Secure & Private`. | `HeroSection.tsx` |
| FR-UI-11 | Hero upload, no file: dashed drop zone `Drag & drop your PDF here` / `or click to browse` / `PDF, TXT, DOCX supported`; accepts drop or a hidden file input restricted to `.pdf,.txt,.docx`; highlight while dragging. | `HeroSection.tsx` |
| FR-UI-12 | Hero upload, file chosen: show name and size in MB (2 decimals), `Remove` button, and a full-width `Create Knowledge Graph` button. | `HeroSection.tsx` |
| FR-UI-13 | Hero upload, in progress: spinner with `Creating Knowledge Graph` / `Processing your document...`. | `HeroSection.tsx` |
| FR-UI-14 | Create action: POST the file to `/upload`; on success toast `Success!` with entity and relation counts and navigate to `/workspace/<graph_id>`; on failure toast `Error` / `Failed to create knowledge graph. Make sure the API is running.` and return to the file-chosen state. | `HeroSection.tsx` |
| FR-UI-15 | Hero decorative image below the upload card. | `HeroSection.tsx` |
| FR-UI-16 | Features section `Powerful Features` with three cards: `Smart Entity Extraction`, `Relationship Mapping`, `Intelligent Q&A` and their descriptions. | `FeaturesSection.tsx` |
| FR-UI-17 | How-it-works section with three numbered steps: `01 Upload — Drop your document`, `02 Process — AI extracts entities & relations`, `03 Explore — Query and visualize insights`, joined by a decorative line on wide screens. | `HowItWorksSection.tsx` |
| FR-UI-18 | Contact section `Get Started Today`: email input with `Get Early Access` button, `or` divider, `Contact Us` button, and the address `madhav@knowledgegraph.ai`. Buttons perform no action. | `ContactSection.tsx` |

#### Workspace

| ID | Requirement | Source |
|---|---|---|
| FR-UI-20 | On load, GET `/graphs` to set the header connection state; when a graph ID is present, GET `/graph/{id}` for its info. | `Workspace.tsx` |
| FR-UI-21 | Initial tab is Overview when a graph ID is present, otherwise My Graphs. | `Workspace.tsx` |
| FR-UI-22 | Sidebar: fixed, collapsible (256 px open, icon rail at 80 px on ≥ small screens, hidden on mobile), showing `Current Graph` and the filename or graph ID or `My Workspace`; chevron toggle; mobile overlay closes it; selecting a tab on mobile closes it; auto-collapses when the viewport becomes mobile. | `WorkspaceSidebar.tsx`, `Workspace.tsx` |
| FR-UI-23 | Sidebar items in order: Overview, Semantic Search, Chat & Q&A, Entity Explorer, SPARQL Query, My Graphs, Settings. Active item highlighted. | `WorkspaceSidebar.tsx` |
| FR-UI-24 | Graph-scoped tabs without a graph ID render `Please select a graph from the My Graphs tab`. Settings renders `Coming soon...`. | `Workspace.tsx` |

#### Overview tab

| ID | Requirement | Source |
|---|---|---|
| FR-UI-30 | GET `/graph/{id}`; show four tiles: `Total Entities` (entities_count), `Total Relations` (relations_count), `Total Triples` (statistics.triples, falling back to entities + relations), `Entity Types` (statistics.entity_types_count, falling back to 0). | `OverviewTab.tsx` |
| FR-UI-31 | Statistics card with a `Show JSON` / `Hide JSON` toggle revealing the raw statistics object. | `OverviewTab.tsx` |
| FR-UI-32 | Visualization card loading `/visualization/{id}` as an image with spinner, an error state `Visualization not available`, and a `View Full Size` button opening the image in a new tab. | `OverviewTab.tsx` |

#### Semantic Search tab

| ID | Requirement | Source |
|---|---|---|
| FR-UI-40 | Query input (Enter submits), `Search` button disabled when empty or loading, results slider 1–20 default 5 with live label `Results: n`. | `SearchTab.tsx` |
| FR-UI-41 | POST `/semantic_search`; render `Results (n)` as cards showing `text` and a percentage badge from `score`. | `SearchTab.tsx` |

#### Chat & Q&A tab

| ID | Requirement | Source |
|---|---|---|
| FR-UI-50 | On open, GET `/chat_history/{id}` and render prior messages, scrolling to the bottom. | `ChatTab.tsx` |
| FR-UI-51 | Left panel (wide screens): `Clear History` button (disabled when empty) that DELETEs history and toasts `Chat cleared`; message count summary or `No messages yet`. | `ChatTab.tsx` |
| FR-UI-52 | Empty thread placeholder: `Start a conversation by asking a question` / `Try: "What are the main entities in this document?"`. | `ChatTab.tsx` |
| FR-UI-53 | Send: Enter sends, Shift+Enter inserts a newline; send button disabled when empty or loading; append the user message, POST it to `/chat_history/{id}`, POST `/question_answer`, append the assistant message with `facts` = `relevant_facts[].text`, POST it to history; on failure toast `Error` / `Failed to get answer. Please try again.`. | `ChatTab.tsx` |
| FR-UI-54 | Assistant messages show a collapsible `Relevant Facts (n)` list and Copy (toast `Copied to clipboard`), thumbs-up, thumbs-down buttons. Thumbs buttons perform no action. | `ChatTab.tsx` |
| FR-UI-55 | Typing indicator (three pulsing dots) while waiting; auto-scroll on new messages; hint `Press Enter to send, Shift+Enter for new line`. | `ChatTab.tsx` |

#### Entity Explorer tab

| ID | Requirement | Source |
|---|---|---|
| FR-UI-60 | Free-text entity input (Enter submits) and `Explore` button. | `EntityTab.tsx` |
| FR-UI-61 | POST `/entity_relations`; show a summary card with the entity name and `n relations found`, an `Export CSV` button (disabled when empty) producing `<entity>-relations.csv` with header `Subject,Predicate,Object`, and a Subject / Predicate / Object table. | `EntityTab.tsx` |

#### SPARQL Query tab

| ID | Requirement | Source |
|---|---|---|
| FR-UI-70 | Monospace textarea editor; `Execute Query` (disabled when empty or loading) and `Clear` buttons; `Executed in n ms` after a run. | `SparqlTab.tsx` |
| FR-UI-71 | Example queries panel (wide screens) with `Get All Entities`, `Entity Count`, `Find Relationships`; clicking loads the query into the editor. | `SparqlTab.tsx` |
| FR-UI-72 | POST `/sparql_query`; on error show a `Query Error` card with the server detail; on success show `Results (n)` in a scrollable table whose columns are the keys of the first row, with `CSV` and `JSON` download buttons (`query-results.csv`, `query-results.json`). | `SparqlTab.tsx` |

#### My Graphs tab

| ID | Requirement | Source |
|---|---|---|
| FR-UI-80 | GET `/graphs`; heading `My Graphs` / `Manage your knowledge graphs`; `Upload New Document` button navigating to `/`. | `MyGraphsTab.tsx` |
| FR-UI-81 | Text filter matching graph ID or filename, case-insensitive. | `MyGraphsTab.tsx` |
| FR-UI-82 | Card per graph: filename, truncated ID (16 chars), `n entities • n relations`, `Created: <local date>`, `Open` (navigate to `/workspace/<id>`), download (open `/download_graph/<id>` in a new tab), delete icon. Delete performs no action. | `MyGraphsTab.tsx` |
| FR-UI-83 | Empty state `No graphs found`. | `MyGraphsTab.tsx` |

### 6.14 Streamlit web UI (FR-SUI)

| ID | Requirement | Source |
|---|---|---|
| FR-SUI-01 | Title `🧠 Knowledge Graph System`; sidebar `System Status` shows `API Connected` or `API Disconnected` with the base URL and stops rendering when disconnected. | `app.py` |
| FR-SUI-02 | Sidebar radio navigation: `Upload Document`, `Browse Graphs`, `Query Graph`. | `app.py` |
| FR-SUI-03 | Upload page: file uploader for pdf/txt/docx/doc; file name and size in KB; `🚀 Create Knowledge Graph` button; on success metrics Entities, Relations, Triples (`statistics.triples`), raw statistics JSON, and the visualization image; instructions and about panels. | `app.py` |
| FR-SUI-04 | Browse page: total count; one expander per graph with created, entities, relations, statistics JSON, and buttons `View Visualization`, `Download RDF` (then `💾 Save File` as `kg_<id>.ttl`), `Select for Query`. | `app.py` |
| FR-SUI-05 | Query page: graph selector labelled `<filename> (<id[:8]>)`; metrics Entities, Relations, Triples, Entity Types (`len(statistics.entity_types)`); four tabs. | `app.py` |
| FR-SUI-06 | Semantic Search tab: text input, slider 1–20 default 5, `Search`; results as `Result i (Score: …)` with text. | `app.py` |
| FR-SUI-07 | Question Answering tab: text area, `Get Answer`; answer box and `View Relevant Facts` expander. | `app.py` |
| FR-SUI-08 | Entity Relations tab: dropdown of `sample_entities` with a free-text fallback; `Find Relations`; results as a data frame. | `app.py` |
| FR-SUI-09 | SPARQL tab: example queries expander; text area; `Execute Query`; results as a data frame with `Download Results as CSV` (`sparql_results.csv`). | `app.py` |

### 6.15 Single-file CLI (FR-CLI)

| ID | Requirement | Source |
|---|---|---|
| FR-CLI-01 | Usage `python main.py [file]`. With a file that does not exist: print an error and exit 1. Without a file: print usage and run on the sample document. | `main.py` |
| FR-CLI-02 | Run the six pipeline steps with headed console sections, print counts, sample entities (first 5), sample relations (first 3), statistics, and output paths. Output directory `output/kg_<id>`. | `main.py` |
| FR-CLI-03 | After a key press, open the query menu: 1 Semantic Search, 2 Question Answering, 3 SPARQL Query (entity relations), 4 Custom SPARQL Query, 5 View Graph Statistics, 6 Exit; invalid input re-prompts. | `main.py` |
| FR-CLI-04 | Semantic search loop: prompt for query (`back`/`exit`/`quit` returns), prompt for result count (default 5), print `subject → predicate → object` with a 3-decimal similarity, ask `Search again? (y/n)`. | `main.py` |
| FR-CLI-05 | Question answering loop: refuse when no API key; prompt, print answer, ask to continue. | `main.py` |
| FR-CLI-06 | Entity relations loop: list the first 20 entities with types; accept a number from that list or a free-text name; print `entity → predicate → object` using labels or the last IRI segment; ask to continue. | `main.py` |
| FR-CLI-07 | Custom SPARQL loop: show an example query; accept `example`, or multi-line input terminated by a line `END`; print each result's variables, or an error message; ask to continue. | `main.py` |
| FR-CLI-08 | Statistics: print each statistic with a title-cased key. | `main.py` |

### 6.16 Batch CLI (FR-BAT)

| ID | Requirement | Source |
|---|---|---|
| FR-BAT-01 | Usage `python batch_processor.py <directory> [pattern]` (pattern default `*.pdf`) or `python batch_processor.py <file> [<file>…]`; print usage when no arguments; error when the first argument is neither. | `batch_processor.py` |
| FR-BAT-02 | Process each document through load → preprocess → entity passes → merge → relation passes → merge; skip a document whose load fails; accumulate entities and relations across documents. | `batch_processor.py` |
| FR-BAT-03 | Unified graph: deduplicate entities by lower-cased text (first occurrence wins) and relations by lower-cased triple (first wins); build; print statistics; write `unified_knowledge_graph.ttl` and `unified_knowledge_graph.png` under one output directory chosen at process start. | `batch_processor.py` |
| FR-BAT-04 | Print a summary (file count, entity and relation totals, file names), build the semantic index, print output paths, and exit. No interactive menu. | `batch_processor.py` |

### 6.17 Process management (FR-OPS)

| ID | Requirement | Source |
|---|---|---|
| FR-OPS-01 | `start.sh` checks for Python, `api.py`, and `app.py`; creates `output/` and `logs/`; starts the API on 0.0.0.0:8000 with auto-reload and the Streamlit app on 8501 headless; verifies each started; tails both logs; stops both on Ctrl-C. | `start.sh` |

---

## 7. Data contracts

### 7.1 RDF model as built

```turtle
@prefix kg:   <http://example.org/kg/> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

kg:Albert_Einstein a foaf:Person ;
    rdfs:label "Albert Einstein" ;
    kg:worked_at kg:Princeton_University .

kg:Princeton_University a foaf:Organization ;
    rdfs:label "Princeton University" .
```

- Entity and predicate IRIs share one namespace and one local-name rule.
- Objects of extracted relations are always IRIs, even when the object text was never extracted as an entity (then the node has no type and no label).
- No provenance, confidence, source span, or named graph is recorded.

### 7.2 Files per graph

| File | Producer |
|---|---|
| `output/kg_<id>/uploaded_<name>` | API upload |
| `output/kg_<id>/knowledge_graph.ttl` | API, CLI |
| `output/kg_<id>/knowledge_graph.png` | API, CLI |
| `output/kg_<id>/metadata.json` | API |
| `output/kg_<id>/unified_knowledge_graph.{ttl,png}` | Batch |

### 7.3 Statistics object

`{total_triples, unique_subjects, unique_predicates, unique_objects}` — all integers.

---

## 8. Non-functional characteristics as observed

| Aspect | Observed behaviour |
|---|---|
| Persistence | Graph registry and chat history are process memory only; restart loses them; Turtle on disk is never reloaded |
| Concurrency | Pipeline runs synchronously inside the request handler; the server is blocked for the duration of an upload |
| Model loading | spaCy loaded per component instance (three times per upload); embedding model loaded per graph |
| Security | No authentication; CORS open to all origins with credentials; SPARQL for entity lookup built by string interpolation |
| Observability | `print` statements; `start.sh` redirects stdout to `logs/` |
| Configuration | Single `.env` key; everything else is a code constant |

---

## 9. Observed defects and contract mismatches (decisions for the port)

Not requirements. Each is an inconsistency in the original that the new implementation must resolve one way or the other.

| # | Observation | Where |
|---|---|---|
| D-01 | `upload_progress` is used but never defined, so every upload fails with 500 in the current revision. The progress messages (20/40/50/60/85/90/95 %) and log lines it writes are the only trace of an intended progress feature; no endpoint exposes them. | `api.py` |
| D-02 | Semantic search results carry `similarity`; the React UI reads `score` and the Streamlit UI reads `score`. Both display nothing meaningful. | `api.py`, `SearchTab.tsx`, `app.py` |
| D-03 | Entity lookup returns `{predicate, object, objLabel}`; the React table reads `subject, predicate, object`. | `api.py`, `EntityTab.tsx` |
| D-04 | Overview reads `statistics.triples` and `statistics.entity_types_count`; Streamlit reads `statistics.triples` and `statistics.entity_types`; the API produces `total_triples` and no type breakdown. | `OverviewTab.tsx`, `app.py` |
| D-05 | Delete-graph button, thumbs up/down, `Get Early Access`, `Contact Us`, footer links, and Settings tab have no behaviour. | React UI |
| D-06 | The React UI uses a hard-coded `http://localhost:8000` in seven places. | React UI |
| D-07 | Two UIs implement the same features; `start.sh` launches only Streamlit. | `app.py`, `start.sh` |
| D-08 | Visualization treats `rdfs:label` literal values and class IRIs as graph nodes, so every entity appears twice (IRI node and label node). | `graph_visualizer.py` |
| D-09 | Text cleaning deletes every non-word character, including all non-Latin scripts and quotation marks, before extraction. | `text_preprocessor.py` |
| D-10 | Entity LLM pass sends the whole document in one prompt with no chunking; relation LLM pass chunks. | `entity_extractor.py` |
| D-11 | LLM relation validation computes the entity set but never uses it; relations between unlisted strings are accepted and minted as IRIs. | `relation_extractor.py` |
| D-12 | Entity IRIs derived from surface text collide across distinct entities and drop non-ASCII characters. | `graph_builder.py` |
| D-13 | Dates are typed as a class (`kg:Date`) rather than stored as `xsd:date` literals. | `graph_builder.py` |
| D-14 | Auto-reload in `start.sh` wipes the in-memory registry on every source save. | `start.sh` |
| D-15 | Deprecated or pinned-broken dependencies: PyPDF2, sentence-transformers 2.2.2; FastAPI unpinned. | `requirements.txt` |

---

## 10. Traceability matrix

| Feature | Backend FRs | API FRs | React UI FRs | Streamlit FRs | CLI FRs |
|---|---|---|---|---|---|
| Ingest document | ING-01…09 | API-03 | UI-11…14 | SUI-03 | CLI-01, BAT-01…02 |
| Extract entities | EXT-01…05 | API-03, API-07 | — | — | CLI-02 |
| Extract relations | REL-01…09 | API-03 | — | — | CLI-02 |
| Build graph | GRA-01…07 | API-03, API-13 | UI-82 | SUI-04 | CLI-02, BAT-03 |
| Visualize | VIS-01…03 | API-12 | UI-32 | SUI-03, SUI-04 | CLI-02, BAT-03 |
| Semantic search | IDX-01…04 | API-08 | UI-40…41 | SUI-06 | CLI-04 |
| Question answering | QA-01…04 | API-09 | UI-50…55 | SUI-07 | CLI-05 |
| Entity lookup | ENT-01…02 | API-10 | UI-60…61 | SUI-08 | CLI-06 |
| SPARQL | SPQ-01…03 | API-11 | UI-70…72 | SUI-09 | CLI-07 |
| Statistics | GRA-07 | API-04, API-05 | UI-30…31 | SUI-05 | CLI-08 |
| Manage graphs | — | API-04…07 | UI-80…83 | SUI-04 | — |
| Chat history | — | API-14…16 | UI-50…51 | — | — |
| Health | — | API-02 | UI-04, UI-20 | SUI-01 | — |
