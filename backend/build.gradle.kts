plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)
}

application {
    // api.py equivalent: `./gradlew :backend:run`
    mainClass = "io.github.juantoanxoup.kg.ApiKt"
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

// Production web UI from `:frontend`; packaged under `static/` in the jar and served by Ktor at `/`.
val webUi by configurations.dependencyScope("webUi")
val webUiFiles by configurations.resolvable("webUiFiles") { extendsFrom(webUi) }

dependencies {
    webUi(project(path = ":frontend", configuration = "webDistribution"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // HTTP server
    implementation(libs.bundles.ktor.server)

    // LLM client, prompt DSL, structured output, embeddings, retrieval (openai_helper.py replacement)
    implementation(libs.bundles.koog)

    // RDF store, SPARQL, serialization
    implementation(libs.jena.arq)

    // NLP: tokenization, sentence splitting, NER, dependency parsing
    implementation(libs.corenlp)
    runtimeOnly(variantOf(libs.corenlp.models) { classifier("models") })

    // Sentence embeddings
    implementation(libs.bundles.djl)

    // Document loading
    implementation(libs.pdfbox)
    implementation(libs.poi.ooxml)

    // Graph layout and PNG rendering
    implementation(libs.bundles.visualization)

    // Logging and configuration
    implementation(libs.logback.classic)
    implementation(libs.dotenv.kotlin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.koog.agents.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    workingDir =
        layout.buildDirectory
            .dir("test-work")
            .get()
            .asFile
            .also { it.mkdirs() }
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir
}

// Only the packaged jars carry the UI, so `run` and the tests stay API-only and skip the webpack build.
tasks.withType<Jar>().matching { it.name == "jar" || it.name == "shadowJar" }.configureEach {
    from(webUiFiles) {
        into("static")
        exclude("**/*.map")
    }
}

// Full stack on port 8000: the API plus the production UI, from the packaged jar.
val runFullStack by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Runs the API and serves the production web UI from the same server on port 8000."
    classpath = files(tasks.jar) + configurations.runtimeClasspath.get()
    mainClass = application.mainClass
    workingDir = projectDir
    jvmArgs("-Djava.awt.headless=true")
}

// main.py equivalent: `./gradlew :backend:runCli [-Pfile=path/to/document.pdf]`
val runCli by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Runs the single-document pipeline and the interactive query menu."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.juantoanxoup.kg.MainKt"
    standardInput = System.`in`
    workingDir = projectDir
    jvmArgs("-Djava.awt.headless=true")
    args = (project.findProperty("file") as String?)?.let { listOf(it) } ?: emptyList()
}

// batch_processor.py equivalent: `./gradlew :backend:runBatch -Pargs="./documents/ *.pdf"`
val runBatch by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Processes a directory or a list of files into one unified graph."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.juantoanxoup.kg.BatchProcessorKt"
    workingDir = projectDir
    jvmArgs("-Djava.awt.headless=true")
    args = (project.findProperty("args") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
}
