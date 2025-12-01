package com.simba.meetingnotification.contact

import com.simba.meetingnotification.ui.contact.ContactCheckBeforeSubmitViewModel
import com.simba.meetingnotification.ui.contact.ContactReadyForSms
import com.simba.meetingnotification.ui.contact.ContactZippedWithDate
import com.simba.meetingnotification.ui.contact.EventDateTitle
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.entities.Event
import com.simba.meetingnotification.ui.data.repositories.BackgroundImageManagerRepository
import com.simba.meetingnotification.ui.data.repositories.ContactRepository
import com.simba.meetingnotification.ui.data.repositories.EventRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ContactCheckBeforeSubmitViewModelTest {

    private lateinit var contactRepository: ContactRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var backgroundImageManagerRepository: BackgroundImageManagerRepository
    private lateinit var viewModel: ContactCheckBeforeSubmitViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        contactRepository = mockk(relaxed = true)
        eventRepository = mockk(relaxed = true)
        backgroundImageManagerRepository = mockk(relaxed = true)

        // Setup default mock responses
        every { backgroundImageManagerRepository.get() } returns flowOf(1)
        every { contactRepository.getAllContactsStream() } returns flowOf(emptyList())
        coEvery { eventRepository.getEventsAfterToday(any()) } returns emptyList()

        viewModel = ContactCheckBeforeSubmitViewModel(
            contactRepository,
            eventRepository,
            backgroundImageManagerRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getDayDuration returns correct number of days between now and future date`() {
        val tomorrow = LocalDate.now().plusDays(1)
        val tomorrowFormatted = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val result = viewModel.getDayDuration(tomorrowFormatted)

        assertEquals("1", result)
    }

    @Test
    fun `getDayDuration returns zero for today`() {
        val today = LocalDate.now()
        val todayFormatted = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val result = viewModel.getDayDuration(todayFormatted)

        assertEquals("0", result)
    }

    @Test
    fun `getDayDuration returns negative number for past date`() {
        val yesterday = LocalDate.now().minusDays(1)
        val yesterdayFormatted = yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val result = viewModel.getDayDuration(yesterdayFormatted)

        assertEquals("-1", result)
    }

    @Test
    fun `loadCalenderData updates calender state`() = runTest {
        val events = listOf(
            EventDateTitle(
                eventName = "Test Event",
                eventDate = LocalDateTime.of(2025, 12, 25, 10, 0)
            )
        )

        viewModel.loadCalenderData(events)

        // Verify by calling zipDatesToContacts which reads from calendar state
        val contacts = listOf(
            Contact(1, "Dr", "Test", "Event", 'm', "123456", "Message")
        )

        viewModel.zipDatesToContacts(contacts)

        // Check that connection was made
        assertEquals(1, viewModel.calenderStateConnectedToContacts.value.size)
    }

    @Test
    fun `zipDatesToContacts correctly matches contacts with calendar events`() {
        val futureDate = LocalDateTime.now().plusDays(5)
        val events = listOf(
            EventDateTitle(
                eventName = "John Doe Meeting",
                eventDate = futureDate
            ),
            EventDateTitle(
                eventName = "Jane Smith Conference",
                eventDate = futureDate.plusDays(1)
            )
        )

        val contacts = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Test message"),
            Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Test message"),
            Contact(3, "Mr", "Bob", "Johnson", 'm', "333333", "Test message")
        )

        viewModel.loadCalenderData(events)
        viewModel.zipDatesToContacts(contacts)

        val zipped = viewModel.calenderStateConnectedToContacts.value

        assertEquals(2, zipped.size)
        assertEquals(1, zipped.find { it.contactId == 1 }?.contactId)
        assertEquals(2, zipped.find { it.contactId == 2 }?.contactId)
    }

    @Test
    fun `zipDatesToContacts handles contacts with no matching events`() {
        val events = listOf(
            EventDateTitle(
                eventName = "Some Other Meeting",
                eventDate = LocalDateTime.now().plusDays(1)
            )
        )

        val contacts = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Test message")
        )

        viewModel.loadCalenderData(events)
        viewModel.zipDatesToContacts(contacts)

        val zipped = viewModel.calenderStateConnectedToContacts.value

        assertEquals(0, zipped.size)
    }

    @Test
    fun `updateListReadyForSms and getContactsReadyForSms work correctly`() {
        val smsContacts = listOf(
            ContactReadyForSms(1, "123456", "Test message", "John Doe"),
            ContactReadyForSms(2, "789012", "Another message", "Jane Smith")
        )

        viewModel.updateListReadyForSms(smsContacts)
        val result = viewModel.getContactsReadyForSms()

        assertEquals(2, result.size)
        assertEquals("John Doe", result[0].fullName)
        assertEquals("Jane Smith", result[1].fullName)
    }

    @Test
    fun `contactUiState emits contacts from repository`() = runTest {
        val testContacts = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message"),
            Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Message")
        )

        every { contactRepository.getAllContactsStream() } returns flowOf(testContacts)

        val newViewModel = ContactCheckBeforeSubmitViewModel(
            contactRepository,
            eventRepository,
            backgroundImageManagerRepository
        )

        val uiState = newViewModel.contactUiState.first()

        assertEquals(2, uiState.contactUiState.size)
        assertEquals("John", uiState.contactUiState[0].firstName)
    }

    @Test
    fun `updateContact calls repository updateItem`() = runTest {
        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Updated message")

        viewModel.updateContact(contact)

        // Allow time for coroutine to execute
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { contactRepository.updateItem(contact) }
    }

    @Test
    fun `insertEventForContact inserts events correctly`() = runTest {
        val zippedData = listOf(
            ContactZippedWithDate(1, "2025-12-25", "10:00"),
            ContactZippedWithDate(2, "2025-12-26", "14:30")
        )

        coEvery { eventRepository.getEventsAfterToday(any()) } returns emptyList()

        viewModel.insertEventForContact(zippedData)

        coVerify {
            eventRepository.insertAllEvents(
                match { events ->
                    events.size == 2 &&
                    events[0].contactOwnerId == 1 &&
                    events[0].eventDate == "2025-12-25" &&
                    events[0].eventTime == "10:00"
                }
            )
        }
    }

    @Test
    fun `isContactNotifiedForUpcomingEvent returns false when no events exist`() = runTest {
        coEvery { eventRepository.getEventsAfterToday(any()) } returns emptyList()

        // Recreate viewModel to trigger init
        val newViewModel = ContactCheckBeforeSubmitViewModel(
            contactRepository,
            eventRepository,
            backgroundImageManagerRepository
        )

        // Allow loading to complete
        testDispatcher.scheduler.advanceUntilIdle()

        val result = newViewModel.isContactNotifiedForUpcomingEvent(1)

        assertFalse(result)
    }

    @Test
    fun `isContactNotifiedForUpcomingEvent returns true when contact has notified event`() = runTest {
        val futureDate = LocalDate.now().plusDays(5).toString()
        val events = listOf(
            Event(
                eventId = 1,
                eventDate = futureDate,
                eventTime = "10:00",
                contactOwnerId = 1,
                isNotified = true
            )
        )

        coEvery { eventRepository.getEventsAfterToday(any()) } returns events

        val newViewModel = ContactCheckBeforeSubmitViewModel(
            contactRepository,
            eventRepository,
            backgroundImageManagerRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val result = newViewModel.isContactNotifiedForUpcomingEvent(1)

        assertTrue(result)
    }

    @Test
    fun `isContactNotifiedForUpcomingEvent returns false when contact event is not notified`() = runTest {
        val futureDate = LocalDate.now().plusDays(5).toString()
        val events = listOf(
            Event(
                eventId = 1,
                eventDate = futureDate,
                eventTime = "10:00",
                contactOwnerId = 1,
                isNotified = false
            )
        )

        coEvery { eventRepository.getEventsAfterToday(any()) } returns events

        val newViewModel = ContactCheckBeforeSubmitViewModel(
            contactRepository,
            eventRepository,
            backgroundImageManagerRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val result = newViewModel.isContactNotifiedForUpcomingEvent(1)

        assertFalse(result)
    }

    @Test
    fun `deleteEventsThatDontExistsInCalenderAnymoreFromDatabase removes non-existing events`() = runTest {
        val futureDate = LocalDate.now().plusDays(5)
        val eventDateTime = LocalDateTime.of(
            futureDate.year,
            futureDate.month,
            futureDate.dayOfMonth,
            10,
            0
        )

        // Event in DB that doesn't exist in calendar
        val dbEvents = listOf(
            Event(
                eventId = 1,
                eventDate = futureDate.toString(),
                eventTime = "10:00",
                contactOwnerId = 1,
                isNotified = false
            )
        )

        // Empty calendar (no events)
        val calendarEvents = emptyList<EventDateTitle>()

        coEvery { eventRepository.getEventsAfterToday(any()) } returns dbEvents

        viewModel.deleteEventsThatDontExistsInCalenderAnymoreFromDatabase(calendarEvents)

        coVerify { eventRepository.deleteItem(dbEvents[0]) }
    }

    @Test
    fun `deleteEventsThatDontExistsInCalenderAnymoreFromDatabase keeps existing events`() = runTest {
        val futureDate = LocalDate.now().plusDays(5)
        val eventDateTime = LocalDateTime.of(
            futureDate.year,
            futureDate.month,
            futureDate.dayOfMonth,
            10,
            0
        )

        val dbEvents = listOf(
            Event(
                eventId = 1,
                eventDate = futureDate.toString(),
                eventTime = "10:00",
                contactOwnerId = 1,
                isNotified = false
            )
        )

        // Calendar has matching event
        val calendarEvents = listOf(
            EventDateTitle(
                eventName = "Test Event",
                eventDate = eventDateTime
            )
        )

        coEvery { eventRepository.getEventsAfterToday(any()) } returns dbEvents

        viewModel.deleteEventsThatDontExistsInCalenderAnymoreFromDatabase(calendarEvents)

        coVerify(exactly = 0) { eventRepository.deleteItem(any()) }
    }

    @Test
    fun `updateContactsMessageAfterZippingItWithDates updates contact messages with date and time`() = runTest {
        val zippedData = listOf(
            ContactZippedWithDate(1, "2025-12-25", "10:00")
        )

        val contacts = listOf(
            Contact(
                id = 1,
                title = "Dr",
                firstName = "John",
                lastName = "Doe",
                sex = 'm',
                phone = "111111",
                message = "Meeting on dd.MM.yyyy at HH:mm"
            )
        )

        viewModel.updateContactsMessageAfterZippingItWithDates(zippedData, contacts)

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            contactRepository.updateAll(
                match { updatedContacts ->
                    updatedContacts.size == 1 &&
                    updatedContacts[0].message.contains("25.12.2025") &&
                    updatedContacts[0].message.contains("10:00")
                }
            )
        }
    }

    @Test
    fun `updateContactsMessageAfterZippingItWithDates handles existing dates in message`() = runTest {
        val zippedData = listOf(
            ContactZippedWithDate(1, "2025-12-25", "10:00")
        )

        val contacts = listOf(
            Contact(
                id = 1,
                title = "Dr",
                firstName = "John",
                lastName = "Doe",
                sex = 'm',
                phone = "111111",
                message = "Meeting on 01.01.2025 at 08:30"
            )
        )

        viewModel.updateContactsMessageAfterZippingItWithDates(zippedData, contacts)

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            contactRepository.updateAll(
                match { updatedContacts ->
                    updatedContacts.size == 1 &&
                    updatedContacts[0].message.contains("25.12.2025") &&
                    updatedContacts[0].message.contains("10:00")
                }
            )
        }
    }

    @Test
    fun `selectedBackgroundPictureId emits value from repository`() = runTest {
        val testImageId = 42
        every { backgroundImageManagerRepository.get() } returns flowOf(testImageId)

        val newViewModel = ContactCheckBeforeSubmitViewModel(
            contactRepository,
            eventRepository,
            backgroundImageManagerRepository
        )

        val result = newViewModel.selectedBackgroundPictureId.first()

        assertEquals(testImageId, result)
    }
}
