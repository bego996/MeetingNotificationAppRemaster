package com.simba.meetingnotification.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.simba.meetingnotification.ui.data.dataStore
import com.simba.meetingnotification.ui.data.repositories.InstructionReadRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for InstructionReadRepository.
 *
 * Note: This repository uses DataStore which requires Android Context.
 * For comprehensive testing, use Android Instrumented Tests.
 * These tests verify basic logic and data flow patterns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InstructionReadRepositoryTest {

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var preferences: Preferences
    private lateinit var repository: InstructionReadRepository

    private val instructionKey = booleanPreferencesKey("instruction_readen_id")

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)
        preferences = mockk(relaxed = true)

        every { context.dataStore } returns dataStore
    }

    @Test
    fun `get returns false when no preference is set`() = runTest {
        every { preferences[instructionKey] } returns null
        every { dataStore.data } returns flowOf(preferences)

        repository = InstructionReadRepository(context)

        val result = repository.get().first()

        assertFalse(result)
    }

    @Test
    fun `get returns true when instruction was marked as read`() = runTest {
        every { preferences[instructionKey] } returns true
        every { dataStore.data } returns flowOf(preferences)

        repository = InstructionReadRepository(context)

        val result = repository.get().first()

        assertTrue(result)
    }

    @Test
    fun `get returns false when instruction was explicitly set to false`() = runTest {
        every { preferences[instructionKey] } returns false
        every { dataStore.data } returns flowOf(preferences)

        repository = InstructionReadRepository(context)

        val result = repository.get().first()

        assertFalse(result)
    }

    /**
     * Note: Testing instructionReaden() properly requires mocking DataStore.edit()
     * which is complex in unit tests. This test verifies the repository
     * can be instantiated and has the method available.
     * For full testing of instructionReaden(), use Android Instrumented Tests.
     */
    @Test
    fun `repository can be instantiated with context`() {
        repository = InstructionReadRepository(context)

        // Verify repository was created successfully
        assertEquals(context, repository.javaClass.getDeclaredField("context").apply {
            isAccessible = true
        }.get(repository))
    }
}
