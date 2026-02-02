plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "org.jd.gui"
version = rootProject.version

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
                outputFileName = "jd-gui-web.js"
            }
            webpackTask {
                mainOutputFileName.set("jd-gui-web.js")
            }
            binaries.executable()
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "jd-gui-wasm.js"
            }
            binaries.executable()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.7.3")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:1.0.0-pre.678")
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-wasm-js:1.7.3")
            }
        }
    }
}

// Copy web resources to build output
tasks.register<Copy>("copyWebResources") {
    from("src/jsMain/resources")
    into("${buildDir}/distributions")
}

tasks.named("jsBrowserDistribution") {
    dependsOn("copyWebResources")
}
