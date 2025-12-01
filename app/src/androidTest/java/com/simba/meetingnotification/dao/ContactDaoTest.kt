package com.simba.meetingnotification.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simba.meetingnotification.ui.data.ContactDatabase
import com.simba.meetingnotification.ui.data.dao.ContactDao
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.entities.Event
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Instrumented tests for ContactDao using in-memory Room database.
 *
 * These tests verify CRUD operations, queries, relations, and cascade deletes.
 *
 * Run with: ./gradlew connectedAndroidTest --tests "*.dao.ContactDaoTest"
 */
@RunWith(AndroidJUnit4::class)
class ContactDaoTest {

    private lateinit var database: ContactDatabase
    private lateinit var contactDao: ContactDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            ContactDatabase::class.java
        )
            .allowMainThreadQueries() // Only for testing
            .build()

        contactDao = database.contactDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertContact_andGetById_returnsCorrectContact() = runTest {
        val contact = Contact(
            id = 1,
            title = "Dr",
            firstName = "John",
            lastName = "Doe",
            sex = 'm',
            phone = "123456789",
            message = "Test message"
        )

        contactDao.insert(contact)
        val retrieved = contactDao.getContact(1)

        assertNotNull(retrieved)
        assertEquals(1, retrieved.id)
        assertEquals("John", retrieved.firstName)
        assertEquals("Doe", retrieved.lastName)
    }

    @Test
    fun insertContactWithSameId_ignoresSecondInsert() = runTest {
        val contact1 = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message 1")
        val contact2 = Contact(1, "Ms", "Jane", "Smith", 'f', "222222", "Message 2")

        contactDao.insert(contact1)
        contactDao.insert(contact2) // Should be ignored due to OnConflictStrategy.IGNORE

        val retrieved = contactDao.getContact(1)

        assertNotNull(retrieved)
        assertEquals("John", retrieved.firstName) // First insert should remain
        assertEquals("Doe", retrieved.lastName)
    }

    @Test
    fun updateContact_changesExistingContact() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Original message")
        contactDao.insert(contact)

        val updated = contact.copy(message = "Updated message", phone = "999999")
        contactDao.update(updated)

        val retrieved = contactDao.getContact(1)

        assertNotNull(retrieved)
        assertEquals("Updated message", retrieved.message)
        assertEquals("999999", retrieved.phone)
    }

    @Test
    fun updateAll_updatesMultipleContacts() = runTest {
        val contacts = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message 1"),
            Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Message 2"),
            Contact(3, "Mr", "Bob", "Johnson", 'm', "333333", "Message 3")
        )

        contacts.forEach { contactDao.insert(it) }

        val updatedContacts = contacts.map { it.copy(message = "Updated") }
        contactDao.updateAll(updatedContacts)

        val allContacts = contactDao.getAllContacts().first()

        assertEquals(3, allContacts.size)
        assertTrue(allContacts.all { it.message == "Updated" })
    }

    @Test
    fun deleteContact_removesContactFromDatabase() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        contactDao.delete(contact)
        val retrieved = contactDao.getContact(1)

        assertNull(retrieved)
    }

    @Test
    fun getAllContacts_returnsSortedByFirstName() = runTest {
        val contacts = listOf(
            Contact(3, "Mr", "Charlie", "Wilson", 'm', "333333", "Message 3"),
            Contact(1, "Dr", "Alice", "Smith", 'f', "111111", "Message 1"),
            Contact(2, "Ms", "Bob", "Jones", 'm', "222222", "Message 2")
        )

        contacts.forEach { contactDao.insert(it) }

        val allContacts = contactDao.getAllContacts().first()

        assertEquals(3, allContacts.size)
        assertEquals("Alice", allContacts[0].firstName)
        assertEquals("Bob", allContacts[1].firstName)
        assertEquals("Charlie", allContacts[2].firstName)
    }

    @Test
    fun getAllContacts_returnsEmptyListWhenNoContacts() = runTest {
        val allContacts = contactDao.getAllContacts().first()

        assertEquals(0, allContacts.size)
    }

    @Test
    fun getContact_returnsNullForNonExistentId() = runTest {
        val retrieved = contactDao.getContact(999)

        assertNull(retrieved)
    }

    @Test
    fun getContactWithEvents_returnsContactAndRelatedEvents() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val eventDao = database.eventDao()
        val events = listOf(
            Event(1, "2025-12-25", "10:00", 1, false),
            Event(2, "2025-12-26", "14:30", 1, false)
        )
        events.forEach { eventDao.insert(it) }

        val contactWithEvents = contactDao.getContactWithEvents(1).first()

        assertEquals(1, contactWithEvents.contact.id)
        assertEquals("John", contactWithEvents.contact.firstName)
        assertEquals(2, contactWithEvents.events.size)
        assertEquals("2025-12-25", contactWithEvents.events[0].eventDate)
        assertEquals("2025-12-26", contactWithEvents.events[1].eventDate)
    }

    @Test
    fun getContactWithEvents_returnsEmptyEventsListWhenNoEvents() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val contactWithEvents = contactDao.getContactWithEvents(1).first()

        assertEquals(1, contactWithEvents.contact.id)
        assertEquals(0, contactWithEvents.events.size)
    }

    @Test
    fun deleteContact_cascadeDeletesRelatedEvents() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val eventDao = database.eventDao()
        val events = listOf(
            Event(1, "2025-12-25", "10:00", 1, false),
            Event(2, "2025-12-26", "14:30", 1, false)
        )
        events.forEach { eventDao.insert(it) }

        // Verify events exist
        val eventsBefore = eventDao.getEvents(1)
        assertEquals(2, eventsBefore.size)

        // Delete contact
        contactDao.delete(contact)

        // Verify events are cascade deleted
        val eventsAfter = eventDao.getEvents(1)
        assertEquals(0, eventsAfter.size)
    }

    @Test
    fun insertMultipleContacts_andRetrieveAll() = runTest {
        val contacts = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message 1"),
            Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Message 2"),
            Contact(3, "Mr", "Bob", "Johnson", 'm', "333333", "Message 3"),
            Contact(4, "Dr", "Alice", "Williams", 'f', "444444", "Message 4"),
            Contact(5, "Mr", "Charlie", "Brown", 'm', "555555", "Message 5")
        )

        contacts.forEach { contactDao.insert(it) }

        val allContacts = contactDao.getAllContacts().first()

        assertEquals(5, allContacts.size)
    }

    @Test
    fun updateContact_withNonExistentId_doesNotThrowException() = runTest {
        val contact = Contact(999, "Dr", "John", "Doe", 'm', "111111", "Message")

        // This should not throw an exception, just do nothing
        contactDao.update(contact)

        val retrieved = contactDao.getContact(999)
        assertNull(retrieved)
    }
}
