/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.sync

import com.google.common.collect.MapDifference
import com.google.common.collect.Maps
import ezvcard.Ezvcard
import ezvcard.VCard
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.io.StringReader
import java.io.StringWriter

private val logger = KotlinLogging.logger {}

/**
 * jCard JSON serialization utilities for vCard using ez-vcard and Kotlinx serialization
 * jCard format: RFC 7095
 */
object JCardSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Convert VCard to jCard JSON string using ez-vcard
     */
    fun vCardToJCard(vcard: VCard): String {
        val writer = StringWriter()
        Ezvcard.writeJson(vcard).go(writer)
        return writer.toString()
    }

    /**
     * Parse jCard JSON string to VCard using ez-vcard
     */
    fun jCardToVCard(jcard: String): VCard? {
        return try {
            Ezvcard.parseJson(jcard).first()
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse jCard JSON" }
            null
        }
    }

    /**
     * Convert VCard to Map<String, Any?> for Guava MapDifference comparison
     */
    fun vCardToMap(vcard: VCard): Map<String, Any?> {
        val jcardJson = vCardToJCard(vcard)
        return jCardJsonToMap(jcardJson)
    }

    /**
     * Parse jCard JSON to Map<String, Any?> using Kotlinx serialization
     */
    fun jCardJsonToMap(jcardJson: String): Map<String, Any?> {
        return try {
            val jsonElement = json.parseToJsonElement(jcardJson)
            jsonElementToMap(jsonElement)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse jCard JSON to map" }
            emptyMap()
        }
    }

    /**
     * Convert JsonElement to Map<String, Any?> recursively
     */
    private fun jsonElementToMap(element: JsonElement): Map<String, Any?> {
        return when (element) {
            is JsonObject -> element.mapValues { (_, value) -> jsonElementToAny(value) }
            is JsonArray -> {
                // jCard is an array: ["vcard", [...properties...]]
                if (element.size >= 2 && element[0].let { it is JsonPrimitive && it.content == "vcard" }) {
                    parseJCardProperties(element[1] as? JsonArray ?: JsonArray(emptyList()))
                } else {
                    mapOf("array" to element.map { jsonElementToAny(it) })
                }
            }
            else -> emptyMap()
        }
    }

    /**
     * Parse jCard properties array to flat map
     * jCard property format: ["property-name", {params}, "value-type", value(s)]
     */
    private fun parseJCardProperties(propertiesArray: JsonArray): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        propertiesArray.forEachIndexed { index, propertyElement ->
            if (propertyElement is JsonArray && propertyElement.size >= 4) {
                val propertyName = (propertyElement[0] as? JsonPrimitive)?.content ?: "unknown"
                val params = propertyElement[1] as? JsonObject ?: JsonObject(emptyMap())
                val valueType = (propertyElement[2] as? JsonPrimitive)?.content ?: "unknown"
                val value = propertyElement.drop(3)

                // Create unique key for multiple values of same property
                val key = if (map.containsKey(propertyName)) "$propertyName.$index" else propertyName

                // Store property with metadata
                map[key] = JCardProperty(
                    name = propertyName,
                    parameters = params.mapValues { (_, v) -> jsonElementToAny(v) },
                    valueType = valueType,
                    values = value.map { jsonElementToAny(it) }
                ).toMap()
            }
        }

        return map
    }

    /**
     * Convert JsonElement to Any type
     */
    private fun jsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonArray -> element.map { jsonElementToAny(it) }
            is JsonObject -> element.mapValues { (_, v) -> jsonElementToAny(v) }
            is JsonNull -> null
        }
    }

    /**
     * Compute difference between two vCards using Guava MapDifference
     */
    fun computeDifference(local: VCard, remote: VCard): JCardDiff {
        val localMap = vCardToMap(local)
        val remoteMap = vCardToMap(remote)

        @Suppress("UNCHECKED_CAST")
        val difference: MapDifference<String, Any?> = Maps.difference(
            localMap as Map<String, Any?>,
            remoteMap as Map<String, Any?>
        )

        return JCardDiff(
            localOnly = difference.entriesOnlyOnLeft(),
            remoteOnly = difference.entriesOnlyOnRight(),
            differing = difference.entriesDiffering().mapValues { (_, valueDiff) ->
                JCardValueDiff(valueDiff.leftValue(), valueDiff.rightValue())
            },
            common = difference.entriesInCommon(),
            areEqual = difference.areEqual()
        )
    }

    /**
     * Merge two jCard maps with conflict resolution
     */
    fun mergeJCardMaps(
        local: Map<String, Any?>,
        remote: Map<String, Any?>,
        resolution: ConflictResolution = ConflictResolution.REMOTE_WINS
    ): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val difference: MapDifference<String, Any?> = Maps.difference(
            local as Map<String, Any?>,
            remote as Map<String, Any?>
        )

        val merged = mutableMapOf<String, Any?>()

        // Add common entries
        merged.putAll(difference.entriesInCommon())

        // Add local-only entries
        merged.putAll(difference.entriesOnlyOnLeft())

        // Add remote-only entries
        merged.putAll(difference.entriesOnlyOnRight())

        // Resolve differing entries based on strategy
        difference.entriesDiffering().forEach { (key, valueDiff) ->
            merged[key] = when (resolution) {
                ConflictResolution.LOCAL_WINS -> valueDiff.leftValue()
                ConflictResolution.REMOTE_WINS -> valueDiff.rightValue()
                ConflictResolution.NEWER_WINS -> {
                    // Check revision timestamp if available
                    if (key == "rev") {
                        // Compare revision timestamps
                        val localTime = parseRevisionTime(valueDiff.leftValue())
                        val remoteTime = parseRevisionTime(valueDiff.rightValue())
                        if (localTime > remoteTime) valueDiff.leftValue() else valueDiff.rightValue()
                    } else {
                        valueDiff.rightValue() // Default to remote for non-rev fields
                    }
                }
                ConflictResolution.MANUAL -> valueDiff.rightValue() // Default to remote, flag for review
            }
        }

        return merged
    }

    /**
     * Convert Map back to VCard
     */
    fun mapToVCard(map: Map<String, Any?>): VCard? {
        return try {
            val jcardJson = mapToJCardJson(map)
            jCardToVCard(jcardJson)
        } catch (e: Exception) {
            logger.error(e) { "Failed to convert map to VCard" }
            null
        }
    }

    /**
     * Convert Map<String, Any?> to jCard JSON string
     */
    fun mapToJCardJson(map: Map<String, Any?>): String {
        val properties = mutableListOf<JsonArray>()

        map.forEach { (key, value) ->
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val propMap = value as Map<String, Any?>
                val name = propMap["name"] as? String ?: key.substringBefore(".")
                val params = propMap["parameters"] as? Map<String, Any?> ?: emptyMap()
                val valueType = propMap["valueType"] as? String ?: "text"
                val values = propMap["values"] as? List<Any?> ?: listOf(propMap["value"])

                val propArray = buildJsonArray {
                    add(name)
                    add(buildJsonObject {
                        params.forEach { (pk, pv) ->
                            put(pk, anyToJsonElement(pv))
                        }
                    })
                    add(valueType)
                    values.forEach { v ->
                        add(anyToJsonElement(v))
                    }
                }
                properties.add(propArray)
            }
        }

        val jcard = buildJsonArray {
            add("vcard")
            add(buildJsonArray {
                properties.forEach { add(it) }
            })
        }

        return json.encodeToString(JsonArray.serializer(), jcard)
    }

    /**
     * Convert Any? to JsonElement
     */
    private fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is List<*> -> buildJsonArray { value.forEach { add(anyToJsonElement(it)) } }
            is Map<*, *> -> buildJsonObject {
                @Suppress("UNCHECKED_CAST")
                (value as Map<String, Any?>).forEach { (k, v) ->
                    put(k, anyToJsonElement(v))
                }
            }
            else -> JsonPrimitive(value.toString())
        }
    }

    /**
     * Parse revision timestamp from various formats
     */
    private fun parseRevisionTime(value: Any?): Long {
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is String -> try {
                java.time.Instant.parse(value).toEpochMilli()
            } catch (e: Exception) {
                0L
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val values = (value as Map<String, Any?>)["values"] as? List<Any?>
                values?.firstOrNull()?.let { parseRevisionTime(it) } ?: 0L
            }
            else -> 0L
        }
    }
}

/**
 * jCard property representation
 */
@Serializable
data class JCardProperty(
    val name: String,
    val parameters: Map<String, Any?>,
    val valueType: String,
    val values: List<Any?>
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "parameters" to parameters,
        "valueType" to valueType,
        "values" to values
    )
}

/**
 * jCard difference result
 */
data class JCardDiff(
    val localOnly: Map<String, Any?>,
    val remoteOnly: Map<String, Any?>,
    val differing: Map<String, JCardValueDiff>,
    val common: Map<String, Any?>,
    val areEqual: Boolean
) {
    fun hasChanges() = !areEqual

    fun toChangeSummary() = ChangeSummary(
        added = remoteOnly.keys.toList(),
        removed = localOnly.keys.toList(),
        modified = differing.keys.toList(),
        unchanged = common.keys.toList(),
        totalChanges = remoteOnly.size + localOnly.size + differing.size
    )
}

/**
 * Value difference between local and remote
 */
data class JCardValueDiff(
    val localValue: Any?,
    val remoteValue: Any?
)

/**
 * Kotlinx serializer for Map<String, Any?>
 * Handles nested maps, lists, and primitives
 */
object AnyMapSerializer : KSerializer<Map<String, Any?>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AnyMap")

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalArgumentException("This serializer only works with JSON")

        val jsonObject = buildJsonObject {
            value.forEach { (key, v) ->
                put(key, anyToJsonElement(v))
            }
        }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalArgumentException("This serializer only works with JSON")

        val jsonElement = jsonDecoder.decodeJsonElement()
        return jsonElementToMap(jsonElement)
    }

    private fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is List<*> -> buildJsonArray { value.forEach { add(anyToJsonElement(it)) } }
            is Map<*, *> -> buildJsonObject {
                @Suppress("UNCHECKED_CAST")
                (value as Map<String, Any?>).forEach { (k, v) ->
                    put(k, anyToJsonElement(v))
                }
            }
            else -> JsonPrimitive(value.toString())
        }
    }

    private fun jsonElementToMap(element: JsonElement): Map<String, Any?> {
        return when (element) {
            is JsonObject -> element.mapValues { (_, v) -> jsonElementToAny(v) }
            else -> emptyMap()
        }
    }

    private fun jsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonArray -> element.map { jsonElementToAny(it) }
            is JsonObject -> element.mapValues { (_, v) -> jsonElementToAny(v) }
            is JsonNull -> null
        }
    }
}

/**
 * Extension to serialize vCard changes as JSON
 */
fun VCardDiff.toJsonString(): String {
    val json = Json { prettyPrint = true }
    return json.encodeToString(
        MapSerializer(String.serializer(), JsonElement.serializer()),
        mapOf(
            "areEqual" to JsonPrimitive(areEqual),
            "added" to JsonArray(onlyInRemote.keys.map { JsonPrimitive(it) }),
            "removed" to JsonArray(onlyInLocal.keys.map { JsonPrimitive(it) }),
            "modified" to JsonArray(differing.keys.map { JsonPrimitive(it) }),
            "unchanged" to JsonPrimitive(common.size)
        )
    )
}
