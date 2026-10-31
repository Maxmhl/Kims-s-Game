package de.max.gedaechtnis.kim.engine.distraction

import de.max.gedaechtnis.kim.engine.DistractionKind
import de.max.gedaechtnis.kim.engine.DistractionSpec
import kotlin.random.Random

enum class DistractionType(val prefix: String) { CALC("calc"), STROOP("stroop"), COUNT("count"), REACTION("reaction") }

/** Farben für Farbwörter (Stroop); Reihenfolge = Index in [Task.Stroop]. */
val STROOP_CODES = listOf("RT", "BL", "GR", "GE", "LI", "OR")

sealed interface Task {
    val type: DistractionType
    val key: String

    data class Calc(val expression: String, val answer: Int, override val key: String) : Task {
        override val type get() = DistractionType.CALC
    }

    /** Farbwörter [words] in den Schriftfarben [inks]; gefragt sind die Schriftfarben nacheinander. */
    data class Stroop(val words: List<Int>, val inks: List<Int>, override val key: String) : Task {
        override val type get() = DistractionType.STROOP
    }

    /** Rückwärtszählen: [start], dann jeweils [step] abziehen, [steps]-mal. */
    data class Count(val start: Int, val step: Int, val steps: Int, override val key: String) : Task {
        override val type get() = DistractionType.COUNT
        val expected: List<Int> get() = (1..steps).map { start - step * it }
    }

    /** Kreis wird nach [delayMs] grün. Kein Schlüssel, da nichts zu merken ist. */
    data class Reaction(val delayMs: Long) : Task {
        override val type get() = DistractionType.REACTION
        override val key: String get() = ""
    }
}

/** Speicher bereits gezeigter Aufgaben (in der App durch Room umgesetzt). */
interface DistractionStore {
    fun exists(type: DistractionType, key: String): Boolean
    fun insert(type: DistractionType, key: String, usedAt: Long)
    fun widenLevel(type: DistractionType): Int
    fun setWidenLevel(type: DistractionType, level: Int)
}

class InMemoryDistractionStore : DistractionStore {
    private val keys = HashSet<String>()
    private val levels = HashMap<DistractionType, Int>()
    override fun exists(type: DistractionType, key: String) = "${type.prefix}|$key" in keys
    override fun insert(type: DistractionType, key: String, usedAt: Long) { keys += "${type.prefix}|$key" }
    override fun widenLevel(type: DistractionType) = levels[type] ?: 0
    override fun setWidenLevel(type: DistractionType, level: Int) { levels[type] = level }
    val size: Int get() = keys.size
}

/** Jede Ablenkungsaufgabe wird nur ein einziges Mal gezeigt. */
class DistractionGenerator(
    private val store: DistractionStore,
    private val spec: DistractionSpec,
    private val rng: Random = Random.Default,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun next(type: DistractionType): Task {
        if (type == DistractionType.REACTION) {
            return Task.Reaction(rng.nextLong(spec.reactionMinMs.toLong(), spec.reactionMaxMs.toLong() + 1))
        }
        var rounds = 0
        while (true) {
            repeat(spec.retries) {
                val t = generate(type)
                if (!store.exists(type, t.key)) {
                    store.insert(type, t.key, clock())
                    return t
                }
            }
            widenRange(type)
            if (++rounds > 20) error("Aufgabenvorrat für $type nicht erweiterbar")
        }
    }

    /** Wählt den Aufgabentyp gemäß Ablenkungsart der Stufe; [index] ist die laufende Nummer. */
    fun typeFor(kind: DistractionKind, index: Int): DistractionType? = when (kind) {
        DistractionKind.NONE -> null
        DistractionKind.CALC -> DistractionType.CALC
        DistractionKind.DOUBLE -> if (index % 2 == 0) DistractionType.CALC else DistractionType.STROOP
        DistractionKind.MIXED -> DistractionType.entries[rng.nextInt(DistractionType.entries.size)]
    }

    fun widenRange(type: DistractionType) {
        store.setWidenLevel(type, store.widenLevel(type) + 1)
    }

    private fun generate(type: DistractionType): Task = when (type) {
        DistractionType.CALC -> calc(store.widenLevel(type))
        DistractionType.STROOP -> stroop(store.widenLevel(type))
        DistractionType.COUNT -> count(store.widenLevel(type))
        DistractionType.REACTION -> Task.Reaction(spec.reactionMinMs.toLong())
    }

    // a ∘ b ∘ c, ∘ aus + − ×; × nur mit einem Faktor ≤ 12; Ergebnis 0–200.
    // Erweiterung: Zahlen bis 199 (Stufe 1), danach zusätzliche Operation (Stufe 2) usw.
    private fun calc(level: Int): Task.Calc {
        val operands = 3 + level / 2
        val hi = if (level >= 1) spec.calcMax * 2 + 1 else spec.calcMax
        val maxResult = spec.calcMaxResult * (1 + level)
        while (true) {
            val nums = IntArray(operands) { rng.nextInt(spec.calcMin, hi + 1) }
            val ops = CharArray(operands - 1) { "+-*"[rng.nextInt(3)] }
            // × nur wenn ein Faktor ≤ 12 ist.
            var ok = true
            for (i in ops.indices) {
                if (ops[i] == '*') {
                    if (rng.nextBoolean()) nums[i] = rng.nextInt(spec.calcMin, spec.calcSmallFactor + 1)
                    else nums[i + 1] = rng.nextInt(spec.calcMin, spec.calcSmallFactor + 1)
                    if (minOf(nums[i], nums[i + 1]) > spec.calcSmallFactor) ok = false
                }
            }
            if (!ok) continue
            val result = evaluate(nums, ops)
            if (result !in 0..maxResult) continue
            val key = buildString {
                append(nums[0])
                for (i in ops.indices) { append(if (ops[i] == '*') 'x' else ops[i]); append(nums[i + 1]) }
            }
            val shown = buildString {
                append(nums[0])
                for (i in ops.indices) {
                    append(" ${when (ops[i]) { '*' -> '×'; '-' -> '−'; else -> '+' }} ")
                    append(nums[i + 1])
                }
            }
            return Task.Calc(shown, result, "calc:$key")
        }
    }

    private fun evaluate(nums: IntArray, ops: CharArray): Int {
        // Punkt-vor-Strich.
        val terms = mutableListOf<Int>()
        var current = nums[0]
        var sign = 1
        for (i in ops.indices) {
            when (ops[i]) {
                '*' -> current *= nums[i + 1]
                else -> {
                    terms += sign * current
                    sign = if (ops[i] == '+') 1 else -1
                    current = nums[i + 1]
                }
            }
        }
        terms += sign * current
        return terms.sum()
    }

    // Folge von 4 Farbwörtern, Schriftfarbe jeweils ≠ Wort; 6 Farben. Erweiterung: 5, 6, ... Wörter.
    private fun stroop(level: Int): Task.Stroop {
        val n = spec.stroopWords + level
        val words = List(n) { rng.nextInt(STROOP_CODES.size) }
        val inks = words.map { w ->
            var c: Int
            do { c = rng.nextInt(STROOP_CODES.size) } while (c == w)
            c
        }
        val key = "stroop:" + words.joinToString("-") { STROOP_CODES[it] } + ">" + inks.joinToString("-") { STROOP_CODES[it] }
        return Task.Stroop(words, inks, key)
    }

    // Start 100–999, Schrittweite 6–9. Erweiterung: Startwerte bis 9 999, dann bis 99 999.
    private fun count(level: Int): Task.Count {
        val hi = when (level) { 0 -> 999; 1 -> 9_999; else -> 99_999 }
        val start = rng.nextInt(100, hi + 1)
        val step = rng.nextInt(6, 10)
        return Task.Count(start, step, spec.countSteps, "count:$start-$step")
    }
}
