plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("kapt")
    id("io.ktor.plugin") version "2.3.7"
    application
}

group = "org.jd.gui"
version = rootProject.version

application {
    mainClass.set("org.jd.gui.server.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:2.3.7")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.7")
    implementation("io.ktor:ktor-server-host-common-jvm:2.3.7")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.7")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.7")
    implementation("io.ktor:ktor-server-auth-jvm:2.3.7")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:2.3.7")
    implementation("io.ktor:ktor-server-sessions-jvm:2.3.7")
    implementation("io.ktor:ktor-server-websockets-jvm:2.3.7")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.7")
    implementation("io.ktor:ktor-server-compression-jvm:2.3.7")
    implementation("io.ktor:ktor-server-default-headers-jvm:2.3.7")
    implementation("io.ktor:ktor-server-caching-headers-jvm:2.3.7")

    // Ktor Client (for Keycloak communication)
    implementation("io.ktor:ktor-client-core-jvm:2.3.7")
    implementation("io.ktor:ktor-client-cio-jvm:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:2.3.7")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // WebDAV Client (Sardine)
    implementation("com.github.lookfirst:sardine:5.12")

    // ez-vcard for organization/user management
    implementation("com.googlecode.ez-vcard:ez-vcard:0.12.1")

    // JWT & Security
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")

    // Yjs for document sync
    implementation("io.github.nicktgn:y-crdt-jvm:0.0.1")

    // Openfire XMPP (Smack library)
    implementation("org.igniterealtime.smack:smack-java8:4.4.8")
    implementation("org.igniterealtime.smack:smack-tcp:4.4.8")
    implementation("org.igniterealtime.smack:smack-im:4.4.8")
    implementation("org.igniterealtime.smack:smack-extensions:4.4.8")

    // Matrix SDK for social features
    implementation("net.folivo:trixnity-client:4.1.1")
    implementation("net.folivo:trixnity-clientserverapi-client:4.1.1")

    // Bedework CalDAV/CardDAV client (caldav4j)
    implementation("com.github.caldav4j:caldav4j:1.0.0")
    implementation("org.mnode.ical4j:ical4j:3.2.14")

    // Guava for MapDifference utilities
    implementation("com.google.guava:guava:33.0.0-jre")

    // MapStruct for object mapping
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    kapt("org.mapstruct:mapstruct-processor:1.5.5.Final")

    // ANTLR for parsing
    implementation("org.antlr:antlr4-runtime:4.13.1")
    implementation("org.antlr:antlr4:4.13.1")

    // PlantUML for diagram generation
    implementation("net.sourceforge.plantuml:plantuml:1.2024.0")

    // Apache Batik for SVG rendering and viewing
    implementation("org.apache.xmlgraphics:batik-transcoder:1.17")
    implementation("org.apache.xmlgraphics:batik-swing:1.17")
    implementation("org.apache.xmlgraphics:batik-codec:1.17")
    implementation("org.apache.xmlgraphics:batik-svggen:1.17")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Configuration
    implementation("io.ktor:ktor-server-config-yaml:2.3.7")

    // Common module
    implementation(project(":common"))

    // Testing
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.7")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.test {
    useJUnitPlatform()
}
