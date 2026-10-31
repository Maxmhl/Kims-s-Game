# Gedächtnistrainer

Native Android-App (Kotlin, Jetpack Compose), komplett offline und werbefrei.

- **Kim's Game** – trainiert das visuelle Kurzzeitgedächtnis in sechs Stufen mit fünf Modi (Was fehlt?, Was hat sich
  verändert?, Rekonstruktion, Freier Abruf, Sequenz), adaptivem Modus und täglicher Challenge.
- **Lernkartei** – Karteikarten nach dem Leitner-System (Normal, Streng, Hart), CSV-Import, tägliche Erinnerung.
- **Statistik** – Verlauf, Fehleranalyse nach Änderungsart, Fehler-Heatmap, Bestwerte und Serie.

Die vollständige Spezifikation liegt in `docs/spec.pdf`, Hinweise zum Bauen in `CLAUDE.md`.

## Bauen
```
./gradlew :app:testDebugUnitTest      # Unit-Tests der Engines
./gradlew :app:assembleDebug          # Debug-APK
./gradlew :app:assembleRelease        # signierte Release-APK (siehe CLAUDE.md)
```
Voraussetzungen: JDK 17+, Android SDK (Plattform 35).

Schriften: Space Grotesk, IBM Plex Sans/Mono (SIL OFL). Icons: Material Symbols (Apache 2.0).
