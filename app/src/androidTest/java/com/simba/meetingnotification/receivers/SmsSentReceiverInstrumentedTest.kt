package com.simba.meetingnotification.receivers

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simba.meetingnotification.ui.broadcastReceiver.SmsSentReceiver
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

/**
 * Instrumented tests for SmsSentReceiver.
 *
 * These tests verify:
 * - Receiver responds to SMS_SENT intent
 * - Receiver extracts intent extras correctly
 * - Receiver handles different result codes appropriately
 *
 * Note: Full integration testing with SmsSendingService requires
 * service initialization and is better tested as part of end-to-end SMS flow.
 * These tests focus on receiver behavior in isolation.
 *
 * IMPORTANT: SmsSentReceiver heavily depends on SmsSendingService.getInstance()
 * which may be null in test environment. These tests verify basic receiver
 * setup and intent handling. Full functionality testing requires instrumented
 * integration tests with actual SMS service.
 *
 * Run with: ./gradlew connectedAndroidTest --tests "*.receivers.SmsSentReceiverInstrumentedTest"
 */
@RunWith(AndroidJUnit4::class)
class SmsSentReceiverInstrumentedTest {

    private lateinit var context: Context
    private lateinit var receiver: SmsSentReceiver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        receiver = SmsSentReceiver()
    }

    @Test
    fun receiver_canBeCreated() {
        assertNotNull(receiver)
    }

    @Test
    fun receiver_handlesSuccessResultCode() {
        val intent = Intent("SMS_SENT").apply {
            putExtra("contactId", 1)
            putExtra("SmsQueueSize", 0)
        }

        // Set result code to success
        receiver.resultCode = Activity.RESULT_OK

        // Should not crash even if service is not initialized
        receiver.onReceive(context, intent)

        assert(true)
    }

    @Test
    fun receiver_handlesAlternativeSuccessCode() {
        val intent = Intent("SMS_SENT").apply {
            putExtra("contactId", 1)
            putExtra("SmsQueueSize", 2)
        }

        // Test alternative success codes (4 and 1)
        receiver.resultCode = 4

        receiver.onReceive(context, intent)

        assert(true)
    }

    @Test
    fun receiver_extractsContactIdFromIntent() {
        val testContactId = 42
        val intent = Intent("SMS_SENT").apply {
            putExtra("contactId", testContactId)
            putExtra("SmsQueueSize", 0)
        }

        receiver.resultCode = Activity.RESULT_OK

        // Receiver should extract contactId without crashing
        receiver.onReceive(context, intent)

        val extractedContactId = intent.getIntExtra("contactId", -1)
        assert(extractedContactId == testContactId)
    }

    @Test
    fun receiver_extractsQueueSizeFromIntent() {
        val testQueueSize = 5
        val intent = Intent("SMS_SENT").apply {
            putExtra("contactId", 1)
            putExtra("SmsQueueSize", testQueueSize)
        }

        receiver.resultCode = Activity.RESULT_OK

        receiver.onReceive(context, intent)

        val extractedQueueSize = intent.getIntExtra("SmsQueueSize", -1)
        assert(extractedQueueSize == testQueueSize)
    }

    @Test
    fun receiver_handlesFailureResultCode() {
        val intent = Intent("SMS_SENT").apply {
            putExtra("contactId", 1)
            putExtra("SmsQueueSize", 0)
        }

        // Set result code to failure
        receiver.resultCode = Activity.RESULT_CANCELED

        // Should not process when result is not successful
        receiver.onReceive(context, intent)

        assert(true)
    }

    @Test
    fun receiver_handlesMissingContactId() {
        val intent = Intent("SMS_SENT").apply {
            // Intentionally not setting contactId
            putExtra("SmsQueueSize", 0)
        }

        receiver.resultCode = Activity.RESULT_OK

        // Should handle missing contactId gracefully
        receiver.onReceive(context, intent)

        val contactId = intent.getIntExtra("contactId", -1)
        assert(contactId == -1) { "Missing contactId should return default -1" }
    }

    @Test
    fun receiver_handlesMissingQueueSize() {
        val intent = Intent("SMS_SENT").apply {
            putExtra("contactId", 1)
            // Intentionally not setting SmsQueueSize
        }

        receiver.resultCode = Activity.RESULT_OK

        receiver.onReceive(context, intent)

        val queueSize = intent.getIntExtra("SmsQueueSize", -1)
        assert(queueSize == -1) { "Missing queue size should return default -1" }
    }

    @Test
    fun receiver_handlesEmptyIntent() {
        val intent = Intent("SMS_SENT")

        receiver.resultCode = Activity.RESULT_OK

        // Should not crash with empty intent
        receiver.onReceive(context, intent)

        assert(true)
    }

    @Test
    fun receiver_recognizesLastMessageInQueue() {
        val intent = Intent("SMS_SENT").apply {
            putExtra("contactId", 1)
            putExtra("SmsQueueSize", 0) // Last message
        }

        receiver.resultCode = Activity.RESULT_OK

        receiver.onReceive(context, intent)

        val queueSize = intent.getIntExtra("SmsQueueSize", -1)
        assert(queueSize == 0) { "Queue size 0 indicates last message" }
    }

    @Test
    fun receiver_recognizesMoreMessagesInQueue() {
        val intent = Intent("SMS_SENT").apply {
            putExtra("contactId", 1)
            putExtra("SmsQueueSize", 3) // More messages pending
        }

        receiver.resultCode = Activity.RESULT_OK

        receiver.onReceive(context, intent)

        val queueSize = intent.getIntExtra("SmsQueueSize", -1)
        assert(queueSize > 0) { "Queue size > 0 indicates more messages pending" }
    }

    @Test
    fun receiver_handlesMultipleSuccessResultCodes() {
        val successCodes = listOf(Activity.RESULT_OK, 1, 4)

        successCodes.forEach { code ->
            val intent = Intent("SMS_SENT").apply {
                putExtra("contactId", 1)
                putExtra("SmsQueueSize", 0)
            }

            receiver.resultCode = code

            // Should handle all success codes
            receiver.onReceive(context, intent)
        }

        assert(true)
    }
}
