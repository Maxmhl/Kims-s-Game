package de.max.gedaechtnis.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KimRepository @Inject constructor(private val db: AppDatabase) {
    val rounds: Flow<List<RoundResult>> = db.rounds().observeAll()
    suspend fun save(result: RoundResult): Long = db.rounds().insert(result)
    val distractionStore: RoomDistractionStore by lazy { RoomDistractionStore(db.distractions()) }
}
