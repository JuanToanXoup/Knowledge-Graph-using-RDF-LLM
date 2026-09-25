package io.github.juantoanxoup.kg.web.components.landing

import io.github.juantoanxoup.kg.web.components.ui.Button
import io.github.juantoanxoup.kg.web.hooks.ToastStore
import io.github.juantoanxoup.kg.web.hooks.ToastVariant
import io.github.juantoanxoup.kg.web.lib.Api
import io.github.juantoanxoup.kg.web.lib.GraphCreationResponse
import io.github.juantoanxoup.kg.web.lib.Lock
import io.github.juantoanxoup.kg.web.lib.Upload
import io.github.juantoanxoup.kg.web.lib.Zap
import io.github.juantoanxoup.kg.web.lib.cn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.useRef
import react.useState
import tanstack.react.router.useNavigate
import tanstack.router.core.RoutePath
import web.cssom.ClassName
import web.dom.ElementId
import web.file.File
import web.html.HTMLInputElement
import web.html.Hidden
import web.html.InputType
import web.html.file
import web.html.`true`

private val scope = MainScope()

/** Hero with headline and the upload drop zone (src/components/landing/HeroSection.tsx). */
val HeroSection =
    FC<Props> {
        var file by useState<File?>(null)
        var isDragging by useState(false)
        var isUploading by useState(false)
        val navigate = useNavigate()
        val fileInput = useRef<HTMLInputElement>()

        fun createGraph() {
            val selected = file ?: return
            isUploading = true
            scope.launch {
                try {
                    val data = Api.upload<GraphCreationResponse>("/upload", selected)
                    ToastStore.toast(
                        "Success!",
                        "Graph created with ${data.entitiesCount} entities and ${data.relationsCount} relations",
                    )
                    navigate { to = RoutePath("/workspace/${data.graphId}") }
                } catch (e: Throwable) {
                    isUploading = false
                    ToastStore.toast(
                        "Error",
                        "Failed to create knowledge graph. Make sure the API is running.",
                        ToastVariant.DESTRUCTIVE,
                    )
                }
            }
        }

        section {
            id = ElementId("home")
            className = ClassName("hero")
            div {
                className = ClassName("hero-grid")

                div {
                    className = ClassName("stack-6")
                    div {
                        className = ClassName("badge")
                        Zap { size = 16 }
                        span { +"Powered by Advanced AI" }
                    }
                    h1 {
                        className = ClassName("hero-title")
                        +"From Data Points to "
                        span {
                            className = ClassName("gradient-text")
                            +"Knowledge Graphs"
                        }
                    }
                    p {
                        className = ClassName("hero-subtitle")
                        +"Build, visualize, and reason through your own intelligent knowledge graph"
                    }
                    div {
                        className = ClassName("row wrap")
                        div {
                            className = ClassName("chip")
                            Zap {
                                size = 20
                                className = ClassName("text-primary")
                            }
                            span { +"Lightning Fast" }
                        }
                        div {
                            className = ClassName("chip")
                            Lock {
                                size = 20
                                className = ClassName("text-primary")
                            }
                            span { +"Secure & Private" }
                        }
                    }
                }

                div {
                    className = ClassName("stack-4")
                    val selected = file
                    when {
                        selected == null ->
                            div {
                                className = ClassName(cn("dropzone", if (isDragging) "dropzone-active" else null))
                                onDrop = { event ->
                                    event.preventDefault()
                                    isDragging = false
                                    event.dataTransfer
                                        ?.files
                                        ?.item(0)
                                        ?.let { file = it }
                                }
                                onDragOver = { event ->
                                    event.preventDefault()
                                    isDragging = true
                                }
                                onDragLeave = { isDragging = false }
                                onClick = { fileInput.current?.click() }

                                Upload {
                                    size = 64
                                    className = ClassName("icon")
                                }
                                h3 {
                                    className = ClassName("text-xl font-semibold")
                                    +"Drag & drop your PDF here"
                                }
                                p {
                                    className = ClassName("text-muted")
                                    +"or click to browse"
                                }
                                p {
                                    className = ClassName("text-sm text-muted")
                                    +"PDF, TXT, DOCX supported"
                                }
                                input {
                                    ref = fileInput
                                    id = ElementId("file-input")
                                    type = InputType.file
                                    accept = ".pdf,.txt,.docx"
                                    hidden = Hidden.`true`
                                    onChange = { event ->
                                        event.target.files
                                            ?.item(0)
                                            ?.let { file = it }
                                    }
                                }
                            }

                        isUploading ->
                            div {
                                className = ClassName("card text-center")
                                div { className = ClassName("spinner spinner-lg") }
                                h3 {
                                    className = ClassName("text-lg font-semibold")
                                    +"Creating Knowledge Graph"
                                }
                                p {
                                    className = ClassName("text-sm text-muted")
                                    +"Processing your document..."
                                }
                            }

                        else ->
                            div {
                                className = ClassName("stack-4")
                                div {
                                    className = ClassName("card")
                                    div {
                                        className = ClassName("row-between")
                                        div {
                                            p {
                                                className = ClassName("font-semibold")
                                                +selected.name
                                            }
                                            p {
                                                className = ClassName("text-sm text-muted")
                                                +"${(selected.size.toDouble() / 1024 / 1024).asFixed(2)} MB"
                                            }
                                        }
                                        Button {
                                            variant = "ghost"
                                            size = "sm"
                                            disabled = isUploading
                                            onClick = { file = null }
                                            +"Remove"
                                        }
                                    }
                                }
                                Button {
                                    className = ClassName("btn-block btn-hero glow")
                                    disabled = isUploading
                                    onClick = { createGraph() }
                                    +"Create Knowledge Graph"
                                }
                            }
                    }

                    div {
                        className = ClassName("hero-image")
                        img {
                            src = "hero-graph.jpg"
                            alt = "Knowledge Graph Visualization"
                        }
                    }
                }
            }
        }
    }

/** `toFixed` equivalent. */
fun Double.asFixed(digits: Int): String = this.asDynamic().toFixed(digits) as String
