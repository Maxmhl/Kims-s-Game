package de.max.gedaechtnis.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.max.gedaechtnis.leitner.engine.Direction
import de.max.gedaechtnis.leitner.engine.LeitnerMode

/** Ergebnis einer Kim-Runde. Zusätzlich zu den Feldern der Spezifikation: Modus, Stufe, Raster und Ablenkung. */
@Entity(tableName = "round_results")
data class RoundResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val epochDay: Long,
    val configJson: String,
    val hits: Int,
    val falseClicks: Int,
    val missed: Int,
    val reactionMs: Long,
    val points: Int,
    val changesJson: String,
    val mode: String,
    val level: Int?,
    val difficulty: Double?,
    val accuracy: Double,
    val lost: Boolean,
    val daily: Boolean,
    val columns: Int,
    val objectCount: Int,
    val distractionCorrect: Int,
    val distractionTotal: Int,
)

/** Bereits gezeigte Ablenkungsaufgabe; wird nie erneut erzeugt. */
@Entity(tableName = "used_distractions", primaryKeys = ["type", "key"])
data class UsedDistraction(val type: String, val key: String, val usedAt: Long)

/** Wie weit der Aufgabenbereich je Typ bereits erweitert wurde. */
@Entity(tableName = "distraction_ranges")
data class DistractionRange(@PrimaryKey val type: String, val level: Int)

@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: LeitnerMode,
)

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val imageUri: String?,
    val box: Int,
    /** epochDay */
    val nextDue: Long,
    val direction: Direction,
    val createdDay: Long,
)

@Entity(tableName = "review_logs")
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val date: Long,
    val correct: Boolean,
    val answerMs: Long,
    val boxBefore: Int,
    val boxAfter: Int,
)

/** Tagesbilanz für Serie und „Heutiges Training“. */
@Entity(tableName = "day_logs")
data class DayLog(
    @PrimaryKey val epochDay: Long,
    val kimRounds: Int = 0,
    val cardReviews: Int = 0,
    val dailyDone: Boolean = false,
    val completed: Boolean = false,
)
