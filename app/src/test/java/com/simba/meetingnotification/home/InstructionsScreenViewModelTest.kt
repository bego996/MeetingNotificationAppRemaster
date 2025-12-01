package com.simba.meetingnotification.home

import com.simba.meetingnotification.ui.data.repositories.InstructionReadRepository
import com.simba.meetingnotification.ui.home.InstructionsScreenViewModel
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InstructionsScreenViewModelTest {

    private lateinit var instructionReadRepository: InstructionReadRepository
    private lateinit var viewModel: InstructionsScreenViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        instructionReadRepository = mockk(relaxed = true)

        // Setup default mock response
        every { instructionReadRepository.get() } returns flowOf(false)

        viewModel = InstructionsScreenViewModel(instructionReadRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `instructionReadState emits false when repository returns false`() = runTest {
        every { instructionReadRepository.get() } returns flowOf(false)

        val newViewModel = InstructionsScreenViewModel(instructionReadRepository)

        val result = newViewModel.instructionReadState.first()

        assertFalse(result)
    }

    @Test
    fun `instructionReadState emits true when repository returns true`() = runTest {
        every { instructionReadRepository.get() } returns flowOf(true)

        val newViewModel = InstructionsScreenViewModel(instructionReadRepository)

        val result = newViewModel.instructionReadState.first()

        assertTrue(result)
    }

    @Test
    fun `setInstructionToReaden calls repository instructionReaden method`() = runTest {
        viewModel.setInstructionToReaden()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { instructionReadRepository.instructionReaden() }
    }

    @Test
    fun `setInstructionToReaden can be called multiple times`() = runTest {
        viewModel.setInstructionToReaden()
        viewModel.setInstructionToReaden()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { instructionReadRepository.instructionReaden() }
    }
}
