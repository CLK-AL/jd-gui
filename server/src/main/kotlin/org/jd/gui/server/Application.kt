/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.jd.gui.server.auth.VCardOrgManager
import org.jd.gui.server.auth.configureKeycloakAuth
import org.jd.gui.server.config.ServerConfig
import org.jd.gui.server.webdav.configureWebDav
import org.jd.gui.server.sync.configureSyncRoutes

private val logger = KotlinLogging.logger {}

// Global VCard manager instance
lateinit var vcardManager: VCardOrgManager
    private set

fun main(args: Array<String>) {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = System.getenv("HOST") ?: "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    logger.info { "Starting JD-GUI Server..." }

    // Load configuration
    val config = ServerConfig.load(environment.config)

    // Initialize VCard manager and restore orgs/users from files
    vcardManager = VCardOrgManager(config.storage.basePath)
    restoreOrganizationsAndUsers(vcardManager)

    // Install plugins
    install(DefaultHeaders) {
        header("X-Engine", "JD-GUI Server")
        header("X-Version", "2026.2.2")
    }

    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024)
        }
    }

    install(CallLogging) {
        level = org.slf4j.event.Level.INFO
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Requested-With")
        allowCredentials = true
        anyHost() // Configure appropriately for production
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception" }
            call.respondError(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
        }
    }

    // Configure Keycloak authentication
    configureKeycloakAuth(config.keycloak)

    // Configure routing
    routing {
        // Health check
        get("/health") {
            call.respondJson(mapOf(
                "status" to "healthy",
                "server" to "jd-gui-ktor",
                "version" to "2026.2.2"
            ))
        }

        // WebDAV routes
        configureWebDav(config.storage)

        // Yjs sync routes
        configureSyncRoutes(config.sync)
    }

    logger.info { "JD-GUI Server started on ${config.host}:${config.port}" }
}

/**
 * Restore organizations and users from vCard files on startup
 */
private fun restoreOrganizationsAndUsers(manager: VCardOrgManager) {
    logger.info { "Restoring organizations and users from vCard files..." }

    try {
        // Load all organizations
        val organizations = manager.listOrganizations()
        logger.info { "Found ${organizations.size} organizations" }

        organizations.forEach { org ->
            logger.info { "  Organization: ${org.name} (${org.id})" }

            // Load users for this organization
            val users = manager.listUsers(org.id)
            logger.info { "    Users: ${users.size}" }

            users.forEach { user ->
                val tokenStatus = if (manager.hasValidTokens(org.id, user.id)) "valid" else "none/expired"
                logger.debug { "      User: ${user.username} (${user.id}) - tokens: $tokenStatus" }
            }
        }

        // Create default organization if none exist
        if (organizations.isEmpty()) {
            logger.info { "No organizations found, creating default organization..." }
            val defaultOrg = manager.createOrganization(
                name = "Default",
                displayName = "Default Organization",
                notes = "Auto-created default organization"
            )
            logger.info { "Created default organization: ${defaultOrg.name} (${defaultOrg.id})" }
        }

        logger.info { "Organization and user restoration complete" }

    } catch (e: Exception) {
        logger.error(e) { "Failed to restore organizations and users" }
    }
}
