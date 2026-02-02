/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.auth

import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.TelephoneType
import ezvcard.property.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Organization represented as a vCard (KIND:org)
 */
@Serializable
data class OrgVCard(
    val id: String,
    val name: String,
    val displayName: String?,
    val email: String?,
    val phone: String?,
    val website: String?,
    val address: AddressInfo?,
    val logo: String?,      // Base64 encoded logo
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val memberCount: Int = 0,
    val attributes: Map<String, String> = emptyMap()
)

/**
 * User member represented as a vCard (KIND:individual)
 */
@Serializable
data class UserVCard(
    val id: String,
    val organizationId: String,
    val username: String,
    val email: String,
    val fullName: String?,
    val givenName: String?,
    val familyName: String?,
    val phone: String?,
    val title: String?,
    val department: String?,
    val photo: String?,     // Base64 encoded photo
    val roles: List<String> = emptyList(),
    val isAdmin: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lastLogin: Long? = null,
    // OAuth tokens stored in vCard (Base64 encoded for storage)
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiresAt: Long? = null,
    val tokenIssuedAt: Long? = null
)

/**
 * Address information
 */
@Serializable
data class AddressInfo(
    val street: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val country: String?
)

/**
 * Token information stored in vCard
 */
@Serializable
data class TokenInfo(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val issuedAt: Long,
    val isExpired: Boolean
)

/**
 * Enriched JWT claims with vCard data
 */
@Serializable
data class EnrichedJwtClaims(
    // Standard JWT claims
    val sub: String,
    val iss: String?,
    val exp: Long?,
    val iat: Long?,

    // Organization vCard data
    val orgId: String,
    val orgName: String,
    val orgDisplayName: String?,
    val orgEmail: String?,
    val orgPhone: String?,
    val orgWebsite: String?,

    // User vCard data
    val userId: String,
    val username: String,
    val email: String,
    val fullName: String?,
    val givenName: String?,
    val familyName: String?,
    val phone: String?,
    val title: String?,
    val department: String?,
    val photo: String?,
    val roles: List<String>,
    val isOrgAdmin: Boolean,

    // File paths
    val userFolderPath: String,
    val orgSharedPath: String,
    val filesPath: String
)

/**
 * VCard-based organization and user manager.
 * Uses ez-vcard library to store org/user data as vCard files.
 *
 * Directory structure:
 * /data/orgs/
 *   ├── {org-uuid}/
 *   │   ├── org.vcf              # Organization vCard (KIND:org)
 *   │   ├── shared/              # Shared organization files
 *   │   └── users/
 *   │       ├── {user-uuid}/
 *   │       │   ├── user.vcf     # User vCard (KIND:individual) with tokens
 *   │       │   └── files/       # User's files
 *   │       └── ...
 *   └── ...
 */
class VCardOrgManager(private val basePath: String) {

    private val orgsDir = File(basePath, "orgs")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        orgsDir.mkdirs()
        logger.info { "VCard manager initialized at $basePath/orgs" }
    }

    // ===================
    // Path Helpers
    // ===================

    private fun getOrgDir(orgId: String) = File(orgsDir, orgId)
    private fun getOrgVCardFile(orgId: String) = File(getOrgDir(orgId), "org.vcf")
    private fun getOrgSharedDir(orgId: String) = File(getOrgDir(orgId), "shared")
    private fun getUsersDir(orgId: String) = File(getOrgDir(orgId), "users")
    private fun getUserDir(orgId: String, userId: String) = File(getUsersDir(orgId), userId)
    private fun getUserVCardFile(orgId: String, userId: String) = File(getUserDir(orgId, userId), "user.vcf")
    private fun getUserFilesDir(orgId: String, userId: String) = File(getUserDir(orgId, userId), "files")

    // ===================
    // Organization Methods
    // ===================

    /**
     * Create a new organization with org.vcf in its folder
     */
    fun createOrganization(
        name: String,
        displayName: String? = null,
        email: String? = null,
        phone: String? = null,
        website: String? = null,
        address: AddressInfo? = null,
        notes: String? = null
    ): OrgVCard {
        val orgId = UUID.randomUUID().toString()
        val now = Instant.now().toEpochMilli()

        // Create org directory structure
        val orgDir = getOrgDir(orgId)
        orgDir.mkdirs()
        getOrgSharedDir(orgId).mkdirs()
        getUsersDir(orgId).mkdirs()

        val vcard = VCard().apply {
            kind = Kind.org()
            uid = Uid(orgId)
            setFormattedName(displayName ?: name)
            organization = Organization().apply { values.add(name) }

            email?.let { addEmail(Email(it)) }
            phone?.let { addTelephoneNumber(Telephone(it).apply { types.add(TelephoneType.WORK) }) }
            website?.let { addUrl(Url(it)) }

            address?.let { addr ->
                addAddress(Address().apply {
                    streetAddress = addr.street
                    locality = addr.city
                    region = addr.state
                    postalCode = addr.postalCode
                    country = addr.country
                })
            }

            notes?.let { addNote(Note(it)) }
            revision = Revision(Date.from(Instant.ofEpochMilli(now)))
        }

        // Save org.vcf in org folder
        Ezvcard.write(vcard).version(VCardVersion.V4_0).go(getOrgVCardFile(orgId))

        logger.info { "Created organization: $name ($orgId) at ${orgDir.absolutePath}" }

        return OrgVCard(
            id = orgId, name = name, displayName = displayName, email = email,
            phone = phone, website = website, address = address, logo = null,
            notes = notes, createdAt = now, updatedAt = now
        )
    }

    /**
     * Get organization by ID (reads org.vcf from org folder)
     */
    fun getOrganization(orgId: String): OrgVCard? {
        val file = getOrgVCardFile(orgId)
        if (!file.exists()) return null

        return try {
            val vcard = Ezvcard.parse(file).first()
            vcardToOrg(orgId, vcard)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse org vCard: $orgId" }
            null
        }
    }

    /**
     * List all organizations (scans org folders for org.vcf)
     */
    fun listOrganizations(): List<OrgVCard> {
        return orgsDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir ->
                val orgVcf = File(dir, "org.vcf")
                if (orgVcf.exists()) getOrganization(dir.name) else null
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Update organization
     */
    fun updateOrganization(orgId: String, updates: Map<String, String?>): OrgVCard? {
        val file = getOrgVCardFile(orgId)
        if (!file.exists()) return null

        val vcard = Ezvcard.parse(file).first()

        updates["name"]?.let { vcard.organization?.values?.set(0, it) }
        updates["displayName"]?.let { vcard.setFormattedName(it) }
        updates["email"]?.let { vcard.emails.clear(); vcard.addEmail(Email(it)) }
        updates["phone"]?.let { vcard.telephoneNumbers.clear(); vcard.addTelephoneNumber(Telephone(it)) }
        updates["website"]?.let { vcard.urls.clear(); vcard.addUrl(Url(it)) }
        updates["notes"]?.let { vcard.notes.clear(); vcard.addNote(Note(it)) }

        vcard.revision = Revision(Date())
        Ezvcard.write(vcard).version(VCardVersion.V4_0).go(file)

        return getOrganization(orgId)
    }

    /**
     * Delete organization and all its contents
     */
    fun deleteOrganization(orgId: String): Boolean {
        val orgDir = getOrgDir(orgId)
        return if (orgDir.exists()) orgDir.deleteRecursively() else false
    }

    // ===================
    // User Methods
    // ===================

    /**
     * Create a new user with user.vcf in their folder
     */
    fun createUser(
        organizationId: String,
        username: String,
        email: String,
        fullName: String? = null,
        givenName: String? = null,
        familyName: String? = null,
        phone: String? = null,
        title: String? = null,
        department: String? = null,
        roles: List<String> = emptyList(),
        isAdmin: Boolean = false
    ): UserVCard? {
        if (getOrganization(organizationId) == null) {
            logger.warn { "Organization not found: $organizationId" }
            return null
        }

        val userId = UUID.randomUUID().toString()
        val now = Instant.now().toEpochMilli()

        // Create user directory structure
        val userDir = getUserDir(organizationId, userId)
        userDir.mkdirs()
        getUserFilesDir(organizationId, userId).mkdirs()

        val vcard = VCard().apply {
            kind = Kind.individual()
            uid = Uid(userId)
            structuredName = StructuredName().apply { given = givenName; family = familyName }
            setFormattedName(fullName ?: "$givenName $familyName".trim().ifEmpty { username })
            addNickname(Nickname(username))
            addEmail(Email(email))

            phone?.let { addTelephoneNumber(Telephone(it)) }
            organization = Organization().apply { values.add(organizationId) }
            title?.let { addTitle(Title(it)) }
            department?.let { addCategories(Categories(it)) }

            if (roles.isNotEmpty()) addExtendedProperty(RawProperty("X-ROLES", roles.joinToString(",")))
            if (isAdmin) addExtendedProperty(RawProperty("X-IS-ADMIN", "true"))

            revision = Revision(Date.from(Instant.ofEpochMilli(now)))
        }

        // Save user.vcf in user folder
        Ezvcard.write(vcard).version(VCardVersion.V4_0).go(getUserVCardFile(organizationId, userId))

        logger.info { "Created user: $username ($userId) at ${userDir.absolutePath}" }

        return UserVCard(
            id = userId, organizationId = organizationId, username = username, email = email,
            fullName = fullName, givenName = givenName, familyName = familyName, phone = phone,
            title = title, department = department, photo = null, roles = roles, isAdmin = isAdmin,
            createdAt = now, updatedAt = now
        )
    }

    /**
     * Get user by ID (reads user.vcf from user folder)
     */
    fun getUser(organizationId: String, userId: String): UserVCard? {
        val file = getUserVCardFile(organizationId, userId)
        if (!file.exists()) return null

        return try {
            val vcard = Ezvcard.parse(file).first()
            vcardToUser(organizationId, userId, vcard)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse user vCard: $userId" }
            null
        }
    }

    /**
     * Find user by email across all organizations
     */
    fun findUserByEmail(email: String): UserVCard? {
        orgsDir.listFiles { f -> f.isDirectory }?.forEach { orgDir ->
            val usersDir = File(orgDir, "users")
            if (usersDir.exists()) {
                usersDir.listFiles { f -> f.isDirectory }?.forEach { userDir ->
                    val userVcf = File(userDir, "user.vcf")
                    if (userVcf.exists()) {
                        try {
                            val vcard = Ezvcard.parse(userVcf).first()
                            if (vcard.emails.any { it.value.equals(email, ignoreCase = true) }) {
                                return vcardToUser(orgDir.name, userDir.name, vcard)
                            }
                        } catch (e: Exception) { /* skip */ }
                    }
                }
            }
        }
        return null
    }

    /**
     * List all users in organization
     */
    fun listUsers(organizationId: String): List<UserVCard> {
        val usersDir = getUsersDir(organizationId)
        if (!usersDir.exists()) return emptyList()

        return usersDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { userDir ->
                val userVcf = File(userDir, "user.vcf")
                if (userVcf.exists()) getUser(organizationId, userDir.name) else null
            }
            ?.sortedBy { it.username }
            ?: emptyList()
    }

    /**
     * Update user
     */
    fun updateUser(organizationId: String, userId: String, updates: Map<String, Any?>): UserVCard? {
        val file = getUserVCardFile(organizationId, userId)
        if (!file.exists()) return null

        val vcard = Ezvcard.parse(file).first()

        updates["email"]?.let { vcard.emails.clear(); vcard.addEmail(Email(it.toString())) }
        updates["fullName"]?.let { vcard.setFormattedName(it.toString()) }
        updates["phone"]?.let { vcard.telephoneNumbers.clear(); vcard.addTelephoneNumber(Telephone(it.toString())) }
        updates["title"]?.let { vcard.titles.clear(); vcard.addTitle(Title(it.toString())) }

        @Suppress("UNCHECKED_CAST")
        (updates["roles"] as? List<String>)?.let { roles ->
            vcard.extendedProperties.removeIf { it.propertyName == "X-ROLES" }
            vcard.addExtendedProperty(RawProperty("X-ROLES", roles.joinToString(",")))
        }

        updates["isAdmin"]?.let { isAdmin ->
            vcard.extendedProperties.removeIf { it.propertyName == "X-IS-ADMIN" }
            if (isAdmin == true) vcard.addExtendedProperty(RawProperty("X-IS-ADMIN", "true"))
        }

        updates["lastLogin"]?.let { timestamp ->
            vcard.extendedProperties.removeIf { it.propertyName == "X-LAST-LOGIN" }
            vcard.addExtendedProperty(RawProperty("X-LAST-LOGIN", timestamp.toString()))
        }

        vcard.revision = Revision(Date())
        Ezvcard.write(vcard).version(VCardVersion.V4_0).go(file)

        return getUser(organizationId, userId)
    }

    /**
     * Delete user and their folder
     */
    fun deleteUser(organizationId: String, userId: String): Boolean {
        val userDir = getUserDir(organizationId, userId)
        return if (userDir.exists()) userDir.deleteRecursively() else false
    }

    /**
     * Update user from a merged vCard object
     * Used by VCardSyncManager after merging local and remote vCards
     */
    fun updateUserFromVCard(organizationId: String, user: User, mergedVCard: VCard): UserVCard? {
        val file = getUserVCardFile(organizationId, user.id)
        if (!file.exists()) return null

        try {
            // Preserve tokens from existing vCard
            val existingVCard = Ezvcard.parse(file).firstOrNull()
            val existingTokens = existingVCard?.extendedProperties?.filter {
                it.propertyName in listOf("X-ACCESS-TOKEN", "X-REFRESH-TOKEN", "X-TOKEN-EXPIRES", "X-TOKEN-ISSUED")
            }

            // Copy token properties to merged vCard
            existingTokens?.forEach { tokenProp ->
                mergedVCard.extendedProperties.removeIf { it.propertyName == tokenProp.propertyName }
                mergedVCard.addExtendedProperty(tokenProp)
            }

            // Update revision
            mergedVCard.revision = Revision(Date())

            // Write merged vCard
            Ezvcard.write(mergedVCard).version(VCardVersion.V4_0).go(file)

            logger.debug { "Updated user vCard from merged data: ${user.id}" }
            return getUser(organizationId, user.id)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update user from vCard: ${user.id}" }
            return null
        }
    }

    // ===================
    // Token Management
    // ===================

    /**
     * Store OAuth tokens in user's vCard (user.vcf)
     */
    fun storeTokens(
        organizationId: String,
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long
    ): Boolean {
        val file = getUserVCardFile(organizationId, userId)
        if (!file.exists()) return false

        return try {
            val vcard = Ezvcard.parse(file).first()
            val now = Instant.now().toEpochMilli()

            val encodedAccess = Base64.getEncoder().encodeToString(accessToken.toByteArray())
            val encodedRefresh = refreshToken?.let { Base64.getEncoder().encodeToString(it.toByteArray()) }

            vcard.extendedProperties.removeIf {
                it.propertyName in listOf("X-ACCESS-TOKEN", "X-REFRESH-TOKEN", "X-TOKEN-EXPIRES", "X-TOKEN-ISSUED")
            }

            vcard.addExtendedProperty(RawProperty("X-ACCESS-TOKEN", encodedAccess))
            encodedRefresh?.let { vcard.addExtendedProperty(RawProperty("X-REFRESH-TOKEN", it)) }
            vcard.addExtendedProperty(RawProperty("X-TOKEN-EXPIRES", expiresAt.toString()))
            vcard.addExtendedProperty(RawProperty("X-TOKEN-ISSUED", now.toString()))

            vcard.revision = Revision(Date())
            Ezvcard.write(vcard).version(VCardVersion.V4_0).go(file)

            logger.debug { "Stored tokens in user.vcf for $userId" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to store tokens for user $userId" }
            false
        }
    }

    /**
     * Get stored tokens from user's vCard
     */
    fun getStoredTokens(organizationId: String, userId: String): TokenInfo? {
        val file = getUserVCardFile(organizationId, userId)
        if (!file.exists()) return null

        return try {
            val vcard = Ezvcard.parse(file).first()

            val encodedAccess = vcard.getExtendedProperty("X-ACCESS-TOKEN")?.value ?: return null
            val encodedRefresh = vcard.getExtendedProperty("X-REFRESH-TOKEN")?.value
            val expiresAt = vcard.getExtendedProperty("X-TOKEN-EXPIRES")?.value?.toLongOrNull() ?: return null
            val issuedAt = vcard.getExtendedProperty("X-TOKEN-ISSUED")?.value?.toLongOrNull()

            TokenInfo(
                accessToken = String(Base64.getDecoder().decode(encodedAccess)),
                refreshToken = encodedRefresh?.let { String(Base64.getDecoder().decode(it)) },
                expiresAt = expiresAt,
                issuedAt = issuedAt ?: 0,
                isExpired = expiresAt < Instant.now().toEpochMilli()
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get tokens for user $userId" }
            null
        }
    }

    /**
     * Clear stored tokens (on logout)
     */
    fun clearTokens(organizationId: String, userId: String): Boolean {
        val file = getUserVCardFile(organizationId, userId)
        if (!file.exists()) return false

        return try {
            val vcard = Ezvcard.parse(file).first()
            vcard.extendedProperties.removeIf {
                it.propertyName in listOf("X-ACCESS-TOKEN", "X-REFRESH-TOKEN", "X-TOKEN-EXPIRES", "X-TOKEN-ISSUED")
            }
            vcard.revision = Revision(Date())
            Ezvcard.write(vcard).version(VCardVersion.V4_0).go(file)
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to clear tokens for user $userId" }
            false
        }
    }

    /**
     * Check if user has valid (non-expired) tokens
     */
    fun hasValidTokens(organizationId: String, userId: String): Boolean {
        val tokens = getStoredTokens(organizationId, userId) ?: return false
        return !tokens.isExpired
    }

    // ===================
    // JWT Enrichment
    // ===================

    /**
     * Enrich JWT with vCard data from org.vcf and user.vcf
     * This combines Keycloak claims with local vCard profile data
     */
    fun enrichJwtWithVCard(
        principal: Phase2Principal,
        issuer: String? = null
    ): EnrichedJwtClaims? {
        val orgId = principal.activeOrganization?.id ?: principal.defaultOrganization ?: return null

        // Get org vCard data
        val org = getOrganization(orgId) ?: return null

        // Find or create user
        var user = findUserByEmail(principal.email ?: return null)

        if (user == null) {
            // Create user from Keycloak principal
            user = createUser(
                organizationId = orgId,
                username = principal.username,
                email = principal.email,
                fullName = principal.name,
                givenName = principal.givenName,
                familyName = principal.familyName,
                roles = principal.organizationMemberships
                    .find { it.organizationId == orgId }
                    ?.roles?.toList() ?: emptyList(),
                isAdmin = principal.isOrgAdmin()
            )
        } else {
            // Update last login
            user = updateUser(user.organizationId, user.id, mapOf(
                "lastLogin" to Instant.now().toEpochMilli()
            ))
        }

        if (user == null) return null

        // Build enriched claims
        return EnrichedJwtClaims(
            // JWT claims
            sub = principal.userId,
            iss = issuer,
            exp = null, // Would come from token
            iat = Instant.now().toEpochMilli(),

            // Org vCard data
            orgId = org.id,
            orgName = org.name,
            orgDisplayName = org.displayName,
            orgEmail = org.email,
            orgPhone = org.phone,
            orgWebsite = org.website,

            // User vCard data
            userId = user.id,
            username = user.username,
            email = user.email,
            fullName = user.fullName,
            givenName = user.givenName,
            familyName = user.familyName,
            phone = user.phone,
            title = user.title,
            department = user.department,
            photo = user.photo,
            roles = user.roles,
            isOrgAdmin = user.isAdmin,

            // File paths
            userFolderPath = getUserDir(orgId, user.id).absolutePath,
            orgSharedPath = getOrgSharedDir(orgId).absolutePath,
            filesPath = getUserFilesDir(orgId, user.id).absolutePath
        )
    }

    /**
     * Serialize enriched claims to JSON
     */
    fun enrichedClaimsToJson(claims: EnrichedJwtClaims): String {
        return json.encodeToString(claims)
    }

    /**
     * Sync Keycloak principal and store tokens, returning enriched claims
     */
    fun syncFromKeycloakWithTokens(
        principal: Phase2Principal,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long,
        issuer: String? = null
    ): EnrichedJwtClaims? {
        val enriched = enrichJwtWithVCard(principal, issuer) ?: return null

        // Store tokens in user.vcf
        storeTokens(enriched.orgId, enriched.userId, accessToken, refreshToken, expiresAt)

        return enriched
    }

    // ===================
    // Helper Methods
    // ===================

    private fun vcardToOrg(orgId: String, vcard: VCard): OrgVCard {
        val revision = vcard.revision?.value?.time ?: System.currentTimeMillis()

        return OrgVCard(
            id = orgId,
            name = vcard.organization?.values?.firstOrNull() ?: "",
            displayName = vcard.formattedName?.value,
            email = vcard.emails.firstOrNull()?.value,
            phone = vcard.telephoneNumbers.firstOrNull()?.text,
            website = vcard.urls.firstOrNull()?.value,
            address = vcard.addresses.firstOrNull()?.let { addr ->
                AddressInfo(addr.streetAddress, addr.locality, addr.region, addr.postalCode, addr.country)
            },
            logo = vcard.logos.firstOrNull()?.data?.let { Base64.getEncoder().encodeToString(it) },
            notes = vcard.notes.firstOrNull()?.value,
            createdAt = revision,
            updatedAt = revision,
            memberCount = listUsers(orgId).size
        )
    }

    private fun vcardToUser(orgId: String, userId: String, vcard: VCard): UserVCard {
        val revision = vcard.revision?.value?.time ?: System.currentTimeMillis()

        val roles = vcard.getExtendedProperty("X-ROLES")?.value?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val isAdmin = vcard.getExtendedProperty("X-IS-ADMIN")?.value == "true"
        val lastLogin = vcard.getExtendedProperty("X-LAST-LOGIN")?.value?.toLongOrNull()

        val encodedAccess = vcard.getExtendedProperty("X-ACCESS-TOKEN")?.value
        val encodedRefresh = vcard.getExtendedProperty("X-REFRESH-TOKEN")?.value
        val tokenExpiresAt = vcard.getExtendedProperty("X-TOKEN-EXPIRES")?.value?.toLongOrNull()
        val tokenIssuedAt = vcard.getExtendedProperty("X-TOKEN-ISSUED")?.value?.toLongOrNull()

        val accessToken = encodedAccess?.let { try { String(Base64.getDecoder().decode(it)) } catch (e: Exception) { null } }
        val refreshToken = encodedRefresh?.let { try { String(Base64.getDecoder().decode(it)) } catch (e: Exception) { null } }

        return UserVCard(
            id = userId,
            organizationId = orgId,
            username = vcard.nicknames.firstOrNull()?.values?.firstOrNull() ?: "",
            email = vcard.emails.firstOrNull()?.value ?: "",
            fullName = vcard.formattedName?.value,
            givenName = vcard.structuredName?.given,
            familyName = vcard.structuredName?.family,
            phone = vcard.telephoneNumbers.firstOrNull()?.text,
            title = vcard.titles.firstOrNull()?.value,
            department = vcard.categories.firstOrNull()?.values?.firstOrNull(),
            photo = vcard.photos.firstOrNull()?.data?.let { Base64.getEncoder().encodeToString(it) },
            roles = roles,
            isAdmin = isAdmin,
            createdAt = revision,
            updatedAt = revision,
            lastLogin = lastLogin,
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenExpiresAt = tokenExpiresAt,
            tokenIssuedAt = tokenIssuedAt
        )
    }
}
