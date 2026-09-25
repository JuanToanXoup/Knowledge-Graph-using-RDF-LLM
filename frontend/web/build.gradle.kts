import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "main.js"
                // Same port as the Vite dev server in the original (vite.config.ts).
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(port = 8080, open = false)
            }
            testTask {
                useKarma {
                    // CI containers run as root, where Chrome refuses to start with its sandbox.
                    useChromeHeadlessNoSandbox()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            // The 3D graph view; bundled into this app's webpack output.
            implementation(project(":frontend:cloud"))
            implementation(project.dependencies.platform(libs.kotlin.wrappers.bom))
            implementation(libs.bundles.react)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // Icon set used by the original UI.
            implementation(npm("lucide-react", "0.462.0"))
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Production bundle (HTML, CSS, JS, images) exposed to `:application`, which serves it through the backend.
val webDistribution = configurations.consumable("webDistribution")
artifacts {
    add(webDistribution.name, layout.buildDirectory.dir("dist/js/productionExecutable")) {
        builtBy(tasks.named("jsBrowserDistribution"))
    }
}

// Kotlin 2.4 registers the Node.js download repository per project as well; the settings file already declares it.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
}
