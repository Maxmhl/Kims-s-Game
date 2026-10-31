package de.max.gedaechtnis.data

import de.max.gedaechtnis.leitner.engine.CsvResult
import de.max.gedaechtnis.leitner.engine.Direction
import de.max.gedaechtnis.leitner.engine.LeitnerMode
import de.max.gedaechtnis.leitner.engine.Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeitnerRepository @Inject constructor(
    private val db: AppDatabase,
    private val scheduler: Scheduler,
    private val clock: Clock,
    private val training: TrainingRepository,
) {
    private val decks get() = db.decks()

    val deckList: Flow<List<Deck>> = decks.observeDecks()
    fun cards(deckId: Long): Flow<List<Card>> = decks.observeCards(deckId)
    suspend fun deck(id: Long) = decks.deck(id)
    suspend fun card(id: Long) = decks.card(id)

    suspend fun createDeck(name: String, mode: LeitnerMode): Long =
        decks.insert(Deck(name = name.trim(), mode = mode))

    suspend fun renameDeck(id: Long, name: String) {
        decks.deck(id)?.let { decks.update(it.copy(name = name.trim())) }
        training.refresh()
    }

    suspend fun deleteDeck(id: Long) { decks.delete(id); training.refresh() }

    /** Legt eine Karte an; im Modus STRICT zusätzlich den Eintrag für die Rückrichtung. */
    suspend fun addCard(deckId: Long, front: String, back: String, imageUri: String?) {
        val deck = decks.deck(deckId) ?: return
        val today = clock.today()
        val s = scheduler.newCard(today)
        val entries = mutableListOf(Card(deckId = deckId, front = front.trim(), back = back.trim(), imageUri = imageUri, box = s.box, nextDue = s.nextDue, direction = Direction.FORWARD, createdDay = today))
        if (deck.mode == LeitnerMode.STRICT) entries += entries[0].copy(direction = Direction.BACKWARD)
        decks.insertCards(entries)
        training.refresh()
    }

    /** Ändert Vorder- und Rückseite beider Richtungen. */
    suspend fun editCard(card: Card, front: String, back: String, imageUri: String?) {
        val all = decks.cards(card.deckId).filter { it.front == card.front && it.back == card.back }
        decks.updateCards(all.map { it.copy(front = front.trim(), back = back.trim(), imageUri = imageUri) })
    }

    suspend fun deleteCard(card: Card) {
        decks.deleteCardPair(card.deckId, card.front, card.back)
        training.refresh()
    }

    /** Moduswechsel: Fächer umrechnen (7 → 5: Fach 6 und 7 werden Fach 5), Rückrichtung bei STRICT ergänzen. */
    suspend fun setMode(deckId: Long, mode: LeitnerMode) {
        val deck = decks.deck(deckId) ?: return
        if (deck.mode == mode) return
        decks.update(deck.copy(mode = mode))
        val cards = decks.cards(deckId)
        val converted = cards.filter { scheduler.convertBox(it.box, mode) != it.box }
            .map { it.copy(box = scheduler.convertBox(it.box, mode)) }
        if (converted.isNotEmpty()) decks.updateCards(converted)
        if (mode == LeitnerMode.STRICT) {
            val forward = cards.filter { it.direction == Direction.FORWARD }
            val hasBack = cards.filter { it.direction == Direction.BACKWARD }.map { it.front to it.back }.toSet()
            val missing = forward.filter { (it.front to it.back) !in hasBack }
                .map { it.copy(id = 0, direction = Direction.BACKWARD, box = 1, nextDue = clock.today()) }
            if (missing.isNotEmpty()) decks.insertCards(missing)
        }
        training.refresh()
    }

    suspend fun importCsv(deckId: Long, result: CsvResult): Int {
        if (result.cards.isEmpty()) return 0
        val deck = decks.deck(deckId) ?: return 0
        val today = clock.today()
        val s = scheduler.newCard(today)
        val cards = result.cards.flatMap { (front, back) ->
            val fwd = Card(deckId = deckId, front = front, back = back, imageUri = null, box = s.box, nextDue = s.nextDue, direction = Direction.FORWARD, createdDay = today)
            if (deck.mode == LeitnerMode.STRICT) listOf(fwd, fwd.copy(direction = Direction.BACKWARD)) else listOf(fwd)
        }
        decks.insertCards(cards)
        training.refresh()
        return result.cards.size
    }

    suspend fun existingFronts(deckId: Long): Set<String> =
        decks.cards(deckId).filter { it.direction == Direction.FORWARD }.map { it.front }.toSet()

    suspend fun dueCards(deckId: Long): List<Card> = decks.dueCards(deckId, clock.today())

    /** Alle fälligen Karten aller Stapel (Tagestraining). */
    suspend fun dueCardsAll(): List<Card> {
        val today = clock.today()
        return deckList.first().flatMap { decks.dueCards(it.id, today) }
    }

    /** Tage seit der letzten Abfrage (bei neuen Karten: seit dem Anlegen). */
    suspend fun daysSinceReview(card: Card): Long {
        val last = db.reviewLogs().lastDay(card.id) ?: card.createdDay
        return (clock.today() - last).coerceAtLeast(0)
    }

    /** Bewertet eine Karte, speichert Fachwechsel und Protokoll. */
    suspend fun review(card: Card, mode: LeitnerMode, correct: Boolean, answerMs: Long) {
        val today = clock.today()
        val r = scheduler.review(mode, card.box, correct, today)
        decks.updateCard(card.copy(box = r.box, nextDue = r.nextDue))
        db.reviewLogs().insert(ReviewLog(cardId = card.id, date = today, correct = correct, answerMs = answerMs, boxBefore = card.box, boxAfter = r.box))
        training.onCardReviewed()
    }

    suspend fun allCards(): List<Card> = decks.allCards()
    val reviewLogs: Flow<List<ReviewLog>> = db.reviewLogs().observeAll()
}
