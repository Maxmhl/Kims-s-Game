package de.max.gedaechtnis.kim.engine

import kotlinx.serialization.Serializable

@Serializable
enum class ObjColor { AMBER, CYAN, WHITE, CORAL }

@Serializable
enum class Mode { MISSING, CHANGED, RECONSTRUCTION, FREE_RECALL, SEQUENCE }

@Serializable
enum class ChangeType { REMOVED, REPLACED, SWAPPED, RECOLORED, ROTATED, FAKE }

@Serializable
enum class DistractionKind { NONE, CALC, MIXED, DOUBLE }

@Serializable
data class GameObject(
    val id: String,
    val category: String,
    val baseObject: String,
    val variant: String,
    val color: ObjColor,
    val iconRes: String,
    val similarityGroup: String,
    val label: String,
    val rotatable: Boolean = true,
)

/** Ein belegtes Rasterfeld: Objekt plus Drehung (0, 45 oder 90 Grad). */
@Serializable
data class Cell(val obj: GameObject, val rotation: Int = 0)

@Serializable
data class Change(
    val type: ChangeType,
    val position: Int,
    val before: GameObject?,
    val after: GameObject?,
    val rotationBefore: Int = 0,
    val rotationAfter: Int = 0,
)

@Serializable
data class Distraction(val kind: DistractionKind, val seconds: Int)

/** Vollständige, deterministisch aus [seed] reproduzierbare Rundenbeschreibung. */
@Serializable
data class RoundConfig(
    val mode: Mode,
    val level: Int?,
    val difficulty: Double?,
    val objectCount: Int,
    val columns: Int,
    val memorizeSec: Int,
    val pauseSec: Int,
    val distraction: Distraction?,
    val changeMin: Int,
    val changeMax: Int,
    val changeTypes: Set<ChangeType>,
    val showChangeCount: Boolean,
    val answerSec: Int?,
    val seed: Long,
    val similarity: Double,
    val falseClickPenalty: Boolean,
    val exactPositions: Boolean,
    val oneErrorLosesRound: Boolean,
    val fakeCount: Int,
    val factor: Double,
    val sequenceItemMs: Int,
    val reverseSequence: Boolean,
    val daily: Boolean = false,
)

/** Eine erzeugte Runde. */
data class Round(
    val config: RoundConfig,
    val original: List<Cell>,
    val changed: List<Cell?>,
    val changes: List<Change>,
    /** Reihenfolge der Objekte im Modus SEQUENCE (so wie gezeigt). */
    val sequence: List<GameObject>,
    /** Auswahlobjekte (Was fehlt?, Sequenz, Rekonstruktion). */
    val options: List<GameObject>,
    /** Getestete Positionen im Modus RECONSTRUCTION. */
    val testedPositions: List<Int>,
) {
    val realChanges: List<Change> get() = changes.filter { it.type != ChangeType.FAKE }
    val fakes: List<Change> get() = changes.filter { it.type == ChangeType.FAKE }
}

/** Antwort des Spielers, je nach Modus. */
sealed interface Answer {
    data class Positions(val marked: Set<Int>) : Answer
    data class Objects(val ids: Set<String>) : Answer
    data class Placement(val placed: Map<Int, String>) : Answer
    data class Names(val bases: Set<String>) : Answer
    data class Order(val ids: List<String>) : Answer
}

enum class Outcome { FOUND, MISSED, FALSE_CLICK }

data class Detail(
    val position: Int,
    val type: ChangeType?,
    val objectBefore: GameObject?,
    val objectAfter: GameObject?,
    val outcome: Outcome,
    val label: String? = null,
)

data class Evaluation(
    val hits: Int,
    val falseClicks: Int,
    val missed: Int,
    val details: List<Detail>,
    val lost: Boolean,
) {
    val accuracy: Double
        get() {
            val total = hits + falseClicks + missed
            return if (total == 0) 0.0 else hits.toDouble() / total
        }
}
