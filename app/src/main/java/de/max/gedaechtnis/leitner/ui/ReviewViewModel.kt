package de.max.gedaechtnis.leitner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.max.gedaechtnis.core.ForegroundState
import de.max.gedaechtnis.core.Sounds
import de.max.gedaechtnis.data.Card
import de.max.gedaechtnis.data.Deck
import de.max.gedaechtnis.data.LeitnerRepository
import de.max.gedaechtnis.data.SettingsRepository
import de.max.gedaechtnis.kim.engine.PausableCountdown
import de.max.gedaechtnis.leitner.engine.AnswerCheck
import de.max.gedaechtnis.leitner.engine.Direction
import de.max.gedaechtnis.leitner.engine.LeitnerMode
import de.max.gedaechtnis.leitner.engine.Scheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReviewVerdict { CORRECT, WRONG, TIMEOUT }

data class ReviewCard(val card: Card, val deck: Deck, val question: String, val answer: String, val daysSince: Long)

data class ReviewResult(val verdict: ReviewVerdict, val newBox: Int, val answer: String)

data class ReviewState(
    val loading: Boolean = true,
    val queue: List<ReviewCard> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val result: ReviewResult? = null,
    val correctCount: Int = 0,
    val finished: Boolean = false,
) {
    val current: ReviewCard? get() = queue.getOrNull(index)
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repo: LeitnerRepository,
    private val scheduler: Scheduler,
    private val settings: SettingsRepository,
    private val sounds: Sounds,
    private val foreground: ForegroundState,
    savedState: SavedStateHandle,
) : ViewModel() {

    /** Stapel-ID; 0 = alle fälligen Karten (Tagestraining). */
    private val deckId: Long = savedState.get<Long>("deckId") ?: 0L

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    private val _timeLeft = MutableStateFlow(0L)
    val timeLeft: StateFlow<Long> = _timeLeft.asStateFlow()
    private var countdown: PausableCountdown? = null
    private var timerJob: Job? = null
    private var startedAt = 0L

    init {
        viewModelScope.launch {
            val cards = if (deckId > 0) repo.dueCards(deckId) else repo.dueCardsAll()
            val decks = repo.deckList.first().associateBy { it.id }
            val queue = cards.shuffled().mapNotNull { c ->
                val deck = decks[c.deckId] ?: return@mapNotNull null
                val backward = c.direction == Direction.BACKWARD
                ReviewCard(c, deck, if (backward) c.back else c.front, if (backward) c.front else c.back, repo.daysSinceReview(c))
            }
            _state.value = ReviewState(loading = false, queue = queue, finished = queue.isEmpty())
            startCard()
        }
        viewModelScope.launch {
            foreground.foreground.collect { fg -> if (fg) countdown?.start() else countdown?.pause() }
        }
    }

    fun typed(): Boolean = state.value.current?.deck?.mode?.let { scheduler.spec(it).typed } ?: false

    private fun startCard() {
        timerJob?.cancel()
        countdown = null
        startedAt = System.nanoTime() / 1_000_000
        val cur = state.value.current ?: return
        val limit = scheduler.spec(cur.deck.mode).timeLimitSec ?: return
        val c = PausableCountdown(limit * 1000L) { System.nanoTime() / 1_000_000 }.also { it.start() }
        countdown = c
        timerJob = viewModelScope.launch {
            while (isActive) {
                _timeLeft.value = c.remainingMs
                if (c.isFinished) { check("", timedOut = true); break }
                delay(100)
            }
        }
    }

    fun reveal() = _state.update { it.copy(revealed = true) }

    /** Hart: Eingabe prüfen. */
    fun check(input: String, timedOut: Boolean = false) {
        val st = state.value
        val cur = st.current ?: return
        if (st.result != null) return
        val ok = AnswerCheck.isCorrect(input, cur.answer, timedOut)
        finish(cur, ok, if (timedOut) ReviewVerdict.TIMEOUT else if (ok) ReviewVerdict.CORRECT else ReviewVerdict.WRONG)
    }

    /** Normal/Streng: Selbstbewertung nach dem Aufdecken. */
    fun rate(known: Boolean) {
        val cur = state.value.current ?: return
        if (state.value.result != null) return
        finish(cur, known, if (known) ReviewVerdict.CORRECT else ReviewVerdict.WRONG)
    }

    private fun finish(cur: ReviewCard, correct: Boolean, verdict: ReviewVerdict) {
        timerJob?.cancel()
        countdown?.pause()
        val answerMs = countdown?.elapsedMs ?: (System.nanoTime() / 1_000_000 - startedAt)
        val newBox = scheduler.review(cur.deck.mode, cur.card.box, correct, 0).box
        viewModelScope.launch {
            repo.review(cur.card, cur.deck.mode, correct, answerMs)
            if (settings.settings.first().sound) { if (correct) sounds.correct() else sounds.wrong() }
        }
        _state.update { it.copy(result = ReviewResult(verdict, newBox, cur.answer), revealed = true, correctCount = it.correctCount + if (correct) 1 else 0) }
    }

    fun next() {
        val st = state.value
        val nextIndex = st.index + 1
        if (nextIndex >= st.queue.size) {
            _state.update { it.copy(finished = true, result = null) }
        } else {
            _state.update { it.copy(index = nextIndex, revealed = false, result = null) }
            startCard()
        }
    }
}
