package de.max.gedaechtnis.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RoundResult::class, UsedDistraction::class, DistractionRange::class,
        Deck::class, Card::class, ReviewLog::class, DayLog::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rounds(): RoundDao
    abstract fun distractions(): DistractionDao
    abstract fun decks(): DeckDao
    abstract fun reviewLogs(): ReviewLogDao
    abstract fun dayLogs(): DayLogDao
}
