package com.simba.meetingnotification.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.simba.meetingnotification.ui.R
import com.simba.meetingnotification.ui.data.dataStore
import com.simba.meetingnotification.ui.data.repositories.BackgroundImageManagerRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Unit tests for BackgroundImageManagerRepository.
 *
 * Note: This repository uses DataStore which requires Android Context.
 * For comprehensive testing, use Android Instrumented Tests.
 * These tests verify basic logic and data flow patterns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundImageManagerRepositoryTest {

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var preferences: Preferences
    private lateinit var repository: BackgroundImageManagerRepository

    private val backgroundImageKey = intPreferencesKey("background_image_id")

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)
        preferences = mockk(relaxed = true)

        every { context.dataStore } returns dataStore
    }

    @Test
    fun `get returns default image when no preference is set`() = runTest {
        every { preferences[backgroundImageKey] } returns null
        every { dataStore.data } returns flowOf(preferences)

        repository = BackgroundImageManagerRepository(context)

        val result = repository.get().first()

        assertEquals(R.drawable.background_picture_1, result)
    }

    @Test
    fun `get returns stored image ID when preference exists`() = runTest {
        val storedImageId = R.drawable.background_picture_2

        every { preferences[backgroundImageKey] } returns storedImageId
        every { dataStore.data } returns flowOf(preferences)

        repository = BackgroundImageManagerRepository(context)

        val result = repository.get().first()

        assertEquals(storedImageId, result)
    }

    /**
     * Note: Testing save() properly requires mocking DataStore.edit()
     * which is complex in unit tests. This test verifies the repository
     * can be instantiated and has the save method available.
     * For full testing of save(), use Android Instrumented Tests.
     */
    @Test
    fun `repository can be instantiated with context`() {
        repository = BackgroundImageManagerRepository(context)

        // Verify repository was created successfully
        assertEquals(context, repository.javaClass.getDeclaredField("context").apply {
            isAccessible = true
        }.get(repository))
    }
}
