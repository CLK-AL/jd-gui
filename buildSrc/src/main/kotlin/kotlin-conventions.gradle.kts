/**
 * Kotlin conventions plugin for JD-GUI project
 * Provides consistent Kotlin configuration across modules
 */

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xjvm-default=all"
        )
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}
