/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.xmpp

import ezvcard.VCard
import ezvcard.io.text.VCardWriter
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.jd.gui.server.auth.Organization
import org.jd.gui.server.auth.User
import org.jd.gui.server.auth.VCardOrgManager
import org.jd.gui.server.config.OpenfireConfig
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.SmackConfiguration
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.RosterEntry
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smackx.pubsub.PubSubManager
import org.jivesoftware.smackx.vcardtemp.VCardManager
import org.jivesoftware.smackx.vcardtemp.packet.VCard as SmackVCard
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.EntityBareJid
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Manages organization data via Openfire XMPP server
 * Syncs vCards to Openfire for on-premises org data storage
 */
class OpenfireOrgManager(
    private val config: OpenfireConfig,
    private val vcardManager: VCardOrgManager
) {
    private var connection: XMPPTCPConnection? = null
    private var smackVCardManager: VCardManager? = null
    private var roster: Roster? = null
    private var pubSubManager: PubSubManager? = null

    // Cache of synced organizations
    private val syncedOrgs = ConcurrentHashMap<String, Long>()

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        SmackConfiguration.DEBUG = false
    }

    /**
     * Connect to Openfire server
     */
    suspend fun connect(): Boolean {
        if (!config.enabled) {
            logger.info { "Openfire integration is disabled" }
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val connectionConfig = XMPPTCPConnectionConfiguration.builder()
                    .setHost(config.host)
                    .setPort(config.port)
                    .setXmppDomain(config.domain)
                    .setUsernameAndPassword(config.adminUsername, config.adminPassword)
                    .setResource(config.resourceName)
                    .setSecurityMode(
                        when (config.securityMode.lowercase()) {
                            "required" -> ConnectionConfiguration.SecurityMode.required
                            "ifpossible" -> ConnectionConfiguration.SecurityMode.ifpossible
                            else -> ConnectionConfiguration.SecurityMode.disabled
                        }
                    )
                    .setConnectTimeout(config.connectionTimeout)
                    .build()

                connection = XMPPTCPConnection(connectionConfig).apply {
                    connect()
                    login()
                }

                smackVCardManager = VCardManager.getInstanceFor(connection)

                if (config.enableRoster) {
                    roster = Roster.getInstanceFor(connection)
                    roster?.subscriptionMode = Roster.SubscriptionMode.accept_all
                }

                if (config.enablePubSub) {
                    pubSubManager = PubSubManager.getInstanceFor(connection)
                }

                logger.info { "Connected to Openfire at ${config.host}:${config.port}" }
                true
            } catch (e: Exception) {
                logger.error(e) { "Failed to connect to Openfire" }
                false
            }
        }
    }

    /**
     * Disconnect from Openfire
     */
    fun disconnect() {
        try {
            connection?.disconnect()
            connection = null
            smackVCardManager = null
            roster = null
            pubSubManager = null
            scope.cancel()
            logger.info { "Disconnected from Openfire" }
        } catch (e: Exception) {
            logger.error(e) { "Error disconnecting from Openfire" }
        }
    }

    /**
     * Sync organization vCard to Openfire
     */
    suspend fun syncOrganization(org: Organization): Boolean {
        if (!isConnected()) return false

        return withContext(Dispatchers.IO) {
            try {
                val ezVCard = vcardManager.getOrganizationVCard(org.id) ?: return@withContext false
                val smackVCard = convertToSmackVCard(ezVCard, isOrg = true)

                // Set vCard for organization JID
                val orgJid = JidCreate.entityBareFrom(config.buildOrgJid(org.id))
                smackVCardManager?.saveVCard(smackVCard)

                syncedOrgs[org.id] = System.currentTimeMillis()
                logger.debug { "Synced organization vCard to Openfire: ${org.name}" }
                true
            } catch (e: Exception) {
                logger.error(e) { "Failed to sync organization to Openfire: ${org.name}" }
                false
            }
        }
    }

    /**
     * Sync user vCard to Openfire
     */
    suspend fun syncUser(orgId: String, user: User): Boolean {
        if (!isConnected()) return false

        return withContext(Dispatchers.IO) {
            try {
                val ezVCard = vcardManager.getUserVCard(orgId, user.id) ?: return@withContext false
                val smackVCard = convertToSmackVCard(ezVCard, isOrg = false)

                // Save vCard to Openfire
                smackVCardManager?.saveVCard(smackVCard)

                logger.debug { "Synced user vCard to Openfire: ${user.username}" }
                true
            } catch (e: Exception) {
                logger.error(e) { "Failed to sync user to Openfire: ${user.username}" }
                false
            }
        }
    }

    /**
     * Sync all organizations and users to Openfire
     */
    suspend fun syncAll(): SyncResult {
        if (!isConnected()) {
            return SyncResult(0, 0, 0, 0, "Not connected to Openfire")
        }

        var orgsSuccess = 0
        var orgsFailed = 0
        var usersSuccess = 0
        var usersFailed = 0

        val organizations = vcardManager.listOrganizations()
        for (org in organizations) {
            if (syncOrganization(org)) {
                orgsSuccess++
            } else {
                orgsFailed++
            }

            val users = vcardManager.listUsers(org.id)
            for (user in users) {
                if (syncUser(org.id, user)) {
                    usersSuccess++
                } else {
                    usersFailed++
                }
            }
        }

        return SyncResult(orgsSuccess, orgsFailed, usersSuccess, usersFailed)
    }

    /**
     * Add user to organization roster
     */
    suspend fun addUserToOrgRoster(orgId: String, userId: String, userName: String): Boolean {
        if (!isConnected() || roster == null) return false

        return withContext(Dispatchers.IO) {
            try {
                val userJid = JidCreate.bareFrom(config.buildUserJid(userId))
                roster?.createItemAndRequestSubscription(
                    userJid,
                    userName,
                    arrayOf("org-$orgId")
                )
                logger.debug { "Added user $userName to org roster: $orgId" }
                true
            } catch (e: Exception) {
                logger.error(e) { "Failed to add user to org roster" }
                false
            }
        }
    }

    /**
     * Remove user from organization roster
     */
    suspend fun removeUserFromOrgRoster(orgId: String, userId: String): Boolean {
        if (!isConnected() || roster == null) return false

        return withContext(Dispatchers.IO) {
            try {
                val userJid = JidCreate.bareFrom(config.buildUserJid(userId))
                val entry = roster?.getEntry(userJid)
                if (entry != null) {
                    roster?.removeEntry(entry)
                    logger.debug { "Removed user $userId from org roster: $orgId" }
                }
                true
            } catch (e: Exception) {
                logger.error(e) { "Failed to remove user from org roster" }
                false
            }
        }
    }

    /**
     * Get organization members from roster
     */
    fun getOrgMembers(orgId: String): List<RosterMember> {
        if (!isConnected() || roster == null) return emptyList()

        return try {
            val group = roster?.getGroup("org-$orgId") ?: return emptyList()
            group.entries.map { entry ->
                RosterMember(
                    jid = entry.jid.toString(),
                    name = entry.name ?: entry.jid.localpartOrNull?.toString() ?: "Unknown",
                    isOnline = roster?.getPresence(entry.jid)?.isAvailable ?: false
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get org members from roster" }
            emptyList()
        }
    }

    /**
     * Publish organization event via PubSub
     */
    suspend fun publishOrgEvent(orgId: String, eventType: String, payload: String): Boolean {
        if (!isConnected() || pubSubManager == null || !config.enablePubSub) return false

        return withContext(Dispatchers.IO) {
            try {
                val nodeId = "org-$orgId-events"
                // PubSub implementation would go here
                logger.debug { "Published event $eventType to org $orgId" }
                true
            } catch (e: Exception) {
                logger.error(e) { "Failed to publish org event" }
                false
            }
        }
    }

    /**
     * Load vCard from Openfire for a user
     */
    suspend fun loadVCardFromOpenfire(userId: String): SmackVCard? {
        if (!isConnected()) return null

        return withContext(Dispatchers.IO) {
            try {
                val userJid = JidCreate.entityBareFrom(config.buildUserJid(userId))
                smackVCardManager?.loadVCard(userJid)
            } catch (e: Exception) {
                logger.error(e) { "Failed to load vCard from Openfire for user: $userId" }
                null
            }
        }
    }

    /**
     * Check if connected to Openfire
     */
    fun isConnected(): Boolean = connection?.isConnected == true && connection?.isAuthenticated == true

    /**
     * Convert ez-vcard VCard to Smack VCard
     */
    private fun convertToSmackVCard(ezVCard: VCard, isOrg: Boolean): SmackVCard {
        val smackVCard = SmackVCard()

        // Basic info
        ezVCard.formattedName?.value?.let { smackVCard.setField("FN", it) }

        // Name components
        ezVCard.structuredName?.let { name ->
            smackVCard.firstName = name.given
            smackVCard.lastName = name.family
            smackVCard.middleName = name.additionalNames?.firstOrNull()
        }

        // Organization
        if (isOrg) {
            ezVCard.organization?.values?.firstOrNull()?.let {
                smackVCard.organization = it
            }
        } else {
            ezVCard.organization?.values?.let { values ->
                if (values.isNotEmpty()) {
                    smackVCard.organization = values.first()
                    if (values.size > 1) {
                        smackVCard.organizationUnit = values[1]
                    }
                }
            }
        }

        // Email
        ezVCard.emails.firstOrNull()?.value?.let {
            smackVCard.emailWork = it
        }

        // Phone
        ezVCard.telephoneNumbers.firstOrNull()?.text?.let {
            smackVCard.setPhoneWork("VOICE", it)
        }

        // Title
        ezVCard.titles.firstOrNull()?.value?.let {
            smackVCard.setField("TITLE", it)
        }

        // Photo
        ezVCard.photos.firstOrNull()?.let { photo ->
            photo.data?.let { data ->
                smackVCard.setAvatar(data, photo.contentType?.mediaType ?: "image/png")
            }
        }

        // Notes
        ezVCard.notes.firstOrNull()?.value?.let {
            smackVCard.setField("DESC", it)
        }

        // UID
        ezVCard.uid?.value?.let {
            smackVCard.setField("UID", it)
        }

        return smackVCard
    }

    /**
     * Convert Smack VCard to ez-vcard string
     */
    fun smackVCardToString(smackVCard: SmackVCard): String {
        val ezVCard = VCard()

        smackVCard.firstName?.let { fn ->
            smackVCard.lastName?.let { ln ->
                val name = ezvcard.property.StructuredName()
                name.given = fn
                name.family = ln
                smackVCard.middleName?.let { name.additionalNames.add(it) }
                ezVCard.structuredName = name
            }
        }

        val fn = listOfNotNull(smackVCard.firstName, smackVCard.middleName, smackVCard.lastName)
            .joinToString(" ")
        if (fn.isNotEmpty()) {
            ezVCard.setFormattedName(fn)
        }

        smackVCard.organization?.let { ezVCard.setOrganization(it) }
        smackVCard.emailWork?.let { ezVCard.addEmail(it) }

        val writer = StringWriter()
        VCardWriter(writer).use { vcardWriter ->
            vcardWriter.write(ezVCard)
        }
        return writer.toString()
    }

    data class SyncResult(
        val organizationsSynced: Int,
        val organizationsFailed: Int,
        val usersSynced: Int,
        val usersFailed: Int,
        val error: String? = null
    )

    data class RosterMember(
        val jid: String,
        val name: String,
        val isOnline: Boolean
    )
}

/**
 * Openfire event listener for organization changes
 */
interface OpenfireOrgEventListener {
    fun onUserJoined(orgId: String, userId: String)
    fun onUserLeft(orgId: String, userId: String)
    fun onOrgUpdated(orgId: String)
    fun onPresenceChanged(userId: String, isOnline: Boolean)
}
