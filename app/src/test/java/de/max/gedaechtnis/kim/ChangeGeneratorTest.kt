package de.max.gedaechtnis.kim

import de.max.gedaechtnis.TestData
import de.max.gedaechtnis.kim.engine.*
import org.junit.Assert.*
import org.junit.Test

class ChangeGeneratorTest {
    private val b = TestData.balancing
    private val diff = Difficulty(b)
    private val gen = RoundGenerator(TestData.catalog, b)

    private fun round(level: Int, seed: Long, mode: Mode = Mode.CHANGED) =
        gen.generate(diff.config(mode, diff.forLevel(level), seed, 390))

    @Test fun changesOnlyOnValidFieldsAndAppearInGrid() {
        for (level in 1..6) for (seed in 0L until 40) {
            val r = round(level, seed)
            for (c in r.changes) {
                assertTrue(c.position in r.original.indices)
                when (c.type) {
                    ChangeType.REMOVED -> assertNull(r.changed[c.position])
                    ChangeType.REPLACED, ChangeType.RECOLORED -> assertNotEquals(c.before!!.id, r.changed[c.position]!!.obj.id)
                    ChangeType.SWAPPED -> assertEquals(c.after, r.changed[c.position]!!.obj)
                    ChangeType.ROTATED -> assertNotEquals(r.original[c.position].rotation, r.changed[c.position]!!.rotation)
                    ChangeType.FAKE -> assertEquals(r.original[c.position], r.changed[c.position])
                }
            }
        }
    }

    @Test fun changeCountWithinLevelRange() {
        for (level in 1..6) {
            val spec = b.level(level)
            for (seed in 0L until 60) {
                val n = round(level, seed).realChanges.size
                assertTrue("Stufe $level hat $n Änderungen", n in spec.changesMin..spec.changesMax)
            }
        }
    }

    @Test fun onlyAllowedChangeTypesAppear() {
        for (level in 1..6) for (seed in 0L until 40) {
            val allowed = b.level(level).changeTypes
            assertTrue(round(level, seed).changes.all { it.type in allowed })
        }
    }

    @Test fun fakeChangesOnlyFromLevelSix() {
        for (level in 1..5) for (seed in 0L until 60) assertTrue(round(level, seed).fakes.isEmpty())
        val withFakes = (0L until 200).count { round(6, it).fakes.isNotEmpty() }
        assertTrue("Fakes kommen in Stufe 6 vor", withFakes > 0)
    }

    @Test fun noChangeIsPossibleOnlyInLevelSix() {
        for (level in 1..5) for (seed in 0L until 60) assertTrue(round(level, seed).realChanges.isNotEmpty())
        assertTrue((0L until 400).any { round(6, it).realChanges.isEmpty() })
    }

    @Test fun adaptiveInterpolationMatchesLevelTable() {
        val d = Difficulty(b)
        assertEquals(1.0, d.position(1.0), 1e-9)
        assertEquals(6.0, d.position(10.0), 1e-9)
        assertEquals(1 + (6.4 - 1) * 5 / 9, d.position(6.4), 1e-9)
        assertEquals(b.level(1).memorizeSec, d.forAdaptive(1.0).memorizeSec)
        assertEquals(b.level(6).memorizeSec, d.forAdaptive(10.0).memorizeSec)
        assertEquals(b.level(6).factor, d.forAdaptive(10.0).factor, 1e-9)
        val mid = d.forAdaptive(5.5)
        assertTrue(mid.memorizeSec in b.level(6).memorizeSec..b.level(1).memorizeSec)
        // monoton fallend bei steigendem d
        val seq = (10..100 step 5).map { d.forAdaptive(it / 10.0).memorizeSec }
        assertEquals(seq.sortedDescending(), seq)
    }
}
