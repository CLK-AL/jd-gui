/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.bedework

import ezvcard.VCard
import ezvcard.io.text.VCardReader
import ezvcard.io.text.VCardWriter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.*
import org.jd.gui.server.auth.Organization
import org.jd.gui.server.auth.User
import org.jd.gui.server.auth.VCardOrgManager
import org.jd.gui.server.config.BedeworkConfig
import java.io.StringReader
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Bedework CalDAV/CardDAV service for calendar and vCard synchronization
 * Provides enterprise calendar and contacts management
 */
class BedeworkService(
    private val config: BedeworkConfig,
    private val vcardManager: VCardOrgManager
) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(
                        username = config.adminUsername,
                        password = config.adminPassword
                    )
                }
                sendWithoutRequest { true }
            }
        }
    }

    // Sync state tracking
    private val lastSyncTimes = ConcurrentHashMap<String, Long>()
    private val syncTokens = ConcurrentHashMap<String, String>()

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Background sync job
    private var syncJob: Job? = null

    /**
     * Initialize Bedework service
     */
    suspend fun initialize(): Boolean {
        if (!config.enabled) {
            logger.info { "Bedework integration is disabled" }
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                // Test connection to Bedework
                val response = httpClient.get("${config.baseUrl}/.well-known/caldav")
                if (response.status.isSuccess() || response.status == HttpStatusCode.MovedPermanently) {
                    logger.info { "Connected to Bedework at ${config.baseUrl}" }

                    // Start background sync
                    startBackgroundSync()
                    true
                } else {
                    logger.error { "Failed to connect to Bedework: ${response.status}" }
                    false
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to initialize Bedework service" }
                false
            }
        }
    }

    /**
     * Start background sync job
     */
    private fun startBackgroundSync() {
        syncJob = scope.launch {
            while (isActive) {
                try {
                    syncAllContacts()
                    delay(config.syncIntervalMs)
                } catch (e: Exception) {
                    logger.error(e) { "Background sync error" }
                    delay(config.syncIntervalMs * 2) // Back off on error
                }
            }
        }
    }

    /**
     * Sync vCard to Bedework CardDAV
     */
    suspend fun syncVCardToCardDav(orgId: String, userId: String): Boolean {
        if (!config.enabled || !config.enableContactsSync) return false

        return withContext(Dispatchers.IO) {
            try {
                val vcard = vcardManager.getUserVCard(orgId, userId) ?: return@withContext false
                val vcardString = vCardToString(vcard)

                val url = "${config.getCardDavUrl(userId)}/${config.defaultAddressBookName}/$userId.vcf"

                val response = httpClient.put(url) {
                    contentType(ContentType("text", "vcard"))
                    setBody(vcardString)
                }

                if (response.status.isSuccess()) {
                    lastSyncTimes["carddav-$userId"] = System.currentTimeMillis()
                    logger.debug { "Synced vCard to Bedework for user $userId" }
                    true
                } else {
                    logger.error { "Failed to sync vCard to Bedework: ${response.status}" }
                    false
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to sync vCard to CardDAV" }
                false
            }
        }
    }

    /**
     * Sync organization vCard to Bedework CardDAV
     */
    suspend fun syncOrgVCardToCardDav(org: Organization): Boolean {
        if (!config.enabled || !config.enableContactsSync) return false

        return withContext(Dispatchers.IO) {
            try {
                val vcard = vcardManager.getOrganizationVCard(org.id) ?: return@withContext false
                val vcardString = vCardToString(vcard)

                val url = "${config.getOrgAddressBookUrl(org.id)}/${org.id}.vcf"

                val response = httpClient.put(url) {
                    contentType(ContentType("text", "vcard"))
                    setBody(vcardString)
                }

                if (response.status.isSuccess()) {
                    lastSyncTimes["carddav-org-${org.id}"] = System.currentTimeMillis()
                    logger.debug { "Synced org vCard to Bedework: ${org.name}" }
                    true
                } else {
                    logger.error { "Failed to sync org vCard to Bedework: ${response.status}" }
                    false
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to sync org vCard to CardDAV" }
                false
            }
        }
    }

    /**
     * Fetch vCard from Bedework CardDAV
     */
    suspend fun fetchVCardFromCardDav(userId: String): VCard? {
        if (!config.enabled) return null

        return withContext(Dispatchers.IO) {
            try {
                val url = "${config.getCardDavUrl(userId)}/${config.defaultAddressBookName}/$userId.vcf"

                val response = httpClient.get(url)
                if (response.status.isSuccess()) {
                    val vcardText = response.bodyAsText()
                    VCardReader(StringReader(vcardText)).use { reader ->
                        reader.readNext()
                    }
                } else null
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch vCard from CardDAV" }
                null
            }
        }
    }

    /**
     * Create calendar event
     */
    suspend fun createCalendarEvent(
        userId: String,
        event: CalendarEvent
    ): Boolean {
        if (!config.enabled || !config.enableCalendarSync) return false

        return withContext(Dispatchers.IO) {
            try {
                val calendar = createICalendar(event)
                val icsContent = calendarToString(calendar)

                val url = "${config.getCalDavUrl(userId)}/${config.defaultCalendarName}/${event.uid}.ics"

                val response = httpClient.put(url) {
                    contentType(ContentType("text", "calendar"))
                    setBody(icsContent)
                }

                response.status.isSuccess().also {
                    if (it) logger.debug { "Created calendar event for user $userId: ${event.summary}" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to create calendar event" }
                false
            }
        }
    }

    /**
     * Get calendar events for user
     */
    suspend fun getCalendarEvents(
        userId: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<CalendarEvent> {
        if (!config.enabled || !config.enableCalendarSync) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val url = "${config.getCalDavUrl(userId)}/${config.defaultCalendarName}/"

                // REPORT request for time-range query
                val reportBody = """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <C:calendar-query xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                      <D:prop>
                        <D:getetag/>
                        <C:calendar-data/>
                      </D:prop>
                      <C:filter>
                        <C:comp-filter name="VCALENDAR">
                          <C:comp-filter name="VEVENT">
                            <C:time-range start="${formatCalDavDate(startDate)}" end="${formatCalDavDate(endDate)}"/>
                          </C:comp-filter>
                        </C:comp-filter>
                      </C:filter>
                    </C:calendar-query>
                """.trimIndent()

                val response = httpClient.request(url) {
                    method = HttpMethod("REPORT")
                    contentType(ContentType.Application.Xml)
                    header("Depth", "1")
                    setBody(reportBody)
                }

                if (response.status.isSuccess()) {
                    parseCalendarResponse(response.bodyAsText())
                } else emptyList()
            } catch (e: Exception) {
                logger.error(e) { "Failed to get calendar events" }
                emptyList()
            }
        }
    }

    /**
     * Create organization calendar
     */
    suspend fun createOrgCalendar(orgId: String, orgName: String): Boolean {
        if (!config.enabled || !config.enableCalendarSync) return false

        return withContext(Dispatchers.IO) {
            try {
                val url = "${config.getOrgCalendarUrl(orgId)}/"

                val mkcolBody = """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <D:mkcol xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                      <D:set>
                        <D:prop>
                          <D:resourcetype>
                            <D:collection/>
                            <C:calendar/>
                          </D:resourcetype>
                          <D:displayname>$orgName Calendar</D:displayname>
                        </D:prop>
                      </D:set>
                    </D:mkcol>
                """.trimIndent()

                val response = httpClient.request(url) {
                    method = HttpMethod("MKCOL")
                    contentType(ContentType.Application.Xml)
                    setBody(mkcolBody)
                }

                response.status.isSuccess().also {
                    if (it) logger.info { "Created org calendar for $orgName" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to create org calendar" }
                false
            }
        }
    }

    /**
     * Create organization address book
     */
    suspend fun createOrgAddressBook(orgId: String, orgName: String): Boolean {
        if (!config.enabled || !config.enableContactsSync) return false

        return withContext(Dispatchers.IO) {
            try {
                val url = "${config.getOrgAddressBookUrl(orgId)}/"

                val mkcolBody = """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <D:mkcol xmlns:D="DAV:" xmlns:CARD="urn:ietf:params:xml:ns:carddav">
                      <D:set>
                        <D:prop>
                          <D:resourcetype>
                            <D:collection/>
                            <CARD:addressbook/>
                          </D:resourcetype>
                          <D:displayname>$orgName Contacts</D:displayname>
                        </D:prop>
                      </D:set>
                    </D:mkcol>
                """.trimIndent()

                val response = httpClient.request(url) {
                    method = HttpMethod("MKCOL")
                    contentType(ContentType.Application.Xml)
                    setBody(mkcolBody)
                }

                response.status.isSuccess().also {
                    if (it) logger.info { "Created org address book for $orgName" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to create org address book" }
                false
            }
        }
    }

    /**
     * Sync all contacts to Bedework
     */
    suspend fun syncAllContacts(): SyncResult {
        var contactsSynced = 0
        var contactsFailed = 0

        val organizations = vcardManager.listOrganizations()
        for (org in organizations) {
            if (syncOrgVCardToCardDav(org)) {
                contactsSynced++
            } else {
                contactsFailed++
            }

            val users = vcardManager.listUsers(org.id)
            for (user in users) {
                if (syncVCardToCardDav(org.id, user.id)) {
                    contactsSynced++
                } else {
                    contactsFailed++
                }
            }
        }

        return SyncResult(contactsSynced, contactsFailed, 0, 0)
    }

    /**
     * Delete calendar event
     */
    suspend fun deleteCalendarEvent(userId: String, eventUid: String): Boolean {
        if (!config.enabled) return false

        return withContext(Dispatchers.IO) {
            try {
                val url = "${config.getCalDavUrl(userId)}/${config.defaultCalendarName}/$eventUid.ics"
                val response = httpClient.delete(url)
                response.status.isSuccess()
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete calendar event" }
                false
            }
        }
    }

    /**
     * Delete vCard from CardDAV
     */
    suspend fun deleteVCard(userId: String): Boolean {
        if (!config.enabled) return false

        return withContext(Dispatchers.IO) {
            try {
                val url = "${config.getCardDavUrl(userId)}/${config.defaultAddressBookName}/$userId.vcf"
                val response = httpClient.delete(url)
                response.status.isSuccess()
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete vCard from CardDAV" }
                false
            }
        }
    }

    /**
     * Shutdown service
     */
    fun shutdown() {
        syncJob?.cancel()
        scope.cancel()
        httpClient.close()
    }

    // Helper methods

    private fun vCardToString(vcard: VCard): String {
        val writer = StringWriter()
        VCardWriter(writer).use { vcardWriter ->
            vcardWriter.write(vcard)
        }
        return writer.toString()
    }

    private fun createICalendar(event: CalendarEvent): Calendar {
        val calendar = Calendar()
        calendar.properties.add(ProdId("-//JD-GUI//EN"))
        calendar.properties.add(Version.VERSION_2_0)
        calendar.properties.add(CalScale.GREGORIAN)

        val vevent = VEvent()
        vevent.properties.add(Uid(event.uid))
        vevent.properties.add(Summary(event.summary))

        event.description?.let {
            vevent.properties.add(Description(it))
        }

        event.location?.let {
            vevent.properties.add(Location(it))
        }

        // Add start/end times
        val startDateTime = net.fortuna.ical4j.model.DateTime(
            Date.from(event.startTime.atZone(ZoneId.systemDefault()).toInstant())
        )
        val endDateTime = net.fortuna.ical4j.model.DateTime(
            Date.from(event.endTime.atZone(ZoneId.systemDefault()).toInstant())
        )

        vevent.properties.add(DtStart(startDateTime))
        vevent.properties.add(DtEnd(endDateTime))
        vevent.properties.add(DtStamp(net.fortuna.ical4j.model.DateTime(Date())))

        calendar.components.add(vevent)
        return calendar
    }

    private fun calendarToString(calendar: Calendar): String {
        val writer = StringWriter()
        CalendarOutputter().output(calendar, writer)
        return writer.toString()
    }

    private fun formatCalDavDate(dateTime: LocalDateTime): String {
        return dateTime.atZone(ZoneId.of("UTC"))
            .toInstant()
            .toString()
            .replace("-", "")
            .replace(":", "")
            .replace(".", "")
            .take(15) + "Z"
    }

    private fun parseCalendarResponse(xmlResponse: String): List<CalendarEvent> {
        // Simplified parser - in production use proper XML parsing
        val events = mutableListOf<CalendarEvent>()

        try {
            val calendarDataPattern = Regex("<C:calendar-data>([\\s\\S]*?)</C:calendar-data>")
            calendarDataPattern.findAll(xmlResponse).forEach { match ->
                val icsData = match.groupValues[1]
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")

                try {
                    val builder = CalendarBuilder()
                    val calendar = builder.build(StringReader(icsData))

                    calendar.components.filterIsInstance<VEvent>().forEach { vevent ->
                        events.add(CalendarEvent(
                            uid = vevent.uid?.value ?: UUID.randomUUID().toString(),
                            summary = vevent.summary?.value ?: "",
                            description = vevent.description?.value,
                            location = vevent.location?.value,
                            startTime = vevent.startDate?.date?.toInstant()
                                ?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
                                ?: LocalDateTime.now(),
                            endTime = vevent.endDate?.date?.toInstant()
                                ?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
                                ?: LocalDateTime.now()
                        ))
                    }
                } catch (e: Exception) {
                    logger.debug { "Failed to parse individual event: ${e.message}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse calendar response" }
        }

        return events
    }

    data class SyncResult(
        val contactsSynced: Int,
        val contactsFailed: Int,
        val eventsSynced: Int,
        val eventsFailed: Int
    )
}

/**
 * Calendar event data class
 */
data class CalendarEvent(
    val uid: String = UUID.randomUUID().toString(),
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val attendees: List<String> = emptyList(),
    val organizer: String? = null,
    val isAllDay: Boolean = false
)
