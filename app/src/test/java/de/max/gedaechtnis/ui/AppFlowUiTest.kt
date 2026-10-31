package de.max.gedaechtnis.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.max.gedaechtnis.MainActivity
import de.max.gedaechtnis.data.LeitnerRepository
import de.max.gedaechtnis.data.SettingsRepository
import de.max.gedaechtnis.kim.engine.Mode
import org.junit.Before
import de.max.gedaechtnis.leitner.engine.LeitnerMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/** UI-Tests auf der JVM (Robolectric): kompletter Kim-Rundenablauf und eine Leitner-Abfrage. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowUiTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject lateinit var leitner: LeitnerRepository
    @Inject lateinit var settings: SettingsRepository

    /** DataStore lebt pro JVM; Modus, Stufe und Adaptiv-Schalter vor jedem Test zurücksetzen. */
    @Before
    fun resetSettings() {
        hilt.inject()
        runBlocking { settings.setMode(Mode.CHANGED); settings.setLevel(1); settings.setAdaptive(false) }
    }

    @Test
    fun kimRoundRunsFromStartToResult() {
        compose.waitForText("Gedächtnistrainer")
        compose.clickText("Kim's Game")                       // Kachel auf dem Start
        compose.waitForText("Runde starten · Stufe 1")
        compose.clickText("Runde starten · Stufe 1")
        compose.waitForText("Fertig eingeprägt")              // Einprägen
        compose.clickText("Fertig eingeprägt")
        compose.waitForText("Auswerten")                      // Pause (3 s) → Abfrage
        compose.clickText("Auswerten")
        compose.waitForText("ERGEBNIS · STUFE 1")             // Ergebnis (Label in Großbuchstaben)
        assertTrue(compose.hasText("Trefferquote"))
        assertTrue(compose.hasText("Nächste Runde"))
        compose.clickText("Zum Start")
        compose.waitForText("Heutiges Training")
    }

    @Test
    fun leitnerReviewMovesCardForward() {
        runBlocking {
            val id = leitner.createDeck("Test", LeitnerMode.NORMAL)
            leitner.addCard(id, "Hauptstadt von Frankreich?", "Paris", null)
        }
        compose.waitForText("1 Karten fällig")                // Start: Karte ist heute fällig
        compose.clickText("Stapel")                           // untere Navigation
        compose.waitForText("Abfrage starten · 1 Karten")
        compose.clickText("Abfrage starten · 1 Karten")
        compose.waitForText("Hauptstadt von Frankreich?")
        compose.clickText("Aufdecken")
        compose.waitForText("Paris")
        compose.clickText("Gewusst")
        compose.waitForText("Richtig – weiter in Fach 2")     // Normal: ein Fach weiter
        compose.clickText("Nächste Karte")
        compose.waitForText("Geschafft")
        assertTrue(compose.hasText("1 von 1 Karten richtig."))
    }

    @Test
    fun abortingARoundReturnsToStart() {
        compose.waitForText("Heutiges Training")
        compose.clickText("Training fortsetzen")              // erster offener Punkt: Kim-Runde
        compose.waitForText("Fertig eingeprägt")
        compose.clickDescription("Schließen")
        compose.waitForText("Runde abbrechen?")
        compose.clickText("Abbrechen")
        compose.waitForText("Heutiges Training")
    }

    @Test
    fun everyKimModeRunsToTheResult() {
        for (mode in listOf("Was fehlt?", "Rekonstruktion", "Freier Abruf", "Sequenz")) {
            compose.waitForText("Heutiges Training")
            compose.clickText("Kim's Game")
            compose.waitForText("Runde starten · Stufe 1")
            compose.clickText(mode)
            compose.clickText("Runde starten · Stufe 1")
            compose.waitForText("Auswerten", timeoutMs = 40_000)   // Einprägen und Pause laufen von selbst ab
            compose.clickText("Auswerten")
            compose.waitForText("ERGEBNIS · STUFE 1")
            compose.clickText("Zum Start")
        }
    }

    @Test
    fun statsAndProfileRenderAfterARound() {
        compose.waitForText("Heutiges Training")
        compose.clickText("Training fortsetzen")
        compose.waitForText("Fertig eingeprägt")
        compose.clickText("Fertig eingeprägt")
        compose.waitForText("Auswerten")
        compose.clickText("Auswerten")
        compose.waitForText("ERGEBNIS · STUFE 1")
        compose.clickText("Zum Start")
        compose.waitForText("Heutiges Training")
        compose.clickText("Statistik")
        compose.waitForText("Verlauf (14 Tage)")
        assertTrue(compose.hasText("Fehler-Heatmap"))
        compose.clickText("Profil")
        compose.waitForText("Tägliche Erinnerung")
    }
}
