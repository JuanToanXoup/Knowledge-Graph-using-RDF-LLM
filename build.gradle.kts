// Root build: declares plugin versions once so modules apply them without a version.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "io.github.juantoanxoup.kg"
    version = "1.0.0"
}

// The Kotlin/JS plugin registers its own Node.js repository on the root project, which
// `FAIL_ON_PROJECT_REPOS` forbids. The distribution repository is declared in settings instead.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
}
