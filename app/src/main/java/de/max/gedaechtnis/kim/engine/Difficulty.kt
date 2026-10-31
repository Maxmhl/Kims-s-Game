package de.max.gedaechtnis.kim.engine

import kotlin.math.floor
import kotlin.math.roundToInt

/** Aufgelöste Parameter einer Runde (aus Stufe oder adaptivem Wert d). */
data class Params(
    val level: Int?,
    val difficulty: Double?,
    val objectsMin: Int,
    val objectsMax: Int,
    val memorizeSec: Int,
    val changesMin: Int,
    val changesMax: Int,
    val changeTypes: Set<ChangeType>,
    val similarity: Double,
    val pauseSec: Int,
    val distraction: DistractionKind,
    val answerSec: Int?,
    val falseClickPenalty: Boolean,
    val unknownCount: Boolean,
    val exactPositions: Boolean,
    val oneErrorLosesRound: Boolean,
    val fakeCount: Int,
    val factor: Double,
    val sequenceLength: Int,
    val sequenceItemMs: Int,
    val reverseSequence: Boolean,
)

class Difficulty(private val b: Balancing) {

    /** Position in der Stufentabelle: p = 1 + (d − 1) · 5 / 9. */
    fun position(d: Double): Double {
        val clamped = d.coerceIn(b.adaptive.min, b.adaptive.max)
        return 1.0 + (clamped - 1.0) * (b.maxLevel - 1) / 9.0
    }

    fun forLevel(level: Int): Params {
        val s = b.level(level)
        return Params(
            level, null, s.objectsMin, s.objectsMax, s.memorizeSec, s.changesMin, s.changesMax, s.changeTypes,
            s.similarity, s.pauseSec, s.distraction, s.answerSec, s.falseClickPenalty, s.unknownCount,
            s.exactPositions, s.oneErrorLosesRound, s.fakeCount, s.factor, s.sequenceLength, s.sequenceItemMs,
            s.reverseSequence,
        )
    }

    /** Alle numerischen Parameter werden linear zwischen den Stufen interpoliert. */
    fun forAdaptive(d: Double): Params {
        val p = position(d)
        val lo = floor(p).toInt().coerceIn(1, b.maxLevel)
        val hi = (lo + 1).coerceAtMost(b.maxLevel)
        val t = if (hi == lo) 0.0 else p - lo
        val a = b.level(lo)
        val c = b.level(hi)
        val near = if (t < 0.5) a else c

        fun lerp(x: Double, y: Double) = x + (y - x) * t
        fun lerpI(x: Int, y: Int) = lerp(x.toDouble(), y.toDouble()).roundToInt()

        val answer = when {
            a.answerSec == null && c.answerSec == null -> null
            a.answerSec == null -> if (t < 0.5) null else c.answerSec
            c.answerSec == null -> a.answerSec
            else -> lerpI(a.answerSec, c.answerSec)
        }
        return Params(
            level = null,
            difficulty = d.coerceIn(b.adaptive.min, b.adaptive.max),
            objectsMin = lerpI(a.objectsMin, c.objectsMin),
            objectsMax = lerpI(a.objectsMax, c.objectsMax),
            memorizeSec = lerpI(a.memorizeSec, c.memorizeSec),
            changesMin = lerpI(a.changesMin, c.changesMin),
            changesMax = lerpI(a.changesMax, c.changesMax),
            changeTypes = near.changeTypes,
            similarity = lerp(a.similarity, c.similarity),
            pauseSec = lerpI(a.pauseSec, c.pauseSec),
            distraction = near.distraction,
            answerSec = answer,
            falseClickPenalty = near.falseClickPenalty,
            unknownCount = near.unknownCount,
            exactPositions = near.exactPositions,
            oneErrorLosesRound = near.oneErrorLosesRound,
            fakeCount = near.fakeCount,
            factor = lerp(a.factor, c.factor),
            sequenceLength = lerpI(a.sequenceLength, c.sequenceLength),
            sequenceItemMs = lerpI(a.sequenceItemMs, c.sequenceItemMs),
            reverseSequence = near.reverseSequence,
        )
    }

    /** Spaltenzahl so wählen, dass jedes Feld mindestens [GridSpec.minCellDp] breit ist. */
    fun columnsFor(objectCount: Int, screenWidthDp: Int): Int {
        val g = b.grid
        var cols = when {
            objectCount >= g.largeFrom -> g.columnsLarge
            objectCount >= g.mediumFrom -> g.columnsMedium
            else -> g.columnsSmall
        }
        val usable = screenWidthDp - 2 * g.marginDp
        while (cols > 1 && (usable - (cols - 1) * g.gapDp).toDouble() / cols < g.minCellDp) cols--
        return cols
    }

    fun config(
        mode: Mode,
        params: Params,
        seed: Long,
        screenWidthDp: Int,
        daily: Boolean = false,
    ): RoundConfig {
        val rng = kotlin.random.Random(seed xor 0x5DEECE66DL)
        val isSequence = mode == Mode.SEQUENCE
        val count = if (isSequence) params.sequenceLength
        else if (params.objectsMax > params.objectsMin) rng.nextInt(params.objectsMin, params.objectsMax + 1)
        else params.objectsMin
        val minChanges = if (mode == Mode.MISSING) maxOf(1, params.changesMin) else params.changesMin
        val maxChanges = maxOf(minChanges, params.changesMax)
        return RoundConfig(
            mode = mode,
            level = params.level,
            difficulty = params.difficulty,
            objectCount = count,
            columns = columnsFor(count, screenWidthDp),
            memorizeSec = params.memorizeSec,
            pauseSec = params.pauseSec,
            distraction = Distraction(params.distraction, params.pauseSec),
            changeMin = minChanges,
            changeMax = maxChanges,
            changeTypes = if (mode == Mode.MISSING) setOf(ChangeType.REMOVED) else params.changeTypes,
            showChangeCount = !params.unknownCount,
            answerSec = params.answerSec,
            seed = seed,
            similarity = params.similarity,
            falseClickPenalty = params.falseClickPenalty,
            exactPositions = params.exactPositions,
            oneErrorLosesRound = params.oneErrorLosesRound,
            fakeCount = if (mode == Mode.CHANGED) params.fakeCount else 0,
            factor = params.factor,
            sequenceItemMs = params.sequenceItemMs,
            reverseSequence = params.reverseSequence,
            daily = daily,
        )
    }
}
