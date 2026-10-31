package de.max.gedaechtnis.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoundDao {
    @Insert suspend fun insert(result: RoundResult): Long
    @Query("SELECT * FROM round_results ORDER BY date ASC") fun observeAll(): Flow<List<RoundResult>>
    @Query("SELECT COUNT(*) FROM round_results WHERE epochDay = :day AND daily = 1") suspend fun dailyCount(day: Long): Int
}

@Dao
interface DistractionDao {
    @Query("SELECT EXISTS(SELECT 1 FROM used_distractions WHERE type = :type AND `key` = :key)")
    fun exists(type: String, key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(used: UsedDistraction)

    @Query("SELECT level FROM distraction_ranges WHERE type = :type") fun level(type: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun setLevel(range: DistractionRange)
}

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY name COLLATE NOCASE") fun observeDecks(): Flow<List<Deck>>
    @Query("SELECT * FROM decks WHERE id = :id") suspend fun deck(id: Long): Deck?
    @Insert suspend fun insert(deck: Deck): Long
    @Update suspend fun update(deck: Deck)
    @Query("DELETE FROM decks WHERE id = :id") suspend fun delete(id: Long)

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id") fun observeCards(deckId: Long): Flow<List<Card>>
    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id") suspend fun cards(deckId: Long): List<Card>
    @Query("SELECT * FROM cards WHERE id = :id") suspend fun card(id: Long): Card?
    @Insert suspend fun insertCards(cards: List<Card>): List<Long>
    @Update suspend fun updateCard(card: Card)
    @Update suspend fun updateCards(cards: List<Card>)
    @Delete suspend fun deleteCards(cards: List<Card>)
    @Query("DELETE FROM cards WHERE deckId = :deckId AND front = :front AND back = :back")
    suspend fun deleteCardPair(deckId: Long, front: String, back: String)
    @Query("SELECT * FROM cards") suspend fun allCards(): List<Card>

    /** Fällige Karten über alle Stapel; Rückrichtung zählt nur im Modus STRICT. */
    @Query(
        "SELECT COUNT(*) FROM cards c JOIN decks d ON c.deckId = d.id " +
            "WHERE c.nextDue <= :today AND (c.direction = 'FORWARD' OR d.mode = 'STRICT')",
    )
    fun observeDueCount(today: Long): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM cards c JOIN decks d ON c.deckId = d.id " +
            "WHERE c.nextDue <= :today AND (c.direction = 'FORWARD' OR d.mode = 'STRICT')",
    )
    suspend fun dueCount(today: Long): Int

    @Query("SELECT COUNT(*) FROM decks") fun observeDeckCount(): Flow<Int>

    @Query(
        "SELECT c.* FROM cards c JOIN decks d ON c.deckId = d.id " +
            "WHERE c.deckId = :deckId AND c.nextDue <= :today AND (c.direction = 'FORWARD' OR d.mode = 'STRICT') ORDER BY c.nextDue, c.id",
    )
    suspend fun dueCards(deckId: Long, today: Long): List<Card>
}

@Dao
interface ReviewLogDao {
    @Insert suspend fun insert(log: ReviewLog)
    @Query("SELECT * FROM review_logs ORDER BY id") suspend fun all(): List<ReviewLog>
    @Query("SELECT * FROM review_logs ORDER BY id") fun observeAll(): Flow<List<ReviewLog>>
    @Query("SELECT MAX(date) FROM review_logs WHERE cardId = :cardId") suspend fun lastDay(cardId: Long): Long?
}

@Dao
interface DayLogDao {
    @Query("SELECT * FROM day_logs WHERE epochDay = :day") suspend fun get(day: Long): DayLog?
    @Query("SELECT * FROM day_logs WHERE epochDay = :day") fun observe(day: Long): Flow<DayLog?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(log: DayLog)
    @Query("SELECT * FROM day_logs ORDER BY epochDay") fun observeAll(): Flow<List<DayLog>>
}
