package io.github.juantoanxoup.kg

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.toRetryingClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Chat Completions access through Koog (openai_helper.py), against OpenAI or any compatible endpoint.
 *
 * The original wrapped the endpoint by hand. Here Koog provides the client, transport-level retries, the prompt
 * DSL, and structured output; this object only wires them up from [Config.openAiBaseUrl] and [Config.openAiModel].
 */
object OpenAiHelper {
    /** The configured chat model, used for every LLM step. Declared explicitly so any model id works. */
    val defaultModel: LLModel =
        LLModel(
            provider = LLMProvider.OpenAI,
            id = Config.openAiModel,
            // `OpenAIEndpoint.Completions` tells Koog's OpenAI client which API to call; without it every request fails.
            capabilities =
                listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Schema.JSON.Basic,
                    LLMCapability.Completion,
                    LLMCapability.OpenAIEndpoint.Completions,
                ),
        )

    /**
     * A prompt executor bound to [apiKey], or null when no key is configured
     * (every LLM step is then skipped, as in the original).
     */
    fun promptExecutor(apiKey: String? = Config.openAiApiKey): PromptExecutor? {
        val key = apiKey ?: return null
        // OPENAI_BASE_URL follows the OpenAI SDK convention and ends in `/v1`; Koog wants the origin and the path apart.
        val settings =
            OpenAIClientSettings(
                baseUrl = Config.openAiBaseUrl.removeSuffix("/v1"),
                chatCompletionsPath = "v1/chat/completions",
                timeoutConfig =
                    ConnectionTimeoutConfig(
                        requestTimeoutMillis = Config.REQUEST_TIMEOUT_MS,
                        connectTimeoutMillis = Config.REQUEST_TIMEOUT_MS,
                        socketTimeoutMillis = Config.REQUEST_TIMEOUT_MS,
                    ),
            )
        val client =
            OpenAILLMClient(
                apiKey = key,
                settings = settings,
                httpClientFactory = KtorKoogHttpClient.Factory(),
            ).toRetryingClient(RetryConfig.PRODUCTION)
        return MultiLLMPromptExecutor(client)
    }
}
