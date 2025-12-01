package com.simba.meetingnotification.repositories

import com.simba.meetingnotification.ui.data.dao.ContactDao
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.relations.ContactWithEvents
import com.simba.meetingnotification.ui.data.repositories.OfflineContactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineContactRepositoryTest {

    private lateinit var contactDao: ContactDao
    private lateinit var repository: OfflineContactRepository

    @Before
    fun setup() {
        contactDao = mockk(relaxed = true)
        repository = OfflineContactRepository(contactDao)
    }

    @Test
    fun `getAllContactsStream delegates to dao`() = runTest {
        val testContacts = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message"),
            Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Message")
        )

        every { contactDao.getAllContacts() } returns flowOf(testContacts)

        val result = repository.getAllContactsStream().first()

        assertEquals(2, result.size)
        assertEquals("John", result[0].firstName)
        assertEquals("Jane", result[1].firstName)
    }

    @Test
    fun `getAllContactsStream returns empty list when dao returns empty`() = runTest {
        every { contactDao.getAllContacts() } returns flowOf(emptyList())

        val result = repository.getAllContactsStream().first()

        assertEquals(0, result.size)
    }

    @Test
    fun `getContactStream returns contact from dao`() = runTest {
        val testContact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")

        coEvery { contactDao.getContact(1) } returns testContact

        val result = repository.getContactStream(1)

        assertNotNull(result)
        assertEquals(1, result.id)
        assertEquals("John", result.firstName)
    }

    @Test
    fun `getContactStream returns null when contact not found`() = runTest {
        coEvery { contactDao.getContact(999) } returns null

        val result = repository.getContactStream(999)

        assertNull(result)
    }

    @Test
    fun `insertItem delegates to dao insert`() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")

        repository.insertItem(contact)

        coVerify { contactDao.insert(contact) }
    }

    @Test
    fun `updateItem delegates to dao update`() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Updated message")

        repository.updateItem(contact)

        coVerify { contactDao.update(contact) }
    }

    @Test
    fun `updateAll delegates to dao updateAll`() = runTest {
        val contacts = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message 1"),
            Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Message 2")
        )

        repository.updateAll(contacts)

        coVerify { contactDao.updateAll(contacts) }
    }

    @Test
    fun `deleteItem delegates to dao delete`() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")

        repository.deleteItem(contact)

        coVerify { contactDao.delete(contact) }
    }

    @Test
    fun `getContactWithEvents delegates to dao`() = runTest {
        val testContact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        val testContactWithEvents = ContactWithEvents(
            contact = testContact,
            events = emptyList()
        )

        every { contactDao.getContactWithEvents(1) } returns flowOf(testContactWithEvents)

        val result = repository.getContactWithEvents(1).first()

        assertEquals(1, result.contact.id)
        assertEquals("John", result.contact.firstName)
        assertEquals(0, result.events.size)
    }

    @Test
    fun `getContactWithEvents includes events`() = runTest {
        val testContact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        val testEvents = listOf(
            com.simba.meetingnotification.ui.data.entities.Event(
                eventId = 1,
                eventDate = "2025-12-25",
                eventTime = "10:00",
                contactOwnerId = 1,
                isNotified = false
            )
        )
        val testContactWithEvents = ContactWithEvents(
            contact = testContact,
            events = testEvents
        )

        every { contactDao.getContactWithEvents(1) } returns flowOf(testContactWithEvents)

        val result = repository.getContactWithEvents(1).first()

        assertEquals(1, result.events.size)
        assertEquals("2025-12-25", result.events[0].eventDate)
    }
}
