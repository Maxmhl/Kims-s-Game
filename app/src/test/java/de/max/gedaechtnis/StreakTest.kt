package de.max.gedaechtnis

import de.max.gedaechtnis.core.Streak
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {
    @Test fun consecutiveDaysCountIncludingToday() = assertEquals(3, Streak.current(setOf(8, 9, 10), 10))
    @Test fun openTodayKeepsYesterdaysSeries() = assertEquals(2, Streak.current(setOf(8, 9), 10))
    @Test fun gapBreaksSeries() = assertEquals(0, Streak.current(setOf(7, 8), 10))
    @Test fun bestSeries() = assertEquals(4, Streak.best(setOf(1, 2, 5, 6, 7, 8, 12)))
    @Test fun lastSevenDays() = assertEquals(listOf(false, true, true, false, true, true, false), Streak.lastDays(setOf(2, 3, 5, 6), 7, 7))
}
