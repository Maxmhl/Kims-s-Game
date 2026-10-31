package de.max.gedaechtnis.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.max.gedaechtnis.MainActivity
import de.max.gedaechtnis.data.LeitnerRepository
import de.max.gedaechtnis.data.SettingsRepository
import de.max.gedaechtnis.kim.engine.Mode
import de.max.gedaechtnis.leitner.engine.LeitnerMode
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Inject

/** Erzeugt Screenshots der Hauptbildschirme nach build/screenshots (zum Abgleich mit Kapitel 7 der Spezifikation). */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenshotTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject lateinit var leitner: LeitnerRepository
    @Inject lateinit var settings: SettingsRepository

    private val dir = File("build/screenshots").also { it.mkdirs() }

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            settings.setMode(Mode.CHANGED); settings.setLevel(1); settings.setAdaptive(false)
            val id = leitner.createDeck("Elektrotechnik", LeitnerMode.STRICT)
            leitner.addCard(id, "Welche Farbe hat der Schutzleiter (PE)?", "grün-gelb", null)
            leitner.addCard(id, "Einheit der Spannung?", "Volt", null)
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun capture() {
        compose.waitForText("Heutiges Training"); shot("1-start")
        compose.clickText("Kim's Game"); compose.waitForText("Runde starten · Stufe 1"); shot("2-level")
        compose.clickText("Runde starten · Stufe 1"); compose.waitForText("Fertig eingeprägt"); shot("3-memorize")
        compose.clickText("Fertig eingeprägt"); compose.waitForText("Auswerten"); shot("4-recall")
        compose.clickText("Auswerten"); compose.waitForText("ERGEBNIS · STUFE 1"); shot("5-result")
        compose.clickText("Zum Start"); compose.waitForText("Heutiges Training")
        compose.clickText("Statistik"); compose.waitForText("Verlauf (14 Tage)"); shot("6-stats")
        compose.clickText("Stapel"); compose.waitForText("Abfrage starten · 4 Karten"); shot("7-decks")
        compose.clickText("Abfrage starten · 4 Karten"); compose.waitForText("Aufdecken"); shot("8-review")
        compose.clickText("Aufdecken"); shot("9-review-revealed")
    }
}
