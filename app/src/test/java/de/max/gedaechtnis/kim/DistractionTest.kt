package de.max.gedaechtnis.kim

import de.max.gedaechtnis.TestData
import de.max.gedaechtnis.kim.engine.distraction.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DistractionTest {
    private val spec = TestData.balancing.distraction

    @Test fun tenThousandTasksPerTypeAreUnique() {
        for (type in listOf(DistractionType.CALC, DistractionType.STROOP, DistractionType.COUNT)) {
            val gen = DistractionGenerator(InMemoryDistractionStore(), spec, Random(1))
            val keys = HashSet<String>()
            repeat(10_000) { assertTrue("doppelt: $type", keys.add(gen.next(type).key)) }
        }
    }

    @Test fun noTaskRepeatsAfterRestart() {
        val store = InMemoryDistractionStore() // bleibt wie die Room-Datenbank erhalten
        val shown = HashSet<String>()
        repeat(2) { session ->
            val gen = DistractionGenerator(store, spec, Random(7)) // gleicher Seed: würde ohne Speicher wiederholen
            repeat(2_000) { assertTrue("Sitzung $session wiederholt Aufgabe", shown.add(gen.next(DistractionType.CALC).key)) }
        }
    }

    @Test fun calcRulesHold() {
        val gen = DistractionGenerator(InMemoryDistractionStore(), spec, Random(3))
        repeat(2_000) {
            val t = gen.next(DistractionType.CALC) as Task.Calc
            assertTrue(t.answer in 0..spec.calcMaxResult)
            val body = t.key.removePrefix("calc:")
            val nums = Regex("\\d+").findAll(body).map { it.value.toInt() }.toList()
            val ops = Regex("[+\\-x]").findAll(body).map { it.value }.toList()
            assertEquals(3, nums.size)
            assertTrue(nums.all { it in spec.calcMin..spec.calcMax })
            for ((i, op) in ops.withIndex()) if (op == "x") assertTrue(minOf(nums[i], nums[i + 1]) <= spec.calcSmallFactor)
            // Ergebnis nachrechnen (Punkt vor Strich)
            val expr = body.replace("x", "*")
            assertEquals(t.answer, evalSimple(expr))
        }
    }

    private fun evalSimple(e: String): Int {
        val tokens = Regex("\\d+|[+\\-*]").findAll(e).map { it.value }.toList()
        val stack = mutableListOf(tokens[0].toInt())
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]; val n = tokens[i + 1].toInt()
            when (op) { "*" -> stack[stack.lastIndex] = stack.last() * n; "+" -> stack += n; else -> stack += -n }
            i += 2
        }
        return stack.sum()
    }

    @Test fun stroopInkNeverMatchesWord() {
        val gen = DistractionGenerator(InMemoryDistractionStore(), spec, Random(4))
        repeat(500) {
            val t = gen.next(DistractionType.STROOP) as Task.Stroop
            assertEquals(spec.stroopWords, t.words.size)
            assertTrue(t.words.indices.all { t.words[it] != t.inks[it] })
            assertTrue((t.words + t.inks).all { it in 0 until STROOP_CODES.size })
        }
    }

    @Test fun countBackwardsRange() {
        val gen = DistractionGenerator(InMemoryDistractionStore(), spec, Random(5))
        repeat(500) {
            val t = gen.next(DistractionType.COUNT) as Task.Count
            assertTrue(t.start in 100..999); assertTrue(t.step in 6..9); assertEquals(5, t.expected.size)
            assertEquals(t.start - t.step, t.expected.first())
        }
    }

    @Test fun rangeWidensWhenSupplyIsExhausted() {
        val store = InMemoryDistractionStore()
        for (start in 100..999) for (step in 6..9) store.insert(DistractionType.COUNT, "count:$start-$step", 0)
        assertEquals(3600, store.size)
        val gen = DistractionGenerator(store, spec, Random(9))
        val t = gen.next(DistractionType.COUNT) as Task.Count
        assertEquals(1, store.widenLevel(DistractionType.COUNT))
        assertTrue("Startwert außerhalb des alten Bereichs", t.start in 1000..9999)
        assertFalse(store.exists(DistractionType.COUNT, "count:100-6").not())
    }

    @Test fun reactionNeedsNoKey() {
        val gen = DistractionGenerator(InMemoryDistractionStore(), spec, Random(2))
        val r = gen.next(DistractionType.REACTION) as Task.Reaction
        assertTrue(r.delayMs in spec.reactionMinMs..spec.reactionMaxMs)
    }
}
