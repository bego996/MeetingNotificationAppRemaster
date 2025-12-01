package com.simba.meetingnotification.services

import android.content.Context
import android.content.Intent
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
 * Instrumented tests for SmsSendingService.
 *
 * These tests verify:
 * - Service lifecycle and initialization
 * - Queue management (add, remove, retrieve)
 * - Repository integration (database operations)
 * - Message tracking
 *
 * Note: SMS sending functionality is not tested as it requires SmsManager
 * and actual device permissions. Use manual testing for SMS functionality.
 *
 * Run with: ./gradlew connectedAndroidTest --tests "*.services.SmsSendingServiceInstrumentedTest"
 */
@RunWith(AndroidJUnit4::class)
class SmsSendingServiceInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: ContactDatabase
    private lateinit var service: SmsSendingService

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

        // Create service instance
        service = SmsSendingService()

        // Initialize repositories
        val contactRepository = OfflineContactRepository(database.contactDao())
        val eventRepository = OfflineEventRepository(database.eventDao())
        val dateMessageSendRepository = OfflineDateMessageSendRepository(database.messageSendDao())

        service.initialize(contactRepository, eventRepository, dateMessageSendRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun service_canBeCreated() {
        assertNotNull(service)
    }

    @Test
    fun addMessageToQueue_addsContactsToQueue() {
        val contacts = listOf(
            ContactReadyForSms(1, "111111", "Message 1", "John Doe"),
            ContactReadyForSms(2, "222222", "Message 2", "Jane Smith")
        )

        service.addMessageToQueue(contacts)

        val queueIds = service.getContactsInSmsQueueWithId()

        assertEquals(2, queueIds.size)
        assertTrue(queueIds.contains(1))
        assertTrue(queueIds.contains(2))
    }

    @Test
    fun addMessageToQueue_preventsDuplicates() {
        val contact = ContactReadyForSms(1, "111111", "Message", "John Doe")

        service.addMessageToQueue(listOf(contact))
        service.addMessageToQueue(listOf(contact)) // Try to add again

        val queueIds = service.getContactsInSmsQueueWithId()

        assertEquals(1, queueIds.size) // Should still be only 1
    }

    @Test
    fun removeContactFromQueue_removesCorrectContact() {
        val contacts = listOf(
            ContactReadyForSms(1, "111111", "Message 1", "John Doe"),
            ContactReadyForSms(2, "222222", "Message 2", "Jane Smith"),
            ContactReadyForSms(3, "333333", "Message 3", "Bob Johnson")
        )

        service.addMessageToQueue(contacts)
        service.removeContactFromQueue(2)

        val queueIds = service.getContactsInSmsQueueWithId()

        assertEquals(2, queueIds.size)
        assertTrue(queueIds.contains(1))
        assertTrue(queueIds.contains(3))
        assertTrue(!queueIds.contains(2))
    }

    @Test
    fun removeContactFromQueue_handlesNonExistentContact() {
        val contact = ContactReadyForSms(1, "111111", "Message", "John Doe")

        service.addMessageToQueue(listOf(contact))
        service.removeContactFromQueue(999) // Non-existent

        val queueIds = service.getContactsInSmsQueueWithId()

        assertEquals(1, queueIds.size) // Should still have original contact
    }

    @Test
    fun getContactsInSmsQueueWithId_returnsEmptyWhenQueueEmpty() {
        val queueIds = service.getContactsInSmsQueueWithId()

        assertEquals(0, queueIds.size)
    }

    @Test
    fun getContactsInSmsQueueWithId_returnsAllQueuedContactIds() {
        val contacts = listOf(
            ContactReadyForSms(10, "111111", "Message 1", "John Doe"),
            ContactReadyForSms(20, "222222", "Message 2", "Jane Smith"),
            ContactReadyForSms(30, "333333", "Message 3", "Bob Johnson")
        )

        service.addMessageToQueue(contacts)
        val queueIds = service.getContactsInSmsQueueWithId()

        assertEquals(3, queueIds.size)
        assertEquals(10, queueIds[0])
        assertEquals(20, queueIds[1])
        assertEquals(30, queueIds[2])
    }

    @Test
    fun updateEventInDatabase_updatesEventCorrectly() = runTest {
        // Insert contact first
        val contactDao = database.contactDao()
        val eventDao = database.eventDao()

        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val event = Event(1, "2025-12-25", "10:00", 1, false)
        eventDao.insert(event)

        // Update via service
        val updatedEvent = event.copy(isNotified = true)
        service.updateEventInDatabase(updatedEvent)

        // Allow time for coroutine to execute
        delay(100)

        // Verify
        val events = eventDao.getEvents(1)
        assertEquals(1, events.size)
        assertTrue(events[0].isNotified)
    }

    @Test
    fun insertDatesForSendMessages_insertsCorrectly() = runTest {
        val dateMessage = DateMessageSent(
            id = 1,
            lastTimeSendet = "14:30",
            lastDateSendet = "01.12.2025"
        )

        service.insertDatesForSendMessages(dateMessage)

        // Allow time for coroutine to execute
        delay(100)

        val retrieved = database.messageSendDao().getLastSendetInfos().first()

        assertNotNull(retrieved)
        assertEquals("14:30", retrieved.lastTimeSendet)
        assertEquals("01.12.2025", retrieved.lastDateSendet)
    }

    @Test
    fun getUpcomingEventForContact_returnsCorrectEvent() = runTest {
        val contactDao = database.contactDao()
        val eventDao = database.eventDao()

        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val futureDate1 = "2030-12-25" // Far future
        val futureDate2 = "2030-12-26" // Further future

        val events = listOf(
            Event(1, futureDate1, "10:00", 1, false),
            Event(2, futureDate2, "14:00", 1, false)
        )
        events.forEach { eventDao.insert(it) }

        var resultEvent: Event? = null
        service.getUpcomingEventForContact(1) { event ->
            resultEvent = event
        }

        // Allow time for coroutine to execute
        delay(100)

        assertNotNull(resultEvent)
        assertEquals(futureDate1, resultEvent?.eventDate) // Should return the earliest future event
    }

    @Test
    fun messageQueue_maintainsOrder() {
        val contacts = listOf(
            ContactReadyForSms(1, "111111", "Message 1", "John Doe"),
            ContactReadyForSms(2, "222222", "Message 2", "Jane Smith"),
            ContactReadyForSms(3, "333333", "Message 3", "Bob Johnson")
        )

        service.addMessageToQueue(contacts)
        val queueIds = service.getContactsInSmsQueueWithId()

        // Queue should maintain FIFO order
        assertEquals(1, queueIds[0])
        assertEquals(2, queueIds[1])
        assertEquals(3, queueIds[2])
    }

    @Test
    fun addAndRemove_multipleOperations() {
        service.addMessageToQueue(listOf(ContactReadyForSms(1, "111111", "Msg", "John")))
        service.addMessageToQueue(listOf(ContactReadyForSms(2, "222222", "Msg", "Jane")))

        assertEquals(2, service.getContactsInSmsQueueWithId().size)

        service.removeContactFromQueue(1)
        assertEquals(1, service.getContactsInSmsQueueWithId().size)

        service.addMessageToQueue(listOf(ContactReadyForSms(3, "333333", "Msg", "Bob")))
        assertEquals(2, service.getContactsInSmsQueueWithId().size)

        service.removeContactFromQueue(2)
        service.removeContactFromQueue(3)
        assertEquals(0, service.getContactsInSmsQueueWithId().size)
    }
}
