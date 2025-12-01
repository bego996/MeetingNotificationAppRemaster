package com.simba.meetingnotification.contact

import com.simba.meetingnotification.ui.contact.ContactsScreenViewModel
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.repositories.BackgroundImageManagerRepository
import com.simba.meetingnotification.ui.data.repositories.ContactRepository
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
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsScreenViewModelTest {

    private lateinit var contactRepository: ContactRepository
    private lateinit var backgroundImageManagerRepository: BackgroundImageManagerRepository
    private lateinit var viewModel: ContactsScreenViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        contactRepository = mockk(relaxed = true)
        backgroundImageManagerRepository = mockk(relaxed = true)

        // Setup default mock responses
        every { backgroundImageManagerRepository.get() } returns flowOf(1)
        every { contactRepository.getAllContactsStream() } returns flowOf(emptyList())

        viewModel = ContactsScreenViewModel(
            contactRepository,
            backgroundImageManagerRepository
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
            Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Message"),
            Contact(3, "Mr", "Bob", "Johnson", 'm', "333333", "Message")
        )

        every { contactRepository.getAllContactsStream() } returns flowOf(testContacts)

        val newViewModel = ContactsScreenViewModel(
            contactRepository,
            backgroundImageManagerRepository
        )

        val uiState = newViewModel.contactsUiState.first()

        assertEquals(3, uiState.contactUiState.size)
        assertEquals("John", uiState.contactUiState[0].firstName)
        assertEquals("Jane", uiState.contactUiState[1].firstName)
        assertEquals("Bob", uiState.contactUiState[2].firstName)
    }

    @Test
    fun `contactsUiState emits empty list when repository has no contacts`() = runTest {
        every { contactRepository.getAllContactsStream() } returns flowOf(emptyList())

        val newViewModel = ContactsScreenViewModel(
            contactRepository,
            backgroundImageManagerRepository
        )

        val uiState = newViewModel.contactsUiState.first()

        assertEquals(0, uiState.contactUiState.size)
    }

    @Test
    fun `selectedBackgroundPictureId emits value from repository`() = runTest {
        val testImageId = 999
        every { backgroundImageManagerRepository.get() } returns flowOf(testImageId)

        val newViewModel = ContactsScreenViewModel(
            contactRepository,
            backgroundImageManagerRepository
        )

        val result = newViewModel.selectedBackgroundPictureId.first()

        assertEquals(testImageId, result)
    }

    @Test
    fun `deleteContact calls repository deleteItem with correct contact`() = runTest {
        val contactToDelete = Contact(
            id = 1,
            title = "Dr",
            firstName = "John",
            lastName = "Doe",
            sex = 'm',
            phone = "111111",
            message = "Test message"
        )

        viewModel.deleteContact(contactToDelete)

        // Allow time for coroutine to execute
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { contactRepository.deleteItem(contactToDelete) }
    }

    @Test
    fun `deleteContact is called multiple times for multiple contacts`() = runTest {
        val contact1 = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message 1")
        val contact2 = Contact(2, "Ms", "Jane", "Smith", 'f', "222222", "Message 2")
        val contact3 = Contact(3, "Mr", "Bob", "Johnson", 'm', "333333", "Message 3")

        viewModel.deleteContact(contact1)
        viewModel.deleteContact(contact2)
        viewModel.deleteContact(contact3)

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { contactRepository.deleteItem(contact1) }
        coVerify(exactly = 1) { contactRepository.deleteItem(contact2) }
        coVerify(exactly = 1) { contactRepository.deleteItem(contact3) }
    }
}
