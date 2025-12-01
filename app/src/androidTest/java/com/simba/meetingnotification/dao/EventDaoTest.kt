package com.simba.meetingnotification.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simba.meetingnotification.ui.data.ContactDatabase
import com.simba.meetingnotification.ui.data.dao.ContactDao
import com.simba.meetingnotification.ui.data.dao.EventDao
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.entities.Event
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Instrumented tests for EventDao using in-memory Room database.
 *
 * These tests verify event CRUD operations, date-based queries,
 * relations to contacts, and notification status tracking.
 *
 * Run with: ./gradlew connectedAndroidTest --tests "*.dao.EventDaoTest"
 */
@RunWith(AndroidJUnit4::class)
class EventDaoTest {

    private lateinit var database: ContactDatabase
    private lateinit var eventDao: EventDao
    private lateinit var contactDao: ContactDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            ContactDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        eventDao = database.eventDao()
        contactDao = database.contactDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertEvent_andGetEventsForContact() = runTest {
        // Insert contact first
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val event = Event(
            eventId = 1,
            eventDate = "2025-12-25",
            eventTime = "10:00",
            contactOwnerId = 1,
            isNotified = false
        )

        eventDao.insert(event)
        val events = eventDao.getEvents(1)

        assertEquals(1, events.size)
        assertEquals("2025-12-25", events[0].eventDate)
        assertEquals("10:00", events[0].eventTime)
        assertEquals(false, events[0].isNotified)
    }

    @Test
    fun insertEvent_withDuplicateId_ignoresSecondInsert() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val event1 = Event(1, "2025-12-25", "10:00", 1, false)
        val event2 = Event(1, "2025-12-26", "14:00", 1, false)

        eventDao.insert(event1)
        eventDao.insert(event2) // Should be ignored

        val events = eventDao.getAllEventsStream()

        assertEquals(1, events.size)
        assertEquals("2025-12-25", events[0].eventDate) // First insert remains
    }

    @Test
    fun updateEvent_changesNotificationStatus() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val event = Event(1, "2025-12-25", "10:00", 1, false)
        eventDao.insert(event)

        val updated = event.copy(isNotified = true)
        eventDao.update(updated)

        val events = eventDao.getEvents(1)

        assertEquals(1, events.size)
        assertTrue(events[0].isNotified)
    }

    @Test
    fun deleteEvent_removesFromDatabase() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val event = Event(1, "2025-12-25", "10:00", 1, false)
        eventDao.insert(event)

        eventDao.delete(event)
        val events = eventDao.getEvents(1)

        assertEquals(0, events.size)
    }

    @Test
    fun deleteExpiredEvents_removesMultipleEvents() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val expiredEvents = listOf(
            Event(1, "2024-01-01", "10:00", 1, false),
            Event(2, "2024-01-02", "14:00", 1, false),
            Event(3, "2024-01-03", "16:00", 1, false)
        )

        expiredEvents.forEach { eventDao.insert(it) }

        eventDao.deleteExpiredEvents(expiredEvents)
        val events = eventDao.getEvents(1)

        assertEquals(0, events.size)
    }

    @Test
    fun getEventsAfterToday_returnsOnlyFutureEvents() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        val tomorrow = LocalDate.now().plusDays(1).toString()

        val events = listOf(
            Event(1, yesterday, "10:00", 1, false),
            Event(2, today, "14:00", 1, false),
            Event(3, tomorrow, "16:00", 1, false)
        )

        events.forEach { eventDao.insert(it) }

        val futureEvents = eventDao.getEventsAfterToday(today)

        assertEquals(2, futureEvents.size) // Today and tomorrow
        assertTrue(futureEvents.all { it.eventDate >= today })
    }

    @Test
    fun getExpiredEvents_returnsOnlyPastEvents() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        val tomorrow = LocalDate.now().plusDays(1).toString()

        val events = listOf(
            Event(1, yesterday, "10:00", 1, false),
            Event(2, today, "14:00", 1, false),
            Event(3, tomorrow, "16:00", 1, false)
        )

        events.forEach { eventDao.insert(it) }

        val expiredEvents = eventDao.getExpiredEvents(today)

        assertEquals(1, expiredEvents.size)
        assertEquals(yesterday, expiredEvents[0].eventDate)
    }

    @Test
    fun getNotNotifiedEventsAndFromActualDateTime_countsCorrectly() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val dateFrom = "2025-12-01"
        val dateTo = "2025-12-31"

        val events = listOf(
            Event(1, "2025-12-10", "10:00", 1, false), // Not notified, in range
            Event(2, "2025-12-15", "14:00", 1, false), // Not notified, in range
            Event(3, "2025-12-20", "16:00", 1, true),  // Notified, in range (excluded)
            Event(4, "2026-01-05", "10:00", 1, false)  // Not notified, out of range (excluded)
        )

        events.forEach { eventDao.insert(it) }

        val count = eventDao.getNotNotifiedEventsAndFromActualDateTime(dateFrom, dateTo)

        assertEquals(2, count)
    }

    @Test
    fun getAllEventsAndFromActualDateTime_returnsUnnotifiedInRange() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val dateFrom = "2025-12-01"
        val dateTo = "2025-12-31"

        val events = listOf(
            Event(1, "2025-12-10", "10:00", 1, false),
            Event(2, "2025-12-15", "14:00", 1, false),
            Event(3, "2025-12-20", "16:00", 1, true),  // Excluded (notified)
            Event(4, "2026-01-05", "10:00", 1, false)  // Excluded (out of range)
        )

        events.forEach { eventDao.insert(it) }

        val result = eventDao.getAllEventsAndFromActualDateTime(dateFrom, dateTo)

        assertEquals(2, result.size)
        assertTrue(result.all { !it.isNotified })
        assertTrue(result.all { it.eventDate in dateFrom..dateTo })
    }

    @Test
    fun getEventFromDateAndTimeParam_findsExactMatch() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val events = listOf(
            Event(1, "2025-12-25", "10:00", 1, false),
            Event(2, "2025-12-25", "14:00", 1, false),
            Event(3, "2025-12-26", "10:00", 1, false)
        )

        events.forEach { eventDao.insert(it) }

        val found = eventDao.getEventFromDateAndTimeParam("2025-12-25", "10:00").first()

        assertEquals(1, found.size)
        assertEquals("2025-12-25", found[0].eventDate)
        assertEquals("10:00", found[0].eventTime)
    }

    @Test
    fun getEventWithContact_returnsRelationCorrectly() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val event = Event(1, "2025-12-25", "10:00", 1, false)
        eventDao.insert(event)

        val eventWithContact = eventDao.getEventWithContact(1).first()

        assertEquals(1, eventWithContact.event.eventId)
        assertEquals("2025-12-25", eventWithContact.event.eventDate)
        assertEquals(1, eventWithContact.contact.id)
        assertEquals("John", eventWithContact.contact.firstName)
    }

    @Test
    fun insertAllEvents_insertsMultiple() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val events = listOf(
            Event(1, "2025-12-25", "10:00", 1, false),
            Event(2, "2025-12-26", "14:00", 1, false),
            Event(3, "2025-12-27", "16:00", 1, false)
        )

        eventDao.insertAll(events)
        val allEvents = eventDao.getAllEventsStream()

        assertEquals(3, allEvents.size)
    }

    @Test
    fun getEvents_forNonExistentContact_returnsEmpty() = runTest {
        val events = eventDao.getEvents(999)

        assertEquals(0, events.size)
    }

    @Test
    fun getAllEventsStream_returnsAllEventsOrderedById() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val events = listOf(
            Event(3, "2025-12-27", "16:00", 1, false),
            Event(1, "2025-12-25", "10:00", 1, false),
            Event(2, "2025-12-26", "14:00", 1, false)
        )

        events.forEach { eventDao.insert(it) }

        val allEvents = eventDao.getAllEventsStream()

        assertEquals(3, allEvents.size)
        assertEquals(1, allEvents[0].eventId)
        assertEquals(2, allEvents[1].eventId)
        assertEquals(3, allEvents[2].eventId)
    }

    @Test
    fun multipleContactsWithEvents_queriesCorrectly() = runTest {
        val contacts = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message 1"),
            Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Message 2")
        )
        contacts.forEach { contactDao.insert(it) }

        val events = listOf(
            Event(1, "2025-12-25", "10:00", 1, false),
            Event(2, "2025-12-25", "14:00", 1, false),
            Event(3, "2025-12-26", "10:00", 2, false)
        )
        events.forEach { eventDao.insert(it) }

        val contact1Events = eventDao.getEvents(1)
        val contact2Events = eventDao.getEvents(2)

        assertEquals(2, contact1Events.size)
        assertEquals(1, contact2Events.size)
    }
}
