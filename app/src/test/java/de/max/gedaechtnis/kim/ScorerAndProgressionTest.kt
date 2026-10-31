package de.max.gedaechtnis.kim

import de.max.gedaechtnis.TestData
import de.max.gedaechtnis.kim.engine.*
import org.junit.Assert.*
import org.junit.Test

class ScorerAndProgressionTest {
    private val b = TestData.balancing
    private val scorer = Scorer(b.scoring)
    private val catalog = TestData.catalog
    private val diff = Difficulty(b)
    private val gen = RoundGenerator(catalog, b)
    private val evaluator = Evaluator(catalog)

    private fun ev(t: Int, f: Int, u: Int, lost: Boolean = false) = Evaluation(t, f, u, emptyList(), lost)

    @Test fun specExampleGives22Points() {
        // T=3, F=1, Ü=1, S=2,0, 40 % Restzeit → (30 − 15 − 5) · 2,0 · 1,08 = 22
        assertEquals(22, scorer.points(ev(3, 1, 1), 2.0, true, 12_000, 30_000))
    }

    @Test fun neverNegative() {
        assertEquals(0, scorer.points(ev(0, 5, 5), 3.0, true, 10_000, 30_000))
    }

    @Test fun falseClicksCostNothingWhenPenaltyOff() {
        assertEquals(10, scorer.points(ev(1, 4, 0), 1.0, false, null, null))
    }

    @Test fun timeBonusAndNoLimit() {
        assertEquals(12, scorer.points(ev(1, 0, 0), 1.0, true, 30_000, 30_000))
        assertEquals(10, scorer.points(ev(1, 0, 0), 1.0, true, 30_000, null))
    }

    @Test fun lostRoundScoresZero() {
        assertEquals(0, scorer.points(ev(7, 0, 1, lost = true), 3.0, true, 10_000, 30_000))
    }

    @Test fun evaluatorCountsHitsFalseClicksAndMissed() {
        val round = gen.generate(diff.config(Mode.CHANGED, diff.forLevel(3), 5, 390))
        val real = round.realChanges.map { it.position }
        val wrong = (0 until round.original.size).first { it !in real }
        val e = evaluator.evaluate(round, Answer.Positions(setOf(real[0], wrong)))
        assertEquals(1, e.hits)
        assertEquals(1, e.falseClicks)
        assertEquals(real.size - 1, e.missed)
        val perfect = evaluator.evaluate(round, Answer.Positions(real.toSet()))
        assertEquals(real.size, perfect.hits)
        assertEquals(1.0, perfect.accuracy, 1e-9)
    }

    @Test fun noChangeRoundAnsweredCorrectly() {
        val c = diff.config(Mode.CHANGED, diff.forLevel(6), 1, 390)
        val seed = (0L until 2000).first { gen.generate(c.copy(seed = it)).realChanges.isEmpty() }
        val round = gen.generate(c.copy(seed = seed))
        val ok = evaluator.evaluate(round, Answer.Positions(emptySet()))
        assertEquals(1, ok.hits); assertEquals(0, ok.falseClicks + ok.missed)
        val wrong = evaluator.evaluate(round, Answer.Positions(setOf(0)))
        assertEquals(0, wrong.hits); assertEquals(1, wrong.falseClicks)
    }

    @Test fun levelSixOneErrorLosesRound() {
        val c = diff.config(Mode.CHANGED, diff.forLevel(6), 1, 390)
        val seed = (0L until 2000).first { gen.generate(c.copy(seed = it)).realChanges.size >= 2 }
        val round = gen.generate(c.copy(seed = seed))
        val all = round.realChanges.map { it.position }.toSet()
        assertFalse(evaluator.evaluate(round, Answer.Positions(all)).lost)
        val missOne = evaluator.evaluate(round, Answer.Positions(all - all.first()))
        assertTrue(missOne.lost)
        assertEquals(0, scorer.points(missOne, 3.0, true, 5000, 30000))
    }

    @Test fun missingModeEvaluation() {
        val round = gen.generate(diff.config(Mode.MISSING, diff.forLevel(2), 3, 390))
        val target = round.realChanges.mapNotNull { it.before?.id }
        val decoy = round.options.first { it.id !in target }.id
        val e = evaluator.evaluate(round, Answer.Objects(setOf(target[0], decoy)))
        assertEquals(1, e.hits); assertEquals(1, e.falseClicks); assertEquals(target.size - 1, e.missed)
    }

    @Test fun reconstructionAndSequenceAndFreeRecall() {
        val rec = gen.generate(diff.config(Mode.RECONSTRUCTION, diff.forLevel(5), 9, 390))
        val exact = rec.testedPositions.associateWith { rec.original[it].obj.id }
        assertEquals(exact.size, evaluator.evaluate(rec, Answer.Placement(exact)).hits)
        assertEquals(exact.size, evaluator.evaluate(rec, Answer.Placement(emptyMap())).missed)

        val seq = gen.generate(diff.config(Mode.SEQUENCE, diff.forLevel(4), 9, 390))
        val expected = seq.sequence.reversed().map { it.id } // ab Stufe 4 rückwärts
        assertEquals(expected.size, evaluator.evaluate(seq, Answer.Order(expected)).hits)
        assertTrue(evaluator.evaluate(seq, Answer.Order(seq.sequence.map { it.id })).hits < expected.size)
        val seq1 = gen.generate(diff.config(Mode.SEQUENCE, diff.forLevel(2), 9, 390))
        assertEquals(seq1.sequence.size, evaluator.evaluate(seq1, Answer.Order(seq1.sequence.map { it.id })).hits)

        val free = gen.generate(diff.config(Mode.FREE_RECALL, diff.forLevel(2), 9, 390))
        val bases = free.original.map { it.obj.baseObject }.toSet()
        val e = evaluator.evaluate(free, Answer.Names(bases + "nichtda"))
        assertEquals(bases.size, e.hits); assertEquals(1, e.falseClicks)
    }

    @Test fun reconstructionNeighbourAcceptedBelowLevelFive() {
        val c = diff.config(Mode.RECONSTRUCTION, diff.forLevel(2), 9, 390)
        val rec = gen.generate(c)
        val pos = rec.testedPositions.first()
        val cols = c.columns
        val neighbour = if (pos % cols + 1 < cols) pos + 1 else pos - 1
        val e = evaluator.evaluate(rec, Answer.Placement(mapOf(neighbour to rec.original[pos].obj.id)))
        assertEquals(1, e.hits)
        val strict = gen.generate(diff.config(Mode.RECONSTRUCTION, diff.forLevel(5), 9, 390))
        val p5 = strict.testedPositions.first()
        val n5 = if (p5 % strict.config.columns + 1 < strict.config.columns) p5 + 1 else p5 - 1
        assertEquals(0, evaluator.evaluate(strict, Answer.Placement(mapOf(n5 to strict.original[p5].obj.id))).hits)
    }

    // ---- Auf-/Abstieg ----
    private val prog = Progression(b)

    @Test fun fiveRoundsAtEightyFivePercentUnlock() {
        var p = LevelProgress(unlocked = 2)
        repeat(4) {
            val u = prog.afterRound(p, 2, 0.85)
            assertNull(u.levelCompleted); p = u.progress
        }
        assertEquals(2, p.unlocked)
        val last = prog.afterRound(p, 2, 0.9)
        assertEquals(3, last.progress.unlocked)
        assertEquals(2, last.levelCompleted)
    }

    @Test fun belowThresholdResetsStreak() {
        var p = LevelProgress(unlocked = 1)
        repeat(4) { p = prog.afterRound(p, 1, 0.9).progress }
        p = prog.afterRound(p, 1, 0.84).progress
        assertEquals(0, p.successStreak)
        assertEquals(1, p.unlocked)
    }

    @Test fun threeRoundsBelowFiftyPercentSuggestDemotion() {
        var p = LevelProgress(unlocked = 3)
        var u = prog.afterRound(p, 3, 0.49); p = u.progress; assertFalse(u.suggestDemotion)
        u = prog.afterRound(p, 3, 0.2); p = u.progress; assertFalse(u.suggestDemotion)
        u = prog.afterRound(p, 3, 0.1)
        assertTrue(u.suggestDemotion)
        assertEquals(3, u.progress.unlocked) // nie automatisch
        assertEquals(2, prog.demote(u.progress).unlocked)
        assertEquals(1, prog.demote(LevelProgress(1)).unlocked)
    }

    @Test fun middleRoundBreaksLowStreak() {
        var p = LevelProgress(unlocked = 3)
        p = prog.afterRound(p, 3, 0.4).progress
        p = prog.afterRound(p, 3, 0.4).progress
        p = prog.afterRound(p, 3, 0.7).progress
        assertEquals(0, p.lowStreak)
        assertFalse(prog.afterRound(p, 3, 0.4).suggestDemotion)
    }

    @Test fun lowerLevelRoundsDoNotCount() {
        val p = LevelProgress(unlocked = 4, successStreak = 2)
        assertEquals(p, prog.afterRound(p, 2, 1.0).progress)
    }

    @Test fun lostRoundIsNotSuccess() {
        val p = prog.afterRound(LevelProgress(unlocked = 6, successStreak = 4), 6, 0.9, lost = true)
        assertEquals(0, p.progress.successStreak)
    }

    // ---- Adaptiv ----
    private val rule = AdaptiveRule(b.adaptive)

    @Test fun twoHighRoundsRaiseByPointThree() {
        var s = AdaptiveState(5.0)
        s = rule.afterRound(s, 0.8); assertEquals(5.0, s.d, 1e-9)
        s = rule.afterRound(s, 0.95); assertEquals(5.3, s.d, 1e-9)
    }

    @Test fun lowRoundLowersByPointFive() {
        assertEquals(4.5, rule.afterRound(AdaptiveState(5.0), 0.59).d, 1e-9)
        assertEquals(5.0, rule.afterRound(AdaptiveState(5.0), 0.6).d, 1e-9)
    }

    @Test fun adaptiveClampedTo1And10() {
        assertEquals(1.0, rule.afterRound(AdaptiveState(1.2), 0.0).d, 1e-9)
        var s = AdaptiveState(9.9)
        repeat(6) { s = rule.afterRound(s, 1.0) }
        assertEquals(10.0, s.d, 1e-9)
    }

    @Test fun countdownPausesAndResumes() {
        var t = 0L
        val c = PausableCountdown(10_000) { t }
        c.start(); t = 3000; c.pause(); t = 9000
        assertEquals(7000, c.remainingMs)
        c.start(); t = 15_000
        assertEquals(1000, c.remainingMs)
        t = 100_000
        assertTrue(c.isFinished); assertEquals(0, c.remainingMs)
    }
}
