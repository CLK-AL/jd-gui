/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.application.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Phase Two (P2) Keycloak organization extension support.
 * https://github.com/p2-inc/keycloak-orgs
 *
 * Extracts organization claims from JWT tokens issued by Phase Two Keycloak.
 */

/**
 * Organization data from Phase Two JWT claims
 */
@Serializable
data class Organization(
    val id: String,           // Organization UUID
    val name: String,         // Organization name
    val displayName: String?, // Display name
    val domains: List<String> = emptyList(), // Associated domains
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Organization membership with roles
 */
@Serializable
data class OrganizationMembership(
    val organizationId: String,
    val organizationName: String,
    val roles: Set<String> = emptySet(),
    val isAdmin: Boolean = false
)

/**
 * Extended Keycloak principal with Phase Two organization support
 */
data class Phase2Principal(
    // Standard Keycloak claims
    val userId: String,
    val username: String,
    val email: String?,
    val emailVerified: Boolean,
    val name: String?,
    val givenName: String?,
    val familyName: String?,
    val realmRoles: Set<String>,
    val clientRoles: Set<String>,
    val groups: Set<String>,
    val rawToken: String,

    // Phase Two organization claims
    val activeOrganization: Organization?,
    val organizationMemberships: List<OrganizationMembership>,
    val defaultOrganization: String?
) {
    companion object {
        /**
         * Extract Phase Two principal from JWT
         */
        fun fromJWT(jwt: DecodedJWT, clientId: String): Phase2Principal {
            // Extract standard claims
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

            // Extract Phase Two organization claims
            val activeOrg = extractActiveOrganization(jwt)
            val orgMemberships = extractOrganizationMemberships(jwt)
            val defaultOrg = jwt.getClaim("default_organization").asString()

            return Phase2Principal(
                userId = jwt.subject,
                username = jwt.getClaim("preferred_username").asString() ?: jwt.subject,
                email = jwt.getClaim("email").asString(),
                emailVerified = jwt.getClaim("email_verified").asBoolean() ?: false,
                name = jwt.getClaim("name").asString(),
                givenName = jwt.getClaim("given_name").asString(),
                familyName = jwt.getClaim("family_name").asString(),
                realmRoles = realmRoles,
                clientRoles = clientRoles,
                groups = groups,
                rawToken = jwt.token,
                activeOrganization = activeOrg,
                organizationMemberships = orgMemberships,
                defaultOrganization = defaultOrg
            )
        }

        private fun extractActiveOrganization(jwt: DecodedJWT): Organization? {
            // Phase Two stores active organization in 'org' or 'active_organization' claim
            val orgClaim = jwt.getClaim("org")
            val activeOrgClaim = jwt.getClaim("active_organization")

            val orgMap = when {
                !activeOrgClaim.isNull -> activeOrgClaim.asMap()
                !orgClaim.isNull -> orgClaim.asMap()
                else -> return null
            } ?: return null

            return try {
                Organization(
                    id = orgMap["id"]?.toString() ?: return null,
                    name = orgMap["name"]?.toString() ?: "",
                    displayName = orgMap["display_name"]?.toString(),
                    domains = (orgMap["domains"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    attributes = (orgMap["attributes"] as? Map<*, *>)
                        ?.filterKeys { it is String }
                        ?.filterValues { it is String }
                        ?.map { it.key.toString() to it.value.toString() }
                        ?.toMap()
                        ?: emptyMap()
                )
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse active organization" }
                null
            }
        }

        private fun extractOrganizationMemberships(jwt: DecodedJWT): List<OrganizationMembership> {
            // Phase Two stores memberships in 'organizations' or 'org_memberships' claim
            val orgsClaim = jwt.getClaim("organizations")
            val membershipsClaim = jwt.getClaim("org_memberships")

            val membershipsData = when {
                !membershipsClaim.isNull -> membershipsClaim.asList(Any::class.java)
                !orgsClaim.isNull -> orgsClaim.asList(Any::class.java)
                else -> return emptyList()
            } ?: return emptyList()

            return membershipsData.mapNotNull { membershipData ->
                try {
                    val data = membershipData as? Map<*, *> ?: return@mapNotNull null

                    OrganizationMembership(
                        organizationId = data["organization_id"]?.toString()
                            ?: data["id"]?.toString()
                            ?: return@mapNotNull null,
                        organizationName = data["organization_name"]?.toString()
                            ?: data["name"]?.toString()
                            ?: "",
                        roles = (data["roles"] as? List<*>)?.filterIsInstance<String>()?.toSet()
                            ?: emptySet(),
                        isAdmin = data["is_admin"]?.toString()?.toBoolean() ?: false
                    )
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to parse organization membership" }
                    null
                }
            }
        }
    }

    /**
     * Get folder path for current organization and user
     * Structure: /{basePath}/orgs/{orgId}/users/{userId}
     */
    fun getOrgUserFolderPath(basePath: String): String {
        val orgId = activeOrganization?.id ?: defaultOrganization ?: "default"
        return "$basePath/orgs/$orgId/users/$userId"
    }

    /**
     * Get shared folder path for current organization
     * Structure: /{basePath}/orgs/{orgId}/shared
     */
    fun getOrgSharedFolderPath(basePath: String): String {
        val orgId = activeOrganization?.id ?: defaultOrganization ?: "default"
        return "$basePath/orgs/$orgId/shared"
    }

    /**
     * Get system folder path (accessible by admins)
     * Structure: /{basePath}/system
     */
    fun getSystemFolderPath(basePath: String): String {
        return "$basePath/system"
    }

    /**
     * Check if user has a specific role in their active organization
     */
    fun hasOrgRole(role: String): Boolean {
        val activeOrgId = activeOrganization?.id ?: return false
        return organizationMemberships
            .find { it.organizationId == activeOrgId }
            ?.roles
            ?.contains(role) ?: false
    }

    /**
     * Check if user is admin of their active organization
     */
    fun isOrgAdmin(): Boolean {
        val activeOrgId = activeOrganization?.id ?: return false
        return organizationMemberships
            .find { it.organizationId == activeOrgId }
            ?.isAdmin ?: false
    }

    /**
     * Check if user is a system admin (realm admin)
     */
    fun isSystemAdmin(): Boolean =
        realmRoles.contains("admin") ||
        realmRoles.contains("jd-gui-admin") ||
        realmRoles.contains("realm-admin")

    /**
     * Get all organization IDs the user belongs to
     */
    fun getOrganizationIds(): List<String> =
        organizationMemberships.map { it.organizationId }

    /**
     * Check if user belongs to a specific organization
     */
    fun belongsToOrganization(orgId: String): Boolean =
        organizationMemberships.any { it.organizationId == orgId }
}

/**
 * Extension to get Phase Two principal from call
 */
fun ApplicationCall.phase2Principal(): Phase2Principal? {
    val token = request.headers[io.ktor.http.HttpHeaders.Authorization]
        ?.removePrefix("Bearer ")
        ?: return null

    return try {
        val jwt = JWT.decode(token)
        Phase2Principal.fromJWT(jwt, "jd-gui-server")
    } catch (e: Exception) {
        logger.warn(e) { "Failed to decode Phase2 principal" }
        null
    }
}

/**
 * Extension to get active organization ID from call
 */
fun ApplicationCall.organizationId(): String? =
    phase2Principal()?.activeOrganization?.id

/**
 * Extension to get user's folder path (org + user)
 */
fun ApplicationCall.userFolderPath(basePath: String): String? =
    phase2Principal()?.getOrgUserFolderPath(basePath)

/**
 * Extension to get org's shared folder path
 */
fun ApplicationCall.sharedFolderPath(basePath: String): String? =
    phase2Principal()?.getOrgSharedFolderPath(basePath)
