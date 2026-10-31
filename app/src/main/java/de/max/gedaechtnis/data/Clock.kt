package de.max.gedaechtnis.data

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Zeitquelle (austauschbar für Tests). */
interface Clock {
    fun nowMillis(): Long
    fun today(): Long
    fun dateKey(): Long
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun nowMillis() = System.currentTimeMillis()
    override fun today(): Long = LocalDate.now().toEpochDay()

    /** Datum als yyyyMMdd – Seed der täglichen Challenge. */
    override fun dateKey(): Long {
        val d = LocalDate.now()
        return d.year * 10_000L + d.monthValue * 100L + d.dayOfMonth
    }
}
