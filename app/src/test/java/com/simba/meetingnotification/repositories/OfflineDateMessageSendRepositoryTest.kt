package com.simba.meetingnotification.repositories

import com.simba.meetingnotification.ui.data.dao.MessageSendDao
import com.simba.meetingnotification.ui.data.entities.DateMessageSent
import com.simba.meetingnotification.ui.data.repositories.OfflineDateMessageSendRepository
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDateMessageSendRepositoryTest {

    private lateinit var messageSendDao: MessageSendDao
    private lateinit var repository: OfflineDateMessageSendRepository

    @Before
    fun setup() {
        messageSendDao = mockk(relaxed = true)
        repository = OfflineDateMessageSendRepository(messageSendDao)
    }

    @Test
    fun `insert delegates to dao insert`() = runTest {
        val dateMessage = DateMessageSent(
            id = 1,
            lastTimeSendet = "14:30",
            lastDateSendet = "01.12.2025"
        )

        repository.insert(dateMessage)

        coVerify { messageSendDao.insert(dateMessage) }
    }

    @Test
    fun `delete delegates to dao delete`() = runTest {
        val dateMessage = DateMessageSent(
            id = 1,
            lastTimeSendet = "14:30",
            lastDateSendet = "01.12.2025"
        )

        repository.delete(dateMessage)

        coVerify { messageSendDao.delete(dateMessage) }
    }

    @Test
    fun `getLastSendetInfos returns message from dao`() = runTest {
        val testMessage = DateMessageSent(
            id = 1,
            lastTimeSendet = "14:30",
            lastDateSendet = "01.12.2025"
        )

        every { messageSendDao.getLastSendetInfos() } returns flowOf(testMessage)

        val result = repository.getLastSendetInfos().first()

        assertEquals(1, result?.id)
        assertEquals("14:30", result?.lastTimeSendet)
        assertEquals("01.12.2025", result?.lastDateSendet)
    }

    @Test
    fun `getLastSendetInfos returns null when no messages exist`() = runTest {
        every { messageSendDao.getLastSendetInfos() } returns flowOf(null)

        val result = repository.getLastSendetInfos().first()

        assertNull(result)
    }

    @Test
    fun `multiple inserts are handled correctly`() = runTest {
        val message1 = DateMessageSent(1, "10:00", "01.12.2025")
        val message2 = DateMessageSent(2, "14:30", "02.12.2025")
        val message3 = DateMessageSent(3, "16:00", "03.12.2025")

        repository.insert(message1)
        repository.insert(message2)
        repository.insert(message3)

        coVerify(exactly = 1) { messageSendDao.insert(message1) }
        coVerify(exactly = 1) { messageSendDao.insert(message2) }
        coVerify(exactly = 1) { messageSendDao.insert(message3) }
    }
}
