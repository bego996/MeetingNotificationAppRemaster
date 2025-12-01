package com.simba.meetingnotification.repositories

import com.simba.meetingnotification.ui.data.dao.EventDao
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.entities.Event
import com.simba.meetingnotification.ui.data.relations.EventWithContact
import com.simba.meetingnotification.ui.data.repositories.OfflineEventRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineEventRepositoryTest {

    private lateinit var eventDao: EventDao
    private lateinit var repository: OfflineEventRepository

    @Before
    fun setup() {
        eventDao = mockk(relaxed = true)
        repository = OfflineEventRepository(eventDao)
    }

    @Test
    fun `insertItem delegates to dao insert`() = runTest {
        val event = Event(
            eventId = 1,
            eventDate = "2025-12-25",
            eventTime = "10:00",
            contactOwnerId = 1,
            isNotified = false
        )

        repository.insertItem(event)

        coVerify { eventDao.insert(event) }
    }

    @Test
    fun `insertAllEvents delegates to dao insertAll`() = runTest {
        val events = listOf(
            Event(1, "2025-12-25", "10:00", 1, false),
            Event(2, "2025-12-26", "14:30", 2, false)
        )

        repository.insertAllEvents(events)

        coVerify { eventDao.insertAll(events) }
    }

    @Test
    fun `updateItem delegates to dao update`() = runTest {
        val event = Event(1, "2025-12-25", "10:00", 1, true)

        repository.updateItem(event)

        coVerify { eventDao.update(event) }
    }

    @Test
    fun `deleteItem delegates to dao delete`() = runTest {
        val event = Event(1, "2025-12-25", "10:00", 1, false)

        repository.deleteItem(event)

        coVerify { eventDao.delete(event) }
    }

    @Test
    fun `deleteExpiredEvents delegates to dao deleteExpiredEvents`() = runTest {
        val events = listOf(
            Event(1, "2024-01-01", "10:00", 1, false),
            Event(2, "2024-01-02", "14:30", 2, false)
        )

        repository.deleteExpiredEvents(events)

        coVerify { eventDao.deleteExpiredEvents(events) }
    }

    @Test
    fun `getEventsAfterToday delegates to dao`() = runTest {
        val testEvents = listOf(
            Event(1, "2025-12-25", "10:00", 1, false),
            Event(2, "2025-12-26", "14:30", 2, false)
        )

        coEvery { eventDao.getEventsAfterToday("2025-12-01") } returns testEvents

        val result = repository.getEventsAfterToday("2025-12-01")

        assertEquals(2, result.size)
        assertEquals("2025-12-25", result[0].eventDate)
    }

    @Test
    fun `getExpiredEvents delegates to dao`() = runTest {
        val testEvents = listOf(
            Event(1, "2024-01-01", "10:00", 1, false)
        )

        coEvery { eventDao.getExpiredEvents("2025-12-01") } returns testEvents

        val result = repository.getExpiredEvents("2025-12-01")

        assertEquals(1, result.size)
        assertEquals("2024-01-01", result[0].eventDate)
    }

    @Test
    fun `getEvents for contact delegates to dao`() = runTest {
        val testEvents = listOf(
            Event(1, "2025-12-25", "10:00", 1, false),
            Event(2, "2025-12-26", "14:30", 1, false)
        )

        coEvery { eventDao.getEvents(1) } returns testEvents

        val result = repository.getEvents(1)

        assertEquals(2, result.size)
        assertEquals(1, result[0].contactOwnerId)
        assertEquals(1, result[1].contactOwnerId)
    }

    @Test
    fun `getAllEventsStream delegates to dao`() = runTest {
        val testEvents = listOf(
            Event(1, "2025-12-25", "10:00", 1, false),
            Event(2, "2025-12-26", "14:30", 2, false)
        )

        coEvery { eventDao.getAllEventsStream() } returns testEvents

        val result = repository.getAllEventsStream()

        assertEquals(2, result.size)
    }

    @Test
    fun `getNotNotifiedEventsAndFromActualDateTime delegates to dao`() {
        every { eventDao.getNotNotifiedEventsAndFromActualDateTime("2025-12-01", "2025-12-31") } returns 5

        val result = repository.getNotNotifiedEventsAndFromActualDateTime("2025-12-01", "2025-12-31")

        assertEquals(5, result)
    }

    @Test
    fun `getAllEventsAndFromActualDateTime delegates to dao`() = runTest {
        val testEvents = listOf(
            Event(1, "2025-12-25", "10:00", 1, false)
        )

        coEvery { eventDao.getAllEventsAndFromActualDateTime("2025-12-01", "2025-12-31") } returns testEvents

        val result = repository.getAllEventsAndFromActualDateTime("2025-12-01", "2025-12-31")

        assertEquals(1, result.size)
        assertFalse(result[0].isNotified)
    }

    @Test
    fun `getEventFromDateAndTimeParam delegates to dao`() = runTest {
        val testEvents = listOf(
            Event(1, "2025-12-25", "10:00", 1, false)
        )

        every { eventDao.getEventFromDateAndTimeParam("2025-12-25", "10:00") } returns flowOf(testEvents)

        val result = repository.getEventFromDateAndTimeParam("2025-12-25", "10:00").first()

        assertEquals(1, result.size)
        assertEquals("2025-12-25", result[0].eventDate)
        assertEquals("10:00", result[0].eventTime)
    }

    @Test
    fun `getEventWithContact delegates to dao`() = runTest {
        val testEvent = Event(1, "2025-12-25", "10:00", 1, false)
        val testContact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        val testEventWithContact = EventWithContact(
            event = testEvent,
            contact = testContact
        )

        every { eventDao.getEventWithContact(1) } returns flowOf(testEventWithContact)

        val result = repository.getEventWithContact(1).first()

        assertEquals(1, result.event.eventId)
        assertEquals(1, result.contact.id)
        assertEquals("John", result.contact.firstName)
    }

    private fun assertFalse(condition: Boolean) {
        assertEquals(false, condition)
    }
}
