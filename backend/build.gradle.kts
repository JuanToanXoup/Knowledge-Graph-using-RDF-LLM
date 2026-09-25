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

dependencies {
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

// main.py equivalent: `./gradlew :backend:runCli [-Pfile=path/to/document.pdf]`
tasks.register<JavaExec>("runCli") {
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
tasks.register<JavaExec>("runBatch") {
    group = "application"
    description = "Processes a directory or a list of files into one unified graph."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.juantoanxoup.kg.BatchProcessorKt"
    workingDir = projectDir
    jvmArgs("-Djava.awt.headless=true")
    args = (project.findProperty("args") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
}
