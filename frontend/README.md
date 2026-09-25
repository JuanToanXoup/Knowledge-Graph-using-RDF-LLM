# frontend — Kotlin/JS React port of the Vite UI

Implements every UI requirement in `docs/FUNCTIONAL_REQUIREMENTS.md` (FR-UI). React itself is unchanged; the components are written in Kotlin and compiled to JavaScript.

## Stack

| Concern | Library | Origin |
|---|---|---|
| React 19.3 bindings | `kotlin-react`, `kotlin-react-dom` | JetBrains kotlin-wrappers (Maven Central, BOM 2026.9.2) |
| Routing | `kotlin-tanstack-react-router` | JetBrains kotlin-wrappers |
| Query client | `kotlin-tanstack-react-query` | JetBrains kotlin-wrappers |
| DOM, fetch, canvas | `kotlin-browser` | JetBrains kotlin-wrappers |
| Icons | `lucide-react` 0.462.0 | npm, declared through Gradle |
| Styling | `resources/index.css` | the original design tokens, no Tailwind build step |

## File map

The original Vite/TypeScript UI was removed once this port was complete; it is preserved in git history at commit `683ef48` and earlier.

| Original | Kotlin |
|---|---|
| `src/main.tsx` | `Main.kt` |
| `src/App.tsx` | `App.kt` |
| `src/pages/*` | `pages/*` |
| `src/components/{Header,Footer,ParticleBackground}.tsx` | `components/*` |
| `src/components/landing/*` | `components/landing/*` |
| `src/components/workspace/*` | `components/workspace/*` |
| `src/components/ui/*` (the eight primitives actually used) | `components/ui/*` |
| `src/hooks/*` | `hooks/*` |
| `src/lib/utils.ts` | `lib/Utils.kt`; plus `lib/Api.kt` (typed client) and `lib/Models.kt` (response models) |
| `index.html`, `src/index.css` | `resources/index.html`, `resources/index.css` |

## Run

| Task | Equivalent | Command |
|---|---|---|
| Dev server on port 8080 | `vite` | `./gradlew :frontend:jsBrowserDevelopmentRun --continuous` |
| Production bundle | `vite build` | `./gradlew :frontend:jsBrowserProductionWebpack` → `build/kotlin-webpack/js/productionExecutable/` |
| Tests | — | `./gradlew :frontend:jsTest` |
| Lint | `eslint` | `./gradlew :frontend:ktlintCheck` |

API calls are same-origin (`lib/Api.kt`). In production `:application` packages this bundle with `:backend`, which serves it itself; in development `webpack.config.d/devServer.js` proxies the API paths to `http://localhost:8000` and falls back to `index.html` for client routes.

## Decisions taken against the spec's §9 list

| Item | Decision |
|---|---|
| D-02 search score field | Reads `similarity`, the field the API sends |
| D-03 entity relation columns | Subject is the queried entity; predicate and object come from the response |
| D-04 overview statistics | Triples from `total_triples`; entity types counted from `/entities/{id}` |
| D-05 delete button | Wired to `DELETE /graph/{id}` |
| D-05 thumbs, early access, contact, footer links, Settings | Left without behaviour, as in the original |
| D-06 hard-coded base URL | Removed: same-origin requests, dev server proxy |
| Hero image | Moved from `src/assets/hero-graph.jpg` to `resources/hero-graph.jpg` |
