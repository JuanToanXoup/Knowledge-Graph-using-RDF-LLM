// 3D graph cloud view: a Kotlin/JS React library embedded by `:frontend:web`.
// three.js, react-three-fiber, drei and d3-force-3d come from npm with hand-written externals (no kotlin-wrappers exist).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

kotlin {
    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project.dependencies.platform(libs.kotlin.wrappers.bom))
            implementation(libs.kotlin.react)
            implementation(libs.kotlin.react.dom)
            implementation(libs.kotlin.browser)
            implementation(libs.kotlinx.serialization.json)
            // Newest releases whose React peer range admits the React that kotlin-wrappers brings (19.3).
            implementation(npm("three", "0.186.1"))
            implementation(npm("@react-three/fiber", "9.8.1"))
            implementation(npm("@react-three/drei", "10.7.9"))
            implementation(npm("d3-force-3d", "3.0.6"))
            implementation(npm("@react-three/postprocessing", "3.1.2"))
            implementation(npm("postprocessing", "6.39.5"))
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Kotlin 2.4 registers the Node.js download repository per project as well; the settings file already declares it.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
}
