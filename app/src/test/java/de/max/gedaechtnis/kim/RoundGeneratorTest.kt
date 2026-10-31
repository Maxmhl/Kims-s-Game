package de.max.gedaechtnis.kim

import de.max.gedaechtnis.TestData
import de.max.gedaechtnis.kim.engine.*
import org.junit.Assert.*
import org.junit.Test

class RoundGeneratorTest {
    private val b = TestData.balancing
    private val catalog = TestData.catalog
    private val diff = Difficulty(b)
    private val gen = RoundGenerator(catalog, b)

    private fun config(mode: Mode, level: Int, seed: Long) = diff.config(mode, diff.forLevel(level), seed, screenWidthDp = 390)

    @Test fun catalogHas300ObjectsIn15Categories() {
        assertEquals(300, catalog.objects.size)
        assertEquals(15, catalog.objects.map { it.category }.distinct().size)
        assertEquals(300, catalog.objects.map { it.id }.distinct().size)
        catalog.objects.groupBy { it.baseObject }.values.forEach { assertTrue(it.size in 3..5) }
    }

    @Test fun objectCountPerLevel() {
        val expected = mapOf(1 to 8, 2 to 12, 3 to 16, 4 to 20, 5 to 25)
        for ((level, count) in expected) {
            repeat(20) { seed ->
                val round = gen.generate(config(Mode.CHANGED, level, seed.toLong()))
                assertEquals(count, round.original.size)
            }
        }
        val counts = (0L until 60).map { gen.generate(config(Mode.CHANGED, 6, it)).original.size }
        assertTrue(counts.all { it in 30..40 })
        assertTrue("Stufe 6 variiert", counts.distinct().size > 1)
    }

    @Test fun noDuplicateObjectsOrPositions() {
        for (level in 1..6) for (seed in 0L until 30) {
            val round = gen.generate(config(Mode.CHANGED, level, seed))
            assertEquals(round.original.size, round.original.map { it.obj.id }.distinct().size)
            val positions = round.changes.map { it.position }
            assertEquals("Position doppelt verändert", positions.size, positions.distinct().size)
        }
    }

    @Test fun sameSeedSameRound() {
        for (mode in Mode.entries) {
            val c = config(mode, 4, 424242L)
            val a = gen.generate(c)
            val b2 = gen.generate(c)
            assertEquals(a.original, b2.original)
            assertEquals(a.changed, b2.changed)
            assertEquals(a.changes, b2.changes)
            assertEquals(a.options, b2.options)
            assertNotEquals(a.original, gen.generate(c.copy(seed = 424243L)).original)
        }
    }

    @Test fun similarityShareIsKept() {
        for ((level, sim) in listOf(2 to 0.15, 3 to 0.35, 4 to 0.6, 5 to 0.8, 6 to 1.0)) {
            for (seed in 0L until 20) {
                val round = gen.generate(config(Mode.CHANGED, level, seed))
                val n = round.original.size
                val top = round.original.groupingBy { it.obj.similarityGroup }.eachCount().values.max()
                val cap = catalog.groups.values.minOf { it.size }
                val want = minOf(Math.round(n * sim).toInt(), cap)
                assertTrue("Stufe $level: $top < $want", top >= want)
            }
        }
    }

    @Test fun uniqueBasesInFreeRecallAndSequence() {
        for (mode in listOf(Mode.FREE_RECALL, Mode.SEQUENCE)) for (level in 1..6) {
            val round = gen.generate(config(mode, level, 7L + level))
            val bases = round.original.map { it.obj.baseObject }
            assertEquals(bases.size, bases.distinct().size)
        }
    }

    @Test fun columnsKeepMinimumCellSize() {
        for (width in listOf(320, 360, 390, 411, 600, 840)) for (count in listOf(8, 12, 16, 20, 25, 30, 40)) {
            val cols = diff.columnsFor(count, width)
            val cell = (width - 2 * b.grid.marginDp - (cols - 1) * b.grid.gapDp).toDouble() / cols
            assertTrue("Breite $width, $count Objekte: Feld $cell dp", cell >= b.grid.minCellDp)
        }
        assertEquals(4, diff.columnsFor(20, 390))
        assertEquals(6, diff.columnsFor(40, 390))
    }

    @Test fun missingModeOffersTwiceAsManyOptions() {
        val round = gen.generate(config(Mode.MISSING, 3, 11))
        val missing = round.realChanges.size
        assertEquals(2 * missing, round.options.size)
        assertTrue(round.realChanges.all { it.type == ChangeType.REMOVED })
    }
}
