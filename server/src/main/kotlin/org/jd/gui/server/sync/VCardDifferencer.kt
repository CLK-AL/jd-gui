/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.sync

import com.google.common.collect.MapDifference
import com.google.common.collect.Maps
import ezvcard.VCard
import ezvcard.io.text.VCardReader
import ezvcard.io.text.VCardWriter
import ezvcard.property.*
import mu.KotlinLogging
import java.io.StringReader
import java.io.StringWriter
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * VCard differencer using Guava's MapDifference to detect and resolve vCard changes
 */
class VCardDifferencer {

    /**
     * Convert VCard to Map<String, Any> for comparison
     */
    fun vCardToMap(vcard: VCard): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        // UID
        map["uid"] = vcard.uid?.value

        // Kind
        map["kind"] = vcard.kind?.value

        // Formatted Name
        map["formattedName"] = vcard.formattedName?.value

        // Structured Name
        vcard.structuredName?.let { name ->
            map["name.given"] = name.given
            map["name.family"] = name.family
            map["name.additional"] = name.additionalNames?.joinToString(",")
            map["name.prefixes"] = name.prefixes?.joinToString(",")
            map["name.suffixes"] = name.suffixes?.joinToString(",")
        }

        // Organization
        vcard.organization?.let { org ->
            map["organization"] = org.values?.joinToString(";")
        }

        // Title
        map["title"] = vcard.titles?.firstOrNull()?.value

        // Role
        map["role"] = vcard.roles?.firstOrNull()?.value

        // Emails (support multiple)
        vcard.emails?.forEachIndexed { index, email ->
            map["email.$index.value"] = email.value
            map["email.$index.types"] = email.types?.map { it.value }?.joinToString(",")
        }
        map["email.count"] = vcard.emails?.size ?: 0

        // Phone numbers (support multiple)
        vcard.telephoneNumbers?.forEachIndexed { index, tel ->
            map["telephone.$index.value"] = tel.text
            map["telephone.$index.types"] = tel.types?.map { it.value }?.joinToString(",")
        }
        map["telephone.count"] = vcard.telephoneNumbers?.size ?: 0

        // Addresses (support multiple)
        vcard.addresses?.forEachIndexed { index, addr ->
            map["address.$index.street"] = addr.streetAddress
            map["address.$index.locality"] = addr.locality
            map["address.$index.region"] = addr.region
            map["address.$index.postalCode"] = addr.postalCode
            map["address.$index.country"] = addr.country
            map["address.$index.types"] = addr.types?.map { it.value }?.joinToString(",")
        }
        map["address.count"] = vcard.addresses?.size ?: 0

        // URLs
        vcard.urls?.forEachIndexed { index, url ->
            map["url.$index"] = url.value
        }
        map["url.count"] = vcard.urls?.size ?: 0

        // Photo (store hash for comparison)
        vcard.photos?.firstOrNull()?.let { photo ->
            map["photo.type"] = photo.contentType?.mediaType
            map["photo.dataHash"] = photo.data?.contentHashCode()
            map["photo.url"] = photo.url
        }

        // Notes
        map["note"] = vcard.notes?.firstOrNull()?.value

        // Categories
        vcard.categories?.let { cats ->
            map["categories"] = cats.values?.joinToString(",")
        }

        // Revision
        map["revision"] = vcard.revision?.value?.time

        // Extended properties (X- properties)
        vcard.extendedProperties?.forEach { prop ->
            map["x.${prop.propertyName}"] = prop.value
        }

        return map.filterValues { it != null }
    }

    /**
     * Compute difference between two vCards
     */
    fun computeDifference(local: VCard, remote: VCard): VCardDiff {
        val localMap = vCardToMap(local)
        val remoteMap = vCardToMap(remote)

        @Suppress("UNCHECKED_CAST")
        val difference: MapDifference<String, Any?> = Maps.difference(
            localMap as Map<String, Any?>,
            remoteMap as Map<String, Any?>
        )

        return VCardDiff(
            onlyInLocal = difference.entriesOnlyOnLeft(),
            onlyInRemote = difference.entriesOnlyOnRight(),
            differing = difference.entriesDiffering().mapValues { (_, valueDiff) ->
                ValueDiff(valueDiff.leftValue(), valueDiff.rightValue())
            },
            common = difference.entriesInCommon(),
            areEqual = difference.areEqual()
        )
    }

    /**
     * Compute difference between vCard string and VCard object
     */
    fun computeDifference(localVCardString: String, remote: VCard): VCardDiff {
        val local = parseVCard(localVCardString) ?: return VCardDiff.empty()
        return computeDifference(local, remote)
    }

    /**
     * Merge changes from remote into local vCard
     * Strategy: Remote wins for conflicts, preserves local-only fields
     */
    fun mergeRemoteIntoLocal(local: VCard, remote: VCard): MergeResult {
        val diff = computeDifference(local, remote)
        val merged = VCard(local) // Copy local

        val appliedChanges = mutableListOf<String>()
        val conflicts = mutableListOf<String>()

        // Apply differing values (remote wins)
        diff.differing.forEach { (key, valueDiff) ->
            applyRemoteValue(merged, key, valueDiff.remoteValue)
            appliedChanges.add("Updated $key: ${valueDiff.localValue} -> ${valueDiff.remoteValue}")
            if (valueDiff.localValue != null) {
                conflicts.add(key)
            }
        }

        // Apply remote-only values
        diff.onlyInRemote.forEach { (key, value) ->
            applyRemoteValue(merged, key, value)
            appliedChanges.add("Added $key: $value")
        }

        // Update revision timestamp
        merged.revision = Revision(Date())

        return MergeResult(
            merged = merged,
            appliedChanges = appliedChanges,
            conflicts = conflicts,
            hasChanges = appliedChanges.isNotEmpty()
        )
    }

    /**
     * Merge changes from local into remote vCard
     * Strategy: Local wins for conflicts, preserves remote-only fields
     */
    fun mergeLocalIntoRemote(local: VCard, remote: VCard): MergeResult {
        val diff = computeDifference(local, remote)
        val merged = VCard(remote) // Copy remote

        val appliedChanges = mutableListOf<String>()
        val conflicts = mutableListOf<String>()

        // Apply differing values (local wins)
        diff.differing.forEach { (key, valueDiff) ->
            applyRemoteValue(merged, key, valueDiff.localValue)
            appliedChanges.add("Updated $key: ${valueDiff.remoteValue} -> ${valueDiff.localValue}")
            if (valueDiff.remoteValue != null) {
                conflicts.add(key)
            }
        }

        // Apply local-only values
        diff.onlyInLocal.forEach { (key, value) ->
            applyRemoteValue(merged, key, value)
            appliedChanges.add("Added $key: $value")
        }

        // Update revision timestamp
        merged.revision = Revision(Date())

        return MergeResult(
            merged = merged,
            appliedChanges = appliedChanges,
            conflicts = conflicts,
            hasChanges = appliedChanges.isNotEmpty()
        )
    }

    /**
     * Three-way merge using a common ancestor
     */
    fun threeWayMerge(ancestor: VCard, local: VCard, remote: VCard): ThreeWayMergeResult {
        val ancestorMap = vCardToMap(ancestor)
        val localMap = vCardToMap(local)
        val remoteMap = vCardToMap(remote)

        val merged = VCard(ancestor)
        val appliedChanges = mutableListOf<String>()
        val conflicts = mutableListOf<ThreeWayConflict>()

        // Get all keys from all three versions
        val allKeys = (ancestorMap.keys + localMap.keys + remoteMap.keys).toSet()

        for (key in allKeys) {
            val ancestorValue = ancestorMap[key]
            val localValue = localMap[key]
            val remoteValue = remoteMap[key]

            when {
                // No change
                localValue == ancestorValue && remoteValue == ancestorValue -> {
                    // Keep as is
                }
                // Only local changed
                localValue != ancestorValue && remoteValue == ancestorValue -> {
                    applyRemoteValue(merged, key, localValue)
                    appliedChanges.add("Applied local change to $key")
                }
                // Only remote changed
                localValue == ancestorValue && remoteValue != ancestorValue -> {
                    applyRemoteValue(merged, key, remoteValue)
                    appliedChanges.add("Applied remote change to $key")
                }
                // Both changed to same value
                localValue == remoteValue -> {
                    applyRemoteValue(merged, key, localValue)
                    appliedChanges.add("Both changed $key to same value")
                }
                // Conflict: both changed to different values
                else -> {
                    // Default: remote wins, but record conflict
                    applyRemoteValue(merged, key, remoteValue)
                    conflicts.add(ThreeWayConflict(
                        key = key,
                        ancestorValue = ancestorValue,
                        localValue = localValue,
                        remoteValue = remoteValue,
                        resolvedValue = remoteValue,
                        resolution = ConflictResolution.REMOTE_WINS
                    ))
                }
            }
        }

        merged.revision = Revision(Date())

        return ThreeWayMergeResult(
            merged = merged,
            appliedChanges = appliedChanges,
            conflicts = conflicts,
            hasConflicts = conflicts.isNotEmpty()
        )
    }

    /**
     * Create a change summary between two vCards
     */
    fun createChangeSummary(local: VCard, remote: VCard): ChangeSummary {
        val diff = computeDifference(local, remote)

        return ChangeSummary(
            added = diff.onlyInRemote.keys.toList(),
            removed = diff.onlyInLocal.keys.toList(),
            modified = diff.differing.keys.toList(),
            unchanged = diff.common.keys.toList(),
            totalChanges = diff.onlyInRemote.size + diff.onlyInLocal.size + diff.differing.size
        )
    }

    /**
     * Parse vCard from string
     */
    fun parseVCard(vcardString: String): VCard? {
        return try {
            VCardReader(StringReader(vcardString)).use { reader ->
                reader.readNext()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse vCard" }
            null
        }
    }

    /**
     * Serialize vCard to string
     */
    fun vCardToString(vcard: VCard): String {
        val writer = StringWriter()
        VCardWriter(writer).use { vcardWriter ->
            vcardWriter.write(vcard)
        }
        return writer.toString()
    }

    /**
     * Apply a value to the vCard based on the key
     */
    private fun applyRemoteValue(vcard: VCard, key: String, value: Any?) {
        try {
            when {
                key == "formattedName" -> vcard.setFormattedName(value as? String)
                key == "name.given" -> vcard.structuredName?.given = value as? String
                key == "name.family" -> vcard.structuredName?.family = value as? String
                key == "organization" -> {
                    val values = (value as? String)?.split(";")
                    if (!values.isNullOrEmpty()) {
                        vcard.setOrganization(*values.toTypedArray())
                    }
                }
                key == "title" -> {
                    vcard.titles.clear()
                    (value as? String)?.let { vcard.addTitle(it) }
                }
                key == "note" -> {
                    vcard.notes.clear()
                    (value as? String)?.let { vcard.addNote(it) }
                }
                key.startsWith("email.") && key.endsWith(".value") -> {
                    val index = key.removePrefix("email.").removeSuffix(".value").toIntOrNull() ?: return
                    if (index < vcard.emails.size) {
                        vcard.emails[index].value = value as? String
                    }
                }
                key.startsWith("telephone.") && key.endsWith(".value") -> {
                    val index = key.removePrefix("telephone.").removeSuffix(".value").toIntOrNull() ?: return
                    if (index < vcard.telephoneNumbers.size) {
                        vcard.telephoneNumbers[index].text = value as? String
                    }
                }
                key.startsWith("x.") -> {
                    val propName = key.removePrefix("x.")
                    vcard.extendedProperties.removeIf { it.propertyName == propName }
                    (value as? String)?.let {
                        vcard.addExtendedProperty(propName, it)
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug { "Failed to apply value for key $key: ${e.message}" }
        }
    }
}

/**
 * VCard difference result
 */
data class VCardDiff(
    val onlyInLocal: Map<String, Any?>,
    val onlyInRemote: Map<String, Any?>,
    val differing: Map<String, ValueDiff>,
    val common: Map<String, Any?>,
    val areEqual: Boolean
) {
    companion object {
        fun empty() = VCardDiff(
            onlyInLocal = emptyMap(),
            onlyInRemote = emptyMap(),
            differing = emptyMap(),
            common = emptyMap(),
            areEqual = true
        )
    }

    fun hasChanges() = !areEqual
}

/**
 * Value difference between local and remote
 */
data class ValueDiff(
    val localValue: Any?,
    val remoteValue: Any?
)

/**
 * Merge result
 */
data class MergeResult(
    val merged: VCard,
    val appliedChanges: List<String>,
    val conflicts: List<String>,
    val hasChanges: Boolean
)

/**
 * Three-way merge result
 */
data class ThreeWayMergeResult(
    val merged: VCard,
    val appliedChanges: List<String>,
    val conflicts: List<ThreeWayConflict>,
    val hasConflicts: Boolean
)

/**
 * Three-way conflict
 */
data class ThreeWayConflict(
    val key: String,
    val ancestorValue: Any?,
    val localValue: Any?,
    val remoteValue: Any?,
    val resolvedValue: Any?,
    val resolution: ConflictResolution
)

/**
 * Conflict resolution strategy
 */
enum class ConflictResolution {
    LOCAL_WINS,
    REMOTE_WINS,
    MANUAL,
    NEWER_WINS
}

/**
 * Change summary
 */
data class ChangeSummary(
    val added: List<String>,
    val removed: List<String>,
    val modified: List<String>,
    val unchanged: List<String>,
    val totalChanges: Int
)
