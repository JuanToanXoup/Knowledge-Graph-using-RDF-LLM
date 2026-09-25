// Dev server only: forward API calls to the Ktor backend on port 8000 and serve index.html for client routes.
// In production Ktor serves the bundle itself, so both run on one origin.
if (config.devServer) {
    config.devServer.historyApiFallback = true;
    config.devServer.proxy = [
        {
            context: [
                "/api",
                "/health",
                "/upload",
                "/graphs",
                "/graph/",
                "/chat_history",
                "/semantic_search",
                "/question_answer",
                "/entity_relations",
                "/sparql_query",
                "/visualization",
                "/download_graph",
                "/entities",
            ],
            target: "http://localhost:8000",
        },
    ];
}
