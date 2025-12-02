package com.simba.meetingnotification.ui.services

import android.app.AlertDialog
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Toast
import com.simba.meetingnotification.ui.R
import com.simba.meetingnotification.ui.contact.ContactReadyForSms
import com.simba.meetingnotification.ui.data.entities.DateMessageSent
import com.simba.meetingnotification.ui.data.entities.Event
import com.simba.meetingnotification.ui.data.repositories.ContactRepository
import com.simba.meetingnotification.ui.data.repositories.DateMessageSendRepository
import com.simba.meetingnotification.ui.data.repositories.EventRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TAG = SmsSendingService::class.simpleName

class SmsSendingService : Service() {                         // Dienst(Service), der SMS-Nachrichten versendet

    //region Properties
    private lateinit var contactRepository: ContactRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var dateMessageSendRepository: DateMessageSendRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val messageQueue = ArrayDeque<SmsMessage>()       // Warteschlange für SMS-Nachrichten
    //endregion

    //region Companion object
    companion object {
        private var instance: SmsSendingService? = null
        fun getInstance(): SmsSendingService? = instance
    }
    //endregion

    //region OverrideMethods
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    //@RequiresApi(Build.VERSION_CODES.TIRAMISU)
    // Wird beim Erstellen des Dienstes aufgerufen
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG,"serviceOnCreate()")
        instance = this
    }

    override fun onDestroy() {                                // Wird beim Zerstören des Dienstes aufgerufen
        instance = null
        super.onDestroy()
    }
    //endregion

    //region Methods
    fun initialize(contactRepository: ContactRepository,eventRepository: EventRepository,dateMessageSendRepository: DateMessageSendRepository){
        this.contactRepository = contactRepository
        this.eventRepository = eventRepository
        this.dateMessageSendRepository = dateMessageSendRepository
        Log.d(TAG,"Initialize repositories in SmsSendingService block done()")
        Log.d(TAG, "contactRepository initialized: ${::contactRepository.isInitialized}")
        Log.d(TAG, "eventRepository initialized: ${::eventRepository.isInitialized}")
        Log.d(TAG, "dateMessageSendRepository initialized: ${::dateMessageSendRepository.isInitialized}")
    }

    //Callback function. Function to get the upcoming event for specific contact from the database.
    fun getUpcomingEventForContact(contactId: Int,callback: (Event) -> Unit){
        val dateTimeNow = LocalDateTime.now()
        val dateFormated = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        serviceScope.launch {
            try {
                val allEventsForChoosenContact = contactRepository.getContactWithEvents(contactId).first().events
                val upcomingEventSortedOut = allEventsForChoosenContact.filter { event ->
                    LocalDateTime.of(LocalDate.parse(event.eventDate,dateFormated), LocalTime.parse(event.eventTime)).isAfter(dateTimeNow) }.sortedBy { event -> event.eventDate }[0]

                callback(upcomingEventSortedOut)
            }catch (e: NoSuchElementException){
                Log.e(TAG,"Exception",e)
                FirebaseCrashlytics.getInstance().recordException(e)
                throw NoSuchElementException("No events found for contactId: $contactId")
            }
        }
    }

    //will be called when a event for a contact is already notified. It updates the Event in the database.
    fun updateEventInDatabase(event: Event){
        serviceScope.launch {
            eventRepository.updateItem(event)
        }
    }

    fun insertDatesForSendMessages(dateMessageSent: DateMessageSent){
        serviceScope.launch {
            dateMessageSendRepository.insert(dateMessageSent)
            Log.d(TAG,"Information for last sended inserted for date ${dateMessageSent.lastDateSendet} and time ${dateMessageSent.lastTimeSendet}")
        }
    }

    fun addMessageToQueue(contactInformation: List<ContactReadyForSms>) {                                   // Fügt eine Liste von Kontakten zur SMS-Warteschlange hinzu
        val contactMaper = contactInformation.map { SmsMessage(it.contactId,it.phoneNumber, it.message, it.fullName) }   // Wandelt die Kontakte in SMS-Nachrichten um
        contactMaper.forEach { contact ->
            if (!messageQueue.contains(contact)) {            // Vermeidet das Hinzufügen doppelter Nachrichten
                messageQueue.add(contact)                     // Fügt die Nachricht zur Warteschlange hinzu
            }
        }
    }

    fun getContactsInSmsQueueWithId() : List<Int> = messageQueue.toList().map { contact -> contact.contactId}

    fun getMessageQueue(): ArrayDeque<SmsMessage> = messageQueue

    fun removeContactFromQueue(contactId: Int){
        if (messageQueue.any { contactInQueue -> contactInQueue.contactId == contactId }){
            val indexOfToBeRemovedContactInQueue = messageQueue.indexOf(messageQueue.first { contact -> contact.contactId == contactId })
            messageQueue.removeAt(indexOfToBeRemovedContactInQueue)
        }
    }

    fun sendNextMessage(context: Context) {                   // Sendet die nächste Nachricht aus der Warteschlange
        Log.d(TAG,"sendNextMessage() is called.")
        if (messageQueue.isNotEmpty()) {                      // Prüft, ob die Warteschlange leer ist
            val nextMessage = messageQueue.removeFirst()      // Holt die erste Nachricht und entfernt sie aus der Warteschlange
            val uniqueRequestCode = nextMessage.contactId       //diesen unique code brauce ich weil ab api 31 nicht mehr FlAG_IMUTABLE (mit dem konnte man vor api 31 die extras aktualisieren im gleichen intent) verwendet werden kann.

            val smsIntent = PendingIntent.getBroadcast(
                context,
                uniqueRequestCode,
                Intent("SMS_SENT").apply {  //hier kommt der unique requestcode rein. In meinem fall die contact id sowie unten (aber mit echtem namen contactId).
                    `package` = context.packageName
                    putExtra("contactId",nextMessage.contactId)     //Fügt die contactId zum Intent hinzu um den Receiver die id des erfolgreich gesendeten nachricht an contacts zu geben.
                    putExtra("SmsQueueSize",messageQueue.size)
                },     // Erzeugt ein PendingIntent für das "SMS_SENT"-Broadcast
                PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {    // Für neuere Android-Versionen
                val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
                val smsManager = getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)

                smsManager.sendTextMessage(
                    nextMessage.phoneNumber,                  // Telefonnummer des Empfängers
                    null,
                    nextMessage.message,                      // Nachrichtentext
                    smsIntent, // PendingIntent für das Ergebnis
                    null
                )
            } else {
                val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
                val smsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId)

                smsManager.sendTextMessage(
                    nextMessage.phoneNumber,
                    null,
                    nextMessage.message,
                    smsIntent,
                    null
                )
            }
        }
    }

    fun showMessageSendDialog(context: Context, fullName: String, onResult: (Boolean) -> Unit) {            // Zeigt einen Bestätigungsdialog an
        val builder = AlertDialog.Builder(context)           // Erstellt einen Dialog-Builder
        builder.setTitle(context.resources.getString(R.string.sms_send_request))                  // Setzt den Titel des Dialogs
        builder.setMessage("${context.resources.getString(R.string.do_you_want_to_send_the_notification_message_to_this_contacts)}: \n$fullName ?")  // Fragt den Benutzer, ob die Nachricht gesendet werden soll

        builder.setPositiveButton(context.resources.getString(R.string.accept)) { dialog, which ->                  // Akzeptiert das Senden der Nachricht
            Toast.makeText(context, context.resources.getString(R.string.accepted), Toast.LENGTH_SHORT).show()      // Zeigt eine Toast-Nachricht an
            onResult(true)                                                           // Setzt das Ergebnis auf `true`
        }

        builder.setNegativeButton(context.resources.getString(R.string.deny)) { dialog, which ->                    // Lehnt das Senden der Nachricht ab
            Toast.makeText(context, context.resources.getString(R.string.denied), Toast.LENGTH_SHORT).show()        // Zeigt eine Toast-Nachricht an
            onResult(false)                                                          // Setzt das Ergebnis auf `false`
        }

        builder.create().show()                                                      // Erstellt und zeigt den Dialog an
    }
    //endregion

    //region Dataclasses
    data class SmsMessage(val contactId: Int,val phoneNumber: String, val message: String, val fullName: String)   // Datenklasse für eine SMS-Nachricht
    //endregion
}


