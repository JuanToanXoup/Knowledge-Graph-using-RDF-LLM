// Google's mirror of Maven Central is listed first: it serves the same artifacts and is not rate limited.
// The URL is repeated because `pluginManagement` is evaluated before any other statement in this file.
pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Provisions the JDK declared by each module's toolchain when it is not installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        // Node.js distribution for the Kotlin/JS toolchain; declared here because project repositories are disallowed.
        ivy("https://nodejs.org/dist") {
            name = "Node Distributions at https://nodejs.org/dist"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
    }
}

rootProject.name = "knowledge-graph-using-rdf-llm"

include("backend")
include("frontend")
// Assembles backend + frontend into one deployable: `./gradlew :application:runFullStack`.
include("application")
