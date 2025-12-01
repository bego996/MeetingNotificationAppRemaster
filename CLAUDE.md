# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Meeting Notification App is an Android application that automatically links contacts with calendar events and sends personalized SMS reminders. The app scans the phone's calendar for events containing contact names, then allows users to send customized SMS notifications to those contacts.

**Tech Stack:**
- Kotlin with Jetpack Compose and Material 3
- Room Database (version 2.6.1)
- WorkManager for background tasks
- DataStore for user preferences
- Firebase (Crashlytics, Analytics, Performance Monitoring)

**Target:** Android API 27-35 (minSdk: 27, compileSdk: 35, targetSdk: 35)

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore.properties configuration)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run specific test class
./gradlew test --tests com.simba.meetingnotification.contact.ContactCheckBeforeSubmitViewModelTest

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

### Database Schema & Relations

The app uses Room with three main entities and foreign key relationships:

**Entities:**
- `Contact` (contacts table): Stores contact information including id, title, firstName, lastName, sex, phone, message
- `Event` (events table): Stores calendar events with eventId, eventDate, eventTime, contactOwnerId (FK to Contact.id), isNotified
- `DateMessageSent` (date_message_sent table): Tracks when messages were sent

**Key Relations:**
- `ContactWithEvents`: One-to-many relation (Contact → Events) using `@Relation`
- `EventWithContact`: Many-to-one relation (Event → Contact)
- Events CASCADE DELETE when parent Contact is deleted

**Database:**
- Location: `ui/data/ContactDatabase.kt`
- Version: 21 (uses fallbackToDestructiveMigration)
- DAOs: `ContactDao`, `EventDao`, `MessageSendDao` (in `ui/data/dao/`)

### Dependency Injection Pattern

The app uses manual dependency injection via `AppContainer`:

1. `AppContainer` (interface) defines repository contracts
2. `AppDataContainer` (implementation) provides lazy-initialized repositories
3. `MeetingNotificationApplication` creates the container instance
4. ViewModels receive repositories via `AppViewModelProvider.Factory`

**Repository implementations:**
- `OfflineContactRepository` → `ContactRepository`
- `OfflineEventRepository` → `EventRepository`
- `OfflineDateMessageSendRepository` → `DateMessageSendRepository`

Additional repositories managed in `MeetingNotificationApplication`:
- `BackgroundImageManagerRepository` (DataStore-based)
- `InstructionReadRepository` (DataStore-based)

### Navigation Architecture

The app uses Jetpack Compose Navigation with destination objects:

**Entry Point:** `NotificationApp.kt` creates NavController and `ContactsSearchScreenViewModel` (shared across screens)

**Navigation Graph:** `ApplicationNavGraph.kt` defines routes:
- `HomeDestination` - Main screen (start destination)
- `BeforeTemplateDestination` - Contact/event review before sending
- `SavedContactsDestination` - View saved contacts
- `SearchContactDestination` - Search phone contacts
- `InstructionsDestination` - Help screen

**Pattern:** Each screen has a companion destination object implementing `NavigationDestination` with route and titleRes.

### SMS Sending Flow

The SMS system uses a Service-based queue architecture:

1. **Service:** `SmsSendingService` maintains an in-memory `ArrayDeque<SmsMessage>` queue
2. **Initialization:** ViewModels call `SmsSendingServiceInteractor.initializeService()` which injects repositories
3. **Queue Management:**
   - `addMessageToQueue()` adds contacts to queue
   - `sendNextMessage()` sends via SmsManager with API-level handling (API 31+ vs older)
   - Uses PendingIntent with unique requestCode (contactId) for delivery tracking
4. **Receiver:** `SmsSentReceiver` handles SMS_SENT broadcasts, updates Event.isNotified in database
5. **Confirmation:** Shows AlertDialog before each send, user must approve each message

**Important:** The service uses a singleton pattern (`getInstance()`) and requires initialization before use.

### Background Workers & Receivers

**Weekly Notification Receiver (`WeeklyAlarmReceiver`):**
- Triggers on `BOOT_COMPLETED` and custom action `ALARM_SET_AFTER_BOOT_OR_ON_FIRST_START`
- Counts upcoming events for the next 7 days (not yet notified)
- Shows notification via `NotificationHelper.showWeeklyReminder()`
- Reschedules itself for next Sunday at 12:00 using AlarmManager
- Handles API 31+ exact alarm permission checks

**Database Cleanup Receiver (`WeeklyEventDbUpdater`):**
- Also triggers on `BOOT_COMPLETED`
- Purpose: Clean up old/expired events (implementation in receiver class)

**Note:** Both receivers are registered in AndroidManifest.xml with `BOOT_COMPLETED` intent filter.

### Calendar Integration

The app reads device calendars to find events matching stored contacts:

1. Searches for events where title contains contact's firstName + lastName
2. Filters events that are in the future and have specific times (not all-day)
3. Creates Event entities linked to contacts via `contactOwnerId` foreign key
4. ViewModel methods handle calendar queries and contact-event matching

**Permissions Required:**
- `READ_CALENDAR`, `WRITE_CALENDAR`
- `READ_CONTACTS`
- `SEND_SMS`
- `POST_NOTIFICATIONS` (API 33+)
- `SCHEDULE_EXACT_ALARM`
- `RECEIVE_BOOT_COMPLETED`

## Key Implementation Patterns

### ViewModel Initialization
ViewModels receive dependencies through `AppViewModelProvider.Factory` using initializer blocks. Access repositories via `inventoryApplication().container.repositoryName`.

### Room Database Version
Current version is 21. When making schema changes, increment version in `ContactDatabase.kt`. Note: Using `fallbackToDestructiveMigration()` - database will be destroyed on version conflicts.

### API-Level Specific Code
The app contains several API-level conditionals (e.g., Build.VERSION_CODES.S for API 31+):
- SmsManager subscription handling
- Exact alarm permissions
- PendingIntent flags (FLAG_IMMUTABLE required for API 31+)

### Firebase Integration
- Crashlytics logs are used throughout for error tracking
- Performance monitoring and analytics included
- Build config includes firebase plugins and dependencies

### Testing
- Unit tests use MockK for mocking (version 1.13.11)
- Coroutine testing with kotlinx-coroutines-test
- One existing test: `ContactCheckBeforeSubmitViewModelTest.kt`

## Localization

The app supports German and English. String resources follow standard Android i18n patterns with default (German) in `values/` and English in `values-en/`.

## Known Quirks

1. **Shared ViewModel:** `ContactsSearchScreenViewModel` is instantiated in `NotificationApp.kt` and passed to the NavHost, making it shared across multiple destinations.

2. **Service Singleton:** `SmsSendingService` uses a singleton pattern with nullable instance that must be initialized before use.

3. **Database Destructive Migration:** Schema changes will wipe all data. For production, implement proper migrations.

4. **Release Build Configuration:** Currently commented out in `app/build.gradle.kts`. Requires `keystore.properties` file for signing.

5. **Package Structure:** All code lives under `com.simba.meetingnotification.ui` even though some files (like data layer) aren't UI-related.
- Du sprichst mit mir nur auf deutsch.

---

## 📋 TODO: Verbleibende Test-Implementierung

### ✅ Phase 1: UI & Integration Tests (Abgeschlossen)
- [x] HomeScreenTest.kt (12 Tests)
- [x] ContactCheckBeforeSubmitTest.kt (11 Tests)
- [x] ContactsScreenTest.kt (9 Tests)
- [x] ContactsSearchScreenTest.kt (18 Tests)
- [x] ContactCreationFlowTest.kt (10 Integration Tests)
- [x] SmsFlowIntegrationTest.kt (13 Integration Tests)
- **Gesamt Phase 1: 73 Tests ✅**

### 🔲 Phase 2: Instrumented Tests (4 Dateien)

#### 1. MonthlyEventDbCleanerInstrumentedTest.kt
- [ ] Testet WeeklyEventDbUpdater BroadcastReceiver
- [ ] Überprüft automatisches Löschen alter/abgelaufener Events
- [ ] Verifiziert Datenbankbereinigung nach BOOT_COMPLETED
- [ ] Testet Query-Logik für expired events

#### 2. NotificationHelperInstrumentedTest.kt
- [ ] Testet NotificationHelper.showWeeklyReminder()
- [ ] Verifiziert Notification Channel Erstellung
- [ ] Prüft Notification Content (Titel, Text, Icons)
- [ ] Testet verschiedene Event-Counts (0, 1, mehrere)

#### 3. ContactsProviderTest.kt
- [ ] Testet System-Kontakt Zugriff via ContentResolver
- [ ] Verifiziert loadContacts() aus ContactsSearchScreenViewModel
- [ ] Prüft Kontakt-Parsing (Name, Telefon, Titel, Geschlecht)
- [ ] Testet Fehlerbehandlung bei fehlenden/ungültigen Feldern

#### 4. CalendarProviderTest.kt
- [ ] Testet Kalender-Integration via CalendarContract
- [ ] Verifiziert loadCalender() aus ContactsSearchScreenViewModel
- [ ] Prüft Event-Queries (Datum-Filter, 7-Tage-Fenster)
- [ ] Testet Event-Parsing (Datum, Zeit, Titel)

---

### 🔲 Phase 3: Edge Cases & DataStore Tests

#### 1. Edge Cases zu bestehenden Tests hinzufügen:
- [ ] **Null/Empty Strings:** Leere Kontaktnamen, Messages, Telefonnummern
- [ ] **Sehr lange Texte:** 1000+ Zeichen in Messages, extrem lange Namen
- [ ] **Ungültige Daten:** Falsche Datumsformate, negative IDs, ungültige Telefonnummern
- [ ] **Grenzwerte:** contactId = 0, MaxInt, sehr weit in Zukunft liegende Daten (Jahr 9999)
- [ ] **Gleichzeitige Zugriffe:** Mehrere Threads schreiben/lesen gleichzeitig aus DB
- [ ] **Duplikate:** Versuche denselben Contact mehrfach einzufügen
- [ ] **Orphan-Daten:** Events ohne Contact (Foreign Key Test)

#### 2. DataStore Integration Tests:
- [ ] **BackgroundImageManagerRepositoryIntegrationTest.kt**
  - Echtes DataStore verwenden (kein Mock)
  - Preferences schreiben, lesen, updaten
  - Mehrere schnelle Änderungen hintereinander

- [ ] **InstructionReadRepositoryIntegrationTest.kt**
  - Boolean-Flag setzen und auslesen
  - Persistenz über App-Neustart simulieren

#### 3. Performance/Stress Tests (optional):
- [ ] 1000+ Kontakte in Datenbank einfügen und laden
- [ ] Scrolling-Performance mit großen Listen testen
- [ ] 100+ SMS-Queue-Operationen nacheinander
- [ ] Event-Queries mit 1000+ Events

---

### 🎯 Nächster Schritt beim Weitermachen:
Einfach sagen: **"Starte mit Phase 2"** und Claude beginnt mit `MonthlyEventDbCleanerInstrumentedTest.kt`

**Aktueller Status:** Phase 1 ✅ (73 Tests) | Phase 2 ⏳ (4 Dateien) | Phase 3 ⏳ (3 Bereiche)