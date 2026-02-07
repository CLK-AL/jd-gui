/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.server.mapping

import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.*
import ezvcard.property.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mu.KotlinLogging
import org.mapstruct.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * RFC 6350 vCard 4.0 Property Mapping
 * Maps ez-vcard properties to service-specific formats:
 * - Keycloak: User attributes and claims
 * - Openfire: XMPP vCard-temp (XEP-0054) and vCard4 (XEP-0292)
 * - Matrix: Profile and account data
 * - Bedework: CardDAV vCard
 *
 * RFC 6350 Properties supported:
 * - Identification: FN, N, NICKNAME, PHOTO, BDAY, ANNIVERSARY, GENDER
 * - Delivery Addressing: ADR
 * - Communications: TEL, EMAIL, IMPP, LANG
 * - Geographical: TZ, GEO
 * - Organizational: TITLE, ROLE, LOGO, ORG, MEMBER, RELATED
 * - Explanatory: CATEGORIES, NOTE, PRODID, REV, SOUND, UID, CLIENTPIDMAP, URL
 * - Security: KEY
 * - Calendar: FBURL, CALADRURI, CALURI
 * - Extended: X-* properties
 */
@Mapper(componentModel = "default")
abstract class RfcVCardMapper {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // =========================================================================
    // RFC 6350 Property to Map<String, Any?> Conversion
    // =========================================================================

    /**
     * Convert full VCard to RFC-compliant Map using ez-vcard as single source
     */
    fun toRfcMap(vcard: VCard): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        // === GENERAL PROPERTIES ===
        map["VERSION"] = vcard.version?.version ?: "4.0"
        map["KIND"] = vcard.kind?.value
        map["SOURCE"] = vcard.sources?.map { it.value }
        map["XML"] = vcard.xmls?.map { it.value }

        // === IDENTIFICATION PROPERTIES (RFC 6350 Section 6.2) ===
        map["FN"] = vcard.formattedName?.value
        map["N"] = vcard.structuredName?.let { mapStructuredName(it) }
        map["NICKNAME"] = vcard.nicknames?.flatMap { it.values }
        map["PHOTO"] = vcard.photos?.map { mapPhoto(it) }
        map["BDAY"] = vcard.birthday?.let { mapDateOrDateTime(it) }
        map["ANNIVERSARY"] = vcard.anniversary?.let { mapDateOrDateTime(it) }
        map["GENDER"] = vcard.gender?.let { mapGender(it) }

        // === DELIVERY ADDRESSING PROPERTIES (RFC 6350 Section 6.3) ===
        map["ADR"] = vcard.addresses?.map { mapAddress(it) }

        // === COMMUNICATIONS PROPERTIES (RFC 6350 Section 6.4) ===
        map["TEL"] = vcard.telephoneNumbers?.map { mapTelephone(it) }
        map["EMAIL"] = vcard.emails?.map { mapEmail(it) }
        map["IMPP"] = vcard.impps?.map { mapImpp(it) }
        map["LANG"] = vcard.languages?.map { mapLanguage(it) }

        // === GEOGRAPHICAL PROPERTIES (RFC 6350 Section 6.5) ===
        map["TZ"] = vcard.timezones?.map { it.text ?: it.offset?.toString() }
        map["GEO"] = vcard.geos?.map { mapGeo(it) }

        // === ORGANIZATIONAL PROPERTIES (RFC 6350 Section 6.6) ===
        map["TITLE"] = vcard.titles?.map { it.value }
        map["ROLE"] = vcard.roles?.map { it.value }
        map["LOGO"] = vcard.logos?.map { mapLogo(it) }
        map["ORG"] = vcard.organization?.let { mapOrganization(it) }
        map["MEMBER"] = vcard.members?.map { it.uri }
        map["RELATED"] = vcard.relations?.map { mapRelated(it) }

        // === EXPLANATORY PROPERTIES (RFC 6350 Section 6.7) ===
        map["CATEGORIES"] = vcard.categories?.values
        map["NOTE"] = vcard.notes?.map { it.value }
        map["PRODID"] = vcard.productId?.value
        map["REV"] = vcard.revision?.let { mapRevision(it) }
        map["SOUND"] = vcard.sounds?.map { mapSound(it) }
        map["UID"] = vcard.uid?.value
        map["CLIENTPIDMAP"] = vcard.clientPidMaps?.map { "${it.pid}:${it.uri}" }
        map["URL"] = vcard.urls?.map { mapUrl(it) }

        // === SECURITY PROPERTIES (RFC 6350 Section 6.8) ===
        map["KEY"] = vcard.keys?.map { mapKey(it) }

        // === CALENDAR PROPERTIES (RFC 6350 Section 6.9) ===
        map["FBURL"] = vcard.fbUrls?.map { it.value }
        map["CALADRURI"] = vcard.calendarRequestUris?.map { it.value }
        map["CALURI"] = vcard.calendarUris?.map { it.value }

        // === EXTENDED PROPERTIES ===
        vcard.extendedProperties?.forEach { prop ->
            map["X-${prop.propertyName.removePrefix("X-")}"] = prop.value
        }

        return map.filterValues { it != null && it != emptyList<Any>() }
    }

    // =========================================================================
    // Service-Specific Mappers
    // =========================================================================

    /**
     * Map VCard to Keycloak user attributes
     * Keycloak stores custom attributes as string lists
     */
    fun toKeycloakAttributes(vcard: VCard): Map<String, List<String>> {
        val attrs = mutableMapOf<String, List<String>>()

        // Standard Keycloak attributes
        vcard.formattedName?.value?.let { attrs["displayName"] = listOf(it) }
        vcard.structuredName?.given?.let { attrs["firstName"] = listOf(it) }
        vcard.structuredName?.family?.let { attrs["lastName"] = listOf(it) }
        vcard.emails?.firstOrNull()?.value?.let { attrs["email"] = listOf(it) }
        vcard.telephoneNumbers?.firstOrNull()?.text?.let { attrs["phoneNumber"] = listOf(it) }

        // Extended vCard attributes for Keycloak
        vcard.titles?.firstOrNull()?.value?.let { attrs["title"] = listOf(it) }
        vcard.organization?.values?.let { attrs["organization"] = it }
        vcard.roles?.map { it.value }?.let { if (it.isNotEmpty()) attrs["roles"] = it }
        vcard.addresses?.firstOrNull()?.let { addr ->
            attrs["street"] = listOfNotNull(addr.streetAddress)
            attrs["locality"] = listOfNotNull(addr.locality)
            attrs["region"] = listOfNotNull(addr.region)
            attrs["postalCode"] = listOfNotNull(addr.postalCode)
            attrs["country"] = listOfNotNull(addr.country)
        }

        // Photo as URL or base64
        vcard.photos?.firstOrNull()?.let { photo ->
            photo.url?.let { attrs["picture"] = listOf(it) }
        }

        // Birthday
        vcard.birthday?.date?.let {
            attrs["birthdate"] = listOf(it.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString())
        }

        // Locale/Language
        vcard.languages?.firstOrNull()?.value?.let { attrs["locale"] = listOf(it) }

        // Timezone
        vcard.timezones?.firstOrNull()?.let { tz ->
            (tz.text ?: tz.offset?.toString())?.let { attrs["timezone"] = listOf(it) }
        }

        // Extended properties
        vcard.extendedProperties?.forEach { prop ->
            val key = prop.propertyName.removePrefix("X-").lowercase()
            attrs[key] = listOf(prop.value)
        }

        return attrs.filterValues { it.isNotEmpty() && it.all { v -> v.isNotEmpty() } }
    }

    /**
     * Map VCard to Openfire XMPP vCard (XEP-0054 vCard-temp)
     * Returns XML structure as Map for Smack VCard
     */
    fun toOpenfireVCard(vcard: VCard): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        // vCard-temp mapping (XEP-0054)
        map["FN"] = vcard.formattedName?.value

        vcard.structuredName?.let { n ->
            map["N"] = mapOf(
                "FAMILY" to n.family,
                "GIVEN" to n.given,
                "MIDDLE" to n.additionalNames?.joinToString(" "),
                "PREFIX" to n.prefixes?.joinToString(" "),
                "SUFFIX" to n.suffixes?.joinToString(" ")
            )
        }

        map["NICKNAME"] = vcard.nicknames?.firstOrNull()?.values?.firstOrNull()

        vcard.emails?.forEach { email ->
            val type = if (email.types?.contains(EmailType.WORK) == true) "WORK" else "HOME"
            map["EMAIL_$type"] = email.value
        }

        vcard.telephoneNumbers?.forEach { tel ->
            val type = when {
                tel.types?.contains(TelephoneType.WORK) == true -> "WORK"
                tel.types?.contains(TelephoneType.CELL) == true -> "CELL"
                tel.types?.contains(TelephoneType.FAX) == true -> "FAX"
                else -> "HOME"
            }
            map["TEL_$type"] = tel.text
        }

        vcard.addresses?.firstOrNull()?.let { addr ->
            map["ADR"] = mapOf(
                "STREET" to addr.streetAddress,
                "LOCALITY" to addr.locality,
                "REGION" to addr.region,
                "PCODE" to addr.postalCode,
                "CTRY" to addr.country
            )
        }

        map["ORG"] = mapOf(
            "ORGNAME" to vcard.organization?.values?.firstOrNull(),
            "ORGUNIT" to vcard.organization?.values?.getOrNull(1)
        )

        map["TITLE"] = vcard.titles?.firstOrNull()?.value
        map["ROLE"] = vcard.roles?.firstOrNull()?.value
        map["URL"] = vcard.urls?.firstOrNull()?.value
        map["DESC"] = vcard.notes?.firstOrNull()?.value
        map["UID"] = vcard.uid?.value

        vcard.photos?.firstOrNull()?.let { photo ->
            map["PHOTO"] = mapOf(
                "TYPE" to photo.contentType?.mediaType,
                "BINVAL" to photo.data?.let { Base64.getEncoder().encodeToString(it) },
                "EXTVAL" to photo.url
            )
        }

        vcard.birthday?.date?.let {
            map["BDAY"] = it.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString()
        }

        return map.filterValues { it != null }
    }

    /**
     * Map VCard to Matrix profile data
     * Matrix uses specific event types for profile information
     */
    fun toMatrixProfile(vcard: VCard): MatrixProfileData {
        return MatrixProfileData(
            displayname = vcard.formattedName?.value,
            avatar_url = vcard.photos?.firstOrNull()?.url,
            // Extended profile data stored in account_data
            accountData = mapOf(
                "m.profile" to buildJsonObject {
                    vcard.structuredName?.let { n ->
                        put("first_name", n.given ?: "")
                        put("last_name", n.family ?: "")
                    }
                    vcard.emails?.firstOrNull()?.value?.let { put("email", it) }
                    vcard.telephoneNumbers?.firstOrNull()?.text?.let { put("phone", it) }
                    vcard.organization?.values?.firstOrNull()?.let { put("organization", it) }
                    vcard.titles?.firstOrNull()?.value?.let { put("title", it) }
                    vcard.timezones?.firstOrNull()?.let { tz ->
                        (tz.text ?: tz.offset?.toString())?.let { put("timezone", it) }
                    }
                    vcard.urls?.firstOrNull()?.value?.let { put("website", it) }
                    vcard.notes?.firstOrNull()?.value?.let { put("bio", it) }
                },
                "m.vcard" to buildJsonObject {
                    // Store full vCard as JSON for Matrix
                    toRfcMap(vcard).forEach { (key, value) ->
                        when (value) {
                            is String -> put(key, value)
                            is Number -> put(key, value)
                            is Boolean -> put(key, value)
                            is List<*> -> put(key, buildJsonArray {
                                value.filterNotNull().forEach { add(JsonPrimitive(it.toString())) }
                            })
                            is Map<*, *> -> put(key, buildJsonObject {
                                @Suppress("UNCHECKED_CAST")
                                (value as Map<String, Any?>).forEach { (k, v) ->
                                    when (v) {
                                        is String -> put(k, v)
                                        is Number -> put(k, v)
                                        else -> put(k, v?.toString() ?: "")
                                    }
                                }
                            })
                        }
                    }
                }
            )
        )
    }

    /**
     * Map VCard to Bedework CardDAV format
     * Bedework uses standard vCard 4.0, so we just ensure RFC compliance
     */
    fun toBedeworkVCard(vcard: VCard): VCard {
        // Clone and ensure vCard 4.0 compliance
        val bedeworkVCard = VCard(vcard)

        // Ensure required properties
        if (bedeworkVCard.uid == null) {
            bedeworkVCard.uid = Uid(UUID.randomUUID().toString())
        }

        if (bedeworkVCard.formattedName == null) {
            val fn = buildString {
                vcard.structuredName?.let { n ->
                    n.prefixes?.forEach { append("$it ") }
                    n.given?.let { append("$it ") }
                    n.additionalNames?.forEach { append("$it ") }
                    n.family?.let { append(it) }
                    n.suffixes?.forEach { append(" $it") }
                }
            }.trim()
            if (fn.isNotEmpty()) {
                bedeworkVCard.setFormattedName(fn)
            }
        }

        // Update revision
        bedeworkVCard.revision = Revision(Date())

        return bedeworkVCard
    }

    // =========================================================================
    // Reverse Mappers (Service to VCard)
    // =========================================================================

    /**
     * Create VCard from Keycloak attributes
     */
    fun fromKeycloakAttributes(attrs: Map<String, List<String>>, userId: String): VCard {
        val vcard = VCard()
        vcard.version = VCardVersion.V4_0
        vcard.kind = Kind.INDIVIDUAL
        vcard.uid = Uid(userId)

        attrs["displayName"]?.firstOrNull()?.let { vcard.setFormattedName(it) }

        val name = StructuredName()
        attrs["firstName"]?.firstOrNull()?.let { name.given = it }
        attrs["lastName"]?.firstOrNull()?.let { name.family = it }
        if (name.given != null || name.family != null) {
            vcard.structuredName = name
        }

        attrs["email"]?.firstOrNull()?.let { vcard.addEmail(it) }
        attrs["phoneNumber"]?.firstOrNull()?.let { vcard.addTelephoneNumber(it) }
        attrs["title"]?.firstOrNull()?.let { vcard.addTitle(it) }
        attrs["organization"]?.let { if (it.isNotEmpty()) vcard.setOrganization(*it.toTypedArray()) }

        // Address
        val hasAddress = listOf("street", "locality", "region", "postalCode", "country")
            .any { attrs[it]?.firstOrNull()?.isNotEmpty() == true }
        if (hasAddress) {
            val addr = Address()
            attrs["street"]?.firstOrNull()?.let { addr.streetAddress = it }
            attrs["locality"]?.firstOrNull()?.let { addr.locality = it }
            attrs["region"]?.firstOrNull()?.let { addr.region = it }
            attrs["postalCode"]?.firstOrNull()?.let { addr.postalCode = it }
            attrs["country"]?.firstOrNull()?.let { addr.country = it }
            vcard.addAddress(addr)
        }

        attrs["picture"]?.firstOrNull()?.let {
            val photo = Photo(it, null)
            vcard.addPhoto(photo)
        }

        attrs["locale"]?.firstOrNull()?.let { vcard.addLanguage(it) }
        attrs["timezone"]?.firstOrNull()?.let { vcard.addTimezone(it) }

        vcard.revision = Revision(Date())
        return vcard
    }

    /**
     * Create VCard from Matrix profile data
     */
    fun fromMatrixProfile(profile: MatrixProfileData, userId: String): VCard {
        val vcard = VCard()
        vcard.version = VCardVersion.V4_0
        vcard.kind = Kind.INDIVIDUAL
        vcard.uid = Uid(userId)

        profile.displayname?.let { vcard.setFormattedName(it) }
        profile.avatar_url?.let { vcard.addPhoto(Photo(it, null)) }

        // Parse extended profile from account_data
        profile.accountData["m.profile"]?.let { profileJson ->
            if (profileJson is JsonObject) {
                val name = StructuredName()
                profileJson["first_name"]?.jsonPrimitive?.contentOrNull?.let { name.given = it }
                profileJson["last_name"]?.jsonPrimitive?.contentOrNull?.let { name.family = it }
                if (name.given != null || name.family != null) {
                    vcard.structuredName = name
                }

                profileJson["email"]?.jsonPrimitive?.contentOrNull?.let { vcard.addEmail(it) }
                profileJson["phone"]?.jsonPrimitive?.contentOrNull?.let { vcard.addTelephoneNumber(it) }
                profileJson["organization"]?.jsonPrimitive?.contentOrNull?.let { vcard.setOrganization(it) }
                profileJson["title"]?.jsonPrimitive?.contentOrNull?.let { vcard.addTitle(it) }
                profileJson["timezone"]?.jsonPrimitive?.contentOrNull?.let { vcard.addTimezone(it) }
                profileJson["website"]?.jsonPrimitive?.contentOrNull?.let { vcard.addUrl(it) }
                profileJson["bio"]?.jsonPrimitive?.contentOrNull?.let { vcard.addNote(it) }
            }
        }

        vcard.revision = Revision(Date())
        return vcard
    }

    // =========================================================================
    // Property Mapping Helpers
    // =========================================================================

    private fun mapStructuredName(n: StructuredName): Map<String, Any?> = mapOf(
        "family" to n.family,
        "given" to n.given,
        "additional" to n.additionalNames,
        "prefixes" to n.prefixes,
        "suffixes" to n.suffixes
    )

    private fun mapPhoto(photo: Photo): Map<String, Any?> = mapOf(
        "url" to photo.url,
        "mediaType" to photo.contentType?.mediaType,
        "data" to photo.data?.let { Base64.getEncoder().encodeToString(it) }
    )

    private fun mapDateOrDateTime(prop: DateOrTimeProperty): Map<String, Any?> = mapOf(
        "date" to prop.date?.toInstant()?.toString(),
        "text" to prop.text,
        "partialDate" to prop.partialDate?.let {
            mapOf("year" to it.year, "month" to it.month, "date" to it.date)
        }
    )

    private fun mapGender(gender: Gender): Map<String, Any?> = mapOf(
        "sex" to gender.sex,
        "text" to gender.text
    )

    private fun mapAddress(addr: Address): Map<String, Any?> = mapOf(
        "poBox" to addr.poBox,
        "extendedAddress" to addr.extendedAddress,
        "street" to addr.streetAddress,
        "locality" to addr.locality,
        "region" to addr.region,
        "postalCode" to addr.postalCode,
        "country" to addr.country,
        "label" to addr.label,
        "types" to addr.types?.map { it.value },
        "pref" to addr.pref
    )

    private fun mapTelephone(tel: Telephone): Map<String, Any?> = mapOf(
        "text" to tel.text,
        "uri" to tel.uri,
        "types" to tel.types?.map { it.value },
        "pref" to tel.pref
    )

    private fun mapEmail(email: Email): Map<String, Any?> = mapOf(
        "value" to email.value,
        "types" to email.types?.map { it.value },
        "pref" to email.pref
    )

    private fun mapImpp(impp: Impp): Map<String, Any?> = mapOf(
        "uri" to impp.uri?.toString(),
        "protocol" to impp.protocol,
        "types" to impp.types?.map { it.value },
        "pref" to impp.pref
    )

    private fun mapLanguage(lang: Language): Map<String, Any?> = mapOf(
        "value" to lang.value,
        "pref" to lang.pref
    )

    private fun mapGeo(geo: Geo): Map<String, Any?> = mapOf(
        "latitude" to geo.latitude,
        "longitude" to geo.longitude,
        "uri" to geo.geoUri?.toString()
    )

    private fun mapLogo(logo: Logo): Map<String, Any?> = mapOf(
        "url" to logo.url,
        "mediaType" to logo.contentType?.mediaType,
        "data" to logo.data?.let { Base64.getEncoder().encodeToString(it) }
    )

    private fun mapOrganization(org: Organization): Map<String, Any?> = mapOf(
        "values" to org.values,
        "type" to org.type
    )

    private fun mapRelated(related: Related): Map<String, Any?> = mapOf(
        "uri" to related.uri,
        "text" to related.text,
        "types" to related.types?.map { it.value }
    )

    private fun mapRevision(rev: Revision): String? =
        rev.value?.toInstant()?.toString()

    private fun mapSound(sound: Sound): Map<String, Any?> = mapOf(
        "url" to sound.url,
        "mediaType" to sound.contentType?.mediaType,
        "data" to sound.data?.let { Base64.getEncoder().encodeToString(it) }
    )

    private fun mapUrl(url: Url): Map<String, Any?> = mapOf(
        "value" to url.value,
        "type" to url.type,
        "pref" to url.pref
    )

    private fun mapKey(key: Key): Map<String, Any?> = mapOf(
        "url" to key.url,
        "mediaType" to key.contentType?.mediaType,
        "data" to key.data?.let { Base64.getEncoder().encodeToString(it) }
    )

    companion object {
        val INSTANCE = RfcVCardMapperImpl()
    }
}

/**
 * Implementation of RfcVCardMapper
 */
class RfcVCardMapperImpl : RfcVCardMapper()

/**
 * Matrix profile data structure
 */
@Serializable
data class MatrixProfileData(
    val displayname: String?,
    val avatar_url: String?,
    val accountData: Map<String, JsonElement> = emptyMap()
)

/**
 * Extension functions for easy RFC mapping
 */
fun VCard.toRfcMap(): Map<String, Any?> = RfcVCardMapper.INSTANCE.toRfcMap(this)
fun VCard.toKeycloakAttributes(): Map<String, List<String>> = RfcVCardMapper.INSTANCE.toKeycloakAttributes(this)
fun VCard.toOpenfireVCard(): Map<String, Any?> = RfcVCardMapper.INSTANCE.toOpenfireVCard(this)
fun VCard.toMatrixProfile(): MatrixProfileData = RfcVCardMapper.INSTANCE.toMatrixProfile(this)
fun VCard.toBedeworkVCard(): VCard = RfcVCardMapper.INSTANCE.toBedeworkVCard(this)
