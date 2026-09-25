package io.github.juantoanxoup.kg

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.toRetryingClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * OpenAI access through Koog (openai_helper.py).
 *
 * The original wrapped the Chat Completions endpoint by hand. Here Koog provides the client,
 * transport-level retries, the prompt DSL, and structured output; this object only wires them up.
 */
object OpenAiHelper {
    /** The default chat model named in config.py. Not in Koog's catalogue, so declared explicitly. */
    val defaultModel: LLModel =
        LLModel(
            provider = LLMProvider.OpenAI,
            id = Config.OPENAI_MODEL,
            capabilities = listOf(LLMCapability.Temperature, LLMCapability.Schema.JSON.Basic, LLMCapability.Completion),
        )

    /** The model the original used for relation extraction. */
    val relationModel: LLModel = OpenAIModels.Chat.GPT4oMini

    /**
     * A prompt executor bound to [apiKey], or null when no key is configured
     * (every LLM step is then skipped, as in the original).
     */
    fun promptExecutor(apiKey: String? = Config.openAiApiKey): PromptExecutor? {
        val key = apiKey ?: return null
        val settings =
            OpenAIClientSettings(
                baseUrl = Config.OPENAI_API_URL.substringBefore("/v1/"),
                chatCompletionsPath = Config.OPENAI_API_URL.substringAfter("://").substringAfter('/'),
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
