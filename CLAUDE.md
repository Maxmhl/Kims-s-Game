# Gedächtnistrainer – Hinweise für Claude Code

Android-App (Kotlin, Jetpack Compose), komplett offline. Zwei Spiele: **Kim's Game** (visuelles Kurzzeitgedächtnis)
und **Lernkartei** (Leitner-System). Quelle der Wahrheit: `docs/spec.pdf` (Stand 31.10.2026).

## Stack und Paketstruktur
Kotlin · Compose + Material 3 · MVVM (ViewModel + StateFlow) · Navigation Compose · Room · DataStore · Hilt · WorkManager ·
Gradle Kotlin DSL mit Version Catalog (`gradle/libs.versions.toml`) · minSdk 26, targetSdk 35.

`app/src/main/java/de/max/gedaechtnis/`
- `core/` Theme (Farben, Typo), gemeinsame Komponenten, Navigation, Sound, `IconRegistry` (generiert)
- `kim/engine/` **reines Kotlin ohne Android-Imports**: RoundGenerator, ChangeGenerator, Evaluator, Scorer, Difficulty,
  Progression/AdaptiveRule, `distraction/DistractionGenerator`
- `kim/ui/` LevelScreen, MemorizeScreen, DistractScreen, RecallScreen, ResultScreen, KimViewModel
- `leitner/engine/` **reines Kotlin**: Scheduler, AnswerCheck, CsvImport · `leitner/ui/` DeckScreen, ReviewScreen, Editor
- `data/` Room (Entities, DAOs), Repositories, DataStore · `home/` Start und Profil · `stats/` Statistik · `reminder/` WorkManager

## Feste Regeln
- Alle Zahlen aus Kapitel 2 und 3 stehen in `app/src/main/assets/balancing.json`, nicht im Code. Objekt-Katalog: `catalog.json`.
- Spiellogik (`kim/engine`, `leitner/engine`) bleibt frei von Android-Imports und ist per Unit-Test abgedeckt.
- Jede Runde entsteht aus einem Seed (`kotlin.random.Random(seed)`); gleicher Seed = identische Runde.
- Zeitmessung per Coroutines im ViewModel (`PausableCountdown`), nie im Composable; Timer pausieren im Hintergrund.
  Beim Einprägen gilt: länger als 3 s im Hintergrund = Runde ungewertet.
- Alle Texte in `res/values/strings.xml` (Deutsch). Keine Werbung, kein Netzwerk, einzige Berechtigung: Benachrichtigungen.
- Touch-Ziele mindestens 48 dp, Kontrast mindestens 4,5 : 1. Nur dunkles Theme.
- Ablenkungsaufgaben werden nie wiederholt (`used_distractions` in Room, Auto Backup sichert die Datenbank).

## Befehle
- Tests: `./gradlew :app:testDebugUnitTest` · Lint: `./gradlew :app:lintDebug` · Debug-APK: `./gradlew :app:assembleDebug`
- Release-APK: `./gradlew :app:assembleRelease` mit `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword)
  oder den Umgebungsvariablen `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Keystores gehören nie ins Repo.
- Assets neu erzeugen: `python3 tools/gen_assets.py <Ordner mit Material-Symbols-SVGs>` (Icons, Katalog, `IconRegistry`).

## UI-Tests (Robolectric, ohne Emulator)
`app/src/test/.../ui/AppFlowUiTest.kt` spielt per Compose-Test auf der JVM den Kim-Rundenablauf (alle Modi), eine
Leitner-Abfrage, Abbruch, Statistik und Profil durch; `ScreenshotTest` schreibt Screenshots nach `app/build/screenshots`.
Läuft Maven Central im Rate-Limit (HTTP 429), die Robolectric-Laufzeiten (`android-all-instrumented-15-robolectric-12650502-i7.jar`)
vorab in einen Ordner laden und `./gradlew -Drobolectric.dependency.dir=<Ordner> :app:testDebugUnitTest` starten.
Instrumentierte Tests auf einem Gerät gibt es bisher nicht.
