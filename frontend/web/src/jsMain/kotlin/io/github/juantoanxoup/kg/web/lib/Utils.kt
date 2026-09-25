package io.github.juantoanxoup.kg.web.lib

import web.blob.Blob
import web.blob.BlobPart
import web.blob.BlobPropertyBag
import web.dom.document
import web.html.HTMLAnchorElement
import web.url.URL
import kotlin.js.Date

/** Joins class names, skipping null and blank entries (the `cn` helper in src/lib/utils.ts). */
fun cn(vararg classes: String?): String = classes.filterNotNull().filter { it.isNotBlank() }.joinToString(" ")

/** Quotes a CSV field when it contains a separator, quote, or newline. */
fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace(
            "\"",
            "\"\"",
        )}\""
    } else {
        value
    }

/** Builds CSV text from a header row and data rows. */
fun toCsv(
    headers: List<String>,
    rows: List<List<String>>,
): String = (listOf(headers) + rows).joinToString("\n") { row -> row.joinToString(",") { csvField(it) } }

/** Triggers a browser download of [content] as [filename]. */
fun downloadText(
    filename: String,
    content: String,
    mimeType: String,
) {
    val blob = Blob(arrayOf(content.unsafeCast<BlobPart>()), BlobPropertyBag(type = mimeType))
    val url = URL.createObjectURL(blob)
    val anchor = document.createElement("a").unsafeCast<HTMLAnchorElement>()
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
}

/** Current time in milliseconds since the epoch. */
fun nowMillis(): Double = Date.now()

/** Local date string for a server ISO timestamp (`toLocaleDateString()` in the original). */
fun localDate(iso: String): String = Date(iso).toLocaleDateString()
