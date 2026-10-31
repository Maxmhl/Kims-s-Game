package de.max.gedaechtnis.core

/** Serie in Tagen: lückenlos erledigte Tage bis heute (oder bis gestern, solange heute noch offen ist). */
object Streak {
    fun current(completedDays: Set<Long>, today: Long): Int {
        var day = if (today in completedDays) today else today - 1
        var n = 0
        while (day in completedDays) { n++; day-- }
        return n
    }

    fun best(completedDays: Set<Long>): Int {
        var best = 0
        for (d in completedDays) {
            if ((d - 1) in completedDays) continue
            var n = 0
            var x = d
            while (x in completedDays) { n++; x++ }
            if (n > best) best = n
        }
        return best
    }

    /** Die letzten [days] Tage, ältester zuerst: true = erledigt. */
    fun lastDays(completedDays: Set<Long>, today: Long, days: Int = 7): List<Boolean> =
        (today - days + 1..today).map { it in completedDays }
}
