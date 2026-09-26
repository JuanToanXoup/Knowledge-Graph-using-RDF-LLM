package io.github.juantoanxoup.kg.web.lib

import js.buffer.AllowSharedBufferSource
import js.promise.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import web.encoding.TextDecodeOptions
import web.encoding.TextDecoder
import web.file.File
import web.form.FormData
import web.http.BodyInit
import web.http.DELETE
import web.http.GET
import web.http.Headers
import web.http.POST
import web.http.RequestInit
import web.http.RequestMethod
import web.http.fetch

/** Thrown when the API answers with a non-2xx status; [detail] is FastAPI-style `{"detail": ...}` text. */
class ApiError(
    val status: Int,
    val detail: String,
) : RuntimeException(detail)

/**
 * Typed HTTP client for the backend. The original components each called `fetch('http://localhost:8000/...')`
 * directly; the base URL is declared once here.
 *
 * Requests are same-origin: Ktor serves the bundled UI itself, and the webpack dev server proxies API paths
 * to port 8000 (`frontend/webpack.config.d/devServer.js`).
 */
object Api {
    const val BASE_URL = ""

    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    suspend fun get(path: String): String = request(path, RequestMethod.GET)

    suspend inline fun <reified T> getJson(path: String): T = json.decodeFromString(get(path))

    suspend inline fun <reified B, reified T> postJson(
        path: String,
        body: B,
    ): T = json.decodeFromString(postRaw(path, json.encodeToString(body)))

    suspend fun postRaw(
        path: String,
        jsonBody: String,
    ): String =
        request(
            path,
            RequestMethod.POST,
            body = BodyInit(jsonBody),
            headers = Headers().apply { append("Content-Type", "application/json") },
        )

    suspend fun delete(path: String): String = request(path, RequestMethod.DELETE)

    /**
     * POSTs [body] and reads the server-sent events of the reply as they arrive, handing each to [onEvent].
     * A non-2xx status throws [ApiError] before any event.
     */
    suspend inline fun <reified B> postSse(
        path: String,
        body: B,
        noinline onEvent: (SseEvent) -> Unit,
    ) = streamRaw(path, json.encodeToString(body), onEvent)

    suspend fun streamRaw(
        path: String,
        jsonBody: String,
        onEvent: (SseEvent) -> Unit,
    ) {
        val headers =
            Headers().apply {
                append("Content-Type", "application/json")
                append("Accept", "text/event-stream")
            }
        val response =
            fetch(url(path), RequestInit(method = RequestMethod.POST, body = BodyInit(jsonBody), headers = headers))
        if (!response.ok) {
            throw ApiError(
                response.status.toInt(),
                extractDetail(response.textAsync().await().toString()),
            )
        }
        val reader = response.body?.getReader() ?: throw ApiError(response.status.toInt(), "No response body")
        val decoder = TextDecoder()
        val parser = SseParser()
        val streaming = js("({stream: true})").unsafeCast<TextDecodeOptions>()
        while (true) {
            val result = reader.readAsync().await()
            if (result.done) break
            val chunk = result.value ?: continue
            parser.feed(decoder.decode(chunk.unsafeCast<AllowSharedBufferSource>(), streaming)).forEach(onEvent)
        }
        parser.end().forEach(onEvent)
    }

    /** Multipart upload of one file under the `file` field. */
    suspend inline fun <reified T> upload(
        path: String,
        file: File,
    ): T {
        val form = FormData().apply { append("file", file) }
        return json.decodeFromString(request(path, RequestMethod.POST, body = form))
    }

    /** URL for links and images. */
    fun url(path: String): String = BASE_URL + path

    suspend fun request(
        path: String,
        method: RequestMethod,
        body: BodyInit? = null,
        // Never null: fetch rejects `headers: null`, which is what a null Kotlin argument becomes.
        headers: Headers = Headers(),
    ): String {
        val response = fetch(url(path), RequestInit(method = method, body = body, headers = headers))
        val text = response.textAsync().await().toString()
        if (!response.ok) throw ApiError(response.status.toInt(), extractDetail(text))
        return text
    }

    private fun extractDetail(text: String): String =
        runCatching {
            json
                .parseToJsonElement(text)
                .jsonObject["detail"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()
            ?: text.ifBlank { "Request failed" }
}
