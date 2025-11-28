# 🔒 Sicherheitsanalyse: Meeting Notification App

**Datum:** 2025-11-28
**Analysierte Version:** 1.0.1 (versionCode: 2)

---

## ⚠️ KRITISCHE SICHERHEITSLÜCKEN

### 1. **Exported BroadcastReceiver ohne Schutz** (KRITISCH - CVE-ähnlich)

**Betroffene Dateien:**
- `app/src/main/AndroidManifest.xml:47-52` (WeeklyAlarmReceiver)
- `app/src/main/AndroidManifest.xml:54-59` (WeeklyEventDbUpdater)

**Problem:**
```xml
<receiver
    android:name=".broadcastReceiver.WeeklyAlarmReceiver"
    android:enabled="true"
    android:exported="true">  <!-- ❌ KRITISCH! -->
```

**Risiko:**
- **Jede App** auf dem Gerät kann diese Receiver triggern
- WeeklyAlarmReceiver kann wiederholt Benachrichtigungen auslösen → **DoS-Angriff**
- WeeklyEventDbUpdater kann Datenbankoperationen auslösen → **Resource Exhaustion**
- Keine Permission-Checks vorhanden

**Exploit-Szenario:**
```kotlin
// Bösartige App kann folgendes ausführen:
val intent = Intent("android.intent.action.BOOT_COMPLETED")
intent.setClassName("com.simba.meetingnotification.ui",
    "com.simba.meetingnotification.ui.broadcastReceiver.WeeklyAlarmReceiver")
context.sendBroadcast(intent)
// → Triggert Datenbankabfragen und Benachrichtigungen
```

**Fix:**
```xml
<receiver
    android:name=".broadcastReceiver.WeeklyAlarmReceiver"
    android:enabled="true"
    android:exported="false">  <!-- ✅ Nur intern verwendbar -->
```

---

### 2. **Firebase API-Key öffentlich im Repository** (KRITISCH)

**Betroffene Datei:**
- `app/google-services.json:18`

**Problem:**
```json
"current_key": "AIzaSyDtx1vWVRsnOoPlR1Skc3OqBxTDizYHV2E"
```

**Risiko:**
- API-Key ist in Git eingecheckt und öffentlich einsehbar
- Kann für **API-Missbrauch** genutzt werden (Crashlytics-Spam, Analytics-Manipulation)
- Quota-Limits könnten ausgeschöpft werden

**Fix:**
1. Datei aus Git entfernen:
   ```bash
   git rm --cached app/google-services.json
   echo "app/google-services.json" >> .gitignore
   ```
2. Firebase API-Keys rotieren (neue Keys generieren)
3. App-Prüfung in Firebase Console aktivieren

---

### 3. **Fehlende Input-Validierung bei SMS-Versand** (HOCH)

**Betroffene Dateien:**
- `SmsSendingService.kt:153-159`
- `ContactCheckBeforeSubmitViewModel.kt:306-311`

**Probleme:**

a) **Keine Telefonnummern-Validierung:**
```kotlin
// app/src/main/java/com/simba/meetingnotification/ui/services/SmsSendingService.kt:154
smsManager.sendTextMessage(
    nextMessage.phoneNumber,  // ❌ Keine Validierung!
    null,
    nextMessage.message,
    smsIntent,
    null
)
```

**Risiko:**
- Premium-Nummern (0900-*) könnten Kosten verursachen
- Internationale Nummern ohne Kontrolle
- Ungültige Formate führen zu Crashes

b) **Öffentliche messageQueue:**
```kotlin
// app/src/main/java/com/simba/meetingnotification/ui/services/SmsSendingService.kt:41
var messageQueue = ArrayDeque<SmsMessage>()  // ❌ Public var!
```

**Risiko:**
- Kann von anderen Komponenten manipuliert werden
- Reflection-Angriffe möglich

c) **Keine Längenbeschränkung:**
- SMS-Nachrichten werden nicht auf max. 160 Zeichen geprüft

**Fix:**
```kotlin
private val messageQueue = ArrayDeque<SmsMessage>()  // ✅ Private

private fun isValidPhoneNumber(number: String): Boolean {
    return number.matches(Regex("^\\+?[1-9]\\d{1,14}$")) &&
           !number.startsWith("0900") // Premium-Nummern blockieren
}
```

---

## 🔶 HOHE SICHERHEITSRISIKEN

### 4. **Unverschlüsseltes Backup sensibler Daten** (HOCH)

**Betroffene Datei:**
- `app/src/main/AndroidManifest.xml:21`

**Problem:**
```xml
android:allowBackup="true"  <!-- ❌ Kein Ausschluss sensibler Daten -->
```

**Risiko:**
- Room-Datenbank mit Kontakten, Telefonnummern und Nachrichten wird gebackupt
- ADB-Backup kann ohne Root extrahiert werden:
  ```bash
  adb backup -f backup.ab com.simba.meetingnotification.ui
  ```
- DataStore-Präferenzen ebenfalls enthalten

**Fix:**
```xml
<!-- backup_rules.xml -->
<full-backup-content>
    <exclude domain="database" path="contact_database"/>
    <exclude domain="sharedpref" path="datastore"/>
</full-backup-content>
```

---

### 5. **SmsSentReceiver akzeptiert unklare ResultCodes** (MITTEL)

**Betroffene Datei:**
- `SmsSentReceiver.kt:25`

**Problem:**
```kotlin
if (resultCode == Activity.RESULT_OK || resultCode == 4 || resultCode == 1) {
    // ❌ Was bedeuten 4 und 1?
```

**Risiko:**
- Undokumentierte ResultCodes können zu Fehlverhalten führen
- Eventuelle falsch-positive Benachrichtigungen
- Code ist schwer wartbar

**Fix:**
```kotlin
companion object {
    private const val RESULT_CODE_CUSTOM_OK_1 = 1
    private const val RESULT_CODE_CUSTOM_OK_4 = 4
}

if (resultCode == Activity.RESULT_OK ||
    resultCode == RESULT_CODE_CUSTOM_OK_1 ||
    resultCode == RESULT_CODE_CUSTOM_OK_4) {
    // Besser: Nur RESULT_OK akzeptieren nach Tests
```

---

### 6. **Fehlende SQL-Injection-Prävention in Kalendername-Matching** (MITTEL)

**Betroffene Datei:**
- `ContactCheckBeforeSubmitViewModel.kt:95-98`

**Problem:**
```kotlin
it.eventName.split(" ")[0] == contact.firstName.split(" ")[0] &&
it.eventName.split(" ")[1] == contact.lastName
```

**Risiko:**
- Kein Array-Bounds-Check → **IndexOutOfBoundsException**
- Namen mit Sonderzeichen könnten zu Problemen führen

**Fix:**
```kotlin
val eventParts = it.eventName.split(" ")
val firstParts = contact.firstName.split(" ")
if (eventParts.size >= 2 && firstParts.isNotEmpty()) {
    eventParts[0] == firstParts[0] && eventParts[1] == contact.lastName
} else false
```

---

## 🟡 MITTLERE SICHERHEITSRISIKEN

### 7. **Code-Obfuscation deaktiviert** (MITTEL)

**Betroffene Datei:**
- `app/build.gradle.kts:48-58`

**Problem:**
```kotlin
// buildTypes {
//     release {
//         signingConfig = signingConfigs.getByName("release")
//         isMinifyEnabled = true  // ❌ Auskommentiert
```

**Risiko:**
- Release-APK enthält lesbaren Code
- Reverse Engineering ist trivial
- Business-Logik kann kopiert werden

**Fix:** Release-Build aktivieren und minify einschalten.

---

### 8. **Fehlende Certificate Pinning** (NIEDRIG)

**Problem:**
- Firebase-Kommunikation ohne Certificate Pinning
- Man-in-the-Middle-Angriffe theoretisch möglich (erfordert Root)

**Fix:** Für hochsensible Apps erwägen:
```xml
<network-security-config>
    <domain-config>
        <domain includeSubdomains="true">firebase.google.com</domain>
        <pin-set>
            <pin digest="SHA-256">...</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

---

## ✅ GUT IMPLEMENTIERTE SICHERHEITSASPEKTE

1. ✅ **SmsSentReceiver** ist korrekt geschützt:
   - `android:exported="false"`
   - `android:permission="android.permission.BROADCAST_SMS"`

2. ✅ **PendingIntent** verwendet `FLAG_IMMUTABLE` (API 31+)

3. ✅ **Room Database** verwendet Parameterized Queries (SQL-Injection-sicher)

4. ✅ **ProGuard-Regeln** sind vorhanden und gut konfiguriert

5. ✅ **Firebase Crashlytics** für Error-Tracking aktiviert

---

## 📋 PRIORISIERTE MASSNAHMEN-LISTE

### Sofort beheben (KRITISCH):
1. ⬜ `WeeklyAlarmReceiver` und `WeeklyEventDbUpdater` auf `exported="false"` setzen
2. ⬜ `google-services.json` aus Git entfernen und API-Keys rotieren
3. ⬜ Telefonnummern-Validierung implementieren
4. ⬜ `messageQueue` auf `private` setzen

### Kurzfristig (HOCH):
5. ⬜ Backup-Ausschlüsse für sensitive Daten konfigurieren
6. ⬜ Array-Bounds-Checks in `zipDatesToContacts()`
7. ⬜ ResultCodes dokumentieren/bereinigen

### Mittelfristig (MITTEL):
8. ⬜ Release-Build mit ProGuard aktivieren
9. ⬜ SMS-Längen-Validierung
10. ⬜ Unit-Tests für Security-kritische Funktionen

---

## 📊 RISIKO-BEWERTUNG NACH OWASP MOBILE TOP 10 (2024)

| OWASP Kategorie | Status | Bemerkungen |
|----------------|--------|-------------|
| M1: Improper Credential Usage | ⚠️ Mittel | Firebase API-Key im Repository |
| M2: Inadequate Supply Chain Security | ✅ Gut | Dependencies aktuell |
| M3: Insecure Authentication/Authorization | ❌ Kritisch | Exported Receiver ohne Auth |
| M4: Insufficient Input/Output Validation | ⚠️ Hoch | Fehlende SMS-Validierung |
| M5: Insecure Communication | ✅ Gut | HTTPS durch Firebase |
| M6: Inadequate Privacy Controls | ⚠️ Hoch | Backup ohne Ausschlüsse |
| M7: Insufficient Binary Protections | ⚠️ Mittel | ProGuard deaktiviert |
| M8: Security Misconfiguration | ❌ Kritisch | Exported Receiver |
| M9: Insecure Data Storage | ⚠️ Hoch | Unverschlüsselte DB im Backup |
| M10: Insufficient Cryptography | ➖ N/A | Keine Crypto verwendet |

**Gesamt-Risikoscore:** 🔴 **HOCH** (6.5/10)

---

## 🔧 SCHNELLREFERENZ FÜR FIXES

### Fix 1: Exported Receiver absichern
```xml
<!-- AndroidManifest.xml -->
<receiver
    android:name=".broadcastReceiver.WeeklyAlarmReceiver"
    android:enabled="true"
    android:exported="false">  <!-- ÄNDERUNG HIER -->
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
    </intent-filter>
</receiver>

<receiver
    android:name=".broadcastReceiver.WeeklyEventDbUpdater"
    android:enabled="true"
    android:exported="false">  <!-- ÄNDERUNG HIER -->
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
    </intent-filter>
</receiver>
```

### Fix 2: google-services.json aus Git entfernen
```bash
git rm --cached app/google-services.json
echo "app/google-services.json" >> .gitignore
git commit -m "Remove Firebase config from version control"
```

### Fix 3: SMS-Validierung hinzufügen
```kotlin
// SmsSendingService.kt
private fun isValidPhoneNumber(number: String): Boolean {
    // E.164 Format: +[country code][number]
    if (!number.matches(Regex("^\\+?[1-9]\\d{1,14}$"))) return false

    // Premium-Nummern blockieren (DE: 0900, 0137, 118)
    if (number.matches(Regex("^(\\+49)?0?(900|137|118).*"))) return false

    return true
}

fun sendNextMessage(context: Context) {
    if (messageQueue.isNotEmpty()) {
        val nextMessage = messageQueue.removeFirst()

        // VALIDIERUNG HINZUFÜGEN
        if (!isValidPhoneNumber(nextMessage.phoneNumber)) {
            Log.e(TAG, "Invalid phone number: ${nextMessage.phoneNumber}")
            FirebaseCrashlytics.getInstance().log("Blocked invalid phone: ${nextMessage.phoneNumber}")
            sendNextMessage(context) // Nächste Nachricht probieren
            return
        }

        // Rest des Codes...
    }
}
```

### Fix 4: messageQueue absichern
```kotlin
// SmsSendingService.kt - Zeile 41
private val messageQueue = ArrayDeque<SmsMessage>()  // private statt var
```

### Fix 5: Backup-Ausschlüsse
```xml
<!-- app/src/main/res/xml/backup_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <!-- Datenbank mit sensiblen Kontaktdaten ausschließen -->
    <exclude domain="database" path="contact_database"/>
    <exclude domain="database" path="contact_database-shm"/>
    <exclude domain="database" path="contact_database-wal"/>

    <!-- DataStore ausschließen -->
    <exclude domain="sharedpref" path="datastore"/>
</full-backup-content>
```

---

**Erstellt mit:** Claude Code (Anthropic)
**Analyst:** Claude Sonnet 4.5
