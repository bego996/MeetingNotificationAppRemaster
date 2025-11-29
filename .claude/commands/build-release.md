Baue eine Release-APK für das Android-Projekt:

WICHTIG: Dieser Build erfordert eine korrekt konfigurierte `keystore.properties` Datei.

1. Überprüfe ob `keystore.properties` existiert (ohne den Inhalt zu lesen)
2. Falls nicht vorhanden, warne den Benutzer und stoppe
3. Falls vorhanden:
   - Führe `./gradlew clean` aus
   - Führe `./gradlew assembleRelease` aus
4. Überprüfe ob der Build erfolgreich war
5. Zeige mir:
   - Den Pfad zur generierten Release-APK
   - Die Dateigröße der APK
   - Ob die APK signiert wurde

Falls der Build fehlschlägt, analysiere die Fehler und schlage Lösungen vor (ohne sensible Keystore-Informationen zu zeigen).
