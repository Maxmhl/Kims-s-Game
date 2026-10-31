package de.max.gedaechtnis.leitner.engine

import de.max.gedaechtnis.kim.engine.ErrorRule
import de.max.gedaechtnis.kim.engine.LeitnerSpec
import kotlinx.serialization.Serializable

@Serializable
enum class LeitnerMode { NORMAL, STRICT, HARD }

enum class Direction { FORWARD, BACKWARD }

data class Schedule(val box: Int, val nextDue: Long)

/** Fälligkeit und Fachwechsel nach dem Leitner-System. Tage als epochDay. */
class Scheduler(private val specs: Map<LeitnerMode, LeitnerSpec>) {

    fun spec(mode: LeitnerMode): LeitnerSpec = specs.getValue(mode)

    fun isDue(nextDue: Long, today: Long): Boolean = today >= nextDue

    /** Neue Karte: Fach 1, heute fällig. */
    fun newCard(today: Long) = Schedule(1, today)

    fun review(mode: LeitnerMode, box: Int, correct: Boolean, today: Long): Schedule {
        val s = spec(mode)
        val b = box.coerceIn(1, s.boxes)
        return if (correct) {
            val nb = minOf(b + 1, s.boxes)
            // Im letzten Fach richtig: gilt als gelernt, bleibt mit letztem Intervall in der Wiederholung.
            Schedule(nb, today + s.intervals[nb - 1])
        } else {
            val nb = when (s.onError) {
                ErrorRule.BACK_ONE -> maxOf(b - 1, 1)
                ErrorRule.TO_FIRST -> 1
            }
            Schedule(nb, today + 1)
        }
    }

    fun isMastered(mode: LeitnerMode, box: Int) = box >= spec(mode).boxes

    /** Fach beim Wechsel der Fächerzahl (7 → 5: Fach 6 und 7 werden zu Fach 5; 5 → 7: unverändert). */
    fun convertBox(box: Int, toMode: LeitnerMode): Int = box.coerceIn(1, spec(toMode).boxes)

    fun intervalLabelDays(mode: LeitnerMode, box: Int): Int = spec(mode).intervals[box.coerceIn(1, spec(mode).boxes) - 1]
}

object AnswerCheck {
    /** Hart: richtig nur wenn antwort.trim().lowercase() == lösung.trim().lowercase(). */
    fun isCorrect(answer: String, solution: String, timedOut: Boolean = false): Boolean {
        if (timedOut) return false
        return answer.trim().lowercase() == solution.trim().lowercase()
    }
}
