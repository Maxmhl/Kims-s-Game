package de.max.gedaechtnis.data

import de.max.gedaechtnis.kim.engine.distraction.DistractionStore
import de.max.gedaechtnis.kim.engine.distraction.DistractionType

/** Room-Anbindung des Ablenkungs-Speichers; Aufrufe nur abseits des Main-Threads. */
class RoomDistractionStore(private val dao: DistractionDao) : DistractionStore {
    override fun exists(type: DistractionType, key: String) = dao.exists(type.name, key)
    override fun insert(type: DistractionType, key: String, usedAt: Long) = dao.insert(UsedDistraction(type.name, key, usedAt))
    override fun widenLevel(type: DistractionType) = dao.level(type.name) ?: 0
    override fun setWidenLevel(type: DistractionType, level: Int) = dao.setLevel(DistractionRange(type.name, level))
}
