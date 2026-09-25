// Full-stack assembly: the `:backend` API plus the production bundle of `:frontend`, served by one Ktor server.
// No sources of its own; it only puts the UI on the backend's classpath under `static/`.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "io.github.juantoanxoup.kg.ApiKt"
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

val webUi = configurations.dependencyScope("webUi")
val webUiFiles = configurations.resolvable("webUiFiles") { extendsFrom(webUi.get()) }

dependencies {
    implementation(project(":backend"))
    add(webUi.name, project(path = ":frontend", configuration = "webDistribution"))
}

// Ktor serves `static/` at `/` when present (see `bundledUiResources` in backend `Api.kt`).
tasks.processResources {
    from(webUiFiles) {
        into("static")
        exclude("**/*.map")
    }
}

tasks.named<JavaExec>("run") {
    // Same working directory as `:backend:run`, so `backend/.env` and `backend/output/` are shared.
    workingDir = rootProject.file("backend")
}

tasks.register("runFullStack") {
    group = "application"
    description = "Runs the API and serves the production web UI from the same server on port 8000."
    dependsOn(tasks.named("run"))
}
