package com.simba.meetingnotification.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simba.meetingnotification.ui.data.ContactDatabase
import com.simba.meetingnotification.ui.data.dao.MessageSendDao
import com.simba.meetingnotification.ui.data.entities.DateMessageSent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Instrumented tests for MessageSendDao using in-memory Room database.
 *
 * These tests verify message send tracking including insert, delete,
 * and retrieval of the most recent message send information.
 *
 * Run with: ./gradlew connectedAndroidTest --tests "*.dao.MessageSendDaoTest"
 */
@RunWith(AndroidJUnit4::class)
class MessageSendDaoTest {

    private lateinit var database: ContactDatabase
    private lateinit var messageSendDao: MessageSendDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            ContactDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        messageSendDao = database.messageSendDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertMessageSend_andRetrieve() = runTest {
        val message = DateMessageSent(
            id = 1,
            lastTimeSendet = "14:30",
            lastDateSendet = "01.12.2025"
        )

        messageSendDao.insert(message)
        val retrieved = messageSendDao.getLastSendetInfos().first()

        assertNotNull(retrieved)
        assertEquals("14:30", retrieved.lastTimeSendet)
        assertEquals("01.12.2025", retrieved.lastDateSendet)
    }

    @Test
    fun insertMessageSend_withDuplicateId_ignoresSecondInsert() = runTest {
        val message1 = DateMessageSent(1, "10:00", "01.12.2025")
        val message2 = DateMessageSent(1, "14:00", "02.12.2025")

        messageSendDao.insert(message1)
        messageSendDao.insert(message2) // Should be ignored

        val retrieved = messageSendDao.getLastSendetInfos().first()

        assertNotNull(retrieved)
        assertEquals("10:00", retrieved.lastTimeSendet) // First insert remains
        assertEquals("01.12.2025", retrieved.lastDateSendet)
    }

    @Test
    fun deleteMessageSend_removesFromDatabase() = runTest {
        val message = DateMessageSent(1, "14:30", "01.12.2025")

        messageSendDao.insert(message)
        messageSendDao.delete(message)

        val retrieved = messageSendDao.getLastSendetInfos().first()

        assertNull(retrieved)
    }

    @Test
    fun getLastSendetInfos_returnsNullWhenEmpty() = runTest {
        val retrieved = messageSendDao.getLastSendetInfos().first()

        assertNull(retrieved)
    }

    @Test
    fun getLastSendetInfos_returnsOnlyMostRecent() = runTest {
        val messages = listOf(
            DateMessageSent(1, "10:00", "01.12.2025"),
            DateMessageSent(2, "14:30", "02.12.2025"),
            DateMessageSent(3, "16:00", "03.12.2025")
        )

        messages.forEach { messageSendDao.insert(it) }

        val retrieved = messageSendDao.getLastSendetInfos().first()

        assertNotNull(retrieved)
        // Should return the most recent based on date and time sorting (DESC)
        assertEquals("03.12.2025", retrieved.lastDateSendet)
        assertEquals("16:00", retrieved.lastTimeSendet)
    }

    @Test
    fun getLastSendetInfos_sortsCorrectlyByDateAndTime() = runTest {
        // Insert in random order
        val messages = listOf(
            DateMessageSent(3, "16:00", "01.12.2025"), // Earlier time on same day
            DateMessageSent(1, "10:00", "02.12.2025"), // Older date
            DateMessageSent(2, "18:00", "01.12.2025")  // Latest time on earliest day
        )

        messages.forEach { messageSendDao.insert(it) }

        val retrieved = messageSendDao.getLastSendetInfos().first()

        assertNotNull(retrieved)
        // Should return the one with the latest date first, then latest time
        assertEquals("02.12.2025", retrieved.lastDateSendet)
        assertEquals("10:00", retrieved.lastTimeSendet)
    }

    @Test
    fun insertMultipleMessages_onlyLastOneReturned() = runTest {
        val messages = listOf(
            DateMessageSent(1, "08:00", "25.11.2025"),
            DateMessageSent(2, "10:30", "26.11.2025"),
            DateMessageSent(3, "14:15", "27.11.2025"),
            DateMessageSent(4, "16:45", "28.11.2025"),
            DateMessageSent(5, "18:00", "29.11.2025")
        )

        messages.forEach { messageSendDao.insert(it) }

        val retrieved = messageSendDao.getLastSendetInfos().first()

        assertNotNull(retrieved)
        assertEquals("29.11.2025", retrieved.lastDateSendet)
        assertEquals("18:00", retrieved.lastTimeSendet)
    }

    @Test
    fun deleteSpecificMessage_othersRemain() = runTest {
        val message1 = DateMessageSent(1, "10:00", "01.12.2025")
        val message2 = DateMessageSent(2, "14:00", "02.12.2025")
        val message3 = DateMessageSent(3, "16:00", "03.12.2025")

        messageSendDao.insert(message1)
        messageSendDao.insert(message2)
        messageSendDao.insert(message3)

        // Delete the middle one
        messageSendDao.delete(message2)

        val retrieved = messageSendDao.getLastSendetInfos().first()

        // Should still return the most recent (message3)
        assertNotNull(retrieved)
        assertEquals("03.12.2025", retrieved.lastDateSendet)
    }

    @Test
    fun insertWithSameDateDifferentTime_sortsCorrectly() = runTest {
        val messages = listOf(
            DateMessageSent(1, "08:00", "01.12.2025"),
            DateMessageSent(2, "14:30", "01.12.2025"),
            DateMessageSent(3, "22:45", "01.12.2025")
        )

        messages.forEach { messageSendDao.insert(it) }

        val retrieved = messageSendDao.getLastSendetInfos().first()

        assertNotNull(retrieved)
        assertEquals("01.12.2025", retrieved.lastDateSendet)
        assertEquals("22:45", retrieved.lastTimeSendet) // Latest time
    }

    @Test
    fun insertAutoGeneratedId_works() = runTest {
        val message = DateMessageSent(
            id = 0, // Auto-generated
            lastTimeSendet = "14:30",
            lastDateSendet = "01.12.2025"
        )

        messageSendDao.insert(message)
        val retrieved = messageSendDao.getLastSendetInfos().first()

        assertNotNull(retrieved)
        assertEquals("14:30", retrieved.lastTimeSendet)
        assertEquals("01.12.2025", retrieved.lastDateSendet)
    }
}
