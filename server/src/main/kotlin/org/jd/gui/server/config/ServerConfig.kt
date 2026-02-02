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
    val sync: SyncConfig
) {
    companion object {
        fun load(config: ApplicationConfig): ServerConfig {
            return ServerConfig(
                host = config.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0",
                port = config.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull() ?: 8080,
                keycloak = KeycloakConfig.load(config),
                storage = StorageConfig.load(config),
                sync = SyncConfig.load(config)
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
