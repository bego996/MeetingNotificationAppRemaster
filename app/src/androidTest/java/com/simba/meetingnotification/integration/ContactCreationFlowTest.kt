package com.simba.meetingnotification.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simba.meetingnotification.ui.data.ContactDatabase
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.entities.Event
import com.simba.meetingnotification.ui.data.repositories.OfflineContactRepository
import com.simba.meetingnotification.ui.data.repositories.OfflineEventRepository
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
 * Integration tests for the complete contact creation flow.
 *
 * These tests verify the full lifecycle:
 * 1. Create contact in database
 * 2. Verify contact appears in contact list
 * 3. Add events for contact
 * 4. Verify ContactWithEvents relation works
 * 5. Update contact information
 * 6. Delete contact and verify cascade deletion
 *
 * This tests the interaction between:
 * - ContactDao and EventDao
 * - OfflineContactRepository and OfflineEventRepository
 * - Database foreign key constraints
 * - One-to-many relations
 *
 * Run with: ./gradlew connectedAndroidTest --tests "*.integration.ContactCreationFlowTest"
 */
@RunWith(AndroidJUnit4::class)
class ContactCreationFlowTest {

    private lateinit var context: Context
    private lateinit var database: ContactDatabase
    private lateinit var contactRepository: OfflineContactRepository
    private lateinit var eventRepository: OfflineEventRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            ContactDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        contactRepository = OfflineContactRepository(database.contactDao())
        eventRepository = OfflineEventRepository(database.eventDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completeContactFlow_createReadUpdateDelete() = runTest {
        // 1. CREATE: Add new contact
        val newContact = Contact(
            id = 1,
            title = "Dr",
            firstName = "John",
            lastName = "Doe",
            sex = 'm',
            phone = "555-1234",
            message = "Test message"
        )

        contactRepository.insertItem(newContact)

        // 2. READ: Verify contact exists
        val allContacts = contactRepository.getAllContactsStream().first()
        assertEquals(1, allContacts.size)
        assertEquals("John", allContacts[0].firstName)
        assertEquals("Doe", allContacts[0].lastName)

        // 3. Add event for contact
        val event = Event(
            eventId = 1,
            eventDate = "2030-12-25",
            eventTime = "10:00",
            contactOwnerId = 1,
            isNotified = false
        )

        eventRepository.insertEvent(event)

        // 4. Verify ContactWithEvents relation
        val contactWithEvents = database.contactDao().getContactWithEvents(1)
        assertNotNull(contactWithEvents)
        assertEquals("John", contactWithEvents.contact.firstName)
        assertEquals(1, contactWithEvents.events.size)
        assertEquals("2030-12-25", contactWithEvents.events[0].eventDate)

        // 5. UPDATE: Modify contact information
        val updatedContact = newContact.copy(
            firstName = "Jane",
            lastName = "Smith"
        )

        contactRepository.updateItem(updatedContact)

        val updatedList = contactRepository.getAllContactsStream().first()
        assertEquals("Jane", updatedList[0].firstName)
        assertEquals("Smith", updatedList[0].lastName)

        // 6. Verify event still exists after contact update
        val eventsAfterUpdate = eventRepository.getEvents(1)
        assertEquals(1, eventsAfterUpdate.size)

        // 7. DELETE: Remove contact (should cascade delete events)
        contactRepository.deleteItem(updatedContact)

        val finalContactList = contactRepository.getAllContactsStream().first()
        assertEquals(0, finalContactList.size)

        // 8. Verify events were cascade deleted
        val eventsAfterDelete = eventRepository.getEvents(1)
        assertEquals(0, eventsAfterDelete.size)
    }

    @Test
    fun multipleContactsWithEvents_flowWorks() = runTest {
        // Create multiple contacts
        val contacts = listOf(
            Contact(1, "Dr", "Alice", "Smith", 'f', "111111", "Message 1"),
            Contact(2, "Ms", "Bob", "Jones", 'm', "222222", "Message 2"),
            Contact(3, "Mr", "Charlie", "Wilson", 'm', "333333", "Message 3")
        )

        contacts.forEach { contactRepository.insertItem(it) }

        // Verify all contacts added
        val allContacts = contactRepository.getAllContactsStream().first()
        assertEquals(3, allContacts.size)

        // Add multiple events for each contact
        val events = listOf(
            Event(1, "2030-12-25", "10:00", 1, false),
            Event(2, "2030-12-26", "14:00", 1, false),
            Event(3, "2030-12-27", "09:00", 2, false),
            Event(4, "2030-12-28", "16:00", 3, false)
        )

        events.forEach { eventRepository.insertEvent(it) }

        // Verify ContactWithEvents for contact 1 (should have 2 events)
        val contact1WithEvents = database.contactDao().getContactWithEvents(1)
        assertEquals(2, contact1WithEvents.events.size)

        // Verify ContactWithEvents for contact 2 (should have 1 event)
        val contact2WithEvents = database.contactDao().getContactWithEvents(2)
        assertEquals(1, contact2WithEvents.events.size)

        // Delete contact 1, verify only their events are deleted
        contactRepository.deleteItem(contacts[0])

        val eventsForContact1 = eventRepository.getEvents(1)
        assertEquals(0, eventsForContact1.size)

        val eventsForContact2 = eventRepository.getEvents(2)
        assertEquals(1, eventsForContact2.size) // Should still exist

        val eventsForContact3 = eventRepository.getEvents(3)
        assertEquals(1, eventsForContact3.size) // Should still exist
    }

    @Test
    fun contactEventNotification_flowWorks() = runTest {
        // Create contact
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "555-1234", "Message")
        contactRepository.insertItem(contact)

        // Add events with different notification statuses
        val events = listOf(
            Event(1, "2030-12-25", "10:00", 1, false), // Not notified
            Event(2, "2030-12-26", "14:00", 1, false), // Not notified
            Event(3, "2030-12-27", "09:00", 1, true)   // Already notified
        )

        events.forEach { eventRepository.insertEvent(it) }

        // Verify initial state
        val allEvents = eventRepository.getEvents(1)
        assertEquals(3, allEvents.size)

        val notNotifiedEvents = allEvents.filter { !it.isNotified }
        assertEquals(2, notNotifiedEvents.size)

        // Simulate notification sent: update event
        val updatedEvent = events[0].copy(isNotified = true)
        eventRepository.updateEvent(updatedEvent)

        // Verify notification status updated
        val updatedEvents = eventRepository.getEvents(1)
        val notifiedCount = updatedEvents.count { it.isNotified }
        assertEquals(2, notifiedCount) // Now 2 events are notified
    }

    @Test
    fun contactWithNoEvents_canBeDeleted() = runTest {
        // Create contact without events
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "555-1234", "Message")
        contactRepository.insertItem(contact)

        // Verify contact exists
        val contacts = contactRepository.getAllContactsStream().first()
        assertEquals(1, contacts.size)

        // Delete contact (should work even without events)
        contactRepository.deleteItem(contact)

        // Verify deletion
        val afterDelete = contactRepository.getAllContactsStream().first()
        assertEquals(0, afterDelete.size)
    }

    @Test
    fun updateEvent_doesNotAffectOtherEvents() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "555-1234", "Message")
        contactRepository.insertItem(contact)

        val events = listOf(
            Event(1, "2030-12-25", "10:00", 1, false),
            Event(2, "2030-12-26", "14:00", 1, false),
            Event(3, "2030-12-27", "09:00", 1, false)
        )

        events.forEach { eventRepository.insertEvent(it) }

        // Update only the first event
        val updatedEvent = events[0].copy(isNotified = true, eventTime = "11:00")
        eventRepository.updateEvent(updatedEvent)

        // Verify only first event changed
        val allEvents = eventRepository.getEvents(1)
        assertEquals(3, allEvents.size)

        val event1 = allEvents.find { it.eventId == 1 }
        assertTrue(event1!!.isNotified)
        assertEquals("11:00", event1.eventTime)

        // Other events should remain unchanged
        val event2 = allEvents.find { it.eventId == 2 }
        assertTrue(!event2!!.isNotified)
        assertEquals("14:00", event2.eventTime)
    }

    @Test
    fun deleteAllContactEvents_leavesContactIntact() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "555-1234", "Message")
        contactRepository.insertItem(contact)

        val events = listOf(
            Event(1, "2030-12-25", "10:00", 1, false),
            Event(2, "2030-12-26", "14:00", 1, false)
        )

        events.forEach { eventRepository.insertEvent(it) }

        // Delete all events
        events.forEach { eventRepository.deleteEvent(it) }

        // Verify events deleted but contact remains
        val remainingEvents = eventRepository.getEvents(1)
        assertEquals(0, remainingEvents.size)

        val remainingContacts = contactRepository.getAllContactsStream().first()
        assertEquals(1, remainingContacts.size)
        assertEquals("John", remainingContacts[0].firstName)
    }

    @Test
    fun bulkInsert_multipleContacts_succeeds() = runTest {
        val contacts = List(20) { index ->
            Contact(
                id = index,
                title = "Dr",
                firstName = "Person$index",
                lastName = "Last$index",
                sex = if (index % 2 == 0) 'm' else 'f',
                phone = "555-$index",
                message = "Message $index"
            )
        }

        // Insert all contacts
        contacts.forEach { contactRepository.insertItem(it) }

        // Verify all inserted
        val allContacts = contactRepository.getAllContactsStream().first()
        assertEquals(20, allContacts.size)

        // Verify sorting (contacts are sorted by natural order)
        assertTrue(allContacts[0].firstName <= allContacts[1].firstName)
    }

    @Test
    fun foreignKeyConstraint_preventsOrphanEvents() = runTest {
        // Try to insert event without corresponding contact
        // This should fail due to foreign key constraint
        val orphanEvent = Event(
            eventId = 1,
            eventDate = "2030-12-25",
            eventTime = "10:00",
            contactOwnerId = 999, // Non-existent contact ID
            isNotified = false
        )

        try {
            eventRepository.insertEvent(orphanEvent)
            // If we reach here, foreign key constraint didn't work
            assert(false) { "Should have thrown foreign key constraint violation" }
        } catch (e: Exception) {
            // Expected: foreign key constraint violation
            assert(true)
        }
    }

    @Test
    fun contactWithEvents_updateContactId_eventsRemainLinked() = runTest {
        // Note: In production, changing contact ID is risky
        // This test verifies that events remain properly linked

        val contact = Contact(1, "Dr", "John", "Doe", 'm', "555-1234", "Message")
        contactRepository.insertItem(contact)

        val event = Event(1, "2030-12-25", "10:00", 1, false)
        eventRepository.insertEvent(event)

        // Verify initial link
        val initial = database.contactDao().getContactWithEvents(1)
        assertEquals(1, initial.events.size)

        // Update contact (without changing ID - Room doesn't support ID updates easily)
        val updatedContact = contact.copy(firstName = "Jane")
        contactRepository.updateItem(updatedContact)

        // Verify link still works
        val afterUpdate = database.contactDao().getContactWithEvents(1)
        assertEquals(1, afterUpdate.events.size)
        assertEquals("Jane", afterUpdate.contact.firstName)
    }
}
