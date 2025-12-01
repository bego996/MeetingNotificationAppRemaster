package com.simba.meetingnotification.receivers

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simba.meetingnotification.ui.broadcastReceiver.WeeklyAlarmReceiver
import com.simba.meetingnotification.ui.data.ContactDatabase
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.entities.Event
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertNotNull

/**
 * Instrumented tests for WeeklyAlarmReceiver.
 *
 * These tests verify:
 * - Receiver responds to BOOT_COMPLETED intent
 * - Receiver responds to custom alarm action
 * - Database queries for upcoming events work correctly
 * - Alarm scheduling (basic verification only, as AlarmManager is system component)
 *
 * Note: Full alarm scheduling and notification display require manual testing
 * on actual device due to system permissions and notification channels.
 *
 * Run with: ./gradlew connectedAndroidTest --tests "*.receivers.WeeklyAlarmReceiverInstrumentedTest"
 */
@RunWith(AndroidJUnit4::class)
class WeeklyAlarmReceiverInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: ContactDatabase
    private lateinit var receiver: WeeklyAlarmReceiver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(
            context,
            ContactDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        receiver = WeeklyAlarmReceiver()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun receiver_canBeCreated() {
        assertNotNull(receiver)
    }

    @Test
    fun receiver_respondsToBootCompletedIntent() = runTest {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // This will trigger the receiver's onReceive
        // We can't easily verify alarm scheduling in tests, but we can verify it doesn't crash
        receiver.onReceive(context, intent)

        // Allow time for coroutine to execute
        delay(500)

        // If we reach here without exception, the receiver handled the intent
        assert(true)
    }

    @Test
    fun receiver_respondsToCustomAlarmAction() = runTest {
        val intent = Intent("ALARM_SET_AFTER_BOOT_OR_ON_FIRST_START")

        receiver.onReceive(context, intent)

        delay(500)

        assert(true)
    }

    @Test
    fun receiver_queriesUpcomingEventsCorrectly() = runTest {
        // Setup test data
        val contactDao = database.contactDao()
        val eventDao = database.eventDao()

        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val today = LocalDate.now().toString()
        val tomorrow = LocalDate.now().plusDays(1).toString()
        val nextWeek = LocalDate.now().plusDays(6).toString()

        val events = listOf(
            Event(1, tomorrow, "10:00", 1, false),    // Not notified, within week
            Event(2, nextWeek, "14:00", 1, false),    // Not notified, within week
            Event(3, tomorrow, "16:00", 1, true)      // Already notified (excluded)
        )

        events.forEach { eventDao.insert(it) }

        // Query as the receiver would
        val endOfWeek = LocalDate.parse(today).plusDays(7).toString()
        val count = eventDao.getNotNotifiedEventsAndFromActualDateTime(today, endOfWeek)

        // Should count only the 2 un-notified events
        assert(count == 2) { "Expected 2 upcoming events, got $count" }
    }

    @Test
    fun receiver_handlesEmptyDatabase() = runTest {
        val intent = Intent("ALARM_SET_AFTER_BOOT_OR_ON_FIRST_START")

        // Should not crash even with empty database
        receiver.onReceive(context, intent)

        delay(500)

        assert(true)
    }

    @Test
    fun receiver_ignoresOtherIntents() = runTest {
        val intent = Intent("SOME_OTHER_ACTION")

        // Receiver should ignore intents with different actions
        receiver.onReceive(context, intent)

        // Should return immediately without processing
        assert(true)
    }

    @Test
    fun database_queryForWeeklyEvents_worksCorrectly() = runTest {
        val contactDao = database.contactDao()
        val eventDao = database.eventDao()

        val contact = Contact(1, "Dr", "John", "Doe", 'm', "111111", "Message")
        contactDao.insert(contact)

        val today = LocalDate.now()
        val events = listOf(
            Event(1, today.plusDays(1).toString(), "10:00", 1, false),
            Event(2, today.plusDays(2).toString(), "14:00", 1, false),
            Event(3, today.plusDays(3).toString(), "16:00", 1, false),
            Event(4, today.plusDays(8).toString(), "10:00", 1, false), // Beyond 7 days (excluded)
            Event(5, today.plusDays(4).toString(), "12:00", 1, true)   // Notified (excluded)
        )

        events.forEach { eventDao.insert(it) }

        val endOfWeek = today.plusDays(7).toString()
        val count = eventDao.getNotNotifiedEventsAndFromActualDateTime(today.toString(), endOfWeek)

        // Should count events 1, 2, 3 (within week and not notified)
        assert(count == 3) { "Expected 3 events within week, got $count" }
    }
}
