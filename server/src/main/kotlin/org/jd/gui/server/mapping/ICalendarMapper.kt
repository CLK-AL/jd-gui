/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.mapping

import com.google.common.collect.MapDifference
import com.google.common.collect.Maps
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mu.KotlinLogging
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.component.*
import net.fortuna.ical4j.model.parameter.*
import net.fortuna.ical4j.model.property.*
import org.mapstruct.*
import java.io.StringReader
import java.io.StringWriter
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * RFC 5545 iCalendar Mapper using ical4j
 * Maps calendar data for CalDAV (Bedework) integration
 *
 * RFC 5545 Components supported:
 * - VCALENDAR: Calendar container
 * - VEVENT: Events
 * - VTODO: Tasks/To-dos
 * - VJOURNAL: Journal entries
 * - VFREEBUSY: Free/busy information
 * - VTIMEZONE: Timezone definitions
 * - VALARM: Alarms (within VEVENT/VTODO)
 *
 * RFC 5545 Properties supported:
 * - Calendar: PRODID, VERSION, CALSCALE, METHOD
 * - Descriptive: ATTACH, CATEGORIES, CLASS, COMMENT, DESCRIPTION, GEO, LOCATION, PERCENT-COMPLETE, PRIORITY, RESOURCES, STATUS, SUMMARY
 * - Date/Time: COMPLETED, DTEND, DUE, DTSTART, DURATION, FREEBUSY, TRANSP
 * - Timezone: TZID, TZNAME, TZOFFSETFROM, TZOFFSETTO, TZURL
 * - Relationship: ATTENDEE, CONTACT, ORGANIZER, RECURRENCE-ID, RELATED-TO, URL, UID
 * - Recurrence: EXDATE, RDATE, RRULE
 * - Alarm: ACTION, REPEAT, TRIGGER
 * - Change Management: CREATED, DTSTAMP, LAST-MODIFIED, SEQUENCE
 * - Misc: REQUEST-STATUS
 */
@Mapper(componentModel = "default")
abstract class ICalendarMapper {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val calendarBuilder = CalendarBuilder()
    private val calendarOutputter = CalendarOutputter()

    // =========================================================================
    // Calendar Parsing and Serialization
    // =========================================================================

    /**
     * Parse iCalendar string to ical4j Calendar
     */
    fun parseCalendar(icsContent: String): Calendar? {
        return try {
            calendarBuilder.build(StringReader(icsContent))
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse iCalendar" }
            null
        }
    }

    /**
     * Serialize ical4j Calendar to iCalendar string
     */
    fun serializeCalendar(calendar: Calendar): String {
        val writer = StringWriter()
        calendarOutputter.output(calendar, writer)
        return writer.toString()
    }

    // =========================================================================
    // Calendar to Map Conversion (for comparison)
    // =========================================================================

    /**
     * Convert Calendar to RFC-compliant Map
     */
    fun calendarToMap(calendar: Calendar): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        // Calendar properties
        map["PRODID"] = calendar.getProperty<ProdId>(Property.PRODID)?.value
        map["VERSION"] = calendar.getProperty<Version>(Property.VERSION)?.value
        map["CALSCALE"] = calendar.getProperty<CalScale>(Property.CALSCALE)?.value
        map["METHOD"] = calendar.getProperty<Method>(Property.METHOD)?.value

        // Components
        map["VEVENT"] = calendar.getComponents<VEvent>(Component.VEVENT).map { eventToMap(it) }
        map["VTODO"] = calendar.getComponents<VTodo>(Component.VTODO).map { todoToMap(it) }
        map["VJOURNAL"] = calendar.getComponents<VJournal>(Component.VJOURNAL).map { journalToMap(it) }
        map["VFREEBUSY"] = calendar.getComponents<VFreeBusy>(Component.VFREEBUSY).map { freeBusyToMap(it) }
        map["VTIMEZONE"] = calendar.getComponents<VTimeZone>(Component.VTIMEZONE).map { timezoneToMap(it) }

        return map.filterValues { it != null && it != emptyList<Any>() }
    }

    /**
     * Convert VEVENT to Map
     */
    fun eventToMap(event: VEvent): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        // Unique identifier
        map["UID"] = event.uid?.value

        // Descriptive properties
        map["SUMMARY"] = event.summary?.value
        map["DESCRIPTION"] = event.description?.value
        map["LOCATION"] = event.location?.value
        map["CATEGORIES"] = event.getProperty<Categories>(Property.CATEGORIES)?.categories?.toList()
        map["CLASS"] = event.classification?.value
        map["STATUS"] = event.status?.value
        map["PRIORITY"] = event.priority?.level
        map["GEO"] = event.geographicPos?.let { mapOf("lat" to it.latitude, "lon" to it.longitude) }
        map["RESOURCES"] = event.getProperty<Resources>(Property.RESOURCES)?.resources?.toList()
        map["COMMENT"] = event.getProperties<Comment>(Property.COMMENT).map { it.value }

        // Date/Time properties
        map["DTSTART"] = event.startDate?.let { mapDateTime(it) }
        map["DTEND"] = event.endDate?.let { mapDateTime(it) }
        map["DURATION"] = event.duration?.duration?.toString()
        map["TRANSP"] = event.transparency?.value

        // Recurrence properties
        map["RRULE"] = event.getProperty<RRule>(Property.RRULE)?.let { mapRRule(it) }
        map["RDATE"] = event.getProperties<RDate>(Property.RDATE).map { mapRDate(it) }
        map["EXDATE"] = event.getProperties<ExDate>(Property.EXDATE).map { mapExDate(it) }
        map["RECURRENCE-ID"] = event.recurrenceId?.let { mapDateTime(it) }

        // Relationship properties
        map["ORGANIZER"] = event.organizer?.let { mapOrganizer(it) }
        map["ATTENDEE"] = event.getProperties<Attendee>(Property.ATTENDEE).map { mapAttendee(it) }
        map["CONTACT"] = event.getProperties<Contact>(Property.CONTACT).map { it.value }
        map["URL"] = event.url?.value
        map["RELATED-TO"] = event.getProperties<RelatedTo>(Property.RELATED_TO).map { it.value }

        // Change management
        map["CREATED"] = event.created?.let { mapDateTime(it) }
        map["DTSTAMP"] = event.dateStamp?.let { mapDateTime(it) }
        map["LAST-MODIFIED"] = event.lastModified?.let { mapDateTime(it) }
        map["SEQUENCE"] = event.sequence?.sequenceNo

        // Alarms
        map["VALARM"] = event.alarms.map { alarmToMap(it) }

        // Attachments
        map["ATTACH"] = event.getProperties<Attach>(Property.ATTACH).map { mapAttach(it) }

        return map.filterValues { it != null && it != emptyList<Any>() }
    }

    /**
     * Convert VTODO to Map
     */
    fun todoToMap(todo: VTodo): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        map["UID"] = todo.uid?.value
        map["SUMMARY"] = todo.summary?.value
        map["DESCRIPTION"] = todo.description?.value
        map["LOCATION"] = todo.location?.value
        map["STATUS"] = todo.status?.value
        map["PRIORITY"] = todo.priority?.level
        map["PERCENT-COMPLETE"] = todo.percentComplete?.percentage
        map["DTSTART"] = todo.startDate?.let { mapDateTime(it) }
        map["DUE"] = todo.due?.let { mapDateTime(it) }
        map["COMPLETED"] = todo.dateCompleted?.let { mapDateTime(it) }
        map["DURATION"] = todo.duration?.duration?.toString()
        map["ORGANIZER"] = todo.organizer?.let { mapOrganizer(it) }
        map["ATTENDEE"] = todo.getProperties<Attendee>(Property.ATTENDEE).map { mapAttendee(it) }
        map["RRULE"] = todo.getProperty<RRule>(Property.RRULE)?.let { mapRRule(it) }
        map["CREATED"] = todo.created?.let { mapDateTime(it) }
        map["DTSTAMP"] = todo.dateStamp?.let { mapDateTime(it) }
        map["LAST-MODIFIED"] = todo.lastModified?.let { mapDateTime(it) }
        map["SEQUENCE"] = todo.sequence?.sequenceNo
        map["VALARM"] = todo.alarms.map { alarmToMap(it) }

        return map.filterValues { it != null && it != emptyList<Any>() }
    }

    /**
     * Convert VJOURNAL to Map
     */
    fun journalToMap(journal: VJournal): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        map["UID"] = journal.uid?.value
        map["SUMMARY"] = journal.summary?.value
        map["DESCRIPTION"] = journal.description?.value
        map["STATUS"] = journal.status?.value
        map["CLASS"] = journal.classification?.value
        map["CATEGORIES"] = journal.getProperty<Categories>(Property.CATEGORIES)?.categories?.toList()
        map["DTSTART"] = journal.startDate?.let { mapDateTime(it) }
        map["ORGANIZER"] = journal.organizer?.let { mapOrganizer(it) }
        map["ATTENDEE"] = journal.getProperties<Attendee>(Property.ATTENDEE).map { mapAttendee(it) }
        map["CREATED"] = journal.created?.let { mapDateTime(it) }
        map["DTSTAMP"] = journal.dateStamp?.let { mapDateTime(it) }
        map["LAST-MODIFIED"] = journal.lastModified?.let { mapDateTime(it) }
        map["SEQUENCE"] = journal.sequence?.sequenceNo

        return map.filterValues { it != null && it != emptyList<Any>() }
    }

    /**
     * Convert VFREEBUSY to Map
     */
    fun freeBusyToMap(freeBusy: VFreeBusy): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        map["UID"] = freeBusy.uid?.value
        map["DTSTART"] = freeBusy.startDate?.let { mapDateTime(it) }
        map["DTEND"] = freeBusy.endDate?.let { mapDateTime(it) }
        map["ORGANIZER"] = freeBusy.organizer?.let { mapOrganizer(it) }
        map["ATTENDEE"] = freeBusy.getProperties<Attendee>(Property.ATTENDEE).map { mapAttendee(it) }
        map["FREEBUSY"] = freeBusy.getProperties<FreeBusy>(Property.FREEBUSY).map { mapFreeBusyPeriods(it) }
        map["DTSTAMP"] = freeBusy.dateStamp?.let { mapDateTime(it) }
        map["URL"] = freeBusy.url?.value

        return map.filterValues { it != null && it != emptyList<Any>() }
    }

    /**
     * Convert VTIMEZONE to Map
     */
    fun timezoneToMap(timezone: VTimeZone): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        map["TZID"] = timezone.timeZoneId?.value
        map["TZURL"] = timezone.getProperty<TzUrl>(Property.TZURL)?.value
        map["LAST-MODIFIED"] = timezone.lastModified?.let { mapDateTime(it) }

        // Standard and Daylight components
        map["STANDARD"] = timezone.observances
            ?.filterIsInstance<Standard>()
            ?.map { observanceToMap(it) }

        map["DAYLIGHT"] = timezone.observances
            ?.filterIsInstance<Daylight>()
            ?.map { observanceToMap(it) }

        return map.filterValues { it != null && it != emptyList<Any>() }
    }

    /**
     * Convert timezone observance to Map
     */
    private fun observanceToMap(observance: Observance): Map<String, Any?> = mapOf(
        "DTSTART" to observance.startDate?.let { mapDateTime(it) },
        "TZOFFSETFROM" to observance.offsetFrom?.offset?.toString(),
        "TZOFFSETTO" to observance.offsetTo?.offset?.toString(),
        "TZNAME" to observance.getProperty<TzName>(Property.TZNAME)?.value,
        "RRULE" to observance.getProperty<RRule>(Property.RRULE)?.let { mapRRule(it) }
    )

    /**
     * Convert VALARM to Map
     */
    fun alarmToMap(alarm: VAlarm): Map<String, Any?> = mapOf(
        "ACTION" to alarm.action?.value,
        "TRIGGER" to alarm.trigger?.let { mapTrigger(it) },
        "DURATION" to alarm.duration?.duration?.toString(),
        "REPEAT" to alarm.repeat?.count,
        "DESCRIPTION" to alarm.description?.value,
        "SUMMARY" to alarm.summary?.value,
        "ATTENDEE" to alarm.getProperties<Attendee>(Property.ATTENDEE).map { mapAttendee(it) },
        "ATTACH" to alarm.getProperty<Attach>(Property.ATTACH)?.let { mapAttach(it) }
    )

    // =========================================================================
    // Property Mapping Helpers
    // =========================================================================

    private fun mapDateTime(prop: DateProperty): Map<String, Any?> = mapOf(
        "value" to prop.date?.toInstant()?.toString(),
        "tzid" to prop.getParameter<TzId>(Parameter.TZID)?.value,
        "isDate" to (prop.date is net.fortuna.ical4j.model.Date &&
                prop.date !is net.fortuna.ical4j.model.DateTime)
    )

    private fun mapRRule(rrule: RRule): Map<String, Any?> {
        val recur = rrule.recur
        return mapOf(
            "freq" to recur.frequency?.name,
            "interval" to recur.interval,
            "count" to recur.count,
            "until" to recur.until?.toInstant()?.toString(),
            "byday" to recur.dayList?.map { it.toString() },
            "bymonth" to recur.monthList?.toList(),
            "bymonthday" to recur.monthDayList?.toList(),
            "byyearday" to recur.yearDayList?.toList(),
            "byweekno" to recur.weekNoList?.toList(),
            "bysetpos" to recur.setPosList?.toList(),
            "wkst" to recur.weekStartDay?.name
        )
    }

    private fun mapRDate(rdate: RDate): Map<String, Any?> = mapOf(
        "dates" to rdate.dates?.map { it.toString() },
        "periods" to rdate.periods?.map { "${it.start}/${it.end ?: it.duration}" }
    )

    private fun mapExDate(exdate: ExDate): Map<String, Any?> = mapOf(
        "dates" to exdate.dates?.map { it.toString() }
    )

    private fun mapOrganizer(organizer: Organizer): Map<String, Any?> = mapOf(
        "uri" to organizer.calAddress?.toString(),
        "cn" to organizer.getParameter<Cn>(Parameter.CN)?.value,
        "dir" to organizer.getParameter<Dir>(Parameter.DIR)?.value,
        "sentBy" to organizer.getParameter<SentBy>(Parameter.SENT_BY)?.value
    )

    private fun mapAttendee(attendee: Attendee): Map<String, Any?> = mapOf(
        "uri" to attendee.calAddress?.toString(),
        "cn" to attendee.getParameter<Cn>(Parameter.CN)?.value,
        "role" to attendee.getParameter<Role>(Parameter.ROLE)?.value,
        "partstat" to attendee.getParameter<PartStat>(Parameter.PARTSTAT)?.value,
        "rsvp" to attendee.getParameter<Rsvp>(Parameter.RSVP)?.value,
        "cutype" to attendee.getParameter<CuType>(Parameter.CUTYPE)?.value,
        "delegatedFrom" to attendee.getParameter<DelegatedFrom>(Parameter.DELEGATED_FROM)?.value,
        "delegatedTo" to attendee.getParameter<DelegatedTo>(Parameter.DELEGATED_TO)?.value,
        "member" to attendee.getParameter<Member>(Parameter.MEMBER)?.value,
        "dir" to attendee.getParameter<Dir>(Parameter.DIR)?.value
    )

    private fun mapTrigger(trigger: Trigger): Map<String, Any?> = mapOf(
        "duration" to trigger.duration?.toString(),
        "dateTime" to trigger.dateTime?.toInstant()?.toString(),
        "related" to trigger.getParameter<Related>(Parameter.RELATED)?.value
    )

    private fun mapAttach(attach: Attach): Map<String, Any?> = mapOf(
        "uri" to attach.uri?.toString(),
        "binary" to attach.binary?.let { Base64.getEncoder().encodeToString(it) },
        "fmttype" to attach.getParameter<FmtType>(Parameter.FMTTYPE)?.value
    )

    private fun mapFreeBusyPeriods(freeBusy: FreeBusy): Map<String, Any?> = mapOf(
        "type" to freeBusy.getParameter<FbType>(Parameter.FBTYPE)?.value,
        "periods" to freeBusy.periods?.map { "${it.start}/${it.end ?: it.duration}" }
    )

    // =========================================================================
    // Data Class Conversion
    // =========================================================================

    /**
     * Convert Calendar to CalendarData
     */
    fun toCalendarData(calendar: Calendar): CalendarData {
        return CalendarData(
            prodId = calendar.getProperty<ProdId>(Property.PRODID)?.value ?: "-//JD-GUI//EN",
            version = calendar.getProperty<Version>(Property.VERSION)?.value ?: "2.0",
            calScale = calendar.getProperty<CalScale>(Property.CALSCALE)?.value ?: "GREGORIAN",
            method = calendar.getProperty<Method>(Property.METHOD)?.value,
            events = calendar.getComponents<VEvent>(Component.VEVENT).map { toEventData(it) },
            todos = calendar.getComponents<VTodo>(Component.VTODO).map { toTodoData(it) },
            journals = calendar.getComponents<VJournal>(Component.VJOURNAL).map { toJournalData(it) }
        )
    }

    /**
     * Convert VEVENT to EventData
     */
    fun toEventData(event: VEvent): EventData {
        return EventData(
            uid = event.uid?.value ?: UUID.randomUUID().toString(),
            summary = event.summary?.value ?: "",
            description = event.description?.value,
            location = event.location?.value,
            startTime = event.startDate?.date?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
            endTime = event.endDate?.date?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
            allDay = event.startDate?.date is net.fortuna.ical4j.model.Date &&
                    event.startDate?.date !is net.fortuna.ical4j.model.DateTime,
            status = event.status?.value,
            classification = event.classification?.value,
            priority = event.priority?.level,
            organizer = event.organizer?.calAddress?.toString(),
            attendees = event.getProperties<Attendee>(Property.ATTENDEE).map {
                AttendeeData(
                    email = it.calAddress?.toString()?.removePrefix("mailto:"),
                    name = it.getParameter<Cn>(Parameter.CN)?.value,
                    role = it.getParameter<Role>(Parameter.ROLE)?.value,
                    status = it.getParameter<PartStat>(Parameter.PARTSTAT)?.value,
                    rsvp = it.getParameter<Rsvp>(Parameter.RSVP)?.value?.toBoolean()
                )
            },
            categories = event.getProperty<Categories>(Property.CATEGORIES)?.categories?.toList() ?: emptyList(),
            recurrence = event.getProperty<RRule>(Property.RRULE)?.let { toRecurrenceData(it) },
            alarms = event.alarms.map { toAlarmData(it) },
            created = event.created?.date?.toInstant(),
            lastModified = event.lastModified?.date?.toInstant(),
            sequence = event.sequence?.sequenceNo ?: 0
        )
    }

    /**
     * Convert VTODO to TodoData
     */
    fun toTodoData(todo: VTodo): TodoData {
        return TodoData(
            uid = todo.uid?.value ?: UUID.randomUUID().toString(),
            summary = todo.summary?.value ?: "",
            description = todo.description?.value,
            location = todo.location?.value,
            startTime = todo.startDate?.date?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
            dueTime = todo.due?.date?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
            completedTime = todo.dateCompleted?.date?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
            status = todo.status?.value,
            priority = todo.priority?.level,
            percentComplete = todo.percentComplete?.percentage,
            organizer = todo.organizer?.calAddress?.toString(),
            categories = todo.getProperty<Categories>(Property.CATEGORIES)?.categories?.toList() ?: emptyList(),
            created = todo.created?.date?.toInstant(),
            lastModified = todo.lastModified?.date?.toInstant(),
            sequence = todo.sequence?.sequenceNo ?: 0
        )
    }

    /**
     * Convert VJOURNAL to JournalData
     */
    fun toJournalData(journal: VJournal): JournalData {
        return JournalData(
            uid = journal.uid?.value ?: UUID.randomUUID().toString(),
            summary = journal.summary?.value ?: "",
            description = journal.description?.value,
            startTime = journal.startDate?.date?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
            status = journal.status?.value,
            classification = journal.classification?.value,
            categories = journal.getProperty<Categories>(Property.CATEGORIES)?.categories?.toList() ?: emptyList(),
            created = journal.created?.date?.toInstant(),
            lastModified = journal.lastModified?.date?.toInstant(),
            sequence = journal.sequence?.sequenceNo ?: 0
        )
    }

    /**
     * Convert RRule to RecurrenceData
     */
    fun toRecurrenceData(rrule: RRule): RecurrenceData {
        val recur = rrule.recur
        return RecurrenceData(
            frequency = recur.frequency?.name ?: "DAILY",
            interval = recur.interval.takeIf { it > 0 } ?: 1,
            count = recur.count.takeIf { it > 0 },
            until = recur.until?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate(),
            byDay = recur.dayList?.map { it.toString() } ?: emptyList(),
            byMonth = recur.monthList?.toList() ?: emptyList(),
            byMonthDay = recur.monthDayList?.toList() ?: emptyList(),
            weekStart = recur.weekStartDay?.name
        )
    }

    /**
     * Convert VAlarm to AlarmData
     */
    fun toAlarmData(alarm: VAlarm): AlarmData {
        return AlarmData(
            action = alarm.action?.value ?: "DISPLAY",
            trigger = alarm.trigger?.let {
                if (it.duration != null) {
                    it.duration.toString()
                } else {
                    it.dateTime?.toInstant()?.toString()
                }
            } ?: "-PT15M",
            description = alarm.description?.value,
            summary = alarm.summary?.value,
            repeat = alarm.repeat?.count,
            duration = alarm.duration?.duration?.toString()
        )
    }

    // =========================================================================
    // Data Class to ical4j Conversion
    // =========================================================================

    /**
     * Convert CalendarData to ical4j Calendar
     */
    fun toIcal4jCalendar(data: CalendarData): Calendar {
        val calendar = Calendar()
        calendar.properties.add(ProdId(data.prodId))
        calendar.properties.add(Version.VERSION_2_0)
        calendar.properties.add(CalScale.GREGORIAN)
        data.method?.let { calendar.properties.add(Method(it)) }

        data.events.forEach { calendar.components.add(toIcal4jEvent(it)) }
        data.todos.forEach { calendar.components.add(toIcal4jTodo(it)) }
        data.journals.forEach { calendar.components.add(toIcal4jJournal(it)) }

        return calendar
    }

    /**
     * Convert EventData to ical4j VEvent
     */
    fun toIcal4jEvent(data: EventData): VEvent {
        val event = VEvent()

        event.properties.add(Uid(data.uid))
        event.properties.add(Summary(data.summary))
        data.description?.let { event.properties.add(Description(it)) }
        data.location?.let { event.properties.add(Location(it)) }

        // Date/Time
        data.startTime?.let {
            val dtStart = if (data.allDay) {
                DtStart(net.fortuna.ical4j.model.Date(Date.from(it.atZone(ZoneId.systemDefault()).toInstant())))
            } else {
                DtStart(net.fortuna.ical4j.model.DateTime(Date.from(it.atZone(ZoneId.systemDefault()).toInstant())))
            }
            event.properties.add(dtStart)
        }

        data.endTime?.let {
            val dtEnd = if (data.allDay) {
                DtEnd(net.fortuna.ical4j.model.Date(Date.from(it.atZone(ZoneId.systemDefault()).toInstant())))
            } else {
                DtEnd(net.fortuna.ical4j.model.DateTime(Date.from(it.atZone(ZoneId.systemDefault()).toInstant())))
            }
            event.properties.add(dtEnd)
        }

        // Status
        data.status?.let { event.properties.add(Status(it)) }
        data.classification?.let { event.properties.add(Clazz(it)) }
        data.priority?.let { event.properties.add(Priority(it)) }

        // Organizer
        data.organizer?.let {
            val uri = if (it.startsWith("mailto:")) it else "mailto:$it"
            event.properties.add(Organizer(java.net.URI(uri)))
        }

        // Attendees
        data.attendees.forEach { attendee ->
            val att = Attendee(java.net.URI("mailto:${attendee.email}"))
            attendee.name?.let { att.parameters.add(Cn(it)) }
            attendee.role?.let { att.parameters.add(Role(it)) }
            attendee.status?.let { att.parameters.add(PartStat(it)) }
            attendee.rsvp?.let { att.parameters.add(Rsvp(it)) }
            event.properties.add(att)
        }

        // Categories
        if (data.categories.isNotEmpty()) {
            event.properties.add(Categories(TextList(data.categories.toTypedArray())))
        }

        // Recurrence
        data.recurrence?.let {
            val rrule = toIcal4jRRule(it)
            event.properties.add(rrule)
        }

        // Alarms
        data.alarms.forEach {
            event.components.add(toIcal4jAlarm(it))
        }

        // Timestamps
        event.properties.add(DtStamp(net.fortuna.ical4j.model.DateTime(Date())))
        data.created?.let { event.properties.add(Created(net.fortuna.ical4j.model.DateTime(Date.from(it)))) }
        data.lastModified?.let { event.properties.add(LastModified(net.fortuna.ical4j.model.DateTime(Date.from(it)))) }
        event.properties.add(Sequence(data.sequence))

        return event
    }

    /**
     * Convert TodoData to ical4j VTodo
     */
    fun toIcal4jTodo(data: TodoData): VTodo {
        val todo = VTodo()

        todo.properties.add(Uid(data.uid))
        todo.properties.add(Summary(data.summary))
        data.description?.let { todo.properties.add(Description(it)) }
        data.location?.let { todo.properties.add(Location(it)) }
        data.status?.let { todo.properties.add(Status(it)) }
        data.priority?.let { todo.properties.add(Priority(it)) }
        data.percentComplete?.let { todo.properties.add(PercentComplete(it)) }

        data.startTime?.let {
            todo.properties.add(DtStart(net.fortuna.ical4j.model.DateTime(Date.from(it.atZone(ZoneId.systemDefault()).toInstant()))))
        }
        data.dueTime?.let {
            todo.properties.add(Due(net.fortuna.ical4j.model.DateTime(Date.from(it.atZone(ZoneId.systemDefault()).toInstant()))))
        }
        data.completedTime?.let {
            todo.properties.add(Completed(net.fortuna.ical4j.model.DateTime(Date.from(it.atZone(ZoneId.systemDefault()).toInstant()))))
        }

        todo.properties.add(DtStamp(net.fortuna.ical4j.model.DateTime(Date())))
        todo.properties.add(Sequence(data.sequence))

        return todo
    }

    /**
     * Convert JournalData to ical4j VJournal
     */
    fun toIcal4jJournal(data: JournalData): VJournal {
        val journal = VJournal()

        journal.properties.add(Uid(data.uid))
        journal.properties.add(Summary(data.summary))
        data.description?.let { journal.properties.add(Description(it)) }
        data.status?.let { journal.properties.add(Status(it)) }
        data.classification?.let { journal.properties.add(Clazz(it)) }

        data.startTime?.let {
            journal.properties.add(DtStart(net.fortuna.ical4j.model.DateTime(Date.from(it.atZone(ZoneId.systemDefault()).toInstant()))))
        }

        journal.properties.add(DtStamp(net.fortuna.ical4j.model.DateTime(Date())))
        journal.properties.add(Sequence(data.sequence))

        return journal
    }

    /**
     * Convert RecurrenceData to ical4j RRule
     */
    fun toIcal4jRRule(data: RecurrenceData): RRule {
        val builder = Recur.Builder()
            .frequency(Recur.Frequency.valueOf(data.frequency))
            .interval(data.interval)

        data.count?.let { builder.count(it) }
        data.until?.let { builder.until(net.fortuna.ical4j.model.Date(Date.from(it.atStartOfDay(ZoneId.systemDefault()).toInstant()))) }

        if (data.byDay.isNotEmpty()) {
            val dayList = WeekDayList()
            data.byDay.forEach { dayList.add(WeekDay.getWeekDay(it)) }
            builder.dayList(dayList)
        }

        if (data.byMonth.isNotEmpty()) {
            val monthList = NumberList()
            data.byMonth.forEach { monthList.add(it) }
            builder.monthList(monthList)
        }

        if (data.byMonthDay.isNotEmpty()) {
            val monthDayList = NumberList()
            data.byMonthDay.forEach { monthDayList.add(it) }
            builder.monthDayList(monthDayList)
        }

        data.weekStart?.let { builder.weekStartDay(WeekDay.getWeekDay(it)) }

        return RRule(builder.build())
    }

    /**
     * Convert AlarmData to ical4j VAlarm
     */
    fun toIcal4jAlarm(data: AlarmData): VAlarm {
        val alarm = VAlarm()

        alarm.properties.add(Action(data.action))

        // Parse trigger
        if (data.trigger.startsWith("-P") || data.trigger.startsWith("P")) {
            alarm.properties.add(Trigger(java.time.Duration.parse(data.trigger)))
        } else {
            alarm.properties.add(Trigger(net.fortuna.ical4j.model.DateTime(data.trigger)))
        }

        data.description?.let { alarm.properties.add(Description(it)) }
        data.summary?.let { alarm.properties.add(Summary(it)) }
        data.repeat?.let { alarm.properties.add(Repeat(it)) }
        data.duration?.let { alarm.properties.add(Duration(java.time.Duration.parse(it))) }

        return alarm
    }

    // =========================================================================
    // Difference Computation
    // =========================================================================

    /**
     * Compute difference between two calendars using Guava MapDifference
     */
    fun computeDifference(local: Calendar, remote: Calendar): CalendarDiff {
        val localMap = calendarToMap(local)
        val remoteMap = calendarToMap(remote)

        @Suppress("UNCHECKED_CAST")
        val difference: MapDifference<String, Any?> = Maps.difference(
            localMap as Map<String, Any?>,
            remoteMap as Map<String, Any?>
        )

        return CalendarDiff(
            localOnly = difference.entriesOnlyOnLeft(),
            remoteOnly = difference.entriesOnlyOnRight(),
            differing = difference.entriesDiffering().mapValues { (_, valueDiff) ->
                CalendarValueDiff(valueDiff.leftValue(), valueDiff.rightValue())
            },
            common = difference.entriesInCommon(),
            areEqual = difference.areEqual()
        )
    }

    companion object {
        val INSTANCE = ICalendarMapperImpl()
    }
}

/**
 * Implementation of ICalendarMapper
 */
class ICalendarMapperImpl : ICalendarMapper()

// =========================================================================
// Data Classes
// =========================================================================

@Serializable
data class CalendarData(
    val prodId: String = "-//JD-GUI//EN",
    val version: String = "2.0",
    val calScale: String = "GREGORIAN",
    val method: String? = null,
    val events: List<EventData> = emptyList(),
    val todos: List<TodoData> = emptyList(),
    val journals: List<JournalData> = emptyList()
)

@Serializable
data class EventData(
    val uid: String,
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val startTime: LocalDateTime? = null,
    val endTime: LocalDateTime? = null,
    val allDay: Boolean = false,
    val status: String? = null,
    val classification: String? = null,
    val priority: Int? = null,
    val organizer: String? = null,
    val attendees: List<AttendeeData> = emptyList(),
    val categories: List<String> = emptyList(),
    val recurrence: RecurrenceData? = null,
    val alarms: List<AlarmData> = emptyList(),
    @Serializable(with = InstantSerializer::class)
    val created: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val lastModified: Instant? = null,
    val sequence: Int = 0
)

@Serializable
data class TodoData(
    val uid: String,
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val startTime: LocalDateTime? = null,
    val dueTime: LocalDateTime? = null,
    val completedTime: LocalDateTime? = null,
    val status: String? = null,
    val priority: Int? = null,
    val percentComplete: Int? = null,
    val organizer: String? = null,
    val categories: List<String> = emptyList(),
    @Serializable(with = InstantSerializer::class)
    val created: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val lastModified: Instant? = null,
    val sequence: Int = 0
)

@Serializable
data class JournalData(
    val uid: String,
    val summary: String,
    val description: String? = null,
    val startTime: LocalDateTime? = null,
    val status: String? = null,
    val classification: String? = null,
    val categories: List<String> = emptyList(),
    @Serializable(with = InstantSerializer::class)
    val created: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val lastModified: Instant? = null,
    val sequence: Int = 0
)

@Serializable
data class AttendeeData(
    val email: String? = null,
    val name: String? = null,
    val role: String? = null,
    val status: String? = null,
    val rsvp: Boolean? = null
)

@Serializable
data class RecurrenceData(
    val frequency: String,
    val interval: Int = 1,
    val count: Int? = null,
    @Serializable(with = LocalDateSerializer::class)
    val until: LocalDate? = null,
    val byDay: List<String> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val weekStart: String? = null
)

@Serializable
data class AlarmData(
    val action: String = "DISPLAY",
    val trigger: String = "-PT15M",
    val description: String? = null,
    val summary: String? = null,
    val repeat: Int? = null,
    val duration: String? = null
)

data class CalendarDiff(
    val localOnly: Map<String, Any?>,
    val remoteOnly: Map<String, Any?>,
    val differing: Map<String, CalendarValueDiff>,
    val common: Map<String, Any?>,
    val areEqual: Boolean
)

data class CalendarValueDiff(
    val localValue: Any?,
    val remoteValue: Any?
)

// Serializers for java.time types
object InstantSerializer : kotlinx.serialization.KSerializer<Instant> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("Instant", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Instant = Instant.parse(decoder.decodeString())
}

object LocalDateSerializer : kotlinx.serialization.KSerializer<LocalDate> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("LocalDate", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

// Extension functions
fun Calendar.toMap(): Map<String, Any?> = ICalendarMapper.INSTANCE.calendarToMap(this)
fun Calendar.toCalendarData(): CalendarData = ICalendarMapper.INSTANCE.toCalendarData(this)
fun CalendarData.toIcal4jCalendar(): Calendar = ICalendarMapper.INSTANCE.toIcal4jCalendar(this)
fun String.parseICalendar(): Calendar? = ICalendarMapper.INSTANCE.parseCalendar(this)
fun Calendar.serialize(): String = ICalendarMapper.INSTANCE.serializeCalendar(this)
