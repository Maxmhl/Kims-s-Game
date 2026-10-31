package de.max.gedaechtnis.kim.engine

import kotlin.math.roundToInt

/**
 * Punkte = round( (10·T − 15·F − 5·Ü) · S · (1 + 0,2 · tRest / tMax) ).
 * Negative Werte werden als 0 gespeichert. Gewichte stammen aus balancing.json.
 */
class Scorer(private val scoring: ScoringSpec) {

    fun points(
        eval: Evaluation,
        factor: Double,
        falseClickPenalty: Boolean,
        tRestMs: Long?,
        tMaxMs: Long?,
    ): Int {
        if (eval.lost) return 0
        val penalty = if (falseClickPenalty) scoring.falseClick * eval.falseClicks else 0
        val raw = scoring.hit * eval.hits - penalty - scoring.missed * eval.missed
        val timeShare = if (tMaxMs != null && tMaxMs > 0 && tRestMs != null) {
            tRestMs.coerceIn(0, tMaxMs).toDouble() / tMaxMs
        } else 0.0
        val value = raw * factor * (1.0 + scoring.timeBonus * timeShare)
        return value.roundToInt().coerceAtLeast(0)
    }
}
