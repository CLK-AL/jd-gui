plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "org.jd.gui"
version = rootProject.version

kotlin {
    // JVM target for desktop application
    jvm {
        jvmToolchain(21)
        withJava()
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    // JavaScript target for PWA
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
                outputFileName = "jd-gui.js"
            }
            binaries.executable()
        }
    }

    // WebAssembly target for high-performance web
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter:5.10.1")
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.7.3")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:1.0.0-pre.678")
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-wasm-js:1.7.3")
            }
        }

        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test-wasm-js"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf("-Xjsr305=strict")
    }
}
