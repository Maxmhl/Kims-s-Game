package de.max.gedaechtnis.leitner

import de.max.gedaechtnis.TestData
import de.max.gedaechtnis.leitner.engine.*
import org.junit.Assert.*
import org.junit.Test

class LeitnerTest {
    private val s = Scheduler(TestData.balancing.leitner)
    private val today = 20_000L

    @Test fun intervalsPerMode() {
        assertEquals(listOf(1, 2, 4, 8, 16), s.spec(LeitnerMode.NORMAL).intervals)
        assertEquals(listOf(1, 3, 7, 14, 30), s.spec(LeitnerMode.STRICT).intervals)
        assertEquals(listOf(1, 2, 4, 8, 16, 32, 64), s.spec(LeitnerMode.HARD).intervals)
        assertEquals(5, s.spec(LeitnerMode.NORMAL).boxes)
        assertEquals(7, s.spec(LeitnerMode.HARD).boxes)
        assertEquals(LeitnerMode.STRICT, TestData.balancing.leitnerDefaultMode)
    }

    @Test fun dueCalculation() {
        assertTrue(s.isDue(today, today)); assertTrue(s.isDue(today - 3, today)); assertFalse(s.isDue(today + 1, today))
        assertEquals(Schedule(1, today), s.newCard(today))
    }

    @Test fun correctMovesUpWithLongerInterval() {
        for (mode in LeitnerMode.entries) {
            val spec = s.spec(mode)
            for (box in 1 until spec.boxes) {
                val r = s.review(mode, box, true, today)
                assertEquals(box + 1, r.box)
                assertEquals(today + spec.intervals[box], r.nextDue)
            }
        }
    }

    @Test fun correctInLastBoxStaysWithLastInterval() {
        for (mode in LeitnerMode.entries) {
            val spec = s.spec(mode)
            val r = s.review(mode, spec.boxes, true, today)
            assertEquals(spec.boxes, r.box)
            assertEquals(today + spec.intervals.last(), r.nextDue)
            assertTrue(s.isMastered(mode, r.box))
        }
    }

    @Test fun normalWrongGoesBackOneBox() {
        assertEquals(Schedule(2, today + 1), s.review(LeitnerMode.NORMAL, 3, false, today))
        assertEquals(Schedule(1, today + 1), s.review(LeitnerMode.NORMAL, 1, false, today))
    }

    @Test fun strictAndHardWrongGoToBoxOne() {
        assertEquals(Schedule(1, today + 1), s.review(LeitnerMode.STRICT, 4, false, today))
        assertEquals(Schedule(1, today + 1), s.review(LeitnerMode.HARD, 6, false, today))
    }

    @Test fun boxConversionBetweenFiveAndSeven() {
        assertEquals(5, s.convertBox(6, LeitnerMode.STRICT))
        assertEquals(5, s.convertBox(7, LeitnerMode.NORMAL))
        assertEquals(3, s.convertBox(3, LeitnerMode.STRICT))
        assertEquals(6, s.convertBox(6, LeitnerMode.HARD)) // 5 → 7 unverändert
        assertEquals(5, s.convertBox(5, LeitnerMode.HARD))
    }

    @Test fun hardTimeLimit() {
        assertEquals(15, s.spec(LeitnerMode.HARD).timeLimitSec)
        assertTrue(s.spec(LeitnerMode.HARD).typed)
        assertTrue(s.spec(LeitnerMode.STRICT).bothDirections)
    }

    @Test fun hardCompareIgnoresCaseAndOuterWhitespaceOnly() {
        assertTrue(AnswerCheck.isCorrect("  Grün-Gelb ", "grün-gelb"))
        assertTrue(AnswerCheck.isCorrect("ÄPFEL", "äpfel"))
        assertFalse(AnswerCheck.isCorrect("grün gelb", "grün-gelb"))
        assertFalse(AnswerCheck.isCorrect("grun-gelb", "grün-gelb"))
        assertFalse(AnswerCheck.isCorrect("gr ün", "grün"))
        assertFalse(AnswerCheck.isCorrect("grün", "grün", timedOut = true))
    }
}
