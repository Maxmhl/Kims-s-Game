package de.max.gedaechtnis.kim.engine

import de.max.gedaechtnis.leitner.engine.LeitnerMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LevelSpec(
    val level: Int,
    val objectsMin: Int,
    val objectsMax: Int,
    val memorizeSec: Int,
    val changesMin: Int,
    val changesMax: Int,
    val changeTypes: Set<ChangeType>,
    val similarity: Double,
    val pauseSec: Int,
    val distraction: DistractionKind,
    val answerSec: Int? = null,
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

@Serializable data class PromotionSpec(val rounds: Int, val minAccuracy: Double)
@Serializable data class DemotionSpec(val rounds: Int, val maxAccuracy: Double)

@Serializable
data class AdaptiveSpec(
    val min: Double,
    val max: Double,
    val start: Double,
    val upAccuracy: Double,
    val upRounds: Int,
    val upStep: Double,
    val downAccuracy: Double,
    val downStep: Double,
)

@Serializable
data class ScoringSpec(val hit: Int, val falseClick: Int, val missed: Int, val timeBonus: Double)

@Serializable
data class GridSpec(
    val columnsSmall: Int,
    val columnsMedium: Int,
    val columnsLarge: Int,
    val mediumFrom: Int,
    val largeFrom: Int,
    val minCellDp: Int,
    val gapDp: Int,
    val marginDp: Int,
)

@Serializable
data class ReconstructionSpec(val testFraction: Double, val minTested: Int, val maxTested: Int)

@Serializable
data class DistractionSpec(
    val taskSeconds: Int,
    val feedbackMs: Int,
    val reactionMinMs: Int,
    val reactionMaxMs: Int,
    val countSteps: Int,
    val retries: Int,
    val calcMin: Int,
    val calcMax: Int,
    val calcMaxResult: Int,
    val calcSmallFactor: Int,
    val stroopWords: Int,
)

@Serializable data class SessionSpec(val backgroundGraceMs: Long, val dailyLevelFromAdaptive: Boolean = true)

@Serializable
enum class ErrorRule { BACK_ONE, TO_FIRST }

@Serializable
data class LeitnerSpec(
    val boxes: Int,
    val intervals: List<Int>,
    val onError: ErrorRule,
    val typed: Boolean,
    val bothDirections: Boolean,
    val timeLimitSec: Int? = null,
)

/** Alle Zahlen aus Kapitel 2 und 3 der Spezifikation; geladen aus assets/balancing.json. */
@Serializable
data class Balancing(
    val levels: List<LevelSpec>,
    val promotion: PromotionSpec,
    val demotion: DemotionSpec,
    val adaptive: AdaptiveSpec,
    val scoring: ScoringSpec,
    val grid: GridSpec,
    val reconstruction: ReconstructionSpec,
    val distraction: DistractionSpec,
    val session: SessionSpec,
    val leitner: Map<LeitnerMode, LeitnerSpec>,
    val leitnerDefaultMode: LeitnerMode,
) {
    fun level(n: Int): LevelSpec = levels.first { it.level == n }
    val maxLevel: Int get() = levels.maxOf { it.level }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): Balancing = json.decodeFromString(serializer(), text)
    }
}
