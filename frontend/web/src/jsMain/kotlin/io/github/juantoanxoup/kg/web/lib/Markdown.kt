package io.github.juantoanxoup.kg.web.lib

import react.FC
import react.PropsWithChildren

// react-markdown and remark-gfm are ESM packages with default exports; Kotlin/JS imports the module object and
// reads `default` from it.

@JsModule("react-markdown")
@JsNonModule
private external val reactMarkdownModule: dynamic

@JsModule("remark-gfm")
@JsNonModule
private external val remarkGfmModule: dynamic

external interface MarkdownProps : PropsWithChildren {
    var remarkPlugins: Array<dynamic>?
}

/** Renders a Markdown string (its text child) as React elements, GitHub-flavoured: lists, tables, strikethrough. */
val Markdown: FC<MarkdownProps> = reactMarkdownModule.default.unsafeCast<FC<MarkdownProps>>()

val remarkGfm: dynamic = remarkGfmModule.default
