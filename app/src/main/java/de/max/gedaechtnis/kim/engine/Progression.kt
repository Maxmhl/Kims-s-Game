package de.max.gedaechtnis.kim.engine

/** Fortschritt in einem Modus. [unlocked] 1..maxLevel+1 (maxLevel+1 = Meister geschafft). */
data class LevelProgress(val unlocked: Int = 1, val successStreak: Int = 0, val lowStreak: Int = 0)

data class ProgressUpdate(
    val progress: LevelProgress,
    val levelCompleted: Int? = null,
    val suggestDemotion: Boolean = false,
)

/** Auf- und Abstieg: 5 Runden in Folge ≥ 85 % schalten frei, 3 Runden in Folge < 50 % lösen einen Vorschlag aus. */
class Progression(private val b: Balancing) {

    fun afterRound(p: LevelProgress, playedLevel: Int, accuracy: Double, lost: Boolean = false): ProgressUpdate {
        if (playedLevel != p.unlocked) return ProgressUpdate(p) // nur die höchste freigeschaltete Stufe zählt
        val success = !lost && accuracy >= b.promotion.minAccuracy
        if (success) {
            val streak = p.successStreak + 1
            if (streak >= b.promotion.rounds) {
                val next = (p.unlocked + 1).coerceAtMost(b.maxLevel + 1)
                return ProgressUpdate(LevelProgress(next, 0, 0), levelCompleted = playedLevel)
            }
            return ProgressUpdate(LevelProgress(p.unlocked, streak, 0))
        }
        val low = if (accuracy < b.demotion.maxAccuracy) p.lowStreak + 1 else 0
        if (low >= b.demotion.rounds) {
            return ProgressUpdate(LevelProgress(p.unlocked, 0, 0), suggestDemotion = true)
        }
        return ProgressUpdate(LevelProgress(p.unlocked, 0, low))
    }

    /** Abstieg ist ein Dialogvorschlag und wird nur auf Wunsch ausgeführt. */
    fun demote(p: LevelProgress): LevelProgress =
        LevelProgress((p.unlocked - 1).coerceAtLeast(1), 0, 0)

    fun isPlayable(p: LevelProgress, level: Int) = level in 1..minOf(p.unlocked, b.maxLevel)
}

data class AdaptiveState(val d: Double, val highStreak: Int = 0)

/** Treppenregel: zwei Runden in Folge ≥ 80 % → d + 0,3; eine Runde < 60 % → d − 0,5; Grenzen 1,0 und 10,0. */
class AdaptiveRule(private val spec: AdaptiveSpec) {
    fun start() = AdaptiveState(spec.start)

    fun afterRound(s: AdaptiveState, accuracy: Double): AdaptiveState {
        fun clamp(v: Double) = Math.round(v.coerceIn(spec.min, spec.max) * 10.0) / 10.0
        return when {
            accuracy < spec.downAccuracy -> AdaptiveState(clamp(s.d - spec.downStep), 0)
            accuracy >= spec.upAccuracy -> {
                val streak = s.highStreak + 1
                if (streak >= spec.upRounds) AdaptiveState(clamp(s.d + spec.upStep), 0)
                else AdaptiveState(clamp(s.d), streak)
            }
            else -> AdaptiveState(clamp(s.d), 0)
        }
    }
}
