package de.max.gedaechtnis.data

import de.max.gedaechtnis.core.Streak
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

data class TodayState(
    val kimDone: Boolean,
    val cardsDue: Int,
    val hasDecks: Boolean,
    val dailyDone: Boolean,
    val streak: Int,
    val last7: List<Boolean>,
    val today: Long,
) {
    /** „x / 3 erledigt“: Kim-Runde, fällige Karten, tägliche Challenge. */
    val cardsDone: Boolean get() = cardsDue == 0
    val doneCount: Int get() = listOf(kimDone, cardsDone, dailyDone).count { it }
}

/** Heutiges Training und Serie, gemeinsam für beide Spiele. */
@Singleton
class TrainingRepository @Inject constructor(private val db: AppDatabase, private val clock: Clock) {

    private val todayTicker: Flow<Long> = flow {
        while (true) { emit(clock.today()); delay(30_000) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun todayState(): Flow<TodayState> = todayTicker.distinctUntilChanged().flatMapLatest { today ->
        combine(
            db.dayLogs().observeAll(),
            db.decks().observeDueCount(today),
            db.decks().observeDeckCount(),
        ) { logs, due, decks ->
            val log = logs.firstOrNull { it.epochDay == today }
            val completed = logs.filter { it.completed }.map { it.epochDay }.toSet()
            TodayState(
                kimDone = (log?.kimRounds ?: 0) > 0,
                cardsDue = due,
                hasDecks = decks > 0,
                dailyDone = log?.dailyDone ?: false,
                streak = Streak.current(completed, today),
                last7 = Streak.lastDays(completed, today),
                today = today,
            )
        }
    }

    val completedDays: Flow<Set<Long>> = db.dayLogs().observeAll().map { l -> l.filter { it.completed }.map { it.epochDay }.toSet() }

    suspend fun onKimRound(daily: Boolean) {
        val day = clock.today()
        val log = db.dayLogs().get(day) ?: DayLog(day)
        db.dayLogs().upsert(log.copy(kimRounds = log.kimRounds + 1, dailyDone = log.dailyDone || daily))
        refresh(day)
    }

    suspend fun onCardReviewed() {
        val day = clock.today()
        val log = db.dayLogs().get(day) ?: DayLog(day)
        db.dayLogs().upsert(log.copy(cardReviews = log.cardReviews + 1))
        refresh(day)
    }

    /** Ein Tag zählt, wenn eine Kim-Runde erledigt ist und keine Karte mehr fällig ist. */
    suspend fun refresh(day: Long = clock.today()) {
        val log = db.dayLogs().get(day) ?: DayLog(day)
        val done = log.kimRounds > 0 && db.decks().dueCount(day) == 0
        if (done != log.completed) db.dayLogs().upsert(log.copy(completed = done))
    }
}
