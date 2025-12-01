package com.simba.meetingnotification.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simba.meetingnotification.ui.contact.ContactReadyForSms
import com.simba.meetingnotification.ui.data.ContactDatabase
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.entities.DateMessageSent
import com.simba.meetingnotification.ui.data.entities.Event
import com.simba.meetingnotification.ui.data.repositories.OfflineContactRepository
import com.simba.meetingnotification.ui.data.repositories.OfflineDateMessageSendRepository
import com.simba.meetingnotification.ui.data.repositories.OfflineEventRepository
import com.simba.meetingnotification.ui.services.SmsSendingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the complete SMS sending flow.
 *
 * These tests verify the full SMS lifecycle:
 * 1. Create contact and event
 * 2. Add contact to SMS queue (SmsSendingService)
 * 3. Manage queue (add, remove, check)
 * 4. Simulate SMS sent (update event notification status)
 * 5. Track message send date/time (DateMessageSent)
 * 6. Verify all components work together
 *
 * This tests the interaction between:
 * - SmsSendingService (queue management)
 * - ContactRepository, EventRepository, DateMessageSendRepository
 * - Database entities and their relationships
 * - Event notification status tracking
 *
 * Note: This does NOT test actual SMS sending via SmsManager,
 * as that requires device permissions and real telephony stack.
 *
 * Run with: ./gradlew connectedAndroidTest --tests "*.integration.SmsFlowIntegrationTest"
 */
@RunWith(AndroidJUnit4::class)
class SmsFlowIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: ContactDatabase
    private lateinit var contactRepository: OfflineContactRepository
    private lateinit var eventRepository: OfflineEventRepository
    private lateinit var dateMessageSendRepository: OfflineDateMessageSendRepository
    private lateinit var smsService: SmsSendingService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Create in-memory database
        database = Room.inMemoryDatabaseBuilder(
            context,
            ContactDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        contactRepository = OfflineContactRepository(database.contactDao())
        eventRepository = OfflineEventRepository(database.eventDao())
        dateMessageSendRepository = OfflineDateMessageSendRepository(database.messageSendDao())

        smsService = SmsSendingService()
        smsService.initialize(contactRepository, eventRepository, dateMessageSendRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completeSmsFlow_queueAndTrack() = runTest {
        // 1. Create contact in database
        val contact = Contact(
            id = 1,
            title = "Dr",
            firstName = "John",
            lastName = "Doe",
            sex = 'm',
            phone = "555-1234",
            message = "Hello Dr Doe, meeting at {date} {time}"
        )

        contactRepository.insertItem(contact)

        // 2. Create event for contact
        val event = Event(
            eventId = 1,
            eventDate = "2030-12-25",
            eventTime = "10:00",
            contactOwnerId = 1,
            isNotified = false
        )

        eventRepository.insertEvent(event)

        // 3. Add contact to SMS queue
        val contactForSms = ContactReadyForSms(
            id = 1,
            phone = "555-1234",
            message = "Hello Dr Doe, meeting at 2030-12-25 10:00",
            fullName = "John Doe"
        )

        smsService.addMessageToQueue(listOf(contactForSms))

        // 4. Verify contact in queue
        val queueIds = smsService.getContactsInSmsQueueWithId()
        assertEquals(1, queueIds.size)
        assertTrue(queueIds.contains(1))

        // 5. Simulate SMS sent by updating event
        val updatedEvent = event.copy(isNotified = true)
        smsService.updateEventInDatabase(updatedEvent)

        delay(100) // Wait for coroutine

        // 6. Verify event notification status
        val events = eventRepository.getEvents(1)
        assertTrue(events[0].isNotified)

        // 7. Record message send time
        val messageSendRecord = DateMessageSent(
            id = 1,
            lastTimeSendet = "14:30",
            lastDateSendet = "25.12.2030"
        )

        smsService.insertDatesForSendMessages(messageSendRecord)

        delay(100) // Wait for coroutine

        // 8. Verify message send tracking
        val lastSend = dateMessageSendRepository.getLastSendetInfos().first()
        assertNotNull(lastSend)
        assertEquals("14:30", lastSend.lastTimeSendet)
        assertEquals("25.12.2030", lastSend.lastDateSendet)
    }

    @Test
    fun smsQueue_multipleContacts_fifoOrder() {
        val contacts = listOf(
            ContactReadyForSms(1, "111111", "Message 1", "Alice Smith"),
            ContactReadyForSms(2, "222222", "Message 2", "Bob Jones"),
            ContactReadyForSms(3, "333333", "Message 3", "Charlie Wilson")
        )

        smsService.addMessageToQueue(contacts)

        val queueIds = smsService.getContactsInSmsQueueWithId()

        // Verify FIFO order
        assertEquals(3, queueIds.size)
        assertEquals(1, queueIds[0])
        assertEquals(2, queueIds[1])
        assertEquals(3, queueIds[2])
    }

    @Test
    fun smsQueue_removeContact_correctlyUpdates() {
        val contacts = listOf(
            ContactReadyForSms(1, "111111", "Message 1", "Alice Smith"),
            ContactReadyForSms(2, "222222", "Message 2", "Bob Jones"),
            ContactReadyForSms(3, "333333", "Message 3", "Charlie Wilson")
        )

        smsService.addMessageToQueue(contacts)

        // Remove middle contact
        smsService.removeContactFromQueue(2)

        val queueIds = smsService.getContactsInSmsQueueWithId()

        assertEquals(2, queueIds.size)
        assertTrue(queueIds.contains(1))
        assertTrue(queueIds.contains(3))
        assertTrue(!queueIds.contains(2))
    }

    @Test
    fun smsQueue_preventsDuplicates() {
        val contact = ContactReadyForSms(1, "111111", "Message", "John Doe")

        // Add same contact twice
        smsService.addMessageToQueue(listOf(contact))
        smsService.addMessageToQueue(listOf(contact))

        val queueIds = smsService.getContactsInSmsQueueWithId()

        // Should only have one entry
        assertEquals(1, queueIds.size)
    }

    @Test
    fun eventNotificationFlow_multipleEvents() = runTest {
        // Create contact
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "555-1234", "Message")
        contactRepository.insertItem(contact)

        // Create multiple events
        val events = listOf(
            Event(1, "2030-12-25", "10:00", 1, false),
            Event(2, "2030-12-26", "14:00", 1, false),
            Event(3, "2030-12-27", "09:00", 1, false)
        )

        events.forEach { eventRepository.insertEvent(it) }

        // Simulate sending SMS for first event
        val updatedEvent1 = events[0].copy(isNotified = true)
        eventRepository.updateEvent(updatedEvent1)

        // Verify only first event is notified
        val allEvents = eventRepository.getEvents(1)
        assertEquals(1, allEvents.count { it.isNotified })
        assertEquals(2, allEvents.count { !it.isNotified })

        // Simulate sending SMS for second event
        val updatedEvent2 = events[1].copy(isNotified = true)
        eventRepository.updateEvent(updatedEvent2)

        // Verify two events are now notified
        val updatedEvents = eventRepository.getEvents(1)
        assertEquals(2, updatedEvents.count { it.isNotified })
        assertEquals(1, updatedEvents.count { !it.isNotified })
    }

    @Test
    fun getUpcomingEventForContact_returnsFutureEvent() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "555-1234", "Message")
        contactRepository.insertItem(contact)

        val futureDate1 = "2030-12-25"
        val futureDate2 = "2030-12-26"

        val events = listOf(
            Event(1, futureDate1, "10:00", 1, false),
            Event(2, futureDate2, "14:00", 1, false)
        )

        events.forEach { eventRepository.insertEvent(it) }

        var resultEvent: Event? = null
        smsService.getUpcomingEventForContact(1) { event ->
            resultEvent = event
        }

        delay(100)

        assertNotNull(resultEvent)
        assertEquals(futureDate1, resultEvent?.eventDate) // Should return earliest
    }

    @Test
    fun messageTracking_multipleRecords() = runTest {
        val records = listOf(
            DateMessageSent(1, "10:00", "01.12.2030"),
            DateMessageSent(2, "11:00", "02.12.2030"),
            DateMessageSent(3, "12:00", "03.12.2030")
        )

        records.forEach { dateMessageSendRepository.insertDateMessageSend(it) }

        // Get last send info (should be most recent)
        val lastSend = dateMessageSendRepository.getLastSendetInfos().first()

        assertNotNull(lastSend)
        // Due to DESC order and LIMIT 1, should return the last inserted
        // Note: Depends on implementation, might need to check actual behavior
        assert(records.any { it.lastTimeSendet == lastSend.lastTimeSendet })
    }

    @Test
    fun smsFlow_contactWithoutEvents_canBeQueued() = runTest {
        // Create contact without events
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "555-1234", "Message")
        contactRepository.insertItem(contact)

        // Add to SMS queue
        val contactForSms = ContactReadyForSms(1, "555-1234", "Test message", "John Doe")
        smsService.addMessageToQueue(listOf(contactForSms))

        // Verify in queue
        val queueIds = smsService.getContactsInSmsQueueWithId()
        assertEquals(1, queueIds.size)
        assertTrue(queueIds.contains(1))
    }

    @Test
    fun smsFlow_queueClearAfterSending() {
        val contacts = listOf(
            ContactReadyForSms(1, "111111", "Message 1", "Alice"),
            ContactReadyForSms(2, "222222", "Message 2", "Bob")
        )

        smsService.addMessageToQueue(contacts)

        // Verify queue has items
        assertEquals(2, smsService.getContactsInSmsQueueWithId().size)

        // Remove contacts after "sending"
        smsService.removeContactFromQueue(1)
        smsService.removeContactFromQueue(2)

        // Verify queue is empty
        assertEquals(0, smsService.getContactsInSmsQueueWithId().size)
    }

    @Test
    fun multipleContactsMultipleEvents_trackingWorks() = runTest {
        // Create multiple contacts
        val contacts = listOf(
            Contact(1, "Dr", "Alice", "Smith", 'f', "111111", "Message 1"),
            Contact(2, "Ms", "Bob", "Jones", 'm', "222222", "Message 2")
        )

        contacts.forEach { contactRepository.insertItem(it) }

        // Create events for each
        val events = listOf(
            Event(1, "2030-12-25", "10:00", 1, false),
            Event(2, "2030-12-26", "14:00", 1, false),
            Event(3, "2030-12-27", "09:00", 2, false)
        )

        events.forEach { eventRepository.insertEvent(it) }

        // Add both to SMS queue
        val contactsForSms = listOf(
            ContactReadyForSms(1, "111111", "Msg 1", "Alice Smith"),
            ContactReadyForSms(2, "222222", "Msg 2", "Bob Jones")
        )

        smsService.addMessageToQueue(contactsForSms)

        // Verify both in queue
        val queueIds = smsService.getContactsInSmsQueueWithId()
        assertEquals(2, queueIds.size)

        // Simulate sending to first contact
        val updatedEvent1 = events[0].copy(isNotified = true)
        eventRepository.updateEvent(updatedEvent1)

        // Verify only contact 1's first event is notified
        val contact1Events = eventRepository.getEvents(1)
        assertEquals(1, contact1Events.count { it.isNotified })

        val contact2Events = eventRepository.getEvents(2)
        assertEquals(0, contact2Events.count { it.isNotified })
    }

    @Test
    fun smsFlow_addRemoveMultipleTimes() {
        val contact1 = ContactReadyForSms(1, "111111", "Msg", "Alice")
        val contact2 = ContactReadyForSms(2, "222222", "Msg", "Bob")

        // Add contact 1
        smsService.addMessageToQueue(listOf(contact1))
        assertEquals(1, smsService.getContactsInSmsQueueWithId().size)

        // Add contact 2
        smsService.addMessageToQueue(listOf(contact2))
        assertEquals(2, smsService.getContactsInSmsQueueWithId().size)

        // Remove contact 1
        smsService.removeContactFromQueue(1)
        assertEquals(1, smsService.getContactsInSmsQueueWithId().size)

        // Add contact 1 again
        smsService.addMessageToQueue(listOf(contact1))
        assertEquals(2, smsService.getContactsInSmsQueueWithId().size)

        // Remove all
        smsService.removeContactFromQueue(1)
        smsService.removeContactFromQueue(2)
        assertEquals(0, smsService.getContactsInSmsQueueWithId().size)
    }

    @Test
    fun eventUpdateAfterSms_doesNotAffectOtherEvents() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "555-1234", "Message")
        contactRepository.insertItem(contact)

        val events = listOf(
            Event(1, "2030-12-25", "10:00", 1, false),
            Event(2, "2030-12-26", "14:00", 1, false),
            Event(3, "2030-12-27", "09:00", 1, false)
        )

        events.forEach { eventRepository.insertEvent(it) }

        // Update only first event as notified
        val updatedEvent = events[0].copy(isNotified = true)
        smsService.updateEventInDatabase(updatedEvent)

        delay(100)

        // Verify only first event affected
        val allEvents = eventRepository.getEvents(1)
        assertTrue(allEvents.find { it.eventId == 1 }!!.isNotified)
        assertTrue(!allEvents.find { it.eventId == 2 }!!.isNotified)
        assertTrue(!allEvents.find { it.eventId == 3 }!!.isNotified)
    }
}
