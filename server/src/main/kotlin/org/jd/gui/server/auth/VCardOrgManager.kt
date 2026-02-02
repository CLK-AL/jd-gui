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
import mu.KotlinLogging
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
 * VCard-based organization and user manager.
 * Uses ez-vcard library to store org/user data as vCard files.
 *
 * Directory structure:
 * /data/vcards/
 *   ├── orgs/
 *   │   ├── {org-uuid}.vcf        # Organization vCard (KIND:org)
 *   │   └── ...
 *   └── users/
 *       ├── {org-uuid}/
 *       │   ├── {user-uuid}.vcf   # User vCard (KIND:individual)
 *       │   └── ...
 *       └── ...
 */
class VCardOrgManager(private val basePath: String) {

    private val orgsDir = File(basePath, "vcards/orgs")
    private val usersDir = File(basePath, "vcards/users")

    init {
        orgsDir.mkdirs()
        usersDir.mkdirs()
        logger.info { "VCard manager initialized at $basePath" }
    }

    // ===================
    // Organization Methods
    // ===================

    /**
     * Create a new organization vCard
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

        val vcard = VCard().apply {
            // Set KIND to org
            kind = Kind.org()

            // Unique ID
            uid = Uid(orgId)

            // Organization name
            setFormattedName(displayName ?: name)
            organization = Organization().apply {
                values.add(name)
            }

            // Contact info
            email?.let {
                addEmail(Email(it))
            }

            phone?.let {
                addTelephoneNumber(Telephone(it).apply {
                    types.add(TelephoneType.WORK)
                })
            }

            website?.let {
                addUrl(Url(it))
            }

            address?.let { addr ->
                addAddress(Address().apply {
                    streetAddress = addr.street
                    locality = addr.city
                    region = addr.state
                    postalCode = addr.postalCode
                    country = addr.country
                })
            }

            notes?.let {
                addNote(Note(it))
            }

            // Timestamps
            revision = Revision(Date.from(Instant.ofEpochMilli(now)))
        }

        // Save to file
        val file = File(orgsDir, "$orgId.vcf")
        Ezvcard.write(vcard).version(VCardVersion.V4_0).go(file)

        logger.info { "Created organization: $name ($orgId)" }

        return OrgVCard(
            id = orgId,
            name = name,
            displayName = displayName,
            email = email,
            phone = phone,
            website = website,
            address = address,
            logo = null,
            notes = notes,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Get organization by ID
     */
    fun getOrganization(orgId: String): OrgVCard? {
        val file = File(orgsDir, "$orgId.vcf")
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
     * List all organizations
     */
    fun listOrganizations(): List<OrgVCard> {
        return orgsDir.listFiles { f -> f.extension == "vcf" }
            ?.mapNotNull { file ->
                val orgId = file.nameWithoutExtension
                getOrganization(orgId)
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Update organization
     */
    fun updateOrganization(orgId: String, updates: Map<String, String?>): OrgVCard? {
        val file = File(orgsDir, "$orgId.vcf")
        if (!file.exists()) return null

        val vcard = Ezvcard.parse(file).first()

        updates["name"]?.let { vcard.organization?.values?.set(0, it) }
        updates["displayName"]?.let { vcard.setFormattedName(it) }
        updates["email"]?.let {
            vcard.emails.clear()
            vcard.addEmail(Email(it))
        }
        updates["phone"]?.let {
            vcard.telephoneNumbers.clear()
            vcard.addTelephoneNumber(Telephone(it))
        }
        updates["website"]?.let {
            vcard.urls.clear()
            vcard.addUrl(Url(it))
        }
        updates["notes"]?.let {
            vcard.notes.clear()
            vcard.addNote(Note(it))
        }

        vcard.revision = Revision(Date())

        Ezvcard.write(vcard).version(VCardVersion.V4_0).go(file)

        return getOrganization(orgId)
    }

    /**
     * Delete organization and all its users
     */
    fun deleteOrganization(orgId: String): Boolean {
        val orgFile = File(orgsDir, "$orgId.vcf")
        val userDir = File(usersDir, orgId)

        if (userDir.exists()) {
            userDir.deleteRecursively()
        }

        return orgFile.delete()
    }

    // ===================
    // User Methods
    // ===================

    /**
     * Create a new user vCard in organization
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
        // Check org exists
        if (getOrganization(organizationId) == null) {
            logger.warn { "Organization not found: $organizationId" }
            return null
        }

        val userId = UUID.randomUUID().toString()
        val now = Instant.now().toEpochMilli()

        val vcard = VCard().apply {
            // Set KIND to individual
            kind = Kind.individual()

            // Unique ID
            uid = Uid(userId)

            // Names
            structuredName = StructuredName().apply {
                given = givenName
                family = familyName
            }
            setFormattedName(fullName ?: "$givenName $familyName".trim().ifEmpty { username })

            // Nickname (username)
            addNickname(Nickname(username))

            // Email
            addEmail(Email(email))

            // Phone
            phone?.let {
                addTelephoneNumber(Telephone(it))
            }

            // Organization and title
            organization = Organization().apply {
                values.add(organizationId)
            }
            title?.let { addTitle(Title(it)) }

            // Department as category
            department?.let {
                addCategories(Categories(it))
            }

            // Roles as extended property
            if (roles.isNotEmpty()) {
                addExtendedProperty(RawProperty("X-ROLES", roles.joinToString(",")))
            }
            if (isAdmin) {
                addExtendedProperty(RawProperty("X-IS-ADMIN", "true"))
            }

            // Timestamps
            revision = Revision(Date.from(Instant.ofEpochMilli(now)))
        }

        // Save to file
        val userOrgDir = File(usersDir, organizationId)
        userOrgDir.mkdirs()
        val file = File(userOrgDir, "$userId.vcf")
        Ezvcard.write(vcard).version(VCardVersion.V4_0).go(file)

        logger.info { "Created user: $username ($userId) in org $organizationId" }

        return UserVCard(
            id = userId,
            organizationId = organizationId,
            username = username,
            email = email,
            fullName = fullName,
            givenName = givenName,
            familyName = familyName,
            phone = phone,
            title = title,
            department = department,
            photo = null,
            roles = roles,
            isAdmin = isAdmin,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Get user by ID
     */
    fun getUser(organizationId: String, userId: String): UserVCard? {
        val file = File(usersDir, "$organizationId/$userId.vcf")
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
        usersDir.listFiles()?.forEach { orgDir ->
            if (orgDir.isDirectory) {
                orgDir.listFiles { f -> f.extension == "vcf" }?.forEach { file ->
                    try {
                        val vcard = Ezvcard.parse(file).first()
                        if (vcard.emails.any { it.value.equals(email, ignoreCase = true) }) {
                            val userId = file.nameWithoutExtension
                            val orgId = orgDir.name
                            return vcardToUser(orgId, userId, vcard)
                        }
                    } catch (e: Exception) {
                        // Skip invalid vcards
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
        val userOrgDir = File(usersDir, organizationId)
        if (!userOrgDir.exists()) return emptyList()

        return userOrgDir.listFiles { f -> f.extension == "vcf" }
            ?.mapNotNull { file ->
                val userId = file.nameWithoutExtension
                getUser(organizationId, userId)
            }
            ?.sortedBy { it.username }
            ?: emptyList()
    }

    /**
     * Update user
     */
    fun updateUser(organizationId: String, userId: String, updates: Map<String, Any?>): UserVCard? {
        val file = File(usersDir, "$organizationId/$userId.vcf")
        if (!file.exists()) return null

        val vcard = Ezvcard.parse(file).first()

        updates["email"]?.let {
            vcard.emails.clear()
            vcard.addEmail(Email(it.toString()))
        }
        updates["fullName"]?.let { vcard.setFormattedName(it.toString()) }
        updates["phone"]?.let {
            vcard.telephoneNumbers.clear()
            vcard.addTelephoneNumber(Telephone(it.toString()))
        }
        updates["title"]?.let {
            vcard.titles.clear()
            vcard.addTitle(Title(it.toString()))
        }

        @Suppress("UNCHECKED_CAST")
        (updates["roles"] as? List<String>)?.let { roles ->
            vcard.extendedProperties.removeIf { it.propertyName == "X-ROLES" }
            vcard.addExtendedProperty(RawProperty("X-ROLES", roles.joinToString(",")))
        }

        updates["isAdmin"]?.let { isAdmin ->
            vcard.extendedProperties.removeIf { it.propertyName == "X-IS-ADMIN" }
            if (isAdmin == true) {
                vcard.addExtendedProperty(RawProperty("X-IS-ADMIN", "true"))
            }
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
     * Delete user
     */
    fun deleteUser(organizationId: String, userId: String): Boolean {
        val file = File(usersDir, "$organizationId/$userId.vcf")
        return file.delete()
    }

    /**
     * Sync user from Keycloak principal
     */
    fun syncFromKeycloak(principal: Phase2Principal): UserVCard? {
        val orgId = principal.activeOrganization?.id ?: principal.defaultOrganization ?: return null

        // Check if user exists
        var user = findUserByEmail(principal.email ?: return null)

        if (user == null) {
            // Create new user
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

        return user
    }

    // ===================
    // Token Management
    // ===================

    /**
     * Store OAuth tokens in user's vCard
     * Tokens are Base64 encoded for safe storage in vCard format
     */
    fun storeTokens(
        organizationId: String,
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long
    ): Boolean {
        val file = File(usersDir, "$organizationId/$userId.vcf")
        if (!file.exists()) return false

        return try {
            val vcard = Ezvcard.parse(file).first()
            val now = Instant.now().toEpochMilli()

            // Encode tokens as Base64 for safe storage
            val encodedAccess = Base64.getEncoder().encodeToString(accessToken.toByteArray())
            val encodedRefresh = refreshToken?.let { Base64.getEncoder().encodeToString(it.toByteArray()) }

            // Remove existing token properties
            vcard.extendedProperties.removeIf {
                it.propertyName in listOf("X-ACCESS-TOKEN", "X-REFRESH-TOKEN", "X-TOKEN-EXPIRES", "X-TOKEN-ISSUED")
            }

            // Add new token properties
            vcard.addExtendedProperty(RawProperty("X-ACCESS-TOKEN", encodedAccess))
            encodedRefresh?.let { vcard.addExtendedProperty(RawProperty("X-REFRESH-TOKEN", it)) }
            vcard.addExtendedProperty(RawProperty("X-TOKEN-EXPIRES", expiresAt.toString()))
            vcard.addExtendedProperty(RawProperty("X-TOKEN-ISSUED", now.toString()))

            vcard.revision = Revision(Date())
            Ezvcard.write(vcard).version(VCardVersion.V4_0).go(file)

            logger.debug { "Stored tokens for user $userId in org $organizationId" }
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
        val file = File(usersDir, "$organizationId/$userId.vcf")
        if (!file.exists()) return null

        return try {
            val vcard = Ezvcard.parse(file).first()

            val encodedAccess = vcard.getExtendedProperty("X-ACCESS-TOKEN")?.value ?: return null
            val encodedRefresh = vcard.getExtendedProperty("X-REFRESH-TOKEN")?.value
            val expiresAt = vcard.getExtendedProperty("X-TOKEN-EXPIRES")?.value?.toLongOrNull() ?: return null
            val issuedAt = vcard.getExtendedProperty("X-TOKEN-ISSUED")?.value?.toLongOrNull()

            // Decode tokens
            val accessToken = String(Base64.getDecoder().decode(encodedAccess))
            val refreshToken = encodedRefresh?.let { String(Base64.getDecoder().decode(it)) }

            TokenInfo(
                accessToken = accessToken,
                refreshToken = refreshToken,
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
        val file = File(usersDir, "$organizationId/$userId.vcf")
        if (!file.exists()) return false

        return try {
            val vcard = Ezvcard.parse(file).first()

            vcard.extendedProperties.removeIf {
                it.propertyName in listOf("X-ACCESS-TOKEN", "X-REFRESH-TOKEN", "X-TOKEN-EXPIRES", "X-TOKEN-ISSUED")
            }

            vcard.revision = Revision(Date())
            Ezvcard.write(vcard).version(VCardVersion.V4_0).go(file)

            logger.debug { "Cleared tokens for user $userId in org $organizationId" }
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

    /**
     * Sync tokens from Keycloak principal
     */
    fun syncTokensFromPrincipal(principal: Phase2Principal, expiresAt: Long): Boolean {
        val orgId = principal.activeOrganization?.id ?: principal.defaultOrganization ?: return false
        val user = findUserByEmail(principal.email ?: return false) ?: return false

        // Store the raw token from the principal
        return storeTokens(
            organizationId = orgId,
            userId = user.id,
            accessToken = principal.rawToken,
            refreshToken = null, // Refresh token would come from token response
            expiresAt = expiresAt
        )
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
                AddressInfo(
                    street = addr.streetAddress,
                    city = addr.locality,
                    state = addr.region,
                    postalCode = addr.postalCode,
                    country = addr.country
                )
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

        val roles = vcard.getExtendedProperty("X-ROLES")?.value
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val isAdmin = vcard.getExtendedProperty("X-IS-ADMIN")?.value == "true"

        val lastLogin = vcard.getExtendedProperty("X-LAST-LOGIN")?.value?.toLongOrNull()

        // Extract token info
        val encodedAccess = vcard.getExtendedProperty("X-ACCESS-TOKEN")?.value
        val encodedRefresh = vcard.getExtendedProperty("X-REFRESH-TOKEN")?.value
        val tokenExpiresAt = vcard.getExtendedProperty("X-TOKEN-EXPIRES")?.value?.toLongOrNull()
        val tokenIssuedAt = vcard.getExtendedProperty("X-TOKEN-ISSUED")?.value?.toLongOrNull()

        val accessToken = encodedAccess?.let {
            try { String(Base64.getDecoder().decode(it)) } catch (e: Exception) { null }
        }
        val refreshToken = encodedRefresh?.let {
            try { String(Base64.getDecoder().decode(it)) } catch (e: Exception) { null }
        }

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
