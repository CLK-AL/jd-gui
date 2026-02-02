pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    plugins {
        // Kotlin Multiplatform
        kotlin("multiplatform") version "1.9.22"
        kotlin("jvm") version "1.9.22"
        kotlin("plugin.serialization") version "1.9.22"
        kotlin("android") version "1.9.22"

        // Compose Multiplatform
        id("org.jetbrains.compose") version "1.5.12"

        // Android
        id("com.android.application") version "8.2.0"
        id("com.android.library") version "8.2.0"

        // Build tools
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
        id("edu.sc.seis.launch4j") version "3.0.5"
        id("com.netflix.nebula.ospackage") version "11.6.0"
        id("org.graalvm.buildtools.native") version "0.9.28"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "jd-gui"

// =============================================================================
// Module Structure for Kotlin Multiplatform
// =============================================================================
//
// jd-gui/
// ├── common/              # Shared KMP code (models, interfaces)
// │   ├── commonMain       # Platform-agnostic code
// │   ├── jvmMain          # JVM-specific (ServiceLoader)
// │   ├── jsMain           # JS-specific (browser APIs)
// │   ├── wasmJsMain       # WASM-specific
// │   ├── androidMain      # Android-specific
// │   └── iosMain          # iOS-specific
// │
// ├── composeApp/          # Compose Multiplatform UI (Android, iOS, Desktop)
// │   ├── commonMain       # Shared Compose UI
// │   ├── androidMain      # Android app
// │   ├── iosMain          # iOS app
// │   └── desktopMain      # Desktop app (alternative to Swing)
// │
// ├── web/                 # PWA with Monaco/Ace editor
// │   ├── jsMain           # JavaScript PWA
// │   └── wasmJsMain       # WASM PWA
// │
// ├── api/                 # Core SPI and ANTLR grammars (JVM legacy)
// ├── services/            # Language providers (JVM legacy)
// └── app/                 # Swing Desktop application (JVM legacy)
//
// =============================================================================

include(":common")           // Shared KMP code
include(":composeApp")       // Compose Multiplatform (Android, iOS, Desktop)
include(":web")              // PWA with Monaco/Ace editor

// Legacy JVM modules (for backward compatibility)
include(":api")              // Core SPI and ANTLR grammars
include(":services")         // Language providers
include(":app")              // Swing Desktop application

// Enable Gradle features
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
