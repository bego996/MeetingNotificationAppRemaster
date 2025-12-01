package com.simba.meetingnotification.home

import android.content.res.Resources
import com.simba.meetingnotification.ui.data.entities.DateMessageSent
import com.simba.meetingnotification.ui.data.repositories.BackgroundImageManagerRepository
import com.simba.meetingnotification.ui.data.repositories.DateMessageSendRepository
import com.simba.meetingnotification.ui.home.HomeScreenViewModel
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
class HomeScreenViewModelTest {

    private lateinit var dateMessageSendRepository: DateMessageSendRepository
    private lateinit var resources: Resources
    private lateinit var backgroundImageManagerRepository: BackgroundImageManagerRepository
    private lateinit var viewModel: HomeScreenViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        dateMessageSendRepository = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        backgroundImageManagerRepository = mockk(relaxed = true)

        // Setup default mock responses
        every { dateMessageSendRepository.getLastSendetInfos() } returns flowOf(null)
        every { backgroundImageManagerRepository.get() } returns flowOf(1)

        viewModel = HomeScreenViewModel(
            dateMessageSendRepository,
            resources,
            backgroundImageManagerRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dateMessageSendUiState emits empty values when repository returns null`() = runTest {
        every { dateMessageSendRepository.getLastSendetInfos() } returns flowOf(null)

        val newViewModel = HomeScreenViewModel(
            dateMessageSendRepository,
            resources,
            backgroundImageManagerRepository
        )

        val uiState = newViewModel.dateMessageSendUiState.first()

        assertEquals("", uiState.lastTimeSendet)
        assertEquals("", uiState.lastDateSendet)
    }

    @Test
    fun `dateMessageSendUiState emits data when repository returns valid message`() = runTest {
        val testMessage = DateMessageSent(
            id = 1,
            lastTimeSendet = "14:30",
            lastDateSendet = "01.12.2025"
        )

        every { dateMessageSendRepository.getLastSendetInfos() } returns flowOf(testMessage)

        val newViewModel = HomeScreenViewModel(
            dateMessageSendRepository,
            resources,
            backgroundImageManagerRepository
        )

        val uiState = newViewModel.dateMessageSendUiState.first()

        assertEquals("14:30", uiState.lastTimeSendet)
        assertEquals("01.12.2025", uiState.lastDateSendet)
    }

    @Test
    fun `selectedBackgroundPictureId emits value from repository`() = runTest {
        val testImageId = 777
        every { backgroundImageManagerRepository.get() } returns flowOf(testImageId)

        val newViewModel = HomeScreenViewModel(
            dateMessageSendRepository,
            resources,
            backgroundImageManagerRepository
        )

        val result = newViewModel.selectedBackgroundPictureId.first()

        assertEquals(testImageId, result)
    }

    @Test
    fun `changeDefaultImageInDatastore calls repository save method`() = runTest {
        viewModel.changeDefaultImageInDatastore()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { backgroundImageManagerRepository.save() }
    }

    @Test
    fun `changeDefaultImageInDatastore can be called multiple times`() = runTest {
        viewModel.changeDefaultImageInDatastore()
        viewModel.changeDefaultImageInDatastore()
        viewModel.changeDefaultImageInDatastore()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 3) { backgroundImageManagerRepository.save() }
    }

    @Test
    fun `resourcesState contains injected resources`() {
        assertEquals(resources, viewModel.resourcesState)
    }
}
