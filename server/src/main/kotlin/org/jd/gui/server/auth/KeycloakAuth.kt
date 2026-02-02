/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.auth

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.http.*
import io.ktor.server.response.*
import mu.KotlinLogging
import org.jd.gui.server.config.KeycloakConfig
import java.net.URL
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Keycloak user principal extracted from JWT claims
 */
data class KeycloakPrincipal(
    val userId: String,           // Keycloak UUID (sub claim)
    val username: String,         // preferred_username
    val email: String?,           // email claim
    val emailVerified: Boolean,   // email_verified claim
    val name: String?,            // name claim (full name)
    val givenName: String?,       // given_name
    val familyName: String?,      // family_name
    val roles: Set<String>,       // realm_access.roles
    val clientRoles: Set<String>, // resource_access.{client}.roles
    val groups: Set<String>,      // groups claim
    val rawToken: String          // Original JWT token
) {
    companion object {
        fun fromJWT(jwt: DecodedJWT, clientId: String): KeycloakPrincipal {
            val realmAccess = jwt.getClaim("realm_access")
            val resourceAccess = jwt.getClaim("resource_access")

            val realmRoles = if (!realmAccess.isNull) {
                realmAccess.asMap()?.get("roles")?.let { roles ->
                    (roles as? List<*>)?.filterIsInstance<String>()?.toSet()
                } ?: emptySet()
            } else emptySet()

            val clientRoles = if (!resourceAccess.isNull) {
                resourceAccess.asMap()?.get(clientId)?.let { client ->
                    (client as? Map<*, *>)?.get("roles")?.let { roles ->
                        (roles as? List<*>)?.filterIsInstance<String>()?.toSet()
                    }
                } ?: emptySet()
            } else emptySet()

            val groups = jwt.getClaim("groups").let { claim ->
                if (!claim.isNull) {
                    claim.asList(String::class.java)?.toSet() ?: emptySet()
                } else emptySet()
            }

            return KeycloakPrincipal(
                userId = jwt.subject,
                username = jwt.getClaim("preferred_username").asString() ?: jwt.subject,
                email = jwt.getClaim("email").asString(),
                emailVerified = jwt.getClaim("email_verified").asBoolean() ?: false,
                name = jwt.getClaim("name").asString(),
                givenName = jwt.getClaim("given_name").asString(),
                familyName = jwt.getClaim("family_name").asString(),
                roles = realmRoles,
                clientRoles = clientRoles,
                groups = groups,
                rawToken = jwt.token
            )
        }
    }

    /**
     * Check if user has a specific realm role
     */
    fun hasRole(role: String): Boolean = roles.contains(role)

    /**
     * Check if user has a specific client role
     */
    fun hasClientRole(role: String): Boolean = clientRoles.contains(role)

    /**
     * Check if user is in a specific group
     */
    fun inGroup(group: String): Boolean = groups.contains(group)

    /**
     * Check if user has admin privileges
     */
    fun isAdmin(): Boolean = hasRole("admin") || hasRole("jd-gui-admin")
}

/**
 * Configure Keycloak JWT authentication
 */
fun Application.configureKeycloakAuth(config: KeycloakConfig) {
    logger.info { "Configuring Keycloak authentication for realm: ${config.realm}" }

    // Create JWK provider for validating tokens
    val jwkProvider = JwkProviderBuilder(URL(config.jwksUrl))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("keycloak") {
            realm = config.realm

            verifier(jwkProvider, config.issuer) {
                acceptLeeway(3) // 3 seconds leeway for clock skew

                withIssuer(config.issuer)
                withAudience(config.clientId, "account")
            }

            validate { credential ->
                try {
                    val jwt = credential.payload

                    // Validate token is not expired
                    if (jwt.expiresAt?.before(java.util.Date()) == true) {
                        logger.warn { "Token expired for user: ${jwt.subject}" }
                        return@validate null
                    }

                    // Create principal from JWT
                    val principal = KeycloakPrincipal.fromJWT(
                        JWT.decode(credential.payload.toString()),
                        config.clientId
                    )

                    logger.debug { "Authenticated user: ${principal.username} (${principal.userId})" }
                    JWTPrincipal(credential.payload)

                } catch (e: Exception) {
                    logger.error(e) { "JWT validation failed" }
                    null
                }
            }

            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf(
                    "error" to "unauthorized",
                    "message" to "Valid JWT token required"
                ))
            }
        }

        // Optional: Basic auth for service-to-service communication
        basic("service") {
            realm = "JD-GUI Service"
            validate { credentials ->
                val serviceUser = System.getenv("SERVICE_USER") ?: "service"
                val servicePassword = System.getenv("SERVICE_PASSWORD") ?: "secret"

                if (credentials.name == serviceUser && credentials.password == servicePassword) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
}

/**
 * Extension to get Keycloak principal from call
 */
fun ApplicationCall.keycloakPrincipal(): KeycloakPrincipal? {
    val jwtPrincipal = principal<JWTPrincipal>() ?: return null

    return try {
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ") ?: return null
        val jwt = JWT.decode(token)
        KeycloakPrincipal.fromJWT(jwt, "jd-gui-server")
    } catch (e: Exception) {
        null
    }
}

/**
 * Extension to get user ID from call
 */
fun ApplicationCall.userId(): String? = keycloakPrincipal()?.userId
