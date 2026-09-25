package io.github.juantoanxoup.kg.web.components.landing

import io.github.juantoanxoup.kg.web.components.ui.Button
import io.github.juantoanxoup.kg.web.components.ui.Input
import io.github.juantoanxoup.kg.web.lib.Mail
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.ClassName
import web.html.InputType
import web.html.email

/** Call to action with an email field; the buttons have no behaviour, as in the original. */
val ContactSection =
    FC<Props> {
        var email by useState("")
        section {
            className = ClassName("section")
            div {
                className = ClassName("contact stack-6")
                h2 {
                    className = ClassName("section-title")
                    +"Get Started Today"
                }
                p {
                    className = ClassName("text-xl text-muted")
                    +"Transform your documents into interactive knowledge graphs"
                }
                div {
                    className = ClassName("contact-form")
                    Input {
                        type = InputType.email
                        placeholder = "Enter your email"
                        value = email
                        onChange = { email = it.target.value }
                        className = ClassName("input-tall")
                    }
                    Button {
                        className = ClassName("btn-tall glow")
                        +"Get Early Access"
                    }
                }
                div {
                    className = ClassName("row")
                    style = js.objects.unsafeJso { justifyContent = web.cssom.JustifyContent.center }
                    div { className = ClassName("divider") }
                    span {
                        className = ClassName("text-muted")
                        +"or"
                    }
                    div { className = ClassName("divider") }
                }
                div {
                    Button {
                        variant = "outline"
                        size = "lg"
                        Mail { size = 16 }
                        +"Contact Us"
                    }
                    p {
                        className = ClassName("text-sm text-muted break-words")
                        +"madhav@knowledgegraph.ai"
                    }
                }
            }
        }
    }
