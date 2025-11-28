# Claude Code GitHub Actions - Nutzungsanleitung

## Übersicht

Dieses Dokument beschreibt, wie Claude Code in GitHub Actions für das Meeting Notification App Projekt genutzt werden kann.

---

## 🚀 Anwendungsfälle

### 1. Automatische Code-Reviews in Pull Requests

**Verfügbare Commands:**
```bash
/review              # Vollständiger Code-Review
/security-review     # Sicherheitsanalyse
/pr-comments         # PR-Kommentare auslesen
```

**Praktisch für unser Projekt:**
- Room Database Schema-Änderungen überprüfen
- SMS-Sending Flow auf Sicherheitslücken checken
- API-Level Conditionals validieren (API 27-35)
- Jetpack Compose Best Practices

---

### 2. @claude Mentions in Issues & Pull Requests

Claude kann direkt in GitHub erwähnt werden:

**In Pull Requests:**
```markdown
@claude review the database migration changes
@claude check if this follows our CLAUDE.md patterns
@claude analyze the SMS permission handling
```

**In Issues:**
```markdown
@claude why might SmsSendingService fail on API 31+?
@claude suggest fix for calendar permission handling
@claude explain the ContactWithEvents relation pattern
```

Claude analysiert automatisch den Code mit Kontext aus `CLAUDE.md`.

---

## 🎯 Konkrete Anwendungsfälle für Meeting Notification App

### Sicherheits-Kritisch (SMS-App!)

- ✅ SMS-Permission Validierung
- ✅ PendingIntent Security (API 31+ Immutable Flags)
- ✅ Broadcast Receiver Security
- ✅ Contact/Calendar Permission Checks

### Datenbank-Operationen

- ✅ Room Migration Strategien (aktuell Version 21)
- ✅ Foreign Key Constraints validieren
- ✅ CASCADE DELETE Logik überprüfen
- ✅ ContactWithEvents Relations testen

### Code-Qualität

- ✅ Kotlin Linting automatisieren
- ✅ Jetpack Compose Best Practices
- ✅ Dependency Injection Pattern Checks
- ✅ WorkManager/AlarmManager Validierung

### Testing

- ✅ Unit Test Generierung (MockK 1.13.11)
- ✅ ViewModel Test Patterns
- ✅ Coroutine Testing

---

## 📝 Beispiel-Prompts für unser Projekt

### Sicherheitsanalyse
```
@claude Review SMS sending in SmsSendingService.
Check for:
- Permission validation
- PendingIntent security (API 31+)
- Receiver broadcast security
Following CLAUDE.md architecture
```

### Database Migration
```
@claude I need to add a field to Contact entity.
Current DB version: 21, using fallbackToDestructiveMigration.
Suggest migration considering foreign key relationships.
```

### Test-Generierung
```
@claude Generate unit tests for EventRepository.
Consider:
- MockK version 1.13.11
- Coroutine testing patterns
- ContactWithEvents relation
```

### Refactoring
```
@claude All code is under com.simba.meetingnotification.ui
even data layer. Suggest refactoring to separate concerns.
```

### Performance-Analyse
```
@claude Analyze WeeklyAlarmReceiver performance.
Check:
- Calendar query efficiency
- Database operations
- AlarmManager scheduling logic
```

---

## 🔄 Automatisierte Workflows

### Bei jedem Pull Request

1. `/review` - Code Review
2. `/security-review` - Security Check
3. Automatische Tests laufen lassen

### Vor jedem Release

1. `/security-review` - Umfassender Security Check
2. `./gradlew test` - Unit Tests
3. `./gradlew assembleRelease` - Release Build

### Bei Issues

- Automatische Bug-Kategorisierung
- Feature-Request Analyse
- Schnelle Problem-Diagnose

---

## ✨ Best Practices

### Besonders wichtig bei der SMS-App

#### 1. Permissions
- Validiere SMS, Contacts, Calendar Permissions
- Checke API-Level spezifische Anforderungen
- Teste Permission-Flows auf verschiedenen Android-Versionen

#### 2. Database
- Jede Schema-Änderung reviewen lassen
- Foreign Keys & Relations testen
- Migration-Strategie vor Release validieren

#### 3. Background Tasks
- WeeklyAlarmReceiver Logik validieren
- WorkManager Patterns checken
- AlarmManager API 31+ Handling überprüfen

#### 4. Lokalisierung
- String-Ressourcen (DE/EN) konsistent halten
- Übersetzungen auf Vollständigkeit prüfen

#### 5. API-Level Handling
- Build.VERSION_CODES Conditionals validieren
- API 31+ spezifische Features testen
- Backwards compatibility sicherstellen (minSdk 27)

---

## 🛠️ GitHub Actions Workflow-Beispiel

```yaml
name: Claude AI Code Review
on:
  pull_request:
    types: [opened, synchronize, reopened]

jobs:
  claude-review:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
      issues: write

    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0

      - name: Run Claude Security Review
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          # Claude führt Security Review durch
          claude /security-review

      - name: Run Claude Code Review
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          # Claude führt Code Review durch
          claude /review
```

---

## 🎓 Erweiterte Nutzung

### alwaysThinkingEnabled: true

Da `alwaysThinkingEnabled: true` in den Settings aktiviert ist, erhältst du:

- **Tiefere Analysen** für komplexe SMS/Calendar Logic
- **Bessere Security-Reviews** mit Extended Reasoning
- **Gründlichere Room Database Validierung**
- **Bessere Refactoring-Vorschläge** mit mehr Kontext

### Custom Commands erweitern

Empfohlene zusätzliche Commands:

```bash
/test-generator      # Tests automatisch generieren
/changelog-gen       # Automatische Release Notes
/lint-fix           # Kotlin Linting automatisieren
/db-migration       # Database Migration Helper
```

---

## 🔐 Sicherheitshinweise

### API Key Management

- ✅ API Key nur als GitHub Secret speichern (`ANTHROPIC_API_KEY`)
- ✅ Niemals API Keys im Code oder Workflow hardcoden
- ✅ Regelmäßige Key-Rotation durchführen
- ✅ Berechtigungen auf Minimum limitieren

### Review-Prozess

- ✅ Alle Claude-Vorschläge vor dem Merge überprüfen
- ✅ Sicherheitskritische Änderungen manuell validieren
- ✅ Besonders bei SMS/Contact/Calendar Operationen vorsichtig sein

---

## 📚 Ressourcen

- **Claude Code Dokumentation**: https://code.claude.com/docs
- **GitHub Actions Setup**: https://github.com/anthropics/claude-code-action
- **Anthropic Console**: https://console.anthropic.com
- **Projekt CLAUDE.md**: Enthält alle projektspezifischen Patterns und Architekturen

---

## 🚦 Erste Schritte

### Sofort verfügbar

1. ✅ @claude in Pull Requests erwähnen
2. ✅ @claude in Issues für Hilfe nutzen
3. ✅ Automatische Code-Reviews bei jedem PR

### Nächste Schritte

1. **GitHub Workflow erstellen**: `.github/workflows/claude.yml` konfigurieren
2. **Custom Commands nutzen**: `/review` und `/security-review` in CI/CD einbauen
3. **Team schulen**: @claude Mentions effektiv nutzen
4. **Monitoring**: Review-Qualität und Feedback-Loops etablieren

---

## 💡 Tipps für effektive Nutzung

### Kontext bereitstellen

Immer Bezug auf `CLAUDE.md` nehmen:
```
@claude Review this according to our dependency injection pattern
described in CLAUDE.md
```

### Spezifisch sein

Statt:
```
@claude review this
```

Besser:
```
@claude Review the SMS sending flow in SmsSendingService.
Focus on API 31+ compatibility and PendingIntent security.
```

### CLAUDE.md aktuell halten

- Nach architektonischen Änderungen aktualisieren
- Neue Patterns dokumentieren
- Known Quirks ergänzen

---

**Erstellt:** 2025-11-28
**Projekt:** Meeting Notification App
**Version:** 1.0
