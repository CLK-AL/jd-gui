/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.mapping

import ezvcard.VCard
import ezvcard.property.*
import org.jd.gui.server.auth.*
import org.jd.gui.server.sync.JCardProperty
import org.mapstruct.*
import java.util.*

/**
 * MapStruct mapper for VCard data classes
 * Provides bidirectional mapping between ez-vcard VCard and our data classes
 */
@Mapper(
    componentModel = "default",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
abstract class VCardMapper {

    /**
     * Map ez-vcard VCard to OrgVCard data class
     */
    @Mapping(target = "id", expression = "java(extractUid(vcard))")
    @Mapping(target = "name", expression = "java(extractOrgName(vcard))")
    @Mapping(target = "displayName", expression = "java(extractFormattedName(vcard))")
    @Mapping(target = "email", expression = "java(extractEmail(vcard))")
    @Mapping(target = "phone", expression = "java(extractPhone(vcard))")
    @Mapping(target = "website", expression = "java(extractUrl(vcard))")
    @Mapping(target = "address", expression = "java(extractAddress(vcard))")
    @Mapping(target = "logo", ignore = true)
    @Mapping(target = "notes", expression = "java(extractNote(vcard))")
    @Mapping(target = "createdAt", expression = "java(System.currentTimeMillis())")
    @Mapping(target = "updatedAt", expression = "java(extractRevision(vcard))")
    @Mapping(target = "memberCount", constant = "0")
    @Mapping(target = "attributes", expression = "java(extractExtendedProperties(vcard))")
    abstract fun toOrgVCard(vcard: VCard): OrgVCard

    /**
     * Map ez-vcard VCard to UserVCard data class
     */
    @Mapping(target = "id", expression = "java(extractUid(vcard))")
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "username", expression = "java(extractUsername(vcard))")
    @Mapping(target = "email", expression = "java(extractEmail(vcard))")
    @Mapping(target = "fullName", expression = "java(extractFormattedName(vcard))")
    @Mapping(target = "givenName", expression = "java(extractGivenName(vcard))")
    @Mapping(target = "familyName", expression = "java(extractFamilyName(vcard))")
    @Mapping(target = "phone", expression = "java(extractPhone(vcard))")
    @Mapping(target = "title", expression = "java(extractTitle(vcard))")
    @Mapping(target = "department", expression = "java(extractDepartment(vcard))")
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "roles", expression = "java(extractRoles(vcard))")
    @Mapping(target = "isAdmin", expression = "java(extractIsAdmin(vcard))")
    @Mapping(target = "createdAt", expression = "java(System.currentTimeMillis())")
    @Mapping(target = "updatedAt", expression = "java(extractRevision(vcard))")
    @Mapping(target = "lastLogin", expression = "java(extractLastLogin(vcard))")
    @Mapping(target = "accessToken", expression = "java(extractAccessToken(vcard))")
    @Mapping(target = "refreshToken", expression = "java(extractRefreshToken(vcard))")
    @Mapping(target = "tokenExpiresAt", expression = "java(extractTokenExpires(vcard))")
    @Mapping(target = "tokenIssuedAt", expression = "java(extractTokenIssued(vcard))")
    abstract fun toUserVCard(vcard: VCard, organizationId: String): UserVCard

    /**
     * Map OrgVCard to ez-vcard VCard
     */
    fun toEzVCard(orgVCard: OrgVCard): VCard {
        val vcard = VCard()

        // Set KIND to org
        vcard.kind = Kind.ORG

        // UID
        vcard.uid = Uid(orgVCard.id)

        // Organization name
        vcard.setOrganization(orgVCard.name)

        // Formatted name (display name)
        orgVCard.displayName?.let { vcard.setFormattedName(it) }
            ?: vcard.setFormattedName(orgVCard.name)

        // Email
        orgVCard.email?.let { vcard.addEmail(it) }

        // Phone
        orgVCard.phone?.let { vcard.addTelephoneNumber(it) }

        // Website
        orgVCard.website?.let { vcard.addUrl(it) }

        // Address
        orgVCard.address?.let { addr ->
            val address = Address()
            address.streetAddress = addr.street
            address.locality = addr.city
            address.region = addr.state
            address.postalCode = addr.postalCode
            address.country = addr.country
            vcard.addAddress(address)
        }

        // Notes
        orgVCard.notes?.let { vcard.addNote(it) }

        // Extended properties
        orgVCard.attributes.forEach { (key, value) ->
            vcard.addExtendedProperty("X-$key", value)
        }

        // Revision
        vcard.revision = Revision(Date(orgVCard.updatedAt))

        return vcard
    }

    /**
     * Map UserVCard to ez-vcard VCard
     */
    fun toEzVCard(userVCard: UserVCard): VCard {
        val vcard = VCard()

        // Set KIND to individual
        vcard.kind = Kind.INDIVIDUAL

        // UID
        vcard.uid = Uid(userVCard.id)

        // Structured name
        val name = StructuredName()
        name.given = userVCard.givenName
        name.family = userVCard.familyName
        vcard.structuredName = name

        // Formatted name
        userVCard.fullName?.let { vcard.setFormattedName(it) }
            ?: vcard.setFormattedName("${userVCard.givenName ?: ""} ${userVCard.familyName ?: ""}".trim())

        // Organization
        vcard.setOrganization(userVCard.organizationId, userVCard.department ?: "")

        // Email
        vcard.addEmail(userVCard.email)

        // Phone
        userVCard.phone?.let { vcard.addTelephoneNumber(it) }

        // Title
        userVCard.title?.let { vcard.addTitle(it) }

        // Username as nickname
        vcard.addNickname(userVCard.username)

        // Roles
        if (userVCard.roles.isNotEmpty()) {
            vcard.addExtendedProperty("X-ROLES", userVCard.roles.joinToString(","))
        }

        // Admin flag
        if (userVCard.isAdmin) {
            vcard.addExtendedProperty("X-IS-ADMIN", "true")
        }

        // Last login
        userVCard.lastLogin?.let {
            vcard.addExtendedProperty("X-LAST-LOGIN", it.toString())
        }

        // OAuth tokens
        userVCard.accessToken?.let {
            vcard.addExtendedProperty("X-ACCESS-TOKEN", it)
        }
        userVCard.refreshToken?.let {
            vcard.addExtendedProperty("X-REFRESH-TOKEN", it)
        }
        userVCard.tokenExpiresAt?.let {
            vcard.addExtendedProperty("X-TOKEN-EXPIRES", it.toString())
        }
        userVCard.tokenIssuedAt?.let {
            vcard.addExtendedProperty("X-TOKEN-ISSUED", it.toString())
        }

        // Revision
        vcard.revision = Revision(Date(userVCard.updatedAt))

        return vcard
    }

    /**
     * Map VCard to flat Map<String, Any?> for JSON/jCard conversion
     */
    fun toMap(vcard: VCard): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        map["uid"] = extractUid(vcard)
        map["kind"] = vcard.kind?.value
        map["formattedName"] = extractFormattedName(vcard)
        map["organization"] = extractOrgName(vcard)
        map["email"] = extractEmail(vcard)
        map["phone"] = extractPhone(vcard)
        map["url"] = extractUrl(vcard)
        map["note"] = extractNote(vcard)
        map["title"] = extractTitle(vcard)
        map["revision"] = extractRevision(vcard)

        // Structured name
        vcard.structuredName?.let { name ->
            map["name.given"] = name.given
            map["name.family"] = name.family
            map["name.additional"] = name.additionalNames?.joinToString(",")
        }

        // Address
        vcard.addresses?.firstOrNull()?.let { addr ->
            map["address.street"] = addr.streetAddress
            map["address.city"] = addr.locality
            map["address.state"] = addr.region
            map["address.postalCode"] = addr.postalCode
            map["address.country"] = addr.country
        }

        // Extended properties
        vcard.extendedProperties?.forEach { prop ->
            map["x.${prop.propertyName}"] = prop.value
        }

        return map.filterValues { it != null }
    }

    /**
     * Map JCardProperty to ez-vcard property
     */
    fun jCardPropertyToVCardProperty(jcardProp: JCardProperty, vcard: VCard) {
        when (jcardProp.name.lowercase()) {
            "fn" -> vcard.setFormattedName(jcardProp.values.firstOrNull()?.toString())
            "n" -> {
                val name = StructuredName()
                val values = jcardProp.values
                if (values.isNotEmpty()) name.family = values[0]?.toString()
                if (values.size > 1) name.given = values[1]?.toString()
                if (values.size > 2) {
                    @Suppress("UNCHECKED_CAST")
                    (values[2] as? List<String>)?.forEach { name.additionalNames.add(it) }
                }
                vcard.structuredName = name
            }
            "email" -> vcard.addEmail(jcardProp.values.firstOrNull()?.toString() ?: "")
            "tel" -> vcard.addTelephoneNumber(jcardProp.values.firstOrNull()?.toString() ?: "")
            "org" -> {
                val values = jcardProp.values.map { it?.toString() ?: "" }
                vcard.setOrganization(*values.toTypedArray())
            }
            "title" -> vcard.addTitle(jcardProp.values.firstOrNull()?.toString() ?: "")
            "note" -> vcard.addNote(jcardProp.values.firstOrNull()?.toString() ?: "")
            "url" -> vcard.addUrl(jcardProp.values.firstOrNull()?.toString() ?: "")
            "uid" -> vcard.uid = Uid(jcardProp.values.firstOrNull()?.toString() ?: UUID.randomUUID().toString())
        }
    }

    // Helper extraction methods

    protected fun extractUid(vcard: VCard): String =
        vcard.uid?.value ?: UUID.randomUUID().toString()

    protected fun extractOrgName(vcard: VCard): String =
        vcard.organization?.values?.firstOrNull() ?: ""

    protected fun extractFormattedName(vcard: VCard): String? =
        vcard.formattedName?.value

    protected fun extractEmail(vcard: VCard): String =
        vcard.emails?.firstOrNull()?.value ?: ""

    protected fun extractPhone(vcard: VCard): String? =
        vcard.telephoneNumbers?.firstOrNull()?.text

    protected fun extractUrl(vcard: VCard): String? =
        vcard.urls?.firstOrNull()?.value

    protected fun extractNote(vcard: VCard): String? =
        vcard.notes?.firstOrNull()?.value

    protected fun extractTitle(vcard: VCard): String? =
        vcard.titles?.firstOrNull()?.value

    protected fun extractDepartment(vcard: VCard): String? =
        vcard.organization?.values?.getOrNull(1)

    protected fun extractGivenName(vcard: VCard): String? =
        vcard.structuredName?.given

    protected fun extractFamilyName(vcard: VCard): String? =
        vcard.structuredName?.family

    protected fun extractUsername(vcard: VCard): String =
        vcard.nicknames?.firstOrNull()?.values?.firstOrNull()
            ?: extractEmail(vcard).substringBefore("@")

    protected fun extractRevision(vcard: VCard): Long =
        vcard.revision?.value?.time ?: System.currentTimeMillis()

    protected fun extractAddress(vcard: VCard): AddressInfo? {
        val addr = vcard.addresses?.firstOrNull() ?: return null
        return AddressInfo(
            street = addr.streetAddress,
            city = addr.locality,
            state = addr.region,
            postalCode = addr.postalCode,
            country = addr.country
        )
    }

    protected fun extractExtendedProperties(vcard: VCard): Map<String, String> =
        vcard.extendedProperties
            ?.filter { !it.propertyName.startsWith("X-ACCESS") && !it.propertyName.startsWith("X-REFRESH") && !it.propertyName.startsWith("X-TOKEN") }
            ?.associate { it.propertyName.removePrefix("X-") to it.value }
            ?: emptyMap()

    protected fun extractRoles(vcard: VCard): List<String> =
        vcard.extendedProperties
            ?.find { it.propertyName == "X-ROLES" }
            ?.value?.split(",")?.filter { it.isNotBlank() }
            ?: emptyList()

    protected fun extractIsAdmin(vcard: VCard): Boolean =
        vcard.extendedProperties
            ?.find { it.propertyName == "X-IS-ADMIN" }
            ?.value == "true"

    protected fun extractLastLogin(vcard: VCard): Long? =
        vcard.extendedProperties
            ?.find { it.propertyName == "X-LAST-LOGIN" }
            ?.value?.toLongOrNull()

    protected fun extractAccessToken(vcard: VCard): String? =
        vcard.extendedProperties?.find { it.propertyName == "X-ACCESS-TOKEN" }?.value

    protected fun extractRefreshToken(vcard: VCard): String? =
        vcard.extendedProperties?.find { it.propertyName == "X-REFRESH-TOKEN" }?.value

    protected fun extractTokenExpires(vcard: VCard): Long? =
        vcard.extendedProperties
            ?.find { it.propertyName == "X-TOKEN-EXPIRES" }
            ?.value?.toLongOrNull()

    protected fun extractTokenIssued(vcard: VCard): Long? =
        vcard.extendedProperties
            ?.find { it.propertyName == "X-TOKEN-ISSUED" }
            ?.value?.toLongOrNull()

    companion object {
        val INSTANCE: VCardMapper = VCardMapperImpl()
    }
}

/**
 * Manual implementation of VCardMapper for Kotlin
 * MapStruct generates Java code, so we provide a Kotlin-friendly implementation
 */
class VCardMapperImpl : VCardMapper() {

    override fun toOrgVCard(vcard: VCard): OrgVCard {
        return OrgVCard(
            id = extractUid(vcard),
            name = extractOrgName(vcard),
            displayName = extractFormattedName(vcard),
            email = extractEmail(vcard).ifEmpty { null },
            phone = extractPhone(vcard),
            website = extractUrl(vcard),
            address = extractAddress(vcard),
            logo = null,
            notes = extractNote(vcard),
            createdAt = System.currentTimeMillis(),
            updatedAt = extractRevision(vcard),
            memberCount = 0,
            attributes = extractExtendedProperties(vcard)
        )
    }

    override fun toUserVCard(vcard: VCard, organizationId: String): UserVCard {
        return UserVCard(
            id = extractUid(vcard),
            organizationId = organizationId,
            username = extractUsername(vcard),
            email = extractEmail(vcard),
            fullName = extractFormattedName(vcard),
            givenName = extractGivenName(vcard),
            familyName = extractFamilyName(vcard),
            phone = extractPhone(vcard),
            title = extractTitle(vcard),
            department = extractDepartment(vcard),
            photo = null,
            roles = extractRoles(vcard),
            isAdmin = extractIsAdmin(vcard),
            createdAt = System.currentTimeMillis(),
            updatedAt = extractRevision(vcard),
            lastLogin = extractLastLogin(vcard),
            accessToken = extractAccessToken(vcard),
            refreshToken = extractRefreshToken(vcard),
            tokenExpiresAt = extractTokenExpires(vcard),
            tokenIssuedAt = extractTokenIssued(vcard)
        )
    }
}

/**
 * Extension functions for easy mapping
 */
fun VCard.toOrgVCard(): OrgVCard = VCardMapper.INSTANCE.toOrgVCard(this)

fun VCard.toUserVCard(organizationId: String): UserVCard = VCardMapper.INSTANCE.toUserVCard(this, organizationId)

fun OrgVCard.toEzVCard(): VCard = VCardMapper.INSTANCE.toEzVCard(this)

fun UserVCard.toEzVCard(): VCard = VCardMapper.INSTANCE.toEzVCard(this)

fun VCard.toMap(): Map<String, Any?> = VCardMapper.INSTANCE.toMap(this)
