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
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.jd.gui.server.auth.VCardOrgManager
import org.jd.gui.server.auth.configureKeycloakAuth
import org.jd.gui.server.bedework.BedeworkService
import org.jd.gui.server.config.ServerConfig
import org.jd.gui.server.matrix.MatrixSocialService
import org.jd.gui.server.webdav.configureWebDav
import org.jd.gui.server.sync.configureSyncRoutes
import org.jd.gui.server.sync.VCardSyncManager
import org.jd.gui.server.xmpp.OpenfireOrgManager
import org.jd.gui.server.puml.configurePlantUmlRoutes
import org.jd.gui.server.svg.configureSvgRoutes

private val logger = KotlinLogging.logger {}

// Global service instances
lateinit var vcardManager: VCardOrgManager
    private set
lateinit var openfireManager: OpenfireOrgManager
    private set
lateinit var matrixService: MatrixSocialService
    private set
lateinit var bedeworkService: BedeworkService
    private set
lateinit var vcardSyncManager: VCardSyncManager
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

    // Initialize Openfire XMPP manager for org vCard sync
    openfireManager = OpenfireOrgManager(config.openfire, vcardManager)

    // Initialize Matrix social service
    matrixService = MatrixSocialService(config.matrix)

    // Initialize Bedework CalDAV/CardDAV service
    bedeworkService = BedeworkService(config.bedework, vcardManager)

    // Initialize unified vCard sync manager
    vcardSyncManager = VCardSyncManager(
        vcardManager = vcardManager,
        openfireManager = openfireManager,
        matrixService = matrixService,
        bedeworkService = bedeworkService,
        basePath = config.storage.basePath
    )

    // Connect to external services asynchronously
    launch {
        initializeExternalServices(config)
    }

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
        // Health check with service status
        get("/health") {
            val serviceStatus = vcardSyncManager.getServiceStatus()
            call.respondJson(mapOf(
                "status" to "healthy",
                "server" to "jd-gui-ktor",
                "version" to "2026.2.2",
                "sync" to mapOf(
                    "pendingOperations" to vcardSyncManager.getPendingCount()
                ),
                "services" to mapOf(
                    "openfire" to mapOf(
                        "enabled" to config.openfire.enabled,
                        "connected" to openfireManager.isConnected(),
                        "status" to serviceStatus[org.jd.gui.server.sync.SyncService.OPENFIRE]?.isOnline
                    ),
                    "matrix" to mapOf(
                        "enabled" to config.matrix.enabled,
                        "status" to serviceStatus[org.jd.gui.server.sync.SyncService.MATRIX]?.isOnline
                    ),
                    "bedework" to mapOf(
                        "enabled" to config.bedework.enabled,
                        "status" to serviceStatus[org.jd.gui.server.sync.SyncService.BEDEWORK]?.isOnline
                    )
                )
            ))
        }

        // WebDAV routes
        configureWebDav(config.storage)

        // Yjs sync routes
        configureSyncRoutes(config.sync)

        // PlantUML diagram generation routes
        configurePlantUmlRoutes()

        // Batik SVG viewer and conversion routes
        configureSvgRoutes()
    }

    // Register shutdown hook
    environment.monitor.subscribe(ApplicationStopped) {
        logger.info { "Shutting down services..." }
        openfireManager.disconnect()
        matrixService.shutdown()
        bedeworkService.shutdown()
    }

    logger.info { "JD-GUI Server started on ${config.host}:${config.port}" }
}

/**
 * Initialize external services (Openfire, Matrix, Bedework)
 */
private suspend fun initializeExternalServices(config: ServerConfig) {
    // Connect to Openfire for org vCard sync
    if (config.openfire.enabled) {
        logger.info { "Connecting to Openfire XMPP server..." }
        if (openfireManager.connect()) {
            // Sync all vCards to Openfire
            val syncResult = openfireManager.syncAll()
            logger.info {
                "Openfire sync complete: ${syncResult.organizationsSynced} orgs, " +
                    "${syncResult.usersSynced} users synced"
            }
        }
    }

    // Initialize Matrix for social features
    if (config.matrix.enabled) {
        logger.info { "Initializing Matrix social service..." }
        if (matrixService.initialize()) {
            logger.info { "Matrix service initialized" }
        }
    }

    // Initialize Bedework for CalDAV/CardDAV
    if (config.bedework.enabled) {
        logger.info { "Initializing Bedework CalDAV/CardDAV service..." }
        if (bedeworkService.initialize()) {
            // Sync all contacts to Bedework
            val syncResult = bedeworkService.syncAllContacts()
            logger.info {
                "Bedework sync complete: ${syncResult.contactsSynced} contacts synced"
            }
        }
    }
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
