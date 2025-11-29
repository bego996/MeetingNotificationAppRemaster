Überprüfe die Android-Permissions im Projekt:

1. Lese die `app/src/main/AndroidManifest.xml` Datei
2. Analysiere alle `<uses-permission>` Einträge
3. Erstelle einen Bericht mit:
   - Liste aller Permissions
   - Kategorisierung in: Normal, Dangerous, Signature
   - Prüfe ob alle Dangerous Permissions im Code auch zur Laufzeit abgefragt werden
   - Identifiziere unnötige oder veraltete Permissions

4. Vergleiche mit den in CLAUDE.md dokumentierten Permissions:
   - READ_CALENDAR, WRITE_CALENDAR
   - READ_CONTACTS
   - SEND_SMS
   - POST_NOTIFICATIONS (API 33+)
   - SCHEDULE_EXACT_ALARM
   - RECEIVE_BOOT_COMPLETED

5. Gib Empfehlungen für:
   - Fehlende Permissions die noch benötigt werden
   - Überflüssige Permissions die entfernt werden können
   - Permissions die noch eine Runtime-Abfrage brauchen
