package com.simba.meetingnotification.ui.contact

import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simba.meetingnotification.ui.R
import com.simba.meetingnotification.ui.data.entities.Contact
import com.simba.meetingnotification.ui.data.entities.Event
import com.simba.meetingnotification.ui.data.repositories.BackgroundImageManagerRepository
import com.simba.meetingnotification.ui.data.repositories.ContactRepository
import com.simba.meetingnotification.ui.data.repositories.EventRepository
import com.simba.meetingnotification.ui.utils.DebugUtils
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val TAG = ContactCheckBeforeSubmitViewModel::class.simpleName

class ContactCheckBeforeSubmitViewModel(
    private val contactRepository: ContactRepository,                // Repository, das zur Datenverwaltung verwendet wird
    private val eventRepository: EventRepository,
    backgroundImageManagerRepository: BackgroundImageManagerRepository
) : ViewModel() {

    //region Initialize
    init {
        Log.i(TAG,"Viewmode created")
    }
    //endregion

    //region Properties
    val selectedBackgroundPictureId: StateFlow<Int> = backgroundImageManagerRepository
        .get().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000), R.drawable.background_picture_1)

    // Ein StateFlow-Objekt, das den aktuellen Zustand der Kontakte verwaltet
    val contactUiState: StateFlow<ContactsUiState3> =
        contactRepository.getAllContactsStream().map { ContactsUiState3(it) }
            .stateIn(
                scope = viewModelScope,                       // Coroutine-Bereich für Nebenläufigkeit
                started = SharingStarted.WhileSubscribed(5_000L), // Teile die Daten, solange abonniert, mit einer Verzögerung von 5000 ms
                initialValue = ContactsUiState3()             // Anfangswert des StateFlow
            )

    //alle kalenderereignisse die in der datenbank gespeichert sind, auch abgelaufene oder neue.
    private val _contactWithEvents = mutableStateOf<List<Event>>(emptyList())
    val contactWithEvents: State<List<Event>> = _contactWithEvents

    //alle kalendreignisse die wirkich eingetragen sind im kalender aber nicht unbedingt in der db sein müssen, keine zurückliegenden vorhanden..
    private val _calenderState = MutableStateFlow<List<EventDateTitle>>(emptyList())    // MutableStateFlow zur Verwaltung der Kalenderdaten
    private val calenderState: StateFlow<List<EventDateTitle>> = _calenderState         // Unveränderlicher StateFlow zur Abfrage der Kalenderdaten

    //nur die nächsten anstehenden kalenereignisse, pro contact nur ein event möglich.
    private val _calenderStateConnectedToContacts = mutableStateOf<List<ContactZippedWithDate>>(emptyList())            // MutableState zur Verknüpfung von Kontakten mit Kalenderdaten
    val calenderStateConnectedToContacts: State<List<ContactZippedWithDate>> = _calenderStateConnectedToContacts        // Öffentlicher Zugriff auf die verknüpften Kalenderdaten

    // Mit by wird der .value wert versteckt aber es ist equivalent zu einem State wie oben, nur das der State mit .value herausgeholt werden muss.
    private var contactListReadyForSms by mutableStateOf(listOf<ContactReadyForSms>())      // Kontakte, die bereit für den SMS-Versand sind

    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading
    //endregion

    //region Methods

    // Lädt die Kalenderdaten in das MutableStateFlow
    fun loadCalenderData(events: List<EventDateTitle>) {
        _calenderState.value = events
    }


    // Verknüpft Kontakte mit Kalenderdaten
    fun zipDatesToContacts(contacts: List<Contact>) {

        val dates = getCalenderState()                                             // Holt die aktuelle Liste der Kalenderereignisse
        val listZipped = mutableListOf<ContactZippedWithDate>()                    // Eine Liste zur Speicherung der verknüpften Daten
        val outputFormatterDate = DateTimeFormatter.ofPattern("yyyy-MM-dd") // Ausgabeformat für das Datum

        DebugUtils.logExecutionTime(TAG,"ZipDatesToContact()"){
            for (contact in contacts) {
                dates.firstOrNull {
                    // Sichere Überprüfung mit Bounds-Check um IndexOutOfBoundsException zu vermeiden
                    val eventParts = it.eventName.split(" ")
                    val firstNameParts = contact.firstName.split(" ")

                    eventParts.size >= 2 &&
                    firstNameParts.isNotEmpty() &&
                    eventParts[0] == firstNameParts[0] &&
                    eventParts[1] == contact.lastName                       // Prüft, ob ein Kalenderereignis zum Kontakt passt
                }
                    ?.let { date ->                                   // Wenn ein passendes Ereignis gefunden wird
                        listZipped.add(
                            ContactZippedWithDate(
                                contact.id,
                                date.eventDate.toLocalDate()
                                    .format(outputFormatterDate),          // Formatiertes Datum
                                date.eventDate.toLocalTime()
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))    // Uhrzeit als Zeichenkette
                            )
                        )
                    }
            }
            _calenderStateConnectedToContacts.value = listZipped           // Aktualisiert die MutableState-Liste mit den verknüpften Daten
        }
    }


    private fun loadContactsWithEvents() {
        viewModelScope.launch {
            DebugUtils.logExecutionTime(TAG,"loadContactsWithEvents()") {
                val mutableListContactsWithEvents = mutableListOf<Event>()
                val dateNow = LocalDate.now()
                val contactAndEvents = eventRepository.getEventsAfterToday(dateNow.toString())

                mutableListContactsWithEvents.addAll(contactAndEvents)

                _contactWithEvents.value = mutableListContactsWithEvents

                _isLoading.value = false
            }
        }
    }


    fun isContactNotifiedForUpcomingEvent(contactId: Int): Boolean {
        val upcomingEventNotified: Boolean

        val allEventsForChoosenContact = contactWithEvents.value.filter { contactWithEvents -> contactWithEvents.contactOwnerId == contactId }   //gibt event zurück oder false, falls es keine events hatt.
        if (allEventsForChoosenContact.isEmpty()) return false

        val dateTimeNow = LocalDateTime.now()
        val dateFormated = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val upcomingEventSortedOut = allEventsForChoosenContact.filter { event ->
            LocalDateTime.of(
                LocalDate.parse(event.eventDate, dateFormated),
                LocalTime.parse(event.eventTime)
            ).isAfter(dateTimeNow)
        }.sortedBy { event -> event.eventDate }

        upcomingEventNotified = upcomingEventSortedOut.firstOrNull()?.isNotified ?: return false

        return upcomingEventNotified
    }


    suspend fun deleteEventsThatDontExistsInCalenderAnymoreFromDatabase(
        allEventsInCalender: List<EventDateTitle> = getCalenderState(),
    ) {
            DebugUtils.logExecutionTime(TAG,"deleteEventsThatDontExistsInCalenderAnymoreFromDatabase()") {
                val outputFormatterDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val dateNow = LocalDate.now()
                val calenderDateTimes = allEventsInCalender.map { it.eventDate }.toSet()

                val allFutureEvents = eventRepository.getEventsAfterToday(dateNow.toString())

                // Events finden, die in der DB sind, aber nicht im Kalender vorkommen
                val eventsToDelete = allFutureEvents.filter { validEvent ->
                    val eventDateTime = LocalDateTime.of(
                        LocalDate.parse(validEvent.eventDate, outputFormatterDate),
                        LocalTime.parse(validEvent.eventTime)
                    )
                    eventDateTime !in calenderDateTimes

                }
                Log.i(TAG, "Size of to be deleted Events: ${eventsToDelete.size}")
                FirebaseCrashlytics.getInstance().log("Size of to be deleted Events: ${eventsToDelete.size}")

                //delete Events that are no more in calender but still in Database.
                if (eventsToDelete.isNotEmpty()) {
                    eventsToDelete.forEach { eventToDelete ->
                        eventRepository.deleteItem(
                            eventToDelete
                        )
                    }
                    loadContactsWithEvents()
                }
            }
    }


    fun getContactsReadyForSms(): List<ContactReadyForSms> = contactListReadyForSms         // Gibt die Liste der Kontakte für den SMS-Versand zurück

    // Aktualisiert die Liste der Kontakte für den SMS-Versand
    fun updateListReadyForSms(contacts: List<ContactReadyForSms>) { contactListReadyForSms = contacts }


    fun updateContact(contact: Contact) { // Aktualisiert einen bestimmten Kontakt im Repository. kein supend nötig weil courtinescope unten ausgeführt wird.
        viewModelScope.launch {
            contactRepository.updateItem(contact)
        }
    }


    suspend fun insertEventForContact(contactZippedWithDate: List<ContactZippedWithDate>) {
            DebugUtils.logExecutionTime(TAG,"insertEventsForContact()") {
                val events = contactZippedWithDate.map {
                        event -> Event(
                    eventDate = event.date,
                    eventTime = event.time,
                    contactOwnerId = event.contactId)
                }
                eventRepository.insertAllEvents(events)
                loadContactsWithEvents()
            }
    }


    private fun getCalenderState(): List<EventDateTitle> = calenderState.value      // Gibt die aktuelle Liste der Kalenderereignisse zurück


    // Berechnet die Anzahl der Tage bis zum angegebenen Datum von heute weg.
    fun getDayDuration(meetingDate: String): String {
        val meetingDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd") // Datumsformat für die Berechnung
        val daysBeetweenNowAndMeetingDate = ChronoUnit.DAYS.between(
            LocalDate.now(),
            LocalDate.parse(meetingDate,meetingDateFormat)
        )
        return "$daysBeetweenNowAndMeetingDate"
    }


    // Aktualisiert die Nachrichten der Kontakte
    fun updateContactsMessageAfterZippingItWithDates(
        zippedDateToContacts: List<ContactZippedWithDate>,
        contactList: List<Contact>
    ) {
        zippedDateToContacts.isNotEmpty()
            .let {                  // Nur wenn verknüpfte Daten vorhanden sind
                viewModelScope.launch {
                    DebugUtils.logExecutionTime(TAG,"updateContactsMessageAfterZippingItWithDates()"){
                        val contacts = mutableListOf<Contact>()

                        for (zipValue in zippedDateToContacts) { // Durchläuft die Liste der verknüpften Daten
                            val germanDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                            val dateConvertedToGermanFormat = LocalDate.parse(zipValue.date).format(germanDateFormatter)

                            contactList.firstOrNull { it.id == zipValue.contactId }
                                ?.let { contact ->
                                    contacts.add(
                                        Contact(
                                            id = contact.id,
                                            title = contact.title,
                                            firstName = contact.firstName,
                                            lastName = contact.lastName,
                                            sex = contact.sex,
                                            phone = contact.phone,
                                            message = updateMessageWithCorrectDateTime(
                                                contact.message,
                                                dateConvertedToGermanFormat,
                                                zipValue.time
                                            )
                                        )
                                    )
                                }
                        }
                        contactRepository.updateAll(contacts)
                    }
                }
            }
    }
    //endregion
}

//region Data classes or outer methods

// Ersetzt das Datum und die Uhrzeit in der ursprünglichen Nachricht
private fun updateMessageWithCorrectDateTime(originMessage: String, dateReplacement: String, timeReplacement: String): String {

    val regexForAllPossibleDates = """(0[1-9]|[12][0-9]|3[01])\.(0[1-9]|1[0-2])\.(\d{4})""".toRegex()
    val regexForAllPossibleTimes = """(0[0-9]|1[0-9]|2[0-3]):(0[0-9]|[1-5][0-9])""".toRegex()
    var messageReplacement = originMessage

    if (regexForAllPossibleDates.containsMatchIn(originMessage).and(regexForAllPossibleTimes.containsMatchIn(originMessage))) {
        messageReplacement = originMessage
            .replace(regexForAllPossibleDates, dateReplacement)
            .replace(regexForAllPossibleTimes, timeReplacement)
    } else if (originMessage.contains("dd.MM.yyyy").and(originMessage.contains("HH:mm"))) {
        messageReplacement = originMessage
            .replace("dd.MM.yyyy", dateReplacement)
            .replace("HH:mm", timeReplacement)
    }
    return messageReplacement
}


// Datenklasse, die Kontakte mit Datum und Uhrzeit verknüpft
data class ContactZippedWithDate(val contactId: Int, val date: String, val time: String)

// Datenklasse für Paare von Kontakt-ID und Status
@Parcelize
data class MutablePairs2(var first: Int, var second: Boolean) : Parcelable

// Datenklasse zur Verwaltung des UI-Zustands der Kontakte
data class ContactsUiState3(val contactUiState: List<Contact> = listOf())

// Datenklasse für Kontakte, die für den SMS-Versand bereit sind
data class ContactReadyForSms(
    val contactId: Int,
    val phoneNumber: String,
    val message: String,
    val fullName: String
)
//endregion
