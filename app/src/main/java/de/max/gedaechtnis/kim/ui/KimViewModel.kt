package de.max.gedaechtnis.kim.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.max.gedaechtnis.core.Sounds
import de.max.gedaechtnis.data.Clock
import de.max.gedaechtnis.data.KimRepository
import de.max.gedaechtnis.data.RoundResult
import de.max.gedaechtnis.data.Settings
import de.max.gedaechtnis.data.SettingsRepository
import de.max.gedaechtnis.data.TrainingRepository
import de.max.gedaechtnis.kim.engine.*
import de.max.gedaechtnis.kim.engine.distraction.DistractionGenerator
import de.max.gedaechtnis.kim.engine.distraction.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.random.Random

enum class Phase { IDLE, MEMORIZE, DISTRACT, RECALL, RESULT }

data class TimerState(val remainingMs: Long = 0, val totalMs: Long = 0, val elapsedMs: Long = 0) {
    val fraction: Float get() = if (totalMs <= 0) 0f else remainingMs.toFloat() / totalMs
}

data class TaskFeedback(val correct: Boolean, val solution: String)

data class DistractState(
    val task: Task? = null,
    val index: Int = 0,
    val planned: Int = 0,
    val feedback: TaskFeedback? = null,
)

/** Eingaben des Spielers in der Abfrage (bleibt bei Drehung erhalten). */
data class RecallInput(
    val marked: Set<Int> = emptySet(),
    val selectedObjects: Set<String> = emptySet(),
    val placed: Map<Int, String> = emptyMap(),
    val names: List<String> = emptyList(),
    val order: List<String> = emptyList(),
)

sealed interface ProgressNote {
    data object None : ProgressNote
    data class Streak(val level: Int, val streak: Int, val required: Int, val counted: Boolean, val threshold: Int) : ProgressNote
    data class LevelUp(val level: Int, val next: Int?) : ProgressNote
    data class Adaptive(val d: Double) : ProgressNote
}

data class RoundOutcome(
    val round: Round,
    val answer: Answer,
    val evaluation: Evaluation,
    val points: Int,
    val note: ProgressNote,
    val suggestDemotion: Boolean,
    val completedDaily: Boolean,
)

data class KimState(
    val phase: Phase = Phase.IDLE,
    val round: Round? = null,
    val outcome: RoundOutcome? = null,
    val voided: Boolean = false,
    val input: RecallInput = RecallInput(),
)

@Serializable
data class DetailRecord(val row: Int, val col: Int, val type: String?, val outcome: String)

@HiltViewModel
class KimViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val kimRepo: KimRepository,
    private val training: TrainingRepository,
    private val balancing: Balancing,
    private val catalog: Catalog,
    private val difficulty: Difficulty,
    private val generator: RoundGenerator,
    private val evaluator: Evaluator,
    private val scorer: Scorer,
    private val progression: Progression,
    private val adaptive: AdaptiveRule,
    private val clock: Clock,
    private val sounds: Sounds,
) : ViewModel() {

    val settings: StateFlow<Settings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(KimState())
    val state: StateFlow<KimState> = _state.asStateFlow()

    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    private val _distract = MutableStateFlow(DistractState())
    val distract: StateFlow<DistractState> = _distract.asStateFlow()

    private var countdown: PausableCountdown? = null
    private var tickJob: Job? = null
    private var taskJob: Job? = null
    private var backgroundedAt: Long? = null
    private var distractionCorrect = 0
    private var distractionTotal = 0
    private var distractionGen: DistractionGenerator? = null

    private fun nowMs() = System.nanoTime() / 1_000_000

    // ---------------------------------------------------------------- Auswahl (Stufenbildschirm)

    fun selectMode(mode: Mode) = viewModelScope.launch { settingsRepo.setMode(mode) }
    fun selectLevel(level: Int) = viewModelScope.launch { settingsRepo.setLevel(level) }
    fun setAdaptive(on: Boolean) = viewModelScope.launch { settingsRepo.setAdaptive(on) }
    fun clearVoided() = _state.update { it.copy(voided = false) }

    fun acceptDemotion() = viewModelScope.launch {
        val s = settings.value ?: settingsRepo.settings.first()
        val mode = state.value.round?.config?.mode ?: s.mode
        val p = progression.demote(s.progressOf(mode))
        settingsRepo.setProgress(mode, p)
        settingsRepo.setLevel(minOf(s.level, p.unlocked))
        _state.update { it.copy(outcome = it.outcome?.copy(suggestDemotion = false)) }
    }

    fun dismissDemotion() = _state.update { it.copy(outcome = it.outcome?.copy(suggestDemotion = false)) }

    // ---------------------------------------------------------------- Rundenstart

    /** Startet eine Runde. [screenWidthDp] bestimmt die Spaltenzahl (jedes Feld mindestens 48 dp). */
    fun startRound(screenWidthDp: Int, daily: Boolean = false) {
        if (state.value.phase != Phase.IDLE && state.value.phase != Phase.RESULT) return
        viewModelScope.launch {
            val s = settings.value ?: settingsRepo.settings.first()
            val mode = if (daily) dailyMode(clock.dateKey()) else s.mode
            val unlocked = s.progressOf(mode).unlocked.coerceAtMost(balancing.maxLevel)
            val params = when {
                daily || s.adaptive -> difficulty.forAdaptive(s.adaptiveState.d)
                else -> difficulty.forLevel(s.level.coerceIn(1, unlocked))
            }
            val seed = if (daily) clock.dateKey() else Random.nextLong()
            val config = difficulty.config(mode, params, seed, screenWidthDp, daily)
            val round = withContext(Dispatchers.Default) { generator.generate(config) }
            begin(round)
        }
    }

    fun dailyMode(dateKey: Long): Mode = Mode.entries[(dateKey % Mode.entries.size).toInt()]

    private fun begin(round: Round) {
        distractionCorrect = 0
        distractionTotal = 0
        backgroundedAt = null
        _distract.value = DistractState()
        _state.value = KimState(phase = Phase.MEMORIZE, round = round)
        val cfg = round.config
        val total = if (cfg.mode == Mode.SEQUENCE) cfg.objectCount.toLong() * cfg.sequenceItemMs else cfg.memorizeSec * 1000L
        startCountdown(total) { endMemorize() }
    }

    // ---------------------------------------------------------------- Timer

    private fun startCountdown(totalMs: Long, onFinish: () -> Unit) {
        tickJob?.cancel()
        val c = PausableCountdown(totalMs, ::nowMs).also { it.start() }
        countdown = c
        tickJob = viewModelScope.launch {
            while (isActive) {
                _timer.value = TimerState(c.remainingMs, totalMs, c.elapsedMs)
                if (c.isFinished) { onFinish(); break }
                delay(50)
            }
        }
    }

    /** App geht in den Hintergrund: alle Timer pausieren. */
    fun onAppBackground() {
        if (state.value.phase == Phase.IDLE || state.value.phase == Phase.RESULT) return
        countdown?.pause()
        if (backgroundedAt == null) backgroundedAt = nowMs()
    }

    fun onAppForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val away = nowMs() - since
        if (state.value.phase == Phase.MEMORIZE && away > balancing.session.backgroundGraceMs) {
            voidRound()
        } else {
            countdown?.start()
        }
    }

    private fun voidRound() {
        stopAll()
        _state.value = KimState(phase = Phase.IDLE, voided = true)
    }

    fun abort() {
        stopAll()
        _state.value = KimState(phase = Phase.IDLE)
    }

    private fun stopAll() {
        tickJob?.cancel(); taskJob?.cancel(); countdown = null
    }

    // ---------------------------------------------------------------- Phasen

    fun finishMemorizeEarly() {
        if (state.value.phase == Phase.MEMORIZE) endMemorize()
    }

    private fun endMemorize() {
        val round = state.value.round ?: return
        tickJob?.cancel()
        val cfg = round.config
        val kind = cfg.distraction?.kind ?: DistractionKind.NONE
        if (cfg.pauseSec <= 0) { beginRecall(); return }
        val planned = if (kind == DistractionKind.NONE) 0 else maxOf(1, cfg.pauseSec / balancing.distraction.taskSeconds)
        _distract.value = DistractState(planned = planned)
        _state.update { it.copy(phase = Phase.DISTRACT) }
        startCountdown(cfg.pauseSec * 1000L) { beginRecall() }
        if (kind != DistractionKind.NONE) nextTask(0)
    }

    private fun generatorForDistraction(): DistractionGenerator =
        distractionGen ?: DistractionGenerator(kimRepo.distractionStore, balancing.distraction, Random.Default, clock::nowMillis)
            .also { distractionGen = it }

    private fun nextTask(index: Int) {
        val kind = state.value.round?.config?.distraction?.kind ?: return
        taskJob?.cancel()
        taskJob = viewModelScope.launch {
            val gen = generatorForDistraction()
            val task = withContext(Dispatchers.IO) {
                val type = gen.typeFor(kind, index) ?: return@withContext null
                gen.next(type)
            } ?: return@launch
            if (state.value.phase == Phase.DISTRACT) {
                _distract.update { it.copy(task = task, index = index, feedback = null, planned = maxOf(it.planned, index + 1)) }
            }
        }
    }

    /** Ergebnis einer Ablenkungsaufgabe; zeigt 0,9 s Rückmeldung und lädt dann die nächste Aufgabe. */
    fun onTaskResult(correct: Boolean, solution: String) {
        val d = _distract.value
        if (d.feedback != null || d.task == null || state.value.phase != Phase.DISTRACT) return
        distractionTotal++
        if (correct) distractionCorrect++
        if (settings.value?.sound != false) { if (correct) sounds.correct() else sounds.wrong() }
        _distract.update { it.copy(feedback = TaskFeedback(correct, solution)) }
        taskJob?.cancel()
        taskJob = viewModelScope.launch {
            delay(balancing.distraction.feedbackMs.toLong())
            if (state.value.phase == Phase.DISTRACT) nextTask(d.index + 1)
        }
    }

    private fun beginRecall() {
        val round = state.value.round ?: return
        taskJob?.cancel(); tickJob?.cancel()
        _state.update { it.copy(phase = Phase.RECALL, input = RecallInput()) }
        val answerMs = round.config.answerSec?.let { it * 1000L }
        startCountdown(answerMs ?: ELAPSED_ONLY) { submit() }
    }

    // ---------------------------------------------------------------- Eingaben

    fun toggleMarked(position: Int) = updateInput { it.copy(marked = it.marked.toggle(position)) }
    fun toggleObject(id: String) = updateInput { it.copy(selectedObjects = it.selectedObjects.toggle(id)) }
    fun place(position: Int, objectId: String) = updateInput {
        val cleared = it.placed.filterValues { id -> id != objectId }.toMutableMap()
        cleared[position] = objectId
        it.copy(placed = cleared)
    }
    fun unplace(position: Int) = updateInput { it.copy(placed = it.placed - position) }
    fun addName(base: String) = updateInput { if (base in it.names) it else it.copy(names = it.names + base) }
    fun removeName(base: String) = updateInput { it.copy(names = it.names - base) }
    fun pushOrder(id: String) = updateInput { if (id in it.order) it else it.copy(order = it.order + id) }
    fun popOrder() = updateInput { it.copy(order = it.order.dropLast(1)) }

    private fun updateInput(f: (RecallInput) -> RecallInput) {
        if (state.value.phase == Phase.RECALL) _state.update { it.copy(input = f(it.input)) }
    }

    private fun <T> Set<T>.toggle(x: T): Set<T> = if (x in this) this - x else this + x

    // ---------------------------------------------------------------- Auswertung

    /** Wertet die Runde aus. [noChange] markiert die Antwort „Keine Änderung“. */
    fun submit(noChange: Boolean = false) {
        val st = state.value
        val round = st.round ?: return
        if (st.phase != Phase.RECALL) return
        val c = countdown
        c?.pause()
        tickJob?.cancel()
        val elapsed = c?.elapsedMs ?: 0L
        val cfg = round.config
        val input = if (noChange) RecallInput() else st.input
        val answer: Answer = when (cfg.mode) {
            Mode.CHANGED -> Answer.Positions(input.marked)
            Mode.MISSING -> Answer.Objects(input.selectedObjects)
            Mode.RECONSTRUCTION -> Answer.Placement(input.placed)
            Mode.FREE_RECALL -> Answer.Names(input.names.toSet())
            Mode.SEQUENCE -> Answer.Order(input.order)
        }
        val eval = evaluator.evaluate(round, answer)
        val tMax = cfg.answerSec?.let { it * 1000L }
        val points = scorer.points(eval, cfg.factor, cfg.falseClickPenalty, tMax?.minus(elapsed), tMax)
        if (settings.value?.sound != false) sounds.finished()

        viewModelScope.launch {
            val s = settings.value ?: settingsRepo.settings.first()
            var note: ProgressNote = ProgressNote.None
            var suggest = false
            val level = cfg.level
            if (cfg.daily || cfg.difficulty != null) {
                val next = adaptive.afterRound(s.adaptiveState, eval.accuracy)
                settingsRepo.setAdaptiveState(next)
                note = ProgressNote.Adaptive(next.d)
            } else if (level != null) {
                val before = s.progressOf(cfg.mode)
                val upd = progression.afterRound(before, level, eval.accuracy, eval.lost)
                settingsRepo.setProgress(cfg.mode, upd.progress)
                suggest = upd.suggestDemotion
                val counted = level == before.unlocked && !eval.lost && eval.accuracy >= balancing.promotion.minAccuracy
                note = when {
                    upd.levelCompleted != null ->
                        ProgressNote.LevelUp(level, upd.progress.unlocked.takeIf { it <= balancing.maxLevel })
                    else -> ProgressNote.Streak(
                        level, if (level == before.unlocked) upd.progress.successStreak else before.successStreak,
                        balancing.promotion.rounds, counted, (balancing.promotion.minAccuracy * 100).toInt(),
                    )
                }
                if (upd.levelCompleted != null) settingsRepo.setLevel(upd.progress.unlocked.coerceAtMost(balancing.maxLevel))
            }
            val outcome = RoundOutcome(round, answer, eval, points, note, suggest, cfg.daily)
            kimRepo.save(toEntity(round, eval, points, elapsed))
            training.onKimRound(cfg.daily)
            _state.update { it.copy(phase = Phase.RESULT, outcome = outcome) }
        }
    }

    private fun toEntity(round: Round, eval: Evaluation, points: Int, reactionMs: Long): RoundResult {
        val cfg = round.config
        val cols = cfg.columns
        val records = eval.details.filter { it.position >= 0 }.map {
            DetailRecord(it.position / cols, it.position % cols, it.type?.name, it.outcome.name)
        }
        val json = Json { encodeDefaults = true }
        return RoundResult(
            date = clock.nowMillis(),
            epochDay = clock.today(),
            configJson = json.encodeToString(cfg),
            hits = eval.hits, falseClicks = eval.falseClicks, missed = eval.missed,
            reactionMs = reactionMs, points = points,
            changesJson = json.encodeToString(records),
            mode = cfg.mode.name, level = cfg.level, difficulty = cfg.difficulty,
            accuracy = eval.accuracy, lost = eval.lost, daily = cfg.daily,
            columns = cols, objectCount = cfg.objectCount,
            distractionCorrect = distractionCorrect, distractionTotal = distractionTotal,
        )
    }

    /** Zurück zum Stufenbildschirm nach dem Ergebnis. */
    fun toStart() { stopAll(); _state.value = KimState(phase = Phase.IDLE) }

    fun catalogLabel(base: String) = catalog.label(base)
    val catalogBases: List<Pair<String, String>> by lazy { catalog.bases.map { it to catalog.label(it) }.sortedBy { it.second } }

    companion object {
        /** Zeitquelle ohne Limit: nur Messung der Antwortdauer. */
        const val ELAPSED_ONLY = Long.MAX_VALUE / 4
    }
}
