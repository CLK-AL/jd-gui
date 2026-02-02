/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.config

import io.ktor.server.config.*

/**
 * Server configuration loaded from application.yaml
 */
data class ServerConfig(
    val host: String,
    val port: Int,
    val keycloak: KeycloakConfig,
    val storage: StorageConfig,
    val sync: SyncConfig,
    val openfire: OpenfireConfig,
    val matrix: MatrixConfig,
    val bedework: BedeworkConfig
) {
    companion object {
        fun load(config: ApplicationConfig): ServerConfig {
            return ServerConfig(
                host = config.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0",
                port = config.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull() ?: 8080,
                keycloak = KeycloakConfig.load(config),
                storage = StorageConfig.load(config),
                sync = SyncConfig.load(config),
                openfire = OpenfireConfig.load(config),
                matrix = MatrixConfig.load(config),
                bedework = BedeworkConfig.load(config)
            )
        }
    }
}

/**
 * Keycloak OIDC configuration
 */
data class KeycloakConfig(
    val realm: String,
    val authServerUrl: String,
    val clientId: String,
    val clientSecret: String?,
    val jwksUrl: String,
    val issuer: String,
    val sslRequired: String,
    val publicClient: Boolean,
    val bearerOnly: Boolean
) {
    companion object {
        fun load(config: ApplicationConfig): KeycloakConfig {
            val keycloak = config.config("keycloak")
            return KeycloakConfig(
                realm = keycloak.propertyOrNull("realm")?.getString() ?: "jd-gui",
                authServerUrl = keycloak.propertyOrNull("auth-server-url")?.getString()
                    ?: System.getenv("KEYCLOAK_URL") ?: "http://localhost:8180",
                clientId = keycloak.propertyOrNull("client-id")?.getString() ?: "jd-gui-server",
                clientSecret = keycloak.propertyOrNull("client-secret")?.getString()
                    ?: System.getenv("KEYCLOAK_CLIENT_SECRET"),
                jwksUrl = keycloak.propertyOrNull("jwks-url")?.getString()
                    ?: "${System.getenv("KEYCLOAK_URL") ?: "http://localhost:8180"}/realms/jd-gui/protocol/openid-connect/certs",
                issuer = keycloak.propertyOrNull("issuer")?.getString()
                    ?: "${System.getenv("KEYCLOAK_URL") ?: "http://localhost:8180"}/realms/jd-gui",
                sslRequired = keycloak.propertyOrNull("ssl-required")?.getString() ?: "external",
                publicClient = keycloak.propertyOrNull("public-client")?.getString()?.toBoolean() ?: false,
                bearerOnly = keycloak.propertyOrNull("bearer-only")?.getString()?.toBoolean() ?: true
            )
        }
    }
}

/**
 * Storage configuration for WebDAV
 */
data class StorageConfig(
    val basePath: String,
    val systemFolder: String,
    val usersFolder: String,
    val maxFileSize: Long,
    val allowedExtensions: List<String>
) {
    companion object {
        fun load(config: ApplicationConfig): StorageConfig {
            val storage = config.config("storage")
            return StorageConfig(
                basePath = storage.propertyOrNull("base-path")?.getString()
                    ?: System.getenv("STORAGE_PATH") ?: "./data",
                systemFolder = storage.propertyOrNull("system-folder")?.getString() ?: "system",
                usersFolder = storage.propertyOrNull("users-folder")?.getString() ?: "users",
                maxFileSize = storage.propertyOrNull("max-file-size")?.getString()?.toLongOrNull()
                    ?: 104857600L,
                allowedExtensions = storage.propertyOrNull("allowed-extensions")?.getList()
                    ?: listOf(".jar", ".class", ".java", ".kt", ".xml", ".json", ".properties")
            )
        }
    }

    /**
     * Get user folder path by Keycloak UUID
     */
    fun getUserFolderPath(userId: String): String {
        return "$basePath/$usersFolder/$userId"
    }

    /**
     * Get system folder path
     */
    fun getSystemFolderPath(): String {
        return "$basePath/$systemFolder"
    }
}

/**
 * Yjs sync configuration
 */
data class SyncConfig(
    val enabled: Boolean,
    val debounceMs: Long,
    val broadcastChanges: Boolean
) {
    companion object {
        fun load(config: ApplicationConfig): SyncConfig {
            val sync = config.config("sync")
            return SyncConfig(
                enabled = sync.propertyOrNull("enabled")?.getString()?.toBoolean() ?: true,
                debounceMs = sync.propertyOrNull("debounce-ms")?.getString()?.toLongOrNull() ?: 500L,
                broadcastChanges = sync.propertyOrNull("broadcast-changes")?.getString()?.toBoolean() ?: true
            )
        }
    }
}

/**
 * Openfire XMPP configuration for organization management
 * Organizations are stored on-premises via Openfire's XMPP/vCard support
 */
data class OpenfireConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val domain: String,
    val adminUsername: String,
    val adminPassword: String,
    val securityMode: String,
    val resourceName: String,
    val connectionTimeout: Int,
    val enableRoster: Boolean,
    val enablePubSub: Boolean
) {
    companion object {
        fun load(config: ApplicationConfig): OpenfireConfig {
            val openfire = config.config("openfire")
            return OpenfireConfig(
                enabled = openfire.propertyOrNull("enabled")?.getString()?.toBoolean() ?: false,
                host = openfire.propertyOrNull("host")?.getString()
                    ?: System.getenv("OPENFIRE_HOST") ?: "localhost",
                port = openfire.propertyOrNull("port")?.getString()?.toIntOrNull() ?: 5222,
                domain = openfire.propertyOrNull("domain")?.getString()
                    ?: System.getenv("OPENFIRE_DOMAIN") ?: "localhost",
                adminUsername = openfire.propertyOrNull("admin-username")?.getString()
                    ?: System.getenv("OPENFIRE_ADMIN_USER") ?: "admin",
                adminPassword = openfire.propertyOrNull("admin-password")?.getString()
                    ?: System.getenv("OPENFIRE_ADMIN_PASSWORD") ?: "",
                securityMode = openfire.propertyOrNull("security-mode")?.getString() ?: "disabled",
                resourceName = openfire.propertyOrNull("resource-name")?.getString() ?: "jd-gui-server",
                connectionTimeout = openfire.propertyOrNull("connection-timeout")?.getString()?.toIntOrNull() ?: 30000,
                enableRoster = openfire.propertyOrNull("enable-roster")?.getString()?.toBoolean() ?: true,
                enablePubSub = openfire.propertyOrNull("enable-pubsub")?.getString()?.toBoolean() ?: true
            )
        }
    }

    /**
     * Build JID for organization
     */
    fun buildOrgJid(orgId: String): String = "org-$orgId@$domain"

    /**
     * Build JID for user in organization
     */
    fun buildUserJid(userId: String): String = "user-$userId@$domain"
}

/**
 * Matrix configuration for user social features
 * Users communicate via Matrix protocol for real-time social interactions
 */
data class MatrixConfig(
    val enabled: Boolean,
    val homeserverUrl: String,
    val serverName: String,
    val adminUsername: String,
    val adminAccessToken: String,
    val enablePresence: Boolean,
    val enableRooms: Boolean,
    val enableDirectMessages: Boolean,
    val orgRoomPrefix: String,
    val syncTimeoutMs: Long
) {
    companion object {
        fun load(config: ApplicationConfig): MatrixConfig {
            val matrix = config.config("matrix")
            return MatrixConfig(
                enabled = matrix.propertyOrNull("enabled")?.getString()?.toBoolean() ?: false,
                homeserverUrl = matrix.propertyOrNull("homeserver-url")?.getString()
                    ?: System.getenv("MATRIX_HOMESERVER_URL") ?: "http://localhost:8008",
                serverName = matrix.propertyOrNull("server-name")?.getString()
                    ?: System.getenv("MATRIX_SERVER_NAME") ?: "localhost",
                adminUsername = matrix.propertyOrNull("admin-username")?.getString()
                    ?: System.getenv("MATRIX_ADMIN_USER") ?: "admin",
                adminAccessToken = matrix.propertyOrNull("admin-access-token")?.getString()
                    ?: System.getenv("MATRIX_ADMIN_TOKEN") ?: "",
                enablePresence = matrix.propertyOrNull("enable-presence")?.getString()?.toBoolean() ?: true,
                enableRooms = matrix.propertyOrNull("enable-rooms")?.getString()?.toBoolean() ?: true,
                enableDirectMessages = matrix.propertyOrNull("enable-dm")?.getString()?.toBoolean() ?: true,
                orgRoomPrefix = matrix.propertyOrNull("org-room-prefix")?.getString() ?: "jd-gui-org",
                syncTimeoutMs = matrix.propertyOrNull("sync-timeout-ms")?.getString()?.toLongOrNull() ?: 30000L
            )
        }
    }

    /**
     * Build Matrix user ID
     */
    fun buildUserId(username: String): String = "@$username:$serverName"

    /**
     * Build Matrix room alias for organization
     */
    fun buildOrgRoomAlias(orgId: String): String = "#$orgRoomPrefix-$orgId:$serverName"
}

/**
 * Bedework CalDAV/CardDAV configuration for calendar and vCard sync
 * Provides enterprise calendar and contacts management
 */
data class BedeworkConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val caldavPath: String,
    val carddavPath: String,
    val adminUsername: String,
    val adminPassword: String,
    val defaultCalendarName: String,
    val defaultAddressBookName: String,
    val syncIntervalMs: Long,
    val enableCalendarSync: Boolean,
    val enableContactsSync: Boolean
) {
    companion object {
        fun load(config: ApplicationConfig): BedeworkConfig {
            val bedework = config.config("bedework")
            return BedeworkConfig(
                enabled = bedework.propertyOrNull("enabled")?.getString()?.toBoolean() ?: false,
                baseUrl = bedework.propertyOrNull("base-url")?.getString()
                    ?: System.getenv("BEDEWORK_URL") ?: "http://localhost:8080/bedework",
                caldavPath = bedework.propertyOrNull("caldav-path")?.getString() ?: "/ucaldav",
                carddavPath = bedework.propertyOrNull("carddav-path")?.getString() ?: "/ucarddav",
                adminUsername = bedework.propertyOrNull("admin-username")?.getString()
                    ?: System.getenv("BEDEWORK_ADMIN_USER") ?: "admin",
                adminPassword = bedework.propertyOrNull("admin-password")?.getString()
                    ?: System.getenv("BEDEWORK_ADMIN_PASSWORD") ?: "",
                defaultCalendarName = bedework.propertyOrNull("default-calendar")?.getString() ?: "calendar",
                defaultAddressBookName = bedework.propertyOrNull("default-addressbook")?.getString() ?: "contacts",
                syncIntervalMs = bedework.propertyOrNull("sync-interval-ms")?.getString()?.toLongOrNull() ?: 60000L,
                enableCalendarSync = bedework.propertyOrNull("enable-calendar-sync")?.getString()?.toBoolean() ?: true,
                enableContactsSync = bedework.propertyOrNull("enable-contacts-sync")?.getString()?.toBoolean() ?: true
            )
        }
    }

    /**
     * Get CalDAV URL for user
     */
    fun getCalDavUrl(userId: String): String = "$baseUrl$caldavPath/user/$userId"

    /**
     * Get CardDAV URL for user
     */
    fun getCardDavUrl(userId: String): String = "$baseUrl$carddavPath/user/$userId"

    /**
     * Get organization calendar URL
     */
    fun getOrgCalendarUrl(orgId: String): String = "$baseUrl$caldavPath/public/org-$orgId"

    /**
     * Get organization address book URL
     */
    fun getOrgAddressBookUrl(orgId: String): String = "$baseUrl$carddavPath/public/org-$orgId"
}
