package com.simba.meetingnotification.contact

import android.content.res.Resources
import com.simba.meetingnotification.ui.contact.ContactReadyForSms
import com.simba.meetingnotification.ui.contact.ContactsSearchScreenViewModel
import com.simba.meetingnotification.ui.contact.EventDateTitle
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.repositories.BackgroundImageManagerRepository
import com.simba.meetingnotification.ui.data.repositories.ContactRepository
import com.simba.meetingnotification.ui.services.ServiceAction
import com.simba.meetingnotification.ui.services.SmsSendingServiceInteractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsSearchScreenViewModelTest {

    private lateinit var contactRepository: ContactRepository
    private lateinit var backgroundImageManagerRepository: BackgroundImageManagerRepository
    private lateinit var resources: Resources
    private lateinit var viewModel: ContactsSearchScreenViewModel
    private lateinit var smsServiceInteractor: SmsSendingServiceInteractor

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        contactRepository = mockk(relaxed = true)
        backgroundImageManagerRepository = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        smsServiceInteractor = mockk(relaxed = true)

        // Setup default mock responses
        every { backgroundImageManagerRepository.get() } returns flowOf(1)
        every { contactRepository.getAllContactsStream() } returns flowOf(emptyList())

        viewModel = ContactsSearchScreenViewModel(
            contactRepository,
            backgroundImageManagerRepository,
            resources
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `contactsUiState emits contacts from repository`() = runTest {
        val testContacts = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message"),
            Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Message")
        )

        every { contactRepository.getAllContactsStream() } returns flowOf(testContacts)

        val newViewModel = ContactsSearchScreenViewModel(
            contactRepository,
            backgroundImageManagerRepository,
            resources
        )

        val uiState = newViewModel.contactsUiState.first()

        assertEquals(2, uiState.contactList.size)
        assertEquals("John", uiState.contactList[0].firstName)
        assertEquals("Jane", uiState.contactList[1].firstName)
    }

    @Test
    fun `selectedBackgroundPictureId emits value from repository`() = runTest {
        val testImageId = 42
        every { backgroundImageManagerRepository.get() } returns flowOf(testImageId)

        val newViewModel = ContactsSearchScreenViewModel(
            contactRepository,
            backgroundImageManagerRepository,
            resources
        )

        val result = newViewModel.selectedBackgroundPictureId.first()

        assertEquals(testImageId, result)
    }

    @Test
    fun `addContactsToDatabase inserts matching contacts`() = runTest {
        val contactList = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message"),
            Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Message"),
            Contact(3, "Mr", "Bob", "Johnson", 'm', "333333", "Message")
        )

        val compareIds = listOf(1, 3)

        viewModel.addContactsToDatabase(contactList, compareIds)

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { contactRepository.insertItem(contactList[0]) }
        coVerify(exactly = 0) { contactRepository.insertItem(contactList[1]) }
        coVerify(exactly = 1) { contactRepository.insertItem(contactList[2]) }
    }

    @Test
    fun `addContactsToDatabase handles empty compareIds list`() = runTest {
        val contactList = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        )

        val compareIds = emptyList<Int>()

        viewModel.addContactsToDatabase(contactList, compareIds)

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { contactRepository.insertItem(any()) }
    }

    @Test
    fun `addContactsToDatabase handles non-matching IDs`() = runTest {
        val contactList = listOf(
            Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        )

        val compareIds = listOf(99, 100)

        viewModel.addContactsToDatabase(contactList, compareIds)

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { contactRepository.insertItem(any()) }
    }

    @Test
    fun `insertContactsToSmsQueue calls service interactor with correct action`() {
        viewModel.smsServiceInteractor = smsServiceInteractor

        val contacts = listOf(
            ContactReadyForSms(1, "111111", "Test message", "John Doe"),
            ContactReadyForSms(2, "222222", "Another message", "Jane Smith")
        )

        viewModel.insertContactsToSmsQueue(contacts)

        verify {
            smsServiceInteractor.performServiceActionToAddOrSend(
                ServiceAction.PushContact,
                contacts
            )
        }
    }

    @Test
    fun `getContactsFromSmsQueue returns contact IDs from service`() {
        viewModel.smsServiceInteractor = smsServiceInteractor

        val expectedIds = listOf(1, 2, 3)
        every {
            smsServiceInteractor.performServiceActionToGetContactFromQueue(ServiceAction.GetContactsFromQueue)
        } returns expectedIds

        val result = viewModel.getContactsFromSmsQueue()

        assertEquals(expectedIds, result)
    }

    @Test
    fun `getContactsFromSmsQueue returns null when service interactor is null`() {
        viewModel.smsServiceInteractor = null

        val result = viewModel.getContactsFromSmsQueue()

        assertNull(result)
    }

    @Test
    fun `sendCommandToSendAllMessages calls service interactor with SendMessage action`() {
        viewModel.smsServiceInteractor = smsServiceInteractor

        viewModel.sendCommandToSendAllMessages()

        verify {
            smsServiceInteractor.performServiceActionToAddOrSend(
                ServiceAction.SendMessage,
                listOf()
            )
        }
    }

    @Test
    fun `removeContactIfInSmsQueue calls service interactor with correct contact ID`() {
        viewModel.smsServiceInteractor = smsServiceInteractor

        val contactId = 42

        viewModel.removeContactIfInSmsQueue(contactId)

        verify {
            smsServiceInteractor.performServiceActionToRemoveFromQueue(
                ServiceAction.DeleteContactFromQueue,
                contactId
            )
        }
    }

    @Test
    fun `getCalender returns empty list initially`() {
        val result = viewModel.getCalender()

        assertEquals(0, result.size)
    }

    @Test
    fun `contactsReadOnly is initially empty`() = runTest {
        val result = viewModel.contactsReadOnly.first()

        assertEquals(0, result.size)
    }

    @Test
    fun `isLoading is true initially`() {
        val result = viewModel.isLoading.value

        assertEquals(true, result)
    }

    @Test
    fun `smsServiceInteractor can be set and accessed`() {
        assertNull(viewModel.smsServiceInteractor)

        viewModel.smsServiceInteractor = smsServiceInteractor

        assertNotNull(viewModel.smsServiceInteractor)
    }
}
