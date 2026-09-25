package io.github.juantoanxoup.kg.web.lib

import js.promise.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 */
object Api {
    const val BASE_URL = "http://localhost:8000"

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

    /** Multipart upload of one file under the `file` field. */
    suspend inline fun <reified T> upload(
        path: String,
        file: File,
    ): T {
        val form = FormData().apply { append("file", file) }
        return json.decodeFromString(request(path, RequestMethod.POST, body = form))
    }

    /** Absolute URL for links and images. */
    fun url(path: String): String = BASE_URL + path

    suspend fun request(
        path: String,
        method: RequestMethod,
        body: BodyInit? = null,
        headers: Headers? = null,
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
